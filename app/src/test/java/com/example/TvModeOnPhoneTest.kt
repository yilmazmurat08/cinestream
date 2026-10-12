package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvPlayerUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Telefonda TV modu: kumanda olmadığı için canlı yayın panelinde kategori listesi (TV'de Sol tuşu) açılamıyor,
 * hep izlenen kanalın kategorisi kalıyordu. Panel başlığına dokununca kategori listesi açılmalı; başka kategoriden
 * kanal seçilip oynatılabilmeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w800dp-h360dp-land")
class TvModeOnPhoneTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val playlist = 7100

    @After
    fun reset() {
        TvDevice.overrideForTest = null
        runBlocking { AppDatabase.getDatabase(app).iptvDao().deleteItemsByPlaylist(playlist) }
    }

    private fun channel(id: Int, name: String, category: String) = IPTVItem(
        id = id, playlistId = playlist, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://10.255.255.1/live/$id.ts", category = category, type = "LIVE"
    )

    private fun waitFor(condition: () -> Boolean): Boolean {
        for (i in 0 until 100) {
            if (condition()) return true
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
        compose.waitForIdle()
        return condition()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun liveChannelPanel_categoriesOpenByTouch_andAnotherCategoryCanBePlayed() {
        TvDevice.overrideForTest = false
        val db = AppDatabase.getDatabase(app)
        val channels = listOf(
            channel(7101, "Haber Bir", "TelTv Haber"),
            channel(7102, "Haber İki", "TelTv Haber"),
            channel(7201, "Spor Bir", "TelTv Spor")
        )
        runBlocking { db.iptvDao().insertItems(channels) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val ui = TvPlayerUiState().apply { launch = TvPlayerUiState.LAUNCH_ENTER }
        var played: IPTVItem? = null
        compose.setContent {
            PlayerScreen(
                item = channels[0],
                siblingItems = emptyList(),
                onBack = {},
                onPlayItem = { played = it },
                onProgressUpdate = { _, _, _ -> },
                iptvViewModel = vm,
                tvMode = true,
                tvUiState = ui
            )
        }
        assertTrue("kanal paneli açılmalı", waitFor { exists("tv_channel_7102") })
        // Telefonda kumanda ipucu yerine dokunma ipucu
        compose.onNodeWithText("Kategoriler için dokun").assertExists()

        compose.onNodeWithTag("tv_panel_header").performClick()
        assertTrue("kategori listesi dokununca açılmalı", waitFor { exists("tv_player_categories") })
        assertTrue(ui.categoriesOpen)

        compose.onNodeWithText("TelTv Spor").performClick()
        assertTrue("seçilen kategorinin kanalları listelenmeli", waitFor { exists("tv_channel_7201") })
        compose.onNodeWithTag("tv_channel_7201").performClick()
        assertEquals(7201, played?.id)
    }
}
