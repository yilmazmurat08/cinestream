package com.example.stress

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.data.repository.FeaturedMovie
import com.example.ui.components.FeaturedHeroCarousel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Öne Çıkan alanı: yüzlerce kaydırma, otomatik geçiş ve kaydırma sırasında listenin değişmesi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiStressTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() = requireStressMode()

    private fun slides(n: Int, tag: String = "") =
        (1..n).map { FeaturedMovie(title = "Film $tag$it", posterUrl = null, overview = "Özet $it", category = "Aile") }

    @Test
    fun hundredsOfSwipes_autoAdvance_andSlidesChangingMidScroll() {
        var current by mutableStateOf(slides(8))
        compose.mainClock.autoAdvance = false
        compose.setContent { FeaturedHeroCarousel(slides = current, onPlayClick = {}) }
        val sizes = listOf(8, 3, 1, 5, 0, 2, 8)
        timed("400 kaydırma + liste değişimi") {
            repeat(400) { i ->
                if (current.isNotEmpty()) {
                    compose.onNodeWithTag("hero_carousel", useUnmergedTree = true).let { node ->
                        runCatching { node.performTouchInput { if (i % 4 == 3) swipeRight() else swipeLeft() } }
                    }
                }
                compose.mainClock.advanceTimeBy(if (i % 10 == 0) 5_500 else 300) // arada otomatik geçiş
                if (i % 25 == 0) current = slides(sizes[(i / 25) % sizes.size], tag = "$i-")
                compose.waitForIdle()
            }
        }
    }
}
