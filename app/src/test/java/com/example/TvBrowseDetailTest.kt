package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvAppState
import com.example.ui.tv.TvBrowseScreen
import com.example.ui.tv.TvDetailScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Filmler ekranı ve detay: kategori listesinden Sağ ile ızgaraya, ilk sütundan Sol ile seçili kategoriye dönülür;
 * poster OK ile detay açılır. Detayda ilk odak "İzle"; oyuncu satırında OK kişi sayfasını açar, Geri ile dönünce
 * odak aynı kişidedir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvBrowseDetailTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, tag: String, max: Int = 6) {
        for (i in 0 until max) {
            if (runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess) return
            press(key)
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun movie(id: Int, title: String, category: String, cast: String = "", director: String = "") = IPTVItem(
        id = id, playlistId = 1, name = title, cleanedName = title, logoUrl = null, streamUrl = "http://s/$id.mp4",
        category = category, type = "MOVIE", summary = "Gerçek bir konu metni burada yer alıyor.", cast = cast,
        director = director, trailerUrl = "https://www.youtube.com/watch?v=abc"
    )

    @Test
    fun categoryToGrid_andLeftFromFirstColumnReturnsToCategory_okOpensDetail() {
        val db = AppDatabase.getDatabase(app)
        runBlocking {
            db.iptvDao().insertItems((1..8).map { movie(9000 + it, "Tv Test Film $it", "TvTest Kategori") })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val state = TvAppState("MOVIE", "MOVIE", "TvTest Kategori", null, null)
        compose.setContent { TvBrowseScreen(viewModel = vm, type = "MOVIE", state = state) }
        waitForTag("tv_poster_MOVIE_9001")
        compose.onNodeWithTag("tv_category_TvTest Kategori").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_poster_MOVIE_9001").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_poster_MOVIE_9002").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("tv_category_TvTest Kategori").assertIsFocused()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(5_000) { vm.selectedItem.value != null }
        assertEquals(9001, vm.selectedItem.value?.id)
    }

    @Test
    fun detail_focusStartsOnPlay_personPageBackRestoresSamePerson() {
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val item = movie(9100, "Tv Detay Filmi", "TvDetay", cast = "Ada Oyuncu, Bora Oyuncu", director = "Can Yönetmen")
        var played: IPTVItem? = null
        compose.setContent {
            TvDetailScreen(viewModel = vm, item = item, onPlay = { played = it })
        }
        waitForTag("tv_detail_play")
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()

        // Aşağı: oyuncular satırı (yönetmen başta)
        waitForTag("tv_person_Can Yönetmen")
        pressUntilFocused(Key.DirectionDown, "tv_person_Can Yönetmen")
        compose.onNodeWithTag("tv_person_Can Yönetmen").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_person_Ada Oyuncu").assertIsFocused()

        press(Key.DirectionCenter)
        waitForTag("tv_person_screen")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_person_screen")).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_person_Ada Oyuncu").assertIsFocused()

        // Yukarı çıkıp İzle
        // Yukarı: düğme satırına (oyuncunun hizasındaki düğme), sonra Sol ile İzle.
        for (i in 0 until 6) {
            val inActions = listOf("tv_detail_play", "tv_detail_trailer", "tv_detail_favorite", "tv_detail_ai").any { tag ->
                compose.onAllNodes(hasTestTag(tag) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            if (inActions) break
            press(Key.DirectionUp)
        }
        pressUntilFocused(Key.DirectionLeft, "tv_detail_play")
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(9100, played?.id)
    }
}
