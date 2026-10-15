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
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kanal sırası: kanallar sağlayıcının sırasıyla (M3U dosyasındaki ya da Xtream kanal numarası) gösterilir.
 * Telefon/TV klasör listesi ve oynatıcının kanal paneli (CH+/CH−) aynı sırayı kullanır. Liste değiştirince sıra
 * korunur. Sırası bilinmeyen eski veride ada göre doğal sıra kullanılır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelOrderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var repo: IPTVRepository
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    // Bilerek alfabetik olmayan sağlayıcı sırası.
    private val providerOrder = listOf("TRT 1", "Kanal D", "Show TV", "Star TV", "ATV", "beIN Sports 1", "beIN Sports 2", "beIN Sports 10")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = IPTVRepository(db.iptvDao(), db)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun m3u(names: List<String>) = buildString {
        appendLine("#EXTM3U")
        names.forEach { appendLine("#EXTINF:-1 group-title=\"Ulusal\",$it"); appendLine("http://e/${it.replace(' ', '_')}.ts") }
    }

    private fun addM3u(name: String, names: List<String>): Int = runBlocking {
        var id = -1
        repo.addPlaylistAndActivate {
            id = repo.insertPlaylist(Playlist(name = name, url = "m3u_text_input")).toInt()
            repo.parseAndSaveM3U(id, m3u(names))
        }
        id
    }

    private fun listOrder() = runBlocking { repo.getItemsByTypeInListOrder("LIVE").first().map { it.name } }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            // Akışlardaki bekleme (debounce) ana iş parçacığının saatiyle ilerler.
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("koşul zamanında sağlanmadı", condition())
    }

    @Test
    fun m3u_channelsKeepTheFileOrder() {
        addM3u("Ev", providerOrder)
        assertEquals(providerOrder, listOrder())
    }

    @Test
    fun folderListAndPlayerChannelPanel_useTheSameProviderOrder() {
        addM3u("Ev", providerOrder)
        val vm = IPTVViewModel(app, repo)
        scope.launch { vm.liveGroups.collect {} }
        scope.launch { vm.allLiveChannels.collect {} }
        waitUntil { vm.liveGroups.value.firstOrNull()?.items?.size == providerOrder.size }
        waitUntil { vm.allLiveChannels.value.size == providerOrder.size }

        assertEquals("klasör listesi", providerOrder, vm.liveGroups.value.first { it.name == "Ulusal" }.items.map { it.name })
        val ring = vm.channelRingFor(vm.allLiveChannels.value.first(), vm.allLiveChannels.value)
        assertEquals("oynatıcının kanal paneli ve CH+/CH−", providerOrder, ring.map { it.name })
    }

    @Test
    fun switchingListsAwayAndBack_keepsTheOrder() {
        val ev = addM3u("Ev", providerOrder)
        addM3u("İş", listOf("Zeta", "Alfa"))
        assertEquals(listOf("Zeta", "Alfa"), listOrder())

        runBlocking { repo.switchToPlaylist(ev) }
        assertEquals(providerOrder, listOrder())
    }

    @Test
    fun legacyDataWithoutOrder_fallsBackToNaturalNameOrder() {
        // Güncellemeden önce kaydedilmiş öğeler: sıra bilgisi yok (0). Liste yenilenene kadar ada göre doğal sıra.
        val playlistId = runBlocking { repo.insertPlaylist(Playlist(name = "Eski", url = "http://a")).toInt() }
        runBlocking {
            db.iptvDao().insertItems(listOf("Kanal 10", "Kanal 2", "Kanal 1").mapIndexed { i, n ->
                IPTVItem(id = 500 + i, playlistId = playlistId, name = n, cleanedName = n, logoUrl = null,
                    streamUrl = "http://e/$i.ts", category = "Ulusal", type = "LIVE")
            })
        }
        val vm = IPTVViewModel(app, repo)
        scope.launch { vm.liveGroups.collect {} }
        waitUntil { vm.liveGroups.value.firstOrNull()?.items?.size == 3 }
        assertEquals(listOf("Kanal 1", "Kanal 2", "Kanal 10"), vm.liveGroups.value.first().items.map { it.name })
    }

    @Test
    fun xtream_usesTheProviderChannelNumber() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("get_live_categories") -> MockResponse().setBody("""[{"category_id":"1","category_name":"Spor"}]""")
                    // Yanıt sırası ve kanal kimlikleri sağlayıcının numarasıyla (num) uyuşmuyor.
                    path.contains("get_live_streams") -> MockResponse().setBody(
                        """[{"num":3,"stream_id":900,"name":"beIN Sports 3","category_id":"1"},
                           {"num":1,"stream_id":950,"name":"beIN Sports 1","category_id":"1"},
                           {"num":2,"stream_id":10,"name":"beIN Sports 2","category_id":"1"}]"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            val playlistId = runBlocking { repo.insertPlaylist(Playlist(name = "X", url = "$host/get.php?username=u&password=p")).toInt() }
            val items = runBlocking { com.example.data.api.MetadataEnricher.fetchXtreamLiveStreamsAsItems(app, host, "u", "p", playlistId) }
            assertEquals(listOf(3, 1, 2), items.map { it.sortOrder })
            runBlocking { db.iptvDao().replaceItemsOfType(playlistId, "LIVE", items) }
            assertEquals(listOf("beIN Sports 1", "beIN Sports 2", "beIN Sports 3"), listOrder())
        } finally {
            server.shutdown()
        }
    }
}

/** 11 → 12: kanal sırası sütunu eklenir; mevcut kanallar, kenardaki listeler ve favoriler korunur. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelOrderMigrationTest {
    private val testDb = "channel-order-migration.db"
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

    @Test
    fun existingChannelsAndParkedLists_areKept_withUnknownOrder() {
        val columns = "id, playlistId, name, cleanedName, logoUrl, streamUrl, category, type, rating, summary, `cast`, director, " +
            "trailerUrl, isFavorite, season, episode, releaseDate, genre, tvgId"
        helper.createDatabase(testDb, 11).apply {
            execSQL("INSERT INTO playlists (id, name, url, isXtream, loadedAt, isActive) VALUES (1, 'Ev', 'http://a', 0, 100, 1)")
            execSQL("INSERT INTO playlists (id, name, url, isXtream, loadedAt, isActive) VALUES (2, 'İş', 'http://b', 0, 50, 0)")
            execSQL("INSERT INTO iptv_items ($columns) VALUES (10, 1, 'TRT 1', 'TRT 1', NULL, 'http://e/10.ts', 'Ulusal', 'LIVE', 0.0, '', '', '', NULL, 1, NULL, NULL, '', '', NULL)")
            execSQL("INSERT INTO iptv_items_parked ($columns) VALUES (20, 2, 'beIN 1', 'beIN 1', NULL, 'http://e/20.ts', 'Spor', 'LIVE', 0.0, '', '', '', NULL, 0, NULL, NULL, '', '', NULL)")
            close()
        }

        helper.runMigrationsAndValidate(testDb, 12, true, AppDatabase.MIGRATION_11_12).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .applyMigrationPolicy()
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val dao = db.iptvDao()
                val trt = dao.getAllItemsDirect().single()
                assertEquals("TRT 1", trt.name)
                assertTrue("favori korunur", trt.isFavorite)
                assertEquals(0, trt.sortOrder)
                assertEquals(1, dao.getParkedItemCount(2))

                IPTVRepository(dao, db).switchToPlaylist(2)
                assertEquals(listOf("beIN 1"), dao.getAllItemsDirect().map { it.name })
            }
        } finally {
            db.close()
        }
    }
}
