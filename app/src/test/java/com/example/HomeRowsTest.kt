package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.TvShow
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import com.example.data.repository.TvCatalogRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.popularSeriesRow
import com.example.util.RatingScale
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ana sayfa: "IMDb 8+" yalnızca gerçek (0–10 arası) puanları gösterir; 10 üzerinden 5 ve altındaki puanlar artık
 * ikiyle çarpılmaz, 10 ve üstü anlamsız puanlar elenir. "Popüler Diziler" TMDB'de bugün popüler olup katalogda
 * bulunan dizilerle başlar (her gün değişir), kalan yer en yüksek puanlılarla dolar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRowsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun xtreamRatings_areOnATenPointScale() {
        // Eskiden: rating "4.6" ve rating_5based varken 4,6 × 2 = 9,2 yazılıyordu.
        assertEquals(4.6, RatingScale.fromXtream("4.6", "2.3"), 0.001)
        assertEquals(5.0, RatingScale.fromXtream("5", "2.5"), 0.001)
        assertEquals(8.2, RatingScale.fromXtream("", "4.1"), 0.001)
        assertEquals(7.3, RatingScale.fromXtream("7,3", null), 0.001)
        // Anlamsız ya da yer tutucu puanlar: bilinmiyor (0).
        assertEquals(0.0, RatingScale.fromXtream("10", ""), 0.001)
        assertEquals(0.0, RatingScale.fromXtream("12.5", null), 0.001)
        assertEquals(0.0, RatingScale.fromXtream("", "5"), 0.001)
        assertEquals(0.0, RatingScale.fromXtream("null", "abc"), 0.001)
    }

    @Test
    fun xtreamMovieList_keepsRealRatings() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("get_vod_categories") -> MockResponse().setBody("""[{"category_id":"1","category_name":"Aksiyon"}]""")
                    path.contains("get_vod_streams") -> MockResponse().setBody(
                        """[{"stream_id":1,"name":"Vasat Film","category_id":"1","rating":"4.6","rating_5based":2.3},
                           {"stream_id":2,"name":"İyi Film","category_id":"1","rating":"8.4","rating_5based":4.2},
                           {"stream_id":3,"name":"Puansız","category_id":"1","rating":"10","rating_5based":0}]"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            val carrier = IPTVItem(id = 0, playlistId = 1, name = "", cleanedName = "", logoUrl = null,
                streamUrl = "$host/movie/u/p/1", category = "", type = "MOVIE")
            val items = runBlocking { com.example.data.api.MetadataEnricher.fetchXtreamVodStreamsAsItems(app, carrier) }
            assertEquals(mapOf("Vasat Film" to 4.6, "İyi Film" to 8.4, "Puansız" to 0.0), items.associate { it.name to it.rating })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun imdb8Row_showsOnlyRealHighRatings_evenForOldStoredData() {
        // Güncellemeden önce kaydedilmiş, sahte puanlı veri.
        fun movie(id: Int, name: String, rating: Double) = IPTVItem(id = id, playlistId = 1, name = name, cleanedName = name,
            logoUrl = null, streamUrl = "http://10.255.255.1/m/$id.mp4", category = "Aksiyon", type = "MOVIE", rating = rating)
        runBlocking {
            db.iptvDao().insertItems(listOf(
                movie(1, "Sahte On", 10.0), movie(2, "Sahte Oniki", 12.0), movie(3, "Başyapıt", 9.1),
                movie(4, "Güzel", 8.4), movie(5, "Vasat", 4.6)
            ))
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        scope.launch { vm.legendItems.collect {} }
        val end = System.currentTimeMillis() + 8_000
        while (vm.legendItems.value.isEmpty() && System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertEquals(listOf("Başyapıt" to 9.1, "Güzel" to 8.4), vm.legendItems.value.map { it.name to it.rating })
    }

    private fun show(id: Int, title: String, rating: Double) =
        TvShow(id = id, title = title, logoUrl = null, category = "Dizi", rating = rating, summary = "", cast = "",
            director = "", isFavorite = false, seasons = emptyList())

    @Test
    fun popularSeriesRow_startsWithTodaysPopular_thenTopRated() {
        val catalog = listOf(
            show(1, "Yüksek Puanlı A", 9.4), show(2, "Yüksek Puanlı B", 9.0), show(3, "Bugün Popüler", 7.1),
            show(4, "Sahte Puanlı", 10.0), show(5, "Dün Popüler", 6.5)
        )
        // Bugün TMDB'de popüler olan ve katalogda bulunan: 3 (5 dündü).
        val today = popularSeriesRow(catalog, popularIds = listOf(3)) { false }.map { it.name }
        assertEquals(listOf("Bugün Popüler", "Yüksek Puanlı A", "Yüksek Puanlı B"), today.take(3))
        assertTrue("sahte 10 puanlı dizi en üste çıkmaz", today.indexOf("Sahte Puanlı") > today.indexOf("Yüksek Puanlı B"))
        // Ertesi gün popülerler değişince sıra da değişir.
        val tomorrow = popularSeriesRow(catalog, popularIds = listOf(5, 2)) { false }.map { it.name }
        assertEquals(listOf("Dün Popüler", "Yüksek Puanlı B", "Yüksek Puanlı A"), tomorrow.take(3))
    }

    @Test
    fun tmdbPopularTitles_areFoundInTheCatalog_despiteTagsAndYears() {
        fun entity(id: Int, name: String) = XtreamSeriesCatalogEntity(seriesId = id, name = name,
            canonicalKey = name.lowercase(), coverUrl = "", categoryId = "Yerli")
        runBlocking {
            db.iptvDao().insertXtreamSeriesCatalog(listOf(
                entity(101, "TR: Kızılcık Şerbeti (2022)"), entity(102, "Breaking Bad [4K]"), entity(103, "Başka Dizi")
            ))
        }
        val candidates = listOf(
            com.example.data.api.GeminiAiService.TitleCandidate("Kızılcık Şerbeti", "Kızılcık Şerbeti", 2022, true),
            com.example.data.api.GeminiAiService.TitleCandidate("Breaking Bad", "Breaking Bad", 2008, true),
            com.example.data.api.GeminiAiService.TitleCandidate("Kütüphanede Olmayan", "Not In Library", 2024, true)
        )
        val found = runBlocking { TvCatalogRepository(db.iptvDao()).findTitles(candidates, limit = 40) }
            .filter { it.playlistId == 0 && it.streamUrl.isEmpty() }
            .map { it.id }
        assertEquals(listOf(101, 102), found)
    }
}
