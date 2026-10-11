package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.components.BufferingOverlay
import com.example.ui.components.SLOW_BUFFERING_HINT_MS
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Oynatıcı veri beklerken dönen daire görünür; uzun sürerse "bağlantı yavaş" notu çıkar; veri gelince kaybolur. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class BufferingOverlayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun settle(ms: Long) {
        // Dönen daire sonsuz animasyon: saat elle ilerletilir.
        repeat((ms / 100).toInt()) {
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(100)
        }
    }

    @Test
    fun overlay_showsSpinner_thenSlowHint_andHidesWhenNotBuffering() {
        compose.mainClock.autoAdvance = false
        var buffering = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { MyApplicationTheme(dynamicColor = false) { BufferingOverlay(visible = buffering.value) } }
        settle(200)
        compose.onNodeWithTag("buffering_overlay").assertExists()
        compose.onNodeWithTag("buffering_text").assertExists()
        compose.onNodeWithTag("buffering_slow_text").assertDoesNotExist()

        settle(SLOW_BUFFERING_HINT_MS + 500)
        compose.onNodeWithTag("buffering_slow_text").assertExists()

        buffering.value = false
        settle(200)
        compose.onNodeWithTag("buffering_overlay").assertDoesNotExist()
    }
}
