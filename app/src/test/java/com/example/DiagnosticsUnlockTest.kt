package com.example

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.legal.LegalSettingsCard
import com.example.util.DiagnosticsUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Çökme günlükleri normal kullanıcıdan gizli (kullanıcı kararı, 19 Ekim 2026): Ayarlar'da sürüm satırına 7 kez
 * dokununca açılır ve bu cihazda açık kalır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class DiagnosticsUnlockTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun reset() {
        app.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
        DiagnosticsUnlock.load(app)
    }

    @Test
    fun sevenTapsOnVersion_unlocksAndIsRemembered() {
        compose.setContent { LegalSettingsCard(onOpen = {}) }
        assertFalse(DiagnosticsUnlock.unlocked.value)
        repeat(6) { compose.onNodeWithTag("settings_app_version").performClick() }
        assertFalse("6 dokunuş yetmez", DiagnosticsUnlock.unlocked.value)
        compose.onNodeWithTag("settings_app_version").performClick()
        assertTrue(DiagnosticsUnlock.unlocked.value)

        // Uygulama yeniden açılınca da açık.
        DiagnosticsUnlock.unlocked.value = false
        DiagnosticsUnlock.load(app)
        assertTrue(DiagnosticsUnlock.unlocked.value)
    }

    @Test
    fun settingsHidesCrashLogsUntilUnlocked() {
        compose.setContent {
            com.example.ui.screens.DataAndMemoryCard(
                reduceCellularQuality = false, onReduceCellularChange = {}, autoRefreshList = true, onAutoRefreshChange = {},
                isClearingCache = false, onClearCache = {}
            )
        }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("view_crash_logs_row")).fetchSemanticsNodes().size)
        compose.runOnUiThread { DiagnosticsUnlock.unlock(app) }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodes(hasTestTag("view_crash_logs_row")).fetchSemanticsNodes().size)
    }
}
