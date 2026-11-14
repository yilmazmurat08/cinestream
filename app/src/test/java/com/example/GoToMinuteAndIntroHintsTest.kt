package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.components.GoToMinuteDialog
import com.example.ui.components.goToMinuteValid
import com.example.ui.screens.IntroHintCard
import com.example.ui.screens.IntroMarkingChip
import com.example.ui.screens.IntroSuggestChip
import com.example.ui.screens.PlayerScreen
import com.example.util.IntroSkip
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Kullanıcı isteği (13 Kasım 2026): "Dakikaya git" (rakamla dakika, ekran tuşları + kumanda) ve bayrağın nasıl
 * kullanılacağını anlatan ipuçları (bir kerelik açıklama, işaretleme şeridi, işaretsiz dizide öneri).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w392dp-h850dp-port-xxhdpi")
class GoToMinuteAndIntroHintsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val shots = System.getenv("CINESTREAM_SHOTS")

    @After
    fun tearDown() {
        db.close()
        app.getSharedPreferences("intro_marks", 0).edit().clear().commit()
    }

    @Test
    fun minuteValidation() {
        assertTrue(goToMinuteValid(65, 6000))
        assertFalse(goToMinuteValid(100, 6000))   // 100 dk > 6000 sn
        assertTrue(goToMinuteValid(0, 6000))
        assertFalse(goToMinuteValid(null, 6000))
    }

    @Test
    fun padTyping_goesToMinute_andTooFarIsBlocked() {
        var went: Int? = null
        compose.setContent { GoToMinuteDialog(durationSec = 6000, onGo = { went = it }, onDismiss = {}) }
        compose.onNodeWithTag("go_key_6").performClick()
        compose.onNodeWithTag("go_key_5").performClick()
        compose.onNodeWithTag("go_to_minute_value").assertTextEquals("65")
        shots?.let { compose.onNode(isDialog()).captureRoboImage("$it/go-to-minute.png") }
        compose.onNodeWithTag("go_key_go").performClick()
        assertEquals(65, went)
    }

    /** Kullanıcı (14 Kasım 2026): yatay telefonda alt tuşlar (7-8-9, ⌫ 0 Git) ekran dışında kalıyordu. */
    @Test
    @Config(qualifiers = "tr-w850dp-h392dp-land-xxhdpi")
    fun landscape_allKeysVisible_goWorks() {
        var went: Int? = null
        compose.setContent { GoToMinuteDialog(durationSec = 55 * 60L, onGo = { went = it }, onDismiss = {}) }
        compose.onNodeWithTag("go_key_2").performClick()
        compose.onNodeWithTag("go_key_3").performClick()
        listOf("7", "8", "9", "back", "0", "go").forEach { compose.onNodeWithTag("go_key_$it").assertIsDisplayed() }
        val root = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
        val go = compose.onNodeWithTag("go_key_go").getUnclippedBoundsInRoot()
        assertTrue("Git tuşu ekranın içinde: $go / $root", go.bottom <= 392.dp)
        shots?.let { compose.onNode(isDialog()).captureRoboImage("$it/go-to-minute-land.png") }
        compose.onNodeWithTag("go_key_go").performClick()
        assertEquals(23, went)
    }

    @Test
    fun tooFar_goDisabled_backspaceFixes() {
        var went: Int? = null
        compose.setContent { GoToMinuteDialog(durationSec = 6000, onGo = { went = it }, onDismiss = {}) }
        listOf("1", "2", "0").forEach { compose.onNodeWithTag("go_key_$it").performClick() }
        compose.onNodeWithTag("go_key_go").performClick()
        assertNull("120 dk > 100 dk: gidilmez", went)
        compose.onNodeWithTag("go_key_back").performClick()
        compose.onNodeWithTag("go_to_minute_value").assertTextEquals("12")
        compose.onNodeWithTag("go_key_go").performClick()
        assertEquals(12, went)
    }

    /** Kumandanın rakam tuşları (Robolectric ayrı pencereye tuş göndermediği için kural doğrudan sınanır). */
    @Test
    fun remoteDigitKeys_andAppendRule() {
        assertEquals(4, com.example.ui.components.goToMinuteDigit(Key.Four))
        assertEquals(9, com.example.ui.components.goToMinuteDigit(Key.NumPad9))
        assertEquals(-1, com.example.ui.components.goToMinuteDigit(Key.DirectionLeft))
        var t = ""
        listOf(0, 4, 2).forEach { t = com.example.ui.components.goToMinuteAppend(t, it) }
        assertEquals("42", t)
        listOf(1, 5).forEach { t = com.example.ui.components.goToMinuteAppend(t, it) }
        assertEquals("421", t) // en çok 3 hane
    }

    @Test
    fun suggestRule_onlyFromSecondEpisode_inFirstMinutes() {
        assertFalse(IntroSkip.shouldSuggest(1, 1, 60))
        assertTrue(IntroSkip.shouldSuggest(1, 2, 60))
        assertTrue(IntroSkip.shouldSuggest(2, 1, 60))
        assertFalse(IntroSkip.shouldSuggest(1, 3, 5))
        assertFalse(IntroSkip.shouldSuggest(1, 3, 400))
    }

    @Test
    fun hintCard_shownOnce_onSeriesEpisode() {
        val episode = IPTVItem(
            id = 8951, playlistId = 1, name = "Gece Treni S01E01", cleanedName = "Gece Treni S01E01", logoUrl = null,
            streamUrl = "http://10.255.255.1/series/8951.mp4", category = "Dizi", type = "SERIES", season = 1, episode = 1
        )
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent {
            PlayerScreen(item = episode, siblingItems = emptyList(), onBack = {}, onPlayItem = {}, onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm)
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onNodeWithTag("intro_hint_card").assertExists()
        assertTrue(IntroSkip.hintShown(app))
        compose.onNodeWithTag("intro_hint_ok").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("intro_hint_card").assertCountEquals(0)
    }

    @Test
    fun visuals() {
        if (shots == null) return
        compose.setContent {
            com.example.ui.theme.MyApplicationTheme {
                Box(Modifier.fillMaxSize().then(Modifier)) {
                    IntroMarkingChip(elapsedSec = 42, tvMode = false, onCancel = {}, modifier = Modifier.align(Alignment.TopCenter))
                    IntroSuggestChip(tvMode = false, onClick = {}, modifier = Modifier.align(Alignment.CenterEnd))
                    IntroHintCard(tvMode = false, onDismiss = {}, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        compose.onRoot().captureRoboImage("$shots/intro-hints.png")
    }
}
