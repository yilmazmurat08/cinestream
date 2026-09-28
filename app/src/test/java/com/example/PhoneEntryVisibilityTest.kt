package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.screens.GeminiApiKeyCard
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvUiMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Telefonla QR ile bilgi girişi: gerçek TV cihazında ve telefonda TV modu seçiliyken giriş ekranında ve
 * ayarlarda görünür; telefon modunda (TV olmayan cihaz) görünmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneEntryVisibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun reset() {
        TvDevice.overrideForTest = null
        TvUiMode.active = false
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun phoneInTvMode_loginShowsQrEntry() {
        TvDevice.overrideForTest = false
        TvUiMode.active = true
        compose.setContent { MyApplicationTheme { LoginScreen(onLoginSuccess = { _, _ -> }) } }
        compose.waitForIdle()
        assertTrue(count("phone_entry_button_playlist") + count("phone_entry_button_gemini_key") > 0)
    }

    @Test
    fun phoneInPhoneMode_loginHidesQrEntry() {
        TvDevice.overrideForTest = false
        TvUiMode.active = false
        compose.setContent { MyApplicationTheme { LoginScreen(onLoginSuccess = { _, _ -> }) } }
        compose.waitForIdle()
        assertEquals(0, count("phone_entry_button_playlist") + count("phone_entry_button_gemini_key"))
    }

    @Test
    fun realTvDevice_settingsGeminiCardShowsQrEntry() {
        TvDevice.overrideForTest = true
        compose.setContent { MyApplicationTheme { GeminiApiKeyCard(apiKey = "", isValidating = false, onApiKeyChange = {}, onValidateClick = {}) } }
        compose.waitForIdle()
        assertEquals(1, count("phone_entry_button_gemini_key"))
    }

    @Test
    fun phoneInTvMode_settingsGeminiCardShowsQrEntry() {
        TvDevice.overrideForTest = false
        TvUiMode.active = true
        compose.setContent { MyApplicationTheme { GeminiApiKeyCard(apiKey = "", isValidating = false, onApiKeyChange = {}, onValidateClick = {}) } }
        compose.waitForIdle()
        assertEquals(1, count("phone_entry_button_gemini_key"))
    }
}
