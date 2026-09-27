package com.example

import android.app.Application
import androidx.activity.ComponentActivity
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
import com.example.data.model.ContinueWatching
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvHomeScreen
import com.example.ui.tv.TvSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV ana sayfası kumanda gezinmesi: ilk odak Canlı TV kartında; aşağı → İzlemeye Devam Et; yukarı → Asistana sor →
 * üst bar. OK açılan bölümü bildirir. Liste boşken de (içerik yokken) çökmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvHomeScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(withHistory: Boolean): IPTVViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        if (withHistory) {
            runBlocking {
                db.iptvDao().insertContinueWatching(
                    ContinueWatching(
                        itemId = 42, itemName = "Örnek Dizi S01E02", itemType = "SERIES", itemLogo = null,
                        streamUrl = "http://example.invalid/42.mp4", category = "Dizi", progressSeconds = 120, totalSeconds = 2400
                    )
                )
            }
        }
        return IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
    }

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun focusStartsOnLive_downGoesToContinue_upGoesToAskThenTopBar() {
        val vm = viewModel(withHistory = true)
        var opened: String? = null
        var resumed: ContinueWatching? = null
        compose.setContent {
            TvHomeScreen(
                viewModel = vm,
                initialFocus = TvSection.LIVE,
                onFocusChanged = {},
                onOpenSection = { opened = it },
                onOpenAssistant = {},
                onOpenSearch = {},
                onPlayContinue = { resumed = it }
            )
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("tv_continue_bar")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_card_LIVE").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_card_MOVIE").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_continue_bar").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(42, resumed?.itemId)

        // Yukarı: en son odaklanan karta döner.
        press(Key.DirectionUp)
        compose.onNodeWithTag("tv_card_MOVIE").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(TvSection.MOVIE, opened)

        press(Key.DirectionUp)
        compose.onNodeWithTag("tv_ask_assistant").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("tv_top_pro").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_ask_assistant").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_card_MOVIE").assertIsFocused()
    }

    @Test
    fun restoresRememberedFocus() {
        val vm = viewModel(withHistory = false)
        compose.setContent {
            TvHomeScreen(
                viewModel = vm,
                initialFocus = TvSection.SAVED,
                onFocusChanged = {},
                onOpenSection = {},
                onOpenAssistant = {},
                onOpenSearch = {},
                onPlayContinue = {}
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_card_SAVED").assertIsFocused()
        compose.onNodeWithTag("tv_hero").assertExists()
    }
}
