package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.IPTVItem
import com.example.ui.screens.IntroSkipButton
import com.example.ui.theme.MyApplicationTheme
import com.example.util.IntroMark
import com.example.util.IntroSkip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Jeneriği geç: işaret dizi adına göre saklanır, düğme yalnızca jenerik aralığında çıkar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class IntroSkipTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun episode(name: String) = IPTVItem(id = name.hashCode(), playlistId = 1, name = name, cleanedName = name, logoUrl = null, streamUrl = "http://x/$name.mkv", category = "Dizi", type = "SERIES")

    @Test
    fun sameShowSharesKeyAcrossEpisodes_moviesHaveNone() {
        val k1 = IntroSkip.showKey(episode("Gece Treni S01E01"))
        val k2 = IntroSkip.showKey(episode("Gece Treni S02E05"))
        assertEquals(k1, k2)
        assertNull(IntroSkip.showKey(episode("Gece Treni S01E01").copy(type = "MOVIE")))
        assertNull(IntroSkip.showKey(episode("Kanal").copy(type = "LIVE")))
    }

    @Test
    fun saveReadClear() {
        val key = IntroSkip.showKey(episode("Mavi Dalga S01E01"))!!
        IntroSkip.save(app, key, IntroMark(30, 95))
        assertEquals(IntroMark(30, 95), IntroSkip.get(app, key))
        IntroSkip.clear(app, key)
        assertNull(IntroSkip.get(app, key))
    }

    @Test
    fun lengthMustBeBetween5SecondsAnd5Minutes() {
        assertFalse(IntroSkip.isValid(10, 12))
        assertFalse(IntroSkip.isValid(50, 40))
        assertFalse(IntroSkip.isValid(0, 301))
        assertTrue(IntroSkip.isValid(0, 5))
        assertTrue(IntroSkip.isValid(60, 150))
    }

    @Test
    fun buttonOnlyInsideIntroWindow() {
        val mark = IntroMark(60, 120)
        assertFalse(IntroSkip.shouldOffer(40, mark))      // çok erken
        assertTrue(IntroSkip.shouldOffer(52, mark))       // 10 sn önceden (bölüm açılışı kayabilir)
        assertTrue(IntroSkip.shouldOffer(100, mark))
        assertFalse(IntroSkip.shouldOffer(118, mark))     // son 3 sn: gösterilmez
        assertFalse(IntroSkip.shouldOffer(200, mark))
        assertFalse(IntroSkip.shouldOffer(100, null))
        assertTrue(IntroSkip.shouldOffer(0, IntroMark(5, 60)))  // başta başlayan jenerik
    }

    @Test
    fun skipButtonCallsSkip() {
        var skipped = 0
        compose.setContent { MyApplicationTheme { IntroSkipButton(tvMode = false, onSkip = { skipped++ }) } }
        compose.onNodeWithText("Jeneriği geç").assertExists()
        compose.onNodeWithTag("intro_skip_button").performClick()
        assertEquals(1, skipped)
    }

    @Test
    fun tvButtonShowsOkHint() {
        compose.setContent { MyApplicationTheme { IntroSkipButton(tvMode = true, onSkip = {}) } }
        compose.onNodeWithText("Jeneriği geç (OK)").assertExists()
    }
}
