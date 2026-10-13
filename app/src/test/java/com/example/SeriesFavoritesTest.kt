package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Xtream dizi favorileri: katalog dizileri `iptv_items` tablosunda yok. Eskiden favori düğmesi
 *  - dizi sayfasında bölümün (veritabanında olmayan) kimliğini işaretliyordu: hiçbir şey kaydedilmiyordu,
 *  - TV katalog kartında dizi numarasını işaretliyordu: aynı numaralı kanal/film favorilere ekleniyordu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeriesFavoritesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var vm: IPTVViewModel
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().contains("get_series_info")) {
                    MockResponse().setBody(
                        """{"info":{},"episodes":{"1":[{"id":"11","title":"Bölüm 1","episode_num":"1","container_extension":"mkv"},
                            {"id":"12","title":"Bölüm 2","episode_num":"2","container_extension":"mkv"}]}}"""
                    )
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
        val host = server.url("/").toString().trimEnd('/')
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.iptvDao().insertItems(
                listOf(
                    // Dizi ile aynı numaralı canlı kanal (Xtream'de kanal ve dizi numaraları ayrı sayılır, çakışabilir).
                    IPTVItem(id = 500, playlistId = 1, name = "Kanal 500", cleanedName = "Kanal 500", logoUrl = null,
                        streamUrl = "$host/live/u/p/500.ts", category = "Ulusal", type = "LIVE"),
                    IPTVItem(id = 900, playlistId = 1, name = "Film", cleanedName = "Film", logoUrl = null,
                        streamUrl = "$host/movie/u/p/900.mkv", category = "Aksiyon", type = "MOVIE")
                )
            )
            db.iptvDao().insertXtreamSeriesCatalog(
                listOf(XtreamSeriesCatalogEntity(seriesId = 500, name = "İstila", canonicalKey = "istila",
                    coverUrl = "http://img/istila.jpg", categoryId = "Amazon"))
            )
        }
        vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        // Ekranlar gibi listeleri dinle (stateIn yalnızca dinleyici varken çalışır).
        scope.launch { vm.favoritesWithSeries.collect {} }
        scope.launch { vm.xtreamSeriesCatalog.collect {} }
        waitUntil { vm.xtreamSeriesCatalog.value.isNotEmpty() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        server.shutdown()
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun liveChannelIsFavorite() = runBlocking { db.iptvDao().getItemById(500)?.isFavorite == true }

    @Test
    fun seriesPage_favoriteButton_savesTheSeries() {
        vm.openCatalogShow(vm.xtreamSeriesCatalog.value.single())
        waitUntil { vm.liveFetchedShow.value != null }
        val episode = vm.liveFetchedShow.value!!.seasons.first().episodes.first().item
        assertTrue("bölüm kimliği veritabanında yok", episode.id < 0)

        vm.toggleFavorite(episode)
        waitUntil { vm.favoritesWithSeries.value.isNotEmpty() }

        assertEquals(setOf(500), vm.favoriteSeriesIds.value)
        assertTrue("dizi sayfasında kalp dolu", vm.isFavorite(episode, emptyList(), vm.favoriteSeriesIds.value))
        val listed = vm.favoritesWithSeries.value.single()
        assertEquals("İstila", listed.name)
        assertEquals("SERIES", listed.type)
        assertEquals("Listem'de klasördeki kapak görünür", "http://img/istila.jpg", listed.logoUrl)
        assertFalse("aynı numaralı kanal favorilere eklenmemeli", liveChannelIsFavorite())

        // Diğer bölümden de kaldırılabilir (dizi bir bütün).
        vm.toggleFavorite(vm.liveFetchedShow.value!!.seasons.first().episodes[1].item)
        waitUntil { vm.favoritesWithSeries.value.isEmpty() }
        assertEquals(emptySet<Int>(), vm.favoriteSeriesIds.value)
    }

    @Test
    fun tvCatalogCard_favorite_doesNotMarkChannelWithSameNumber() {
        val catalogCard = IPTVItem(id = 500, playlistId = 0, name = "İstila", cleanedName = "İstila",
            logoUrl = "http://img/istila.jpg", streamUrl = "", category = "Dizi", type = "SERIES")
        vm.toggleFavorite(catalogCard)
        waitUntil { vm.favoritesWithSeries.value.isNotEmpty() }

        assertFalse("aynı numaralı kanal favorilere eklenmemeli", liveChannelIsFavorite())
        assertEquals(setOf(500), vm.favoriteSeriesIds.value)
        assertTrue(vm.isFavorite(catalogCard, emptyList(), vm.favoriteSeriesIds.value))
        // Kanalın kendi kalbi etkilenmez.
        val channel = runBlocking { db.iptvDao().getItemById(500)!! }
        assertFalse(vm.isFavorite(channel, emptyList(), vm.favoriteSeriesIds.value))
    }

    /**
     * Ana sayfa "Popüler Diziler", arama, favoriler, oyuncu filmografisi: katalog dizisi açılınca klasördeki gibi dizinin
     * kendisi (aynı kapakla) açılır. Eskiden numarayla aranınca aynı numaralı kanal ya da adı benzeyen başka kayıt açılıyordu.
     */
    @Test
    fun openingCatalogSeriesCard_opensTheSeriesWithFolderPoster() {
        val card = vm.xtreamSeriesCatalog.value.single().let {
            IPTVItem(id = it.id, playlistId = 0, name = it.title, cleanedName = it.title, logoUrl = it.logoUrl,
                streamUrl = "", category = it.category, type = "SERIES", rating = it.rating)
        }
        vm.selectItem(card)
        waitUntil { vm.selectedItem.value != null && vm.liveFetchedShow.value != null }

        val opened = vm.selectedItem.value!!
        assertEquals("SERIES", opened.type)
        assertTrue("dizinin bölümü açılmalı (kanal 500 değil)", opened.id < 0)
        val show = vm.liveFetchedShow.value!!
        assertEquals(500, show.id)
        assertEquals("klasördeki kapak", "http://img/istila.jpg", show.logoUrl)
        assertEquals(2, show.seasons.single().episodes.size)
    }

    @Test
    fun seriesFavorite_survivesAppRestart() {
        vm.toggleFavorite(IPTVItem(id = 500, playlistId = 0, name = "İstila", cleanedName = "İstila",
            logoUrl = null, streamUrl = "", category = "Dizi", type = "SERIES"))
        val restarted = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        assertEquals(setOf(500), restarted.favoriteSeriesIds.value)
    }
}
