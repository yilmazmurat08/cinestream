package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.ui.screens.SubtitlesCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Altyazı ayarı: önizleme (kullanıcı isteği, 13 Kasım 2026) seçilen boyutu anında gösterir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w392dp-h850dp-port-xxhdpi")
class SubtitlePreviewTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun previewFollowsSizeAndColor() {
        var size by mutableIntStateOf(14)
        var color by mutableStateOf("Beyaz")
        compose.setContent {
            MyApplicationTheme {
                SubtitlesCard(
                    subtitleSize = size, onSizeChange = { size = it }, selectedColor = color,
                    colorOptions = listOf("Beyaz", "Sarı", "Yeşil", "Mavi"),
                    colorMap = mapOf("Beyaz" to Color.White, "Sarı" to Color(0xFFFFEB3B), "Yeşil" to Color(0xFF4CAF50), "Mavi" to Color(0xFF2196F3)),
                    onColorSelect = { color = it }
                )
            }
        }
        compose.onNodeWithTag("subtitle_preview").assertExists()
        val small = compose.onNodeWithTag("subtitle_preview_text").getUnclippedBoundsInRoot().height
        size = 24
        color = "Sarı"
        compose.waitForIdle()
        val big = compose.onNodeWithTag("subtitle_preview_text").getUnclippedBoundsInRoot().height
        assertTrue("büyük yazı daha yüksek: $small -> $big", big > small + 4.dp)
        System.getenv("CINESTREAM_SHOTS")?.let { compose.onRoot().captureRoboImage("$it/subtitle-preview.png") }
    }
}
