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
import com.example.ui.screens.liveShowcaseRow
import com.example.ui.screens.localEpochDay
import com.example.ui.screens.popularMoviesRow
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
    fun topRatedRow_neverUsesProviderRatings() {
        // Sağlayıcının (sahte ya da şişirilmiş) puanları "En Yüksek Puanlılar" satırına girmez: satır yalnızca
        // TMDB'nin en yüksek puanlılarından kütüphanede olanlarla dolar (TMDB'ye ulaşılamadıysa satır görünmez).
        fun movie(id: Int, name: String, rating: Double) = IPTVItem(id = id, playlistId = 1, name = name, cleanedName = name,
            logoUrl = null, streamUrl = "http://10.255.255.1/m/$id.mp4", category = "Aksiyon", type = "MOVIE", rating = rating)
        runBlocking {
            db.iptvDao().insertItems(listOf(movie(1, "Sahte Dokuz", 9.6), movie(2, "Başyapıt", 9.1), movie(3, "Güzel", 8.4)))
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        scope.launch { vm.legendItems.collect {} }
        val end = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertEquals(emptyList<IPTVItem>(), vm.legendItems.value)
    }

    private fun show(id: Int, title: String, rating: Double) =
        TvShow(id = id, title = title, logoUrl = null, category = "Dizi", rating = rating, summary = "", cast = "",
            director = "", isFavorite = false, seasons = emptyList())

    @Test
    fun popularSeriesRow_startsWithTodaysPopular_thenTmdbTopRated() {
        val catalog = listOf(
            show(1, "TMDB Zirvesi A", 9.4), show(2, "TMDB Zirvesi B", 9.0), show(3, "Bugün Popüler", 7.1),
            show(4, "Sahte Puanlı", 10.0), show(5, "Dün Popüler", 6.5)
        )
        // Bugün TMDB'de popüler olan: 3; boş yerler TMDB'nin en yüksek puanlılarından (2, 1) dolar.
        val today = popularSeriesRow(catalog, popularIds = listOf(3), topRatedIds = listOf(2, 1)) { false }.map { it.name }
        assertEquals(listOf("Bugün Popüler", "TMDB Zirvesi B", "TMDB Zirvesi A"), today)
        assertTrue("sağlayıcının sahte puanı satıra girmez", "Sahte Puanlı" !in today)
        // Ertesi gün popülerler değişince sıra da değişir.
        val tomorrow = popularSeriesRow(catalog, popularIds = listOf(5), topRatedIds = listOf(2, 1)) { false }.map { it.name }
        assertEquals(listOf("Dün Popüler", "TMDB Zirvesi B", "TMDB Zirvesi A"), tomorrow)
        // TMDB'ye hiç ulaşılamadıysa satır boş kalmaz (sağlayıcı sırası yedek).
        assertEquals(5, popularSeriesRow(catalog, popularIds = emptyList(), topRatedIds = emptyList()) { false }.size)
    }

    private fun film(id: Int, name: String, rating: Double) = IPTVItem(id = id, playlistId = 1, name = name, cleanedName = name,
        logoUrl = null, streamUrl = "http://10.255.255.1/m/$id.mp4", category = "Film", type = "MOVIE", rating = rating)

    @Test
    fun trendMoviesRow_startsWithTodaysPopular_thenTmdbTopRated() {
        val movies = listOf(
            film(1, "TMDB Zirvesi A", 9.2), film(2, "TMDB Zirvesi B", 8.8), film(3, "Bugün Popüler", 6.9),
            film(4, "Bugün Popüler", 6.9), film(5, "Sahte Puanlı", 10.0), film(6, "Dün Popüler", 7.0)
        )
        // Aynı filmin iki kalitesi (3 ve 4) tek kez görünür; boş yerler TMDB'nin en yüksek puanlılarıyla dolar.
        val today = popularMoviesRow(movies, popularIds = listOf(3, 4), topRatedIds = listOf(1, 2)) { false }
        assertEquals(listOf(3, 1, 2), today.map { it.id })
        assertTrue("sağlayıcının sahte puanı satıra girmez", today.none { it.id == 5 })
        val tomorrow = popularMoviesRow(movies, popularIds = listOf(6), topRatedIds = listOf(1, 2)) { false }
        assertEquals(listOf(6, 1, 2), tomorrow.map { it.id })
        // TMDB'ye hiç ulaşılamadıysa satır boş kalmaz (sağlayıcı sırası yedek).
        assertTrue(popularMoviesRow(movies, emptyList(), emptyList()) { false }.isNotEmpty())
    }

    @Test
    fun tmdbPopularMovies_areFoundInTheLibrary() {
        runBlocking {
            db.iptvDao().insertItems(listOf(film(201, "TR: Oppenheimer (2023) 4K", 8.3), film(202, "Başka Film", 6.0)))
        }
        val candidates = listOf(
            com.example.data.api.GeminiAiService.TitleCandidate("Oppenheimer", "Oppenheimer", 2023, false),
            com.example.data.api.GeminiAiService.TitleCandidate("Kütüphanede Yok", "Not In Library", 2025, false)
        )
        val found = runBlocking { TvCatalogRepository(db.iptvDao()).findTitles(candidates, limit = 40) }
            .filter { it.type == "MOVIE" && it.streamUrl.isNotEmpty() }
            .map { it.id }
        assertEquals(listOf(201), found)
    }

    @Test
    fun tvTop10AndTopRated_useTmdbLists_withProviderFallbackOnlyOffline() {
        runBlocking {
            db.iptvDao().insertItems(listOf(
                film(301, "TMDB Zirvesi", 9.0), film(302, "Bugün Popüler", 6.8), film(303, "Sahte On", 10.0),
                film(304, "Sahte Dokuz", 9.6), film(305, "İyi", 8.2)
            ))
        }
        val tv = TvCatalogRepository(db.iptvDao())
        val section = com.example.data.repository.TvSpecialSection.TOP10
        val top10 = runBlocking { tv.special("MOVIE", section, popularIds = listOf(302), topRatedIds = listOf(301, 305)) }
        assertEquals(listOf(302, 301, 305), top10.map { it.item.id })
        val topRated = runBlocking {
            tv.special("MOVIE", com.example.data.repository.TvSpecialSection.TOP_RATED, topRatedIds = listOf(305, 301))
        }
        assertEquals("TMDB sırası", listOf(305, 301), topRated.map { it.item.id })
        // TMDB'ye ulaşılamadıysa (liste yok): sağlayıcı puanı, 10 ve üstü sahteler hariç.
        val offline = runBlocking { tv.special("MOVIE", com.example.data.repository.TvSpecialSection.TOP_RATED) }
        assertTrue(offline.none { it.item.id == 303 })
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

    private fun channel(id: Int, name: String, category: String) = IPTVItem(id = id, playlistId = 1, name = name,
        cleanedName = name, logoUrl = null, streamUrl = "http://10.255.255.1/live/$id.ts", category = category, type = "LIVE")

    @Test
    fun liveShowcase_isCinemaAndSeriesChannels_fromManyFolders_changingDaily() {
        // Sağlayıcı sırası: ilk klasör haber kanallarıyla dolu (eskiden bölüm hep bunlardı).
        val news = (1..30).map { channel(it, "Haber $it", "Ulusal Haber") }
        val cinema = listOf(
            channel(101, "HD: Sinema TV", "Sinema"), channel(102, "FHD: Sinema TV", "Sinema"),
            channel(103, "Sinema Aksiyon", "Sinema"), channel(104, "Yeşilçam TV", "Sinema"),
            channel(111, "FilmBox Premium", "FİLM KANALLARI"), channel(112, "Movie Smart", "FİLM KANALLARI"),
            channel(113, "beIN Movies Action", "FİLM KANALLARI"), channel(121, "TV+ Dizi", "Dizi Kanalları"),
            channel(122, "Dizi Max", "Dizi Kanalları"), channel(123, "Kanal D Dizi", "Dizi Kanalları"),
            channel(124, "Star Dizi", "Dizi Kanalları"), channel(131, "Cine5", "Ulusal")
        )
        val notCinema = listOf(channel(201, "Minika", "Çizgi Film"), channel(202, "Cartoon Network", "Çocuk"),
            channel(203, "Sinema XXX", "Yetişkin"))
        val all = news + cinema + notCinema
        val adult: (IPTVItem) -> Boolean = { it.category == "Yetişkin" }

        val today = liveShowcaseRow(all, day = 20_000, isAdult = adult)
        assertTrue(today.cinemaFocused)
        assertEquals(10, today.items.size)
        assertTrue("yalnızca sinema ve dizi kanalları", today.items.all { it.id in 101..131 })
        assertTrue("aynı kanalın HD ve FHD'si birlikte çıkmaz", !(today.items.any { it.id == 101 } && today.items.any { it.id == 102 }))
        assertTrue("ilk dört kanal farklı klasörlerden", today.items.take(4).map { it.category }.distinct().size == 4)
        assertEquals("aynı gün aynı seçki", today.items.map { it.id }, liveShowcaseRow(all, day = 20_000, isAdult = adult).items.map { it.id })
        val days = (20_001..20_005).map { d -> liveShowcaseRow(all, day = d.toLong(), isAdult = adult).items.map { it.id } }
        assertTrue("seçki günden güne değişir", days.any { it != today.items.map { i -> i.id } })

        // Sinema/dizi kanalı olmayan listede bölüm yine dolar, ama farklı klasörlerden.
        val plain = news + (1..5).map { channel(300 + it, "Müzik $it", "Müzik") }
        val fallback = liveShowcaseRow(plain, day = 20_000, isAdult = { false })
        assertTrue(!fallback.cinemaFocused)
        assertEquals(10, fallback.items.size)
        assertEquals(setOf("Ulusal Haber", "Müzik"), fallback.items.take(2).map { it.category }.toSet())
    }

    @Test
    fun localEpochDay_changesAtLocalMidnight() {
        val tz = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Istanbul"))
            // 15 Ekim 2026 23:30 ve 16 Ekim 00:30 (İstanbul) farklı günler.
            val before = java.time.ZonedDateTime.of(2026, 10, 15, 23, 30, 0, 0, java.time.ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()
            assertEquals(1L, localEpochDay(before + 3_600_000) - localEpochDay(before))
        } finally {
            java.util.TimeZone.setDefault(tz)
        }
    }

    @Test
    fun tmdbRatings_areMatchedToTheLibrary_andStored() {
        runBlocking {
            db.iptvDao().insertItems(listOf(film(401, "TR: The Dark Knight (2008) 4K", 9.6), film(402, "Başka Film", 6.0)))
        }
        val rated = listOf(
            com.example.data.repository.TMDBRepository.RatedTitle(
                com.example.data.api.GeminiAiService.TitleCandidate("Kara Şövalye", "The Dark Knight", 2008, false), 8.5),
            com.example.data.repository.TMDBRepository.RatedTitle(
                com.example.data.api.GeminiAiService.TitleCandidate("Kütüphanede Yok", "Not In Library", 2025, false), 8.9)
        )
        val found = runBlocking { TvCatalogRepository(db.iptvDao()).findRatedTitles(rated) }
        assertEquals(listOf(401 to 8.5), found.map { it.first.id to it.second })

        val key = com.example.util.TmdbRatings.key(found.single().first)
        com.example.util.TmdbRatings.save(app, mapOf(key to 8.5))
        assertEquals(8.5, com.example.util.TmdbRatings.load(app)[key]!!, 0.001)
    }

    @Test
    fun ratingKeys_keepCatalogSeriesApartFromChannels() {
        val channel = IPTVItem(id = 7, playlistId = 1, name = "K", cleanedName = "K", logoUrl = null, streamUrl = "http://h/7.ts",
            category = "", type = "LIVE")
        val catalogShow = IPTVItem(id = 7, playlistId = 0, name = "D", cleanedName = "D", logoUrl = null, streamUrl = "",
            category = "", type = "SERIES")
        assertTrue(com.example.util.TmdbRatings.key(channel) != com.example.util.TmdbRatings.key(catalogShow))
        val fromTmdb = catalogShow.copy(id = -55)
        assertTrue(com.example.util.TmdbRatings.isFromTmdb(fromTmdb))
        assertTrue(!com.example.util.TmdbRatings.isFromTmdb(channel))
    }
}
