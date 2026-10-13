package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 1.0.5: Liste yenilenirken okuma çökmemeli ve favoriler korunmalı.
 * Hata kaydı: SQLiteBlobTooBigException "Row too big to fit into CursorWindow requiredPos=6742, totalRows=1187"
 * (IPTVDao.getAllItemsFlow). Ana sayfa tüm öğeleri okurken Xtream yenilemesi filmleri önce silip sonra ekliyordu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshDuringReadTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "refresh-during-read.db"

    private fun movie(id: Int, playlistId: Int = 1, summary: String = "") = IPTVItem(
        id = id, playlistId = playlistId, name = "Film $id", cleanedName = "Film $id", logoUrl = null,
        streamUrl = "http://h/movie/u/p/$id.mkv", category = "Filmler", type = "MOVIE", summary = summary
    )

    private fun channel(id: Int, type: String = "LIVE") = IPTVItem(
        id = id, playlistId = 1, name = "Kanal $id", cleanedName = "Kanal $id", logoUrl = null,
        streamUrl = "http://h/live/u/p/$id.ts", category = "Haber", type = type
    )

    private fun show(id: Int) = XtreamSeriesCatalogEntity(seriesId = id, name = "Dizi $id", canonicalKey = "dizi $id", coverUrl = "")

    /** Gerçek uygulamadaki gibi dosyada tutulan veritabanı (okuyucu ve yazıcı ayrı bağlantılar kullanabilir). */
    private fun <T> withFileDb(block: (AppDatabase) -> T): T {
        context.deleteDatabase(dbName)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        return try {
            block(db)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun readingAllItems_whileRefreshReplacesMovies_neverFailsOrSeesHalfTable() = withFileDb { db ->
        val dao = db.iptvDao()
        // Satırlar büyük: tüm liste birden çok 2 MB'lık okuma penceresine yayılır (hata kaydındaki durum).
        val summary = "ö".repeat(1000)
        val full = (1..4000).map { movie(it, summary = summary) }
        val shrunk = (1..700).map { movie(it, summary = summary) }
        val fullCatalog = (1..2500).map { show(it).copy(plot = summary) }
        val shrunkCatalog = (1..300).map { show(it).copy(plot = summary) }
        runBlocking {
            dao.insertItems(full)
            dao.insertXtreamSeriesCatalog(fullCatalog)
        }

        val errors = ConcurrentLinkedQueue<Throwable>()
        val writing = AtomicBoolean(true)
        val reads = AtomicInteger()
        val reader = thread {
            while (writing.get()) {
                try {
                    val size = runBlocking { dao.getAllItemsDirect().size }
                    if (size != full.size && size != shrunk.size) errors += AssertionError("yarım tablo okundu: $size satır")
                    val shows = runBlocking { dao.getAllXtreamSeriesCatalogDirect().size }
                    if (shows != fullCatalog.size && shows != shrunkCatalog.size) errors += AssertionError("yarım dizi kataloğu okundu: $shows")
                    reads.incrementAndGet()
                } catch (t: Throwable) {
                    errors += t
                    if (errors.size > 200) break
                }
            }
        }
        // Ana sayfanın kullandığı akış da aynı anda dinlenir.
        val flowReader = thread {
            try {
                runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(60_000) {
                        dao.getAllItemsFlow().collect { list ->
                            if (list.size != full.size && list.size != shrunk.size) errors += AssertionError("akış yarım tablo verdi: ${list.size}")
                            if (!writing.get()) throw kotlinx.coroutines.CancellationException("bitti")
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (t: Throwable) {
                errors += t
            }
        }
        repeat(12) { i ->
            runBlocking {
                dao.replaceItemsOfType(1, "MOVIE", if (i % 2 == 0) shrunk else full)
                dao.replaceXtreamSeriesCatalog(if (i % 2 == 0) shrunkCatalog else fullCatalog)
            }
        }
        writing.set(false)
        runBlocking { dao.replaceItemsOfType(1, "MOVIE", full) } // akış son kez yayınlayıp kapansın
        reader.join(60_000)
        flowReader.join(60_000)

        assertTrue("okuma yapılmadı", reads.get() > 0)
        assertTrue(
            "okuma hataları: " + errors.map { "${it.javaClass.simpleName}: ${it.message?.take(90)}" }.distinct().take(4),
            errors.isEmpty()
        )
    }

    @Test
    fun replace_keepsFavorites_andLeavesOtherPlaylistsAlone() = withFileDb { db ->
        val dao = db.iptvDao()
        runBlocking {
            dao.insertItems(
                listOf(
                    channel(7).copy(isFavorite = true), channel(8, "RADIO").copy(isFavorite = true), channel(9),
                    movie(100).copy(isFavorite = true), movie(101), movie(500, playlistId = 2).copy(isFavorite = true)
                )
            )
            // Sağlayıcıdan gelen yeni liste favori bilgisini taşımaz; 100 numaralı filmin kimliği değişmiş ama adresi aynı.
            dao.replaceItemsOfType(1, "LIVE", listOf(channel(7), channel(8, "RADIO"), channel(9), channel(10)))
            dao.replaceItemsOfType(1, "MOVIE", listOf(movie(100).copy(id = 1100), movie(101)))

            val favorites = dao.getFavoriteItemsFlow().first().map { it.id }.toSet()
            assertEquals(setOf(7, 8, 1100, 500), favorites)
            assertEquals(listOf(7, 8, 9, 10, 101, 500, 1100), dao.getAllItemsDirect().map { it.id }.sorted())
            assertFalse(dao.getItemById(10)!!.isFavorite)
        }
    }

    @Test
    fun seriesCatalog_isReplacedInOneStep() = withFileDb { db ->
        val dao = db.iptvDao()
        runBlocking {
            dao.insertXtreamSeriesCatalog(listOf(show(1), show(2)))
            dao.replaceXtreamSeriesCatalog(listOf(show(3), show(4)))
            assertEquals(listOf(3, 4), dao.getAllXtreamSeriesCatalogDirect().map { it.seriesId }.sorted())
        }
    }

    /** Uçtan uca: Xtream hesabı eklenir, favori seçilir, liste yenilenir (açılış / Ayarlar > Yenile yolu). */
    @Test
    fun xtreamRefresh_keepsFavorites_andDoesNotDuplicatePlaylist() = withFileDb { db ->
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("get_live_categories") -> MockResponse().setBody("""[{"category_id":"1","category_name":"Haber"}]""")
                    path.contains("get_live_streams") -> MockResponse().setBody(
                        """[{"stream_id":7,"name":"Kanal 7","category_id":"1"},{"stream_id":8,"name":"Kanal 8","category_id":"1"}]"""
                    )
                    path.contains("get_vod_categories") -> MockResponse().setBody("""[{"category_id":"2","category_name":"Filmler"}]""")
                    path.contains("get_vod_streams") -> MockResponse().setBody(
                        """[{"stream_id":100,"name":"Film 100","category_id":"2","container_extension":"mkv"},{"stream_id":101,"name":"Film 101","category_id":"2"}]"""
                    )
                    path.contains("get_series_categories") -> MockResponse().setBody("""[{"category_id":"3","category_name":"Amazon"}]""")
                    path.contains("get_series") -> MockResponse().setBody("""[{"series_id":900,"name":"Dizi","category_id":"3"}]""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val dao = db.iptvDao()
            val repo = IPTVRepository(dao, db, context)
            val host = server.url("/").toString().trimEnd('/')
            runBlocking {
                assertTrue(repo.importXtreamDirectly(context, "Test", host, "u", "p") > 0)
                dao.updateFavorite(7, true)
                dao.updateFavorite(100, true)

                val playlist = dao.getAllPlaylists().single()
                assertTrue(repo.syncPlaylist(playlist) > 0)

                assertEquals(setOf(7, 100), dao.getFavoriteItemsFlow().first().map { it.id }.toSet())
                assertEquals(listOf(7, 8, 100, 101), dao.getAllItemsDirect().map { it.id }.sorted())
                assertEquals(1, dao.getAllPlaylists().size)
                assertEquals(listOf(900), dao.getAllXtreamSeriesCatalogDirect().map { it.seriesId })
            }
        } finally {
            server.shutdown()
        }
    }
}
