package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.db.AppDatabase
import com.example.data.db.AppDatabase.Companion.applyMigrationPolicy
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Çoklu liste: aynı anda tek liste ekranda görünür, listeler arasında ağsız ve kayıpsız geçilir.
 * Her listenin öğeleri, favorileri ve dizi kataloğu kendi içinde korunur; iki liste hiçbir zaman karışmaz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiPlaylistTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var repo: IPTVRepository
    private val dao get() = db.iptvDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = IPTVRepository(db.iptvDao(), db)
    }

    @After
    fun tearDown() = db.close()

    private fun m3u(vararg channels: String) = buildString {
        appendLine("#EXTM3U")
        channels.forEach { name ->
            appendLine("#EXTINF:-1 group-title=\"Ulusal\",$name")
            appendLine("http://example.com/${name.replace(' ', '_')}.ts")
        }
    }

    /** Ayarlardaki "liste ekle" ile aynı yol: liste kaydı + içerik, yeni liste ekrana gelir. */
    private suspend fun addM3u(name: String, vararg channels: String): Int {
        var id = -1
        val count = repo.addPlaylistAndActivate {
            id = repo.insertPlaylist(Playlist(name = name, url = "m3u_text_input")).toInt()
            repo.parseAndSaveM3U(id, m3u(*channels))
        }
        assertTrue(count > 0)
        return id
    }

    private suspend fun visibleNames() = dao.getAllItemsDirect().map { it.name }.sorted()

    @Test
    fun addingASecondList_showsOnlyTheNewList_andTheFirstListIsKept() = runBlocking {
        val ev = addM3u("Ev", "TRT 1", "ATV")
        val is_ = addM3u("İş", "beIN 1")

        assertEquals(listOf("beIN 1"), visibleNames())
        assertEquals(is_, dao.getActivePlaylist()?.id)
        assertEquals("ilk listenin kanalları silinmez, kenarda bekler", 2, dao.getParkedItemCount(ev))
        assertEquals(2, dao.getAllPlaylists().size)
    }

    @Test
    fun switchingBack_restoresChannelsAndFavorites_withoutNetwork() = runBlocking {
        val ev = addM3u("Ev", "TRT 1", "ATV")
        val trt = dao.getAllItemsDirect().first { it.name == "TRT 1" }
        dao.updateFavorite(trt.id, true)
        val is_ = addM3u("İş", "beIN 1")

        assertTrue(repo.switchToPlaylist(ev))
        assertEquals(listOf("ATV", "TRT 1"), visibleNames())
        assertEquals(listOf("TRT 1"), dao.getAllItemsDirect().filter { it.isFavorite }.map { it.name })
        assertEquals(ev, dao.getActivePlaylist()?.id)
        assertEquals(1, dao.getParkedItemCount(is_))
        assertEquals(0, dao.getParkedItemCount(ev))

        assertFalse("zaten açık listeye geçiş bir şey değiştirmez", repo.switchToPlaylist(ev))
    }

    @Test
    fun favoritesStayPerList() = runBlocking {
        addM3u("Ev", "TRT 1")
        val is_ = addM3u("İş", "beIN 1")
        dao.updateFavorite(dao.getAllItemsDirect().single().id, true)
        val ev = dao.getAllPlaylists().first { it.name == "Ev" }.id

        repo.switchToPlaylist(ev)
        assertTrue("Ev listesinde favori yok", dao.getAllItemsDirect().filter { it.isFavorite }.isEmpty())
        repo.switchToPlaylist(is_)
        assertEquals(listOf("beIN 1"), dao.getAllItemsDirect().filter { it.isFavorite }.map { it.name })
    }

    @Test
    fun seriesCatalogMovesWithItsList() = runBlocking {
        val xtream = addM3u("Xtream", "TRT 1")
        dao.replaceXtreamSeriesCatalog(listOf(XtreamSeriesCatalogEntity(seriesId = 5, name = "Dizi", canonicalKey = "dizi", coverUrl = "")))
        val m3uOnly = addM3u("Düz", "ATV")
        assertEquals("diğer listenin dizileri görünmez", 0, dao.seriesCatalogCount())

        repo.switchToPlaylist(xtream)
        assertEquals(1, dao.seriesCatalogCount())
        repo.switchToPlaylist(m3uOnly)
        assertEquals(0, dao.seriesCatalogCount())
    }

    @Test
    fun manySwitchesAtTheSameTime_neverMixOrLoseChannels() = runBlocking {
        // Ör. kullanıcı "Aç"a basarken ücretsiz liste kuralı da aynı anda 1. listeye dönmek isterse.
        val ev = addM3u("Ev", "TRT 1", "ATV")
        val is_ = addM3u("İş", "beIN 1")
        repeat(5) { round ->
            (1..20).map { i ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
                    repo.switchToPlaylist(if ((i + round) % 3 == 0) is_ else ev)
                }
            }.awaitAll()
            val active = dao.getActivePlaylist()!!.id
            val expected = if (active == ev) listOf("ATV", "TRT 1") else listOf("beIN 1")
            assertEquals("ekranda yalnızca açık liste olur", expected, visibleNames())
            assertEquals(3, dao.getAllItemsDirect().size + dao.getParkedItemCount(ev) + dao.getParkedItemCount(is_))
        }
    }

    @Test
    fun deletingTheActiveList_opensTheNextList() = runBlocking {
        val ev = addM3u("Ev", "TRT 1")
        val is_ = addM3u("İş", "beIN 1")
        repo.deletePlaylist(is_)

        assertEquals(ev, dao.getActivePlaylist()?.id)
        assertEquals(listOf("TRT 1"), visibleNames())
        assertEquals(listOf("Ev"), dao.getAllPlaylists().map { it.name })
    }

    @Test
    fun deletingAParkedList_doesNotTouchTheActiveList() = runBlocking {
        val ev = addM3u("Ev", "TRT 1")
        val is_ = addM3u("İş", "beIN 1")
        repo.deletePlaylist(ev)

        assertEquals(is_, dao.getActivePlaylist()?.id)
        assertEquals(listOf("beIN 1"), visibleNames())
        assertEquals(0, dao.getParkedItemCount(ev))
    }

    @Test
    fun deletingTheOnlyList_leavesNothingBehind() = runBlocking {
        val ev = addM3u("Ev", "TRT 1")
        repo.deletePlaylist(ev)
        assertNull(dao.getActivePlaylist())
        assertTrue(dao.getAllItemsDirect().isEmpty())
    }

    @Test
    fun failedAdd_restoresThePreviousList() = runBlocking {
        val ev = addM3u("Ev", "TRT 1")

        // Boş liste: yeni kayıt silinir, eski liste geri gelir.
        val count = repo.addPlaylistAndActivate {
            val id = repo.insertPlaylist(Playlist(name = "Boş", url = "m3u_text_input")).toInt()
            repo.parseAndSaveM3U(id, "#EXTM3U\n")
        }
        assertEquals(0, count)
        assertEquals(ev, dao.getActivePlaylist()?.id)
        assertEquals(listOf("TRT 1"), visibleNames())
        assertEquals(listOf("Ev"), dao.getAllPlaylists().map { it.name })

        // Hata: aynı şekilde geri alınır ve hata çağırana iletilir.
        try {
            repo.addPlaylistAndActivate {
                val id = repo.insertPlaylist(Playlist(name = "Bozuk", url = "m3u_text_input")).toInt()
                repo.parseAndSaveM3U(id, m3u("X"))
                throw IOException("bağlantı koptu")
            }
            fail("hata iletilmeli")
        } catch (e: IOException) {
            // beklenen
        }
        assertEquals(ev, dao.getActivePlaylist()?.id)
        assertEquals(listOf("TRT 1"), visibleNames())
        assertEquals(listOf("Ev"), dao.getAllPlaylists().map { it.name })
    }

    @Test
    fun refreshingAParkedList_bringsItToTheScreenFirst() = runBlocking {
        val ev = addM3u("Ev", "TRT 1")
        addM3u("İş", "beIN 1")
        // Kenardaki listeye yazmak (ör. yenileme) önce onu ekrana getirir; iki liste asla karışmaz.
        repo.parseAndSaveM3U(ev, m3u("TRT 1", "TRT 2"))
        assertEquals(ev, dao.getActivePlaylist()?.id)
        assertEquals(listOf("TRT 1", "TRT 2"), visibleNames())
    }

    @Test
    fun onlyTheActiveListIsRefreshedInTheBackground() = runBlocking {
        addM3u("Ev", "TRT 1")
        val is_ = addM3u("İş", "beIN 1")
        assertEquals(is_, repo.playlistToRefresh()?.id)
    }

    @Test
    fun legacyMergedData_isNormalizedToTheNewestList() = runBlocking {
        // Eski sürüm: iki liste, öğeler karışık, hiçbiri işaretli değil.
        val old = dao.insertPlaylist(Playlist(name = "Eski", url = "a", loadedAt = 1)).toInt()
        val new = dao.insertPlaylist(Playlist(name = "Yeni", url = "b", loadedAt = 2)).toInt()
        dao.insertItems(listOf(item(1, old, "Eski kanal"), item(2, new, "Yeni kanal")))

        repo.ensureSingleActivePlaylist()
        assertEquals(new, dao.getActivePlaylist()?.id)
        assertEquals(listOf("Yeni kanal"), visibleNames())
        assertEquals(1, dao.getParkedItemCount(old))

        repo.ensureSingleActivePlaylist() // ikinci çağrı bir şey değiştirmez
        assertEquals(listOf("Yeni kanal"), visibleNames())
    }

    @Test
    fun legacyData_keepsTheListWhoseChannelsAreOnScreen() = runBlocking {
        // En yeni kayıtta hiç kanal yok (ör. yarım kalmış ekleme); ekrandaki kanallar eski listeye ait.
        val withChannels = dao.insertPlaylist(Playlist(name = "Ev", url = "a", loadedAt = 1)).toInt()
        dao.insertPlaylist(Playlist(name = "Boş", url = "b", loadedAt = 2))
        dao.insertItems(listOf(item(1, withChannels, "TRT 1")))

        repo.ensureSingleActivePlaylist()
        assertEquals(withChannels, dao.getActivePlaylist()?.id)
        assertEquals(listOf("TRT 1"), visibleNames())
    }

    private fun item(id: Int, playlist: Int, name: String) = IPTVItem(
        id = id, playlistId = playlist, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://example.com/$id.ts", category = "Ulusal", type = "LIVE"
    )
}

