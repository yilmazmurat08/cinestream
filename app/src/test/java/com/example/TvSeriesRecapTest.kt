package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvDetailScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * İzlenen bölümlerin spoilersız AI özeti yalnızca dizi detayında: son izlenen S1B3 ise "S1E4 öncesi" özet istenir
 * (telefondaki gibi); hiç izlenmemişse "yolun başındasınız" ve İlk Bölümü Başlat. Film detayında bu özellik yok.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television")
class TvSeriesRecapTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.waitUntil(10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun episode(id: Int, show: String, ep: Int) = IPTVItem(
        id = id, playlistId = 1, name = "$show S01E0$ep", cleanedName = "$show S01E0$ep", logoUrl = null,
        streamUrl = "http://s/$id.mp4", category = "Recap Dizileri", type = "SERIES", summary = "Bir ailenin hikâyesi."
    )

    @Test
    fun watchedSeries_recapAsksForEpisodesBeforeNext() {
        val db = AppDatabase.getDatabase(app)
        val eps = (1..5).map { episode(9600 + it, "Recap Dizisi", it) }
        runBlocking {
            db.iptvDao().insertItems(eps)
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 9603, itemName = "Recap Dizisi S01E03", itemType = "SERIES", itemLogo = null,
                    streamUrl = "http://s/9603.mp4", category = "Recap Dizileri", progressSeconds = 1300, totalSeconds = 1400)
            )
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent { TvDetailScreen(viewModel = vm, item = eps[0], onPlay = {}) }
        waitFor(hasTestTag("tv_series_recap"))
        // Son izlenen bölüm yazıyor
        waitFor(hasText("Sezon 1, Bölüm 3", substring = true))
        // Düğme satırında AI düğmesi yok
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_detail_ai")).fetchSemanticsNodes().size)

        compose.onNodeWithTag("tv_series_recap").performScrollTo()
        compose.onNodeWithTag("tv_series_recap").performClick()
        compose.waitForIdle()
        // Telefonla aynı: S1E4 öncesi (1–3. bölümler) özetlenir
        waitFor(hasText("S1E4", substring = true))
        // Anahtar yoksa telefondaki mesaj; varsa Gemini metni. Her iki durumda da metin gösterilir.
        waitFor(hasTestTag("tv_series_recap_text"))
    }

    @Test
    fun unwatchedSeries_showsStartOfJourneyAndPlaysFirstEpisode() {
        val db = AppDatabase.getDatabase(app)
        val eps = (1..3).map { episode(9700 + it, "Yeni Recap Dizisi", it) }
        runBlocking { db.iptvDao().insertItems(eps) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        var played: IPTVItem? = null
        compose.setContent { TvDetailScreen(viewModel = vm, item = eps[2], onPlay = { played = it }) }
        waitFor(hasTestTag("tv_series_recap"))
        compose.onNodeWithTag("tv_series_recap").performScrollTo()
        compose.onNodeWithTag("tv_series_recap").performClick()
        compose.waitForIdle()
        waitFor(hasTestTag("tv_series_recap_start"))
        compose.onNodeWithTag("tv_series_recap_first").performScrollTo()
        compose.onNodeWithTag("tv_series_recap_first").performClick()
        compose.waitForIdle()
        assertEquals(9701, played?.id)
    }

    @Test
    fun movieDetail_hasNoEpisodeRecap() {
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val movie = IPTVItem(id = 9800, playlistId = 1, name = "Recap Filmi", cleanedName = "Recap Filmi", logoUrl = null,
            streamUrl = "http://s/9800.mp4", category = "Film", type = "MOVIE", summary = "Konu.")
        compose.setContent { TvDetailScreen(viewModel = vm, item = movie, onPlay = {}) }
        waitFor(hasTestTag("tv_detail_play"))
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_series_recap")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_detail_ai")).fetchSemanticsNodes().size)
    }
}
