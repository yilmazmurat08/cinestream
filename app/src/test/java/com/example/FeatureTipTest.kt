package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.components.FavoriteAddedTip
import com.example.ui.components.FeatureTip
import com.example.ui.components.FeatureTips
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bilgi balonları (kullanıcı isteği, 14 Kasım 2026): her biri bir kez, aynı anda tek balon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w392dp-h850dp-port-xxhdpi")
class FeatureTipTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun on() { FeatureTips.enabled = true; FeatureTips.resetAll(compose.activity) }
    @After fun off() { FeatureTips.resetAll(compose.activity); FeatureTips.enabled = false }

    private fun waitTip() { compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle() }

    @Test
    fun showsOnceOneAtATimeAndClosesWithGotIt() {
        var visible by mutableStateOf(true)
        var clicked = false
        compose.setContent {
            MyApplicationTheme {
                if (visible) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Box { Text("A"); FeatureTip(FeatureTips.COVER, "Kapak balonu", tvMode = false, onClick = { clicked = true }) }
                        Box { Text("B"); FeatureTip(FeatureTips.RESUME, "Devam balonu", tvMode = false) }
                    }
                }
            }
        }
        waitTip()
        compose.onNodeWithTag("feature_tip_cover_change").assertExists()
        compose.onNodeWithTag("feature_tip_resume_shelf").assertDoesNotExist() // aynı anda tek balon
        System.getenv("CINESTREAM_SHOTS")?.let { compose.onRoot().captureRoboImage("$it/feature-tip.png") }
        compose.onNodeWithTag("feature_tip_ok_cover_change").performClick()
        waitTip()
        compose.onNodeWithTag("feature_tip_cover_change").assertDoesNotExist()
        compose.onNodeWithTag("feature_tip_resume_shelf").assertExists() // sıradaki açılır
        assertTrue(!clicked)
        // Ekran yeniden açılınca görülen balonlar bir daha çıkmaz.
        visible = false; compose.waitForIdle(); visible = true
        waitTip()
        compose.onNodeWithTag("feature_tip_cover_change").assertDoesNotExist()
        compose.onNodeWithTag("feature_tip_resume_shelf").assertDoesNotExist()
        assertTrue(FeatureTips.seen(compose.activity, FeatureTips.COVER) && FeatureTips.seen(compose.activity, FeatureTips.RESUME))
    }

    @Test
    fun autoHidesAndSameTipInManyCardsShowsOnce() {
        compose.setContent {
            MyApplicationTheme {
                Column {
                    repeat(3) { Box { Text("Kanal $it"); FeatureTip(FeatureTips.EPG_REMINDER, "EPG", tvMode = true) } }
                }
            }
        }
        waitTip()
        compose.onNodeWithTag("feature_tip_epg_reminder").assertExists() // tek düğüm (birden fazlaysa hata verir)
        compose.onNodeWithTag("feature_tip_ok_epg_reminder").assertDoesNotExist() // TV: "Anladım" yok, odak çalınmaz
        compose.mainClock.advanceTimeBy(FeatureTips.VISIBLE_MS + 500); compose.waitForIdle()
        compose.onNodeWithTag("feature_tip_epg_reminder").assertDoesNotExist()
    }

    @Test
    fun newEpisodeTipOnlyWhenSeriesIsFavorited() {
        var fav by mutableStateOf(false)
        compose.setContent { MyApplicationTheme { Box { FavoriteAddedTip(FeatureTips.NEW_EPISODE, "Yeni bölüm", fav, tvMode = false) } } }
        waitTip()
        compose.onNodeWithTag("feature_tip_new_episode").assertDoesNotExist()
        fav = true
        compose.waitForIdle()
        waitTip()
        compose.onNodeWithTag("feature_tip_new_episode").assertExists()
    }
}
