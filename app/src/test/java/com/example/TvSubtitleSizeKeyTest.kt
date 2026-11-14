package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.example.ui.screens.SubtitlesCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TV geri bildirimi (14 Kasım 2026): altyazı boyutu kumandanın Sağ/Sol tuşuyla değişmiyordu. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
class TvSubtitleSizeKeyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun remoteRightLeftChangesSize() {
        var size by mutableIntStateOf(16)
        compose.setContent {
            MyApplicationTheme {
                SubtitlesCard(
                    subtitleSize = size, onSizeChange = { size = it }, selectedColor = "Beyaz",
                    colorOptions = listOf("Beyaz"), colorMap = mapOf("Beyaz" to Color.White),
                    onColorSelect = {}, selectedBackground = com.example.util.SubtitleBackground.NONE, onBackgroundSelect = {}
                )
            }
        }
        compose.onNodeWithTag("subtitle_size_slider").requestFocus()
        compose.onNodeWithTag("subtitle_size_slider").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithTag("subtitle_size_slider").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNodeWithTag("subtitle_size_slider").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        assertEquals(17, size)
        repeat(20) { compose.onNodeWithTag("subtitle_size_slider").performKeyInput { pressKey(Key.DirectionRight) } }
        compose.waitForIdle()
        assertEquals(28, size)
    }
}