/** 10 → 11 göçü: kullanıcının hiçbir listesi, kanalı, favorisi ya da izleme geçmişi kaybolmaz. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiPlaylistMigrationTest {
    private val testDb = "multi-playlist-migration.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        context.deleteDatabase(testDb)
    }

    private fun insertItem(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Int, playlist: Int, name: String, favorite: Boolean) {
        db.execSQL(
            "INSERT INTO iptv_items (id, playlistId, name, cleanedName, logoUrl, streamUrl, category, type, rating, " +
                "summary, `cast`, director, trailerUrl, isFavorite, season, episode, releaseDate, genre, tvgId) " +
                "VALUES ($id, $playlist, '$name', '$name', NULL, 'http://example.com/$id.ts', 'Ulusal', 'LIVE', 0.0, " +
                "'', '', '', NULL, ${if (favorite) 1 else 0}, NULL, NULL, '', '', NULL)"
        )
    }

    @Test
    fun twoMergedLists_becomeOneActiveAndOneParked_withNothingLost() {
        helper.createDatabase(testDb, 10).apply {
            execSQL("INSERT INTO playlists (id, name, url, isXtream, loadedAt) VALUES (1, 'Eski', 'http://a', 0, 100)")
            execSQL("INSERT INTO playlists (id, name, url, isXtream, loadedAt) VALUES (2, 'Yeni', 'http://b', 0, 200)")
            insertItem(this, 10, 1, "Eski 1", favorite = true)
            insertItem(this, 11, 1, "Eski 2", favorite = false)
            insertItem(this, 20, 2, "Yeni 1", favorite = true)
            insertItem(this, 99, 77, "Sahipsiz", favorite = false)
            execSQL(
                "INSERT INTO continue_watching (itemId, itemName, itemType, itemLogo, streamUrl, category, progressSeconds, " +
                    "totalSeconds, lastPlayedAt) VALUES (10, 'Eski 1', 'LIVE', NULL, 'http://example.com/10.ts', 'Ulusal', 60, 0, 5)"
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 11, true, AppDatabase.MIGRATION_10_11).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .applyMigrationPolicy()
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = db.iptvDao()
                assertEquals("en son yüklenen liste aktif", 2, dao.getActivePlaylist()?.id)
                assertEquals(listOf("Sahipsiz", "Yeni 1"), dao.getAllItemsDirect().map { it.name }.sorted())
                assertEquals(2, dao.getParkedItemCount(1))
                assertEquals(1, dao.getAllContinueWatchingOnce().size)

                IPTVRepository(dao, db).switchToPlaylist(1)
                val restored = dao.getAllItemsDirect().filter { it.playlistId == 1 }
                assertEquals(listOf("Eski 1", "Eski 2"), restored.map { it.name }.sorted())
                assertTrue("favori korunur", restored.first { it.name == "Eski 1" }.isFavorite)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun singleListUser_seesNoChange() {
        helper.createDatabase(testDb, 10).apply {
            execSQL("INSERT INTO playlists (id, name, url, isXtream, loadedAt) VALUES (7, 'Ev', 'http://a', 0, 1)")
            insertItem(this, 42, 7, "TRT 1", favorite = true)
            close()
        }
        helper.runMigrationsAndValidate(testDb, 11, true, AppDatabase.MIGRATION_10_11).close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .applyMigrationPolicy().allowMainThreadQueries().build()
        try {
            runBlocking {
                assertEquals(7, db.iptvDao().getActivePlaylist()?.id)
                val items = db.iptvDao().getAllItemsDirect()
                assertEquals(listOf("TRT 1"), items.map { it.name })
                assertTrue(items.single().isFavorite)
            }
        } finally {
            db.close()
        }
    }
}
