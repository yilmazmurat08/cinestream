package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.data.repository.FeaturedMovie
import com.example.ui.buildHeroSlides
import com.example.ui.components.FeaturedHeroCarousel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Öne Çıkan alanı: 5 sn'de bir otomatik geçiş, parmakla kaydırma ve yetişkin içerik filtresi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeaturedHeroCarouselTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun movie(title: String, category: String = "Aile Filmleri") =
        FeaturedMovie(title = title, posterUrl = null, overview = "$title özeti", category = category)

    private fun show(slides: List<FeaturedMovie>) {
        compose.mainClock.autoAdvance = false
        compose.setContent { FeaturedHeroCarousel(slides = slides, onPlayClick = {}) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun advancesAutomaticallyEveryFiveSeconds() {
        show(listOf(movie("Birinci Film"), movie("İkinci Film"), movie("Üçüncü Film")))
        compose.onNodeWithText("Birinci Film").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Birinci Film").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(2_000) // 5 sn doldu + kaydırma animasyonu
        compose.waitForIdle()
        compose.onNodeWithText("İkinci Film").assertIsDisplayed()
    }

    @Test
    fun swipeLeftShowsNextMovie() {
        show(listOf(movie("Birinci Film"), movie("İkinci Film")))
        compose.onNodeWithTag("hero_carousel").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("İkinci Film").assertIsDisplayed()
    }

    @Test
    fun adultContentIsNeverInSlides_andTitlesAreUnique() {
        val adultCheck = { m: FeaturedMovie -> m.category == "XXX +18" }
        val slides = buildHeroSlides(
            featured = movie("Yetişkin Film", category = "XXX +18"),
            carousel = listOf(movie("Aile Filmi"), movie("aile filmi "), movie("Başka", category = "XXX +18"), movie("Macera")),
            isAdult = adultCheck
        )
        assertEquals(listOf("Aile Filmi", "Macera"), slides.map { it.title })
    }
}
