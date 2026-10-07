package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.ui.screens.WatchedFractions
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 1.0.3: Xtream yenilemesi, oynatıcıdaki bölüm paneli ilerlemesi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XtreamRefreshAndTrayTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun <T> withRepo(block: suspend (IPTVRepository, AppDatabase) -> T): T {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        return try {
            runBlocking { block(IPTVRepository(db.iptvDao()), db) }
        } finally {
            db.close()
        }
    }

    @Test
    fun xtreamPlaylist_isRefreshedThroughPlayerApi_notGetPhp() = withRepo { repo, _ ->
        val saved = Playlist(
            name = "tivigo", url = "http://example.com:8080/get.php?username=u1&password=p%40ss&type=m3u_plus&output=ts",
            isXtream = true, xtreamUsername = "u1", xtreamPassword = "p@ss"
        )
        assertEquals(Triple("http://example.com:8080", "u1", "p@ss"), repo.xtreamCredentialsOf(saved))

        // Eski kayıtlarda bayrak yoksa adresten anlaşılır.
        val legacy = Playlist(name = "x", url = "http://example.com/get.php?username=a&password=b%26c")
        assertEquals(Triple("http://example.com", "a", "b&c"), repo.xtreamCredentialsOf(legacy))

        // Düz M3U listeleri eski yoldan yenilenir.
        assertNull(repo.xtreamCredentialsOf(Playlist(name = "m3u", url = "https://example.com/list.m3u")))
    }

    @Test
    fun playlistById_findsRecordForRefresh() = withRepo { _, db ->
        val id = db.iptvDao().insertPlaylist(Playlist(name = "a", url = "http://example.com/get.php?username=a&password=b")).toInt()
        assertEquals("a", db.iptvDao().getPlaylistById(id)?.name)
        assertNull(db.iptvDao().getPlaylistById(id + 99))
    }

    @Test
    fun trayProgress_matchesByIdOrStreamUrl() {
        fun cw(itemId: Int, url: String, progress: Long, total: Long) = ContinueWatching(
            itemId = itemId, itemName = "İstila S01E01", itemType = "SERIES", itemLogo = null,
            streamUrl = url, category = "Amazon", progressSeconds = progress, totalSeconds = total
        )
        val fractions = WatchedFractions(
            listOf(
                cw(-101, "http://h/series/u/p/101.mkv", 1500, 3000),
                cw(-102, "http://h/series/u/p/102.mkv", 0, 3000),
                cw(-103, "http://h/series/u/p/103.mkv", 900, 0)
            )
        )
        fun ep(id: Int, url: String) = IPTVItem(id = id, playlistId = 1, name = "İstila", cleanedName = "İstila",
            logoUrl = null, streamUrl = url, category = "Amazon", type = "SERIES")

        assertEquals(0.5f, fractions.of(ep(-101, "x"))!!, 0.001f)
        // Liste yenilendiyse kimlik değişmiş olabilir: adresle eşleşir.
        assertEquals(0.5f, fractions.of(ep(555, "http://h/series/u/p/101.mkv"))!!, 0.001f)
        // Hiç izlenmemiş ya da süresi bilinmeyen bölümde çubuk yok.
        assertNull(fractions.of(ep(-102, "http://h/series/u/p/102.mkv")))
        assertNull(fractions.of(ep(-103, "http://h/series/u/p/103.mkv")))
    }

    @Test
    fun xtreamRequest_retriesWithOtherIdentity_on403() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (request.getHeader("User-Agent") != com.example.util.AppUserAgent.app) return MockResponse().setResponseCode(403)
                return when {
                    path.contains("get_live_categories") -> MockResponse().setBody("""[{"category_id":"1","category_name":"Haber"}]""")
                    path.contains("get_live_streams") -> MockResponse().setBody(
                        """[{"stream_id":7,"name":"Kanal 7","category_id":"1","epg_channel_id":"k7.tr"}]"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            val items = runBlocking {
                com.example.data.api.MetadataEnricher.fetchXtreamLiveStreamsAsItems(context, host, "u", "p", 1)
            }
            assertEquals(1, items.size)
            assertEquals("Haber", items[0].category)
            assertEquals("k7.tr", items[0].tvgId)
        } finally {
            server.shutdown()
        }
    }
}
