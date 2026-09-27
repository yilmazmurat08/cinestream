package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ViewMode
import com.example.ui.tv.ModeSelectionScreen
import com.example.ui.tv.TvDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** İlk açılış görünüm modu seçimi: otomatik algılanan seçenek odaklı gelir, dokunma ve kumanda ile seçilir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeSelectionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onTv_tvCardIsFocused_andOkSelectsTv() {
        TvDevice.overrideForTest = true
        var selected: String? = null
        compose.setContent { ModeSelectionScreen(onSelect = { selected = it }) }
        compose.waitForIdle()
        compose.onNodeWithTag("mode_card_tv").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(ViewMode.TV, selected)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onTv_leftMovesToPhoneCard() {
        TvDevice.overrideForTest = true
        var selected: String? = null
        compose.setContent { ModeSelectionScreen(onSelect = { selected = it }) }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithTag("mode_card_phone").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(ViewMode.PHONE, selected)
    }

    @Test
    fun onPhone_touchSelectsPhone_andChoiceIsSaved() {
        TvDevice.overrideForTest = false
        val repo = SettingsRepository(compose.activity)
        compose.setContent {
            ModeSelectionScreen(onSelect = { mode -> runBlocking { repo.setViewMode(mode) } })
        }
        compose.onNodeWithTag("mode_card_phone").performClick()
        compose.waitForIdle()
        assertEquals(ViewMode.PHONE, runBlocking { repo.viewModeFlow.first() })
    }
}
