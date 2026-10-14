package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvApp
import com.example.ui.tv.TvAppState
import com.example.ui.tv.TvSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV çıkış onay kartı: ana sayfada Geri kartı açar (uygulama kapanmaz). İlk odak "Kal"da; yanlışlıkla OK'e
 * basan ya da tekrar Geri'ye basan uygulamada kalır. Yalnızca "Çık" uygulamayı kapatır. Yarım kalan içerik
 * varsa "Kaldığın yerden devam et" onu oynatır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvExitDialogTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    private val history = ContinueWatching(
        itemId = 42, itemName = "Örnek Dizi S01E02", itemType = "SERIES", itemLogo = null,
        streamUrl = "http://example.invalid/42.mp4", category = "Dizi", progressSeconds = 120, totalSeconds = 2400
    )

    private var resumed: ContinueWatching? = null

    private fun showHome(withHistory: Boolean) {
        if (withHistory) runBlocking { db.iptvDao().insertContinueWatching(history) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent {
            TvApp(
                viewModel = vm,
                state = TvAppState("HOME", TvSection.LIVE, null, null, null),
                onPlayItem = {},
                onPlayContinue = { resumed = it },
                onOpenAssistant = {}
            )
        }
        compose.waitUntil(10_000) { has("tv_card_LIVE") }
        if (withHistory) compose.waitUntil(10_000) { has("tv_continue_bar") }
        compose.waitForIdle()
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun isFinishing(): Boolean {
        var finishing = false
        compose.activityRule.scenario.onActivity { finishing = it.isFinishing }
        return finishing
    }

    @OptIn(ExperimentalTestApi::class)
    private fun pressOnDialog(key: Key) {
        compose.onNodeWithTag("tv_exit_dialog").performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun backOnHome_opensTheCard_andDoesNotExit_stayIsFocused() {
        showHome(withHistory = false)
        pressBack()

        compose.waitUntil(5_000) { has("tv_exit_dialog") }
        assertFalse("Geri tek başına uygulamayı kapatmaz", isFinishing())
        compose.onNodeWithTag("tv_exit_stay").assertIsFocused()
        assertTrue(has("tv_exit_message"))
        assertFalse("yarım içerik yoksa devam düğmesi olmaz", has("tv_exit_continue"))
    }

    @Test
    fun accidentalOk_keepsTheUserInTheApp() {
        showHome(withHistory = false)
        pressBack()
        compose.waitUntil(5_000) { has("tv_exit_dialog") }

        pressOnDialog(Key.DirectionCenter)

        compose.waitUntil(5_000) { !has("tv_exit_dialog") }
        assertFalse(isFinishing())
        assertTrue(has("tv_card_LIVE"))
    }

    @Test
    fun backAgainOnTheCard_closesTheCard_andStaysInTheApp() {
        showHome(withHistory = false)
        pressBack()
        compose.waitUntil(5_000) { has("tv_exit_dialog") }

        // Gerçek kumandadaki gibi: Geri tuşu diyalog penceresine gider.
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        }

        compose.waitUntil(5_000) { !has("tv_exit_dialog") }
        assertFalse("iki kez Geri artık uygulamayı kapatmaz", isFinishing())
        assertTrue(has("tv_card_LIVE"))
    }

    @Test
    fun exitButton_closesTheApp() {
        showHome(withHistory = false)
        pressBack()
        compose.waitUntil(5_000) { has("tv_exit_dialog") }

        pressOnDialog(Key.DirectionRight)
        compose.onNodeWithTag("tv_exit_leave").assertIsFocused()
        pressOnDialog(Key.DirectionCenter)

        assertTrue(isFinishing())
    }

    @Test
    fun continueButton_playsTheUnfinishedItem() {
        showHome(withHistory = true)
        pressBack()
        compose.waitUntil(5_000) { has("tv_exit_continue") }

        compose.onNodeWithTag("tv_exit_continue").performClick()
        compose.waitUntil(5_000) { !has("tv_exit_dialog") }

        assertEquals(42, resumed?.itemId)
        assertFalse(isFinishing())
    }
}
