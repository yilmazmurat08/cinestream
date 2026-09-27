package com.example

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.example.ui.tv.TvDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Döndürme ve yeniden oluşturma (ActivityScenario.recreate): dil/tema/yazı boyutu değişimi ya da
 * süreç ölümünden dönüşte MainActivity çökmemeli, açılış ekranı tekrar gösterilmemeli (rememberSaveable)
 * ve ilk kurulum ekranı yeniden çizilmeli. Küçük telefon, büyük yazı boyutu, yatay kullanım ve TV denenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityRecreateTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    private fun passIntro() {
        // Açılış ekranı ağ/izin durumundan bağımsız olarak ~3.4 sn sonra kendiliğinden biter.
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        rule.onNode(hasTestTag("cinestream_intro_stage")).assertDoesNotExist()
    }

    private fun assertLoginVisibleAfterRecreate() {
        rule.onNode(hasTestTag("login_screen")).assertExists()
        rule.activityRule.scenario.recreate()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
        // Splash yeniden oynatılmaz; kurulum ekranı geri gelir.
        rule.onNode(hasTestTag("cinestream_intro_stage")).assertDoesNotExist()
        rule.onNode(hasTestTag("login_screen")).assertExists()
    }

    @Test
    fun phonePortrait_recreate() {
        passIntro()
        assertLoginVisibleAfterRecreate()
    }

    @Test
    @Config(qualifiers = "w320dp-h570dp-port")
    fun smallPhone_largestFontScale_recreate() {
        RuntimeEnvironment.setFontScale(2.0f)
        passIntro()
        assertLoginVisibleAfterRecreate()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun phoneLandscape_rotateToPortrait_andRecreate() {
        passIntro()
        assertLoginVisibleAfterRecreate()
        RuntimeEnvironment.setQualifiers("w360dp-h640dp-port")
        rule.activityRule.scenario.recreate()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onNode(hasTestTag("login_screen")).assertExists()
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp-port")
    fun tablet_recreate() {
        passIntro()
        assertLoginVisibleAfterRecreate()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun androidTv_recreate() {
        TvDevice.overrideForTest = true
        rule.activityRule.scenario.recreate()
        passIntro()
        assertLoginVisibleAfterRecreate()
    }
}
