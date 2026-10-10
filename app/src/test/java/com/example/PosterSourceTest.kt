package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MetadataEnricher
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.DetailScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Collections

/**
 * Posterler doğru yerden gelir: liste kartındaki poster detayda ve listede değişmez; dizi bilgisi başka bir diziden
 * alınmaz; bilgi yoksa uydurma tarih/tür/oyuncu gösterilmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PosterSourceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer
    private lateinit var host: String
    private val requests = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests.add(path)
                return when {
                    path.contains("get_vod_info") -> MockResponse().setBody(
                        """{"info":{"plot":"Filmin konusu","movie_image":"http://img/poster.jpg","cover_big":"http://img/poster_big.jpg",
                            "backdrop_path":["http://img/backdrop.jpg"],"cast":"Ada Yıldız"}}"""
                    )
                    // Bölüm numarası (11) başka bir dizinin numarası: yanlış istek atılırsa o dizinin bilgisi gelir.
                    path.contains("get_series_info") && path.contains("series_id=11") -> MockResponse().setBody(
                        """{"info":{"plot":"Başka dizinin konusu","cover":"http://img/other.jpg","cast":"Yanlış Oyuncu"},"episodes":{}}"""
                    )
                    path.contains("get_series_info") && path.contains("series_id=500") -> MockResponse().setBody(
                        """{"info":{},"episodes":{"1":[{"id":"11","title":"Bölüm 1","episode_num":"1","container_extension":"mkv"}]}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        host = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
        return condition()
    }

    private fun movie(logo: String?) = IPTVItem(
        id = 79900, playlistId = 1, name = "Film", cleanedName = "Film", logoUrl = logo,
        streamUrl = "$host/movie/u/p/79900.mkv", category = "Aksiyon", type = "MOVIE"
    )

    private fun <T> withVm(items: List<IPTVItem>, block: (IPTVViewModel, AppDatabase) -> T): T {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.iptvDao().insertItems(items)
            db.iptvDao().insertXtreamSeriesCatalog(
                listOf(XtreamSeriesCatalogEntity(seriesId = 500, name = "İstila", canonicalKey = "istila",
                    coverUrl = "http://img/istila.jpg", categoryId = "Amazon"))
            )
        }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return try {
            val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
            scope.launch { vm.xtreamSeriesCatalog.collect {} }
            waitUntil { vm.xtreamSeriesCatalog.value.isNotEmpty() }
            block(vm, db)
        } finally {
            scope.cancel()
            db.close()
        }
    }

    /** Film detayı: listedeki poster yerini sahne görseline (backdrop) bırakmaz, veritabanına da yazılmaz. */
    @Test
    fun movieDetail_keepsTheListPoster() = withVm(listOf(movie("http://img/card.jpg"))) { vm, db ->
        vm.selectItem(movie("http://img/card.jpg"))
        assertTrue("konu yüklenmeli", waitUntil { vm.selectedItem.value?.summary == "Filmin konusu" })
        assertEquals("http://img/card.jpg", vm.selectedItem.value?.logoUrl)
        // Sahne görseli yalnızca detayın üst görseli için ayrıca verilir.
        assertEquals(79900 to "http://img/backdrop.jpg", vm.detailBackdrop.value)

        vm.flushPendingMetadata()
        waitUntil(2_000) { runBlocking { db.iptvDao().getItemById(79900)?.summary } == "Filmin konusu" }
        assertEquals("listedeki poster aynı kalmalı", "http://img/card.jpg", runBlocking { db.iptvDao().getItemById(79900)?.logoUrl })
    }

    /** Posteri olmayan filmde sağlayıcının posteri kullanılır, yatay sahne görseli değil. */
    @Test
    fun movieWithoutPoster_getsProviderPoster_notBackdrop() = withVm(listOf(movie(null))) { vm, _ ->
        vm.selectItem(movie(null))
        assertTrue(waitUntil { vm.selectedItem.value?.summary == "Filmin konusu" })
        assertEquals("http://img/poster_big.jpg", vm.selectedItem.value?.logoUrl)
    }

    /** Dizi bölümünün adresindeki numara bölüm numarasıdır: onunla dizi bilgisi istenirse başka dizinin bilgisi gelir. */
    @Test
    fun seriesEpisode_isNeverLookedUpAsAnotherSeries() {
        val episode = IPTVItem(id = -11, playlistId = 1, name = "İstila - Bölüm 1", cleanedName = "İstila",
            logoUrl = "http://img/istila.jpg", streamUrl = "$host/series/u/p/11.mkv", category = "Amazon", type = "SERIES")
        val meta = runBlocking { MetadataEnricher.fetchXtreamMetadata(app, episode) }
        assertNull("başka dizinin bilgisi dönmemeli", meta)
        assertFalse("bölüm numarasıyla dizi bilgisi istenmemeli", requests.any { it.contains("series_id=11") })
    }

    /** Dizi sayfası: açılan bölümün bilgisi başka bir diziyle doldurulmaz (poster, konu, oyuncular). */
    @Test
    fun seriesPage_isNotFilledWithAnotherSeries() = withVm(listOf(movie("http://img/card.jpg"))) { vm, _ ->
        vm.openCatalogShow(vm.xtreamSeriesCatalog.value.single())
        assertTrue(waitUntil { vm.selectedItem.value != null })
        // Eski kodda yanlış istek hemen atılıyordu; atılmadığını görmek için bir süre beklenir.
        waitUntil(3_000) { requests.any { it.contains("series_id=11") } }
        assertFalse("bölüm numarasıyla dizi bilgisi istenmemeli", requests.any { it.contains("series_id=11") })
        val shown = vm.selectedItem.value!!
        assertFalse(shown.cast.contains("Yanlış Oyuncu"))
        assertFalse(shown.summary.contains("Başka dizinin"))
        assertEquals("http://img/istila.jpg", shown.logoUrl)
    }

    /** Dizi detayının üst görseli: kataloğda yatay sahne görseli varsa o verilir, yoksa null (poster büyütülmez). */
    @Test
    fun seriesHeroBackdrop_comesFromCatalog_orIsNull() = withVm(emptyList()) { vm, db ->
        runBlocking {
            db.iptvDao().insertXtreamSeriesCatalog(
                listOf(XtreamSeriesCatalogEntity(seriesId = 600, name = "Away", canonicalKey = "away",
                    coverUrl = "http://img/away_poster.jpg", backdropUrl = "http://img/away_wide.jpg", categoryId = "Netflix"))
            )
            assertEquals("http://img/away_wide.jpg", vm.catalogBackdropOf(600, "Away"))
            // Sahne görseli olmayan dizi (500: yalnızca poster) için null: üst görsel posterden yumuşatılır.
            assertNull(vm.catalogBackdropOf(500, "İstila"))
        }
    }

    /** Detay ekranındaki sonsuz arka plan animasyonu test saatini kilitlemesin: saat elle ilerletilir. */
    private fun showDetail(item: IPTVItem) {
        compose.mainClock.autoAdvance = false
        compose.setContent { DetailScreen(item = item, onDismiss = {}, onPlay = {}, onToggleFavorite = {}) }
        repeat(20) {
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
    }

    /** Eski sürümlerin bölüm numarasıyla bulup kaydettiği (başka dizilere ait) kapaklar bir kez temizlenir. */
    @Test
    fun seriesCoversSavedByOldVersions_areDroppedOnce() = withVm(emptyList()) { vm, db ->
        runBlocking {
            db.iptvDao().upsertSeriesCover(com.example.data.model.SeriesCoverEntity("istila", "http://img/other.jpg"))
            vm.dropUnverifiedSeriesCoversOnce()
            assertTrue("eski kapaklar silinmeli", db.iptvDao().getAllSeriesCovers().isEmpty())
            // Yalnızca bir kez: sonradan sağlayıcı listesinden kaydedilen kapaklar kalır.
            db.iptvDao().upsertSeriesCover(com.example.data.model.SeriesCoverEntity("istila", "http://img/istila.jpg"))
            vm.dropUnverifiedSeriesCoversOnce()
            assertEquals(listOf("http://img/istila.jpg"), db.iptvDao().getAllSeriesCovers().map { it.coverUrl })
        }
    }

    /** Bilgi yoksa uydurma tarih, tür ve oyuncu gösterilmez. */
    @Test
    fun detailWithoutInfo_showsNoMadeUpDateGenreOrCast() {
        val bare = IPTVItem(id = 79901, playlistId = 1, name = "Bilgisiz Film", cleanedName = "Bilgisiz Film", logoUrl = null,
            streamUrl = "http://h/x.mp4", category = "", type = "MOVIE")
        showDetail(bare)
        compose.onNodeWithText("2026-04-29").assertDoesNotExist()
        compose.onNodeWithText("Komedi, Dram").assertDoesNotExist()
        compose.onNodeWithText("Meryl Streep, Anne H...").assertDoesNotExist()
        compose.onNodeWithText("Bilgisiz Film").assertExists()
    }

    /** Dizi detayında da aynı: bilgi yoksa uydurma değer yok. */
    @Test
    fun seriesDetailWithoutInfo_showsNoMadeUpDateGenreOrCast() {
        val bare = IPTVItem(id = 79902, playlistId = 1, name = "Bilgisiz Dizi S01E01", cleanedName = "Bilgisiz Dizi S01E01", logoUrl = null,
            streamUrl = "http://h/s.mp4", category = "", type = "SERIES", season = 1, episode = 1)
        showDetail(bare)
        compose.onNodeWithText("2026-04-29").assertDoesNotExist()
        compose.onNodeWithText("Komedi, Dram").assertDoesNotExist()
        compose.onNodeWithText("Meryl Streep, Anne H...").assertDoesNotExist()
    }
}
