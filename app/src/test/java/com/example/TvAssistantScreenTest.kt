package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvAssistantScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV asistanı: anahtar varken odak "Sahnesini anlatayım" düğmesinde; bulunan yapım kütüphanedeyse kart gösterilir,
 * odak karta gelir ve OK detayı açar; kütüphanede yoksa bu açıkça yazılır ve açma/oynatma kartı çıkmaz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvAssistantScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun viewModel(): IPTVViewModel {
        runBlocking { SettingsRepository(app).setGeminiApiKey("AIzaTestKeyForUnitTests0000000000000000") }
        val db = AppDatabase.getDatabase(app)
        return IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
    }

    @Suppress("UNCHECKED_CAST")
    private fun IPTVViewModel.setMessages(list: List<IPTVViewModel.ChatMessage>) {
        val field = IPTVViewModel::class.java.getDeclaredField("_chatMessages").apply { isAccessible = true }
        (field.get(this) as MutableStateFlow<List<IPTVViewModel.ChatMessage>>).value = list
    }

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun foundMovie_cardGetsFocus_andOkOpensIt() {
        val vm = viewModel()
        var opened: IPTVItem? = null
        compose.setContent { TvAssistantScreen(viewModel = vm, onOpenItem = { opened = it }, onOpenSettings = {}) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_ai_scene")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_ai_scene").assertIsFocused()

        val movie = IPTVItem(
            id = 7, playlistId = 1, name = "Inception", cleanedName = "Inception", logoUrl = null,
            streamUrl = "http://example.invalid/7.mp4", category = "Film", type = "MOVIE", releaseDate = "2010-07-16"
        )
        vm.setMessages(
            listOf(
                IPTVViewModel.ChatMessage("user", "Rüya içinde rüya, dönen topaç"),
                IPTVViewModel.ChatMessage("ai", "Bu Inception olabilir.", matchedItem = movie, detectedTitle = "Inception")
            )
        )
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_ai_item_7")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_ai_item_7").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(7, opened?.id)
    }

    @Test
    fun notInLibrary_isStated_andNoOpenCard() {
        val vm = viewModel()
        compose.setContent { TvAssistantScreen(viewModel = vm, onOpenItem = {}, onOpenSettings = {}) }
        vm.setMessages(
            listOf(
                IPTVViewModel.ChatMessage("user", "Uzayda kaybolan astronot"),
                IPTVViewModel.ChatMessage("ai", "Bu Gravity olabilir.", detectedTitle = "Gravity")
            )
        )
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_ai_not_in_library")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_ai_item_0")).fetchSemanticsNodes().size)
        // Odak kaybolmaz: sol panelde kalır.
        compose.onNodeWithTag("tv_ai_scene").assertIsFocused()
    }
}
