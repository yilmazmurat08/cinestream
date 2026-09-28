package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.GeminiAiService
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.repository.IPTVRepository
import com.example.data.repository.TvCatalogRepository
import com.example.ui.IPTVViewModel
import com.example.ui.components.trailerHtml
import com.example.ui.tv.TvDetailScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Kullanıcının bildirdiği sorunların düzeltmeleri:
 * - Dizi bölümlerinde sezon/bölüm bilgisi alanlardan okunur (hep "S1 B1" görünmez).
 * - Benzer içerikler dizide bölümler değil, aynı klasördeki diğer dizilerdir.
 * - Yapay zekâ adaylarından kütüphane eşleştirmesi (Türkçe ad / orijinal ad).
 * - Fragman uygulama içinde oynar; YouTube uygulaması açılmaz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvFixesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun item(
        id: Int, name: String, type: String, category: String,
        season: Int? = null, episode: Int? = null, trailer: String? = null
    ) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = null, streamUrl = "http://s/$id.mp4",
        category = category, type = type, season = season, episode = episode, trailerUrl = trailer,
        summary = "Gerçek bir konu metni burada yer alıyor."
    )

    @Test
    fun episodeInfo_prefersSeasonEpisodeFields() {
        val xtream = item(1, "4400 - Kayıp Zaman", "SERIES", "Dizi", season = 3, episode = 5)
        val info = SeriesParser.episodeInfoOf(xtream)!!
        assertEquals("4400", info.showTitle)
        assertEquals(3, info.season)
        assertEquals(5, info.episode)
        assertEquals("Kayıp Zaman", info.episodeName)

        val m3u = item(2, "Dark S02E04", "SERIES", "Dizi")
        val parsed = SeriesParser.episodeInfoOf(m3u)!!
        assertEquals(2, parsed.season)
        assertEquals(4, parsed.episode)
    }

    @Test
    fun chatResult_parsesCandidatesAndFallsBackToDetectedTitle() {
        val result = GeminiAiService.parseChatResult(
            """{"reply":"Bu sahne Mahşer filminden olabilir.","candidates":[{"title":"Mahşer","originalTitle":"The Mist","year":2007,"type":"movie"},{"title":"Karanlık","originalTitle":"Dark","type":"series"}]}"""
        )
        assertEquals(2, result.candidates.size)
        assertEquals("The Mist", result.candidates[0].originalTitle)
        assertEquals(2007, result.candidates[0].year)
        assertFalse(result.candidates[0].isSeries)
        assertTrue(result.candidates[1].isSeries)
        assertEquals("Mahşer", result.detectedTitle)

        val legacy = GeminiAiService.parseChatResult("""{"reply":"Tamam","detectedTitle":"Inception"}""")
        assertEquals(listOf("Inception"), legacy.candidates.map { it.title })
    }

    @Test
    fun findTitles_matchesTurkishAndOriginalNames() {
        val db = AppDatabase.getDatabase(app)
        runBlocking {
            db.iptvDao().insertItems(listOf(
                item(7701, "MAHŞER (2007)", "MOVIE", "Korku"),
                item(7702, "Başka Bir Film", "MOVIE", "Korku")
            ))
            val repo = TvCatalogRepository(db.iptvDao())
            val byTurkish = repo.findTitles(listOf(GeminiAiService.TitleCandidate("Mahşer", "The Mist", 2007, false)))
            assertEquals(7701, byTurkish.firstOrNull()?.id)
            val none = repo.findTitles(listOf(GeminiAiService.TitleCandidate("Olmayan Yapım", "Missing Title", null, false)))
            assertTrue(none.none { it.id == 7702 })
        }
    }

    @Test
    fun similarForSeries_showsOtherShowsInFolder_notEpisodes() {
        val db = AppDatabase.getDatabase(app)
        runBlocking {
            db.iptvDao().insertItems(
                (1..4).map { item(7800 + it, "Kara Dizi S01E0$it", "SERIES", "Yerli Diziler Test") } +
                    (1..3).map { item(7810 + it, "Mavi Dizi S01E0$it", "SERIES", "Yerli Diziler Test") } +
                    (1..2).map { item(7820 + it, "Yeşil Dizi S02E0$it", "SERIES", "Yerli Diziler Test") }
            )
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val episode = item(7801, "Kara Dizi S01E01", "SERIES", "Yerli Diziler Test")
        val similar = runBlocking {
            val show = vm.tvShowForItem(episode, emptyMap())
            vm.tvSimilar(episode, show, emptyMap())
        }
        val titles = similar.map { it.title }
        assertTrue("benzerler: $titles", titles.any { it.contains("Mavi Dizi", true) })
        assertTrue("benzerler: $titles", titles.any { it.contains("Yeşil Dizi", true) })
        assertTrue("kendi bölümleri listelenmemeli: $titles", titles.none { it.contains("Kara Dizi", true) })
        assertTrue("bölüm adları listelenmemeli: $titles", titles.none { it.contains("S01E", true) })
    }

    @Test
    fun trailerHtml_usesOnlySafeVideoIdCharacters() {
        val html = trailerHtml("abc123_-XY'); alert(1);//", showControls = false, origin = "https://com.cinestream.iptv")
        assertTrue(html.contains("videoId: 'abc123_-XYalert1'"))
        assertFalse(html.contains("alert(1)"))
        assertTrue(html.contains("controls: 0"))
        assertTrue(html.contains("origin: 'https://com.cinestream.iptv'"))
    }

    @Test
    fun tvDetail_trailerPlaysInApp_neverOpensYouTube() {
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val movie = item(7901, "Fragman Test Filmi", "MOVIE", "Test", trailer = "https://www.youtube.com/watch?v=abcdefghijk")
        compose.setContent { TvDetailScreen(viewModel = vm, item = movie, onPlay = {}) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tv_detail_trailer")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()

        compose.onNodeWithTag("tv_detail_trailer").performClick()
        compose.waitForIdle()
        // Oynatıcı açılır (Robolectric'te video başlamadığı için süre dolunca hata mesajına düşebilir); her iki
        // durumda da harici bir uygulama (YouTube/tarayıcı) açılmamalıdır.
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasTestTag("tv_detail_trailer_player")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag("tv_detail_trailer_failed")).fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
