package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.IPTVRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ViewMode
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvSettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TV Ayarlar: dikey liste; odaklanan ayarın kartı sağ panelde; OK panele girer, Sol listeye döner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvSettingsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun list_panel_viewModeChange_andLeftReturns() {
        runBlocking { SettingsRepository(app).setViewMode(ViewMode.TV) }
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent { TvSettingsScreen(viewModel = vm) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_setting_PLAYLISTS")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_setting_PLAYLISTS").assertIsFocused()

        // Aşağı: EPG, Dil, Görünüm modu
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_setting_VIEW_MODE").assertIsFocused()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()

        // OK: panele gir, Telefon seçeneğini seç
        press(Key.DirectionCenter)
        val inPanel = compose.onAllNodes(hasTestTag("tv_settings_panel")).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodes(isFocused()).fetchSemanticsNodes().none { node ->
                node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("tv_setting_") == true
            }
        assertEquals(true, inPanel)
        compose.onNodeWithTag("view_mode_option_phone").assertExists()

        // Sol: listeye, aynı satıra dön
        press(Key.DirectionLeft)
        compose.onNodeWithTag("tv_setting_VIEW_MODE").assertIsFocused()

        compose.onNodeWithTag("view_mode_option_phone").performClick()
        compose.waitForIdle()
        // Kayıt arka planda yazılır
        var mode = ViewMode.TV
        for (i in 0 until 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            mode = runBlocking { SettingsRepository(app).viewModeFlow.first() }
            if (mode == ViewMode.PHONE) break
            Thread.sleep(50)
        }
        assertEquals(ViewMode.PHONE, mode)
    }
}
