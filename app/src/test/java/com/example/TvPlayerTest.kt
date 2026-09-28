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
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvPlayerUiState
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
 * TV modu oynatıcı (gerçek PlayerScreen, tvMode = true): Canlı TV'ye girişte kanal paneli açık ve odak izlenen
 * kanalda; Sol kategori listesini açar; Geri önce kategori listesini, sonra paneli kapatır ve odak oynat/duraklat'a
 * gelir; kontroller gizliyken Yukarı sonraki kanala geçer (bilgi bandıyla); rakam tuşları kanala atlar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvPlayerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    private fun channel(id: Int, name: String, category: String) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://10.255.255.1/live/$id.ts", category = category, type = "LIVE"
    )

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test
    fun liveEntry_panelFocus_categories_backOrder_zapAndNumbers() {
        TvDevice.overrideForTest = true
        val db = AppDatabase.getDatabase(app)
        val channels = listOf(
            channel(7001, "Kanal Bir", "TvPlayer Haber"),
            channel(7002, "Kanal İki", "TvPlayer Haber"),
            channel(7003, "Kanal Üç", "TvPlayer Haber"),
            channel(7004, "Spor Bir", "TvPlayer Spor")
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
        // Giriş: kanal paneli açık, odak izlenen kanalda (kanal listesi akışı gecikmeli dolar)
        for (i in 0 until 100) {
            if (compose.onAllNodes(hasTestTag("tv_channel_7003")).fetchSemanticsNodes().isNotEmpty()) break
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_channel_7001").assertIsFocused()
        compose.onNodeWithTag("tv_control_bar").assertExists()
        compose.onNodeWithTag("tv_channel_info").assertExists()

        // Sol: kategori listesi
        press(Key.DirectionLeft)
        waitForTag("tv_player_categories")
        assertTrue(ui.categoriesOpen)

        // Geri: önce kategoriler, sonra panel; odak oynat/duraklat
        back()
        assertFalse(ui.categoriesOpen)
        assertTrue(ui.panelOpen)
        back()
        assertFalse(ui.panelOpen)
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("tv_bar_play").assertIsFocused() }.isSuccess
        }
        // Canlıda ileri/geri sarma odaklanamaz: Sol, önceki kanal düğmesine atlar
        press(Key.DirectionLeft)
        compose.onNodeWithTag("tv_bar_prev").assertIsFocused()

        // Geri: kontroller gizlenir; Yukarı sonraki kanal (bilgi bandı modunda)
        back()
        compose.waitForIdle()
        press(Key.DirectionUp)
        // Sıra, telefondaki CH+/CH− ile aynı kanal halkası
        val ring = vm.channelRingFor(channels[0], vm.liveChannels.value)
        assertEquals(listOf(7001, 7002, 7003).toSet(), ring.map { it.id }.toSet())
        val current = ring.indexOfFirst { it.id == 7001 }
        assertEquals(ring[(current + 1) % ring.size].id, played?.id)
        assertEquals(TvPlayerUiState.LAUNCH_ZAP, ui.launch)

        // Numara: 3 → 1,5 sn sonra 3. kanal (7001 zaten 3. ise 1'e basılır)
        played = null
        val digit = if (ring[2].id == 7001) 1 else 3
        press(if (digit == 1) Key.One else Key.Three)
        compose.onNodeWithTag("tv_player_digits").assertExists()
        compose.mainClock.advanceTimeBy(1_700)
        compose.waitForIdle()
        assertEquals(ring[digit - 1].id, played?.id)
    }

    @Test
    fun movie_controlsFocusPlay_seekButtonsFocusable_backHides_downShows() {
        TvDevice.overrideForTest = true
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val movie = IPTVItem(
            id = 7100, playlistId = 1, name = "Tv Oynatıcı Filmi", cleanedName = "Tv Oynatıcı Filmi", logoUrl = null,
            streamUrl = "http://10.255.255.1/movie/7100.mp4", category = "Film", type = "MOVIE"
        )
        compose.setContent {
            PlayerScreen(
                item = movie, siblingItems = emptyList(), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm, tvMode = true, tvUiState = TvPlayerUiState()
            )
        }
        waitForTag("tv_bar_play")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_bar_play").assertIsFocused() }.isSuccess }
        compose.mainClock.advanceTimeBy(1_000) // açılıştaki gecikmeli odak isteği odağı çalmamalı
        compose.waitForIdle()
        compose.onNodeWithTag("tv_bar_play").assertIsFocused()
        // Filmde kanal paneli ve kanal bilgi kartı yok; geri/ileri sarma odaklanabilir
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_channel_info")).fetchSemanticsNodes().size)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("tv_bar_rew").assertIsFocused()

        back()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_control_bar")).fetchSemanticsNodes().isEmpty() }
        press(Key.DirectionDown)
        waitForTag("tv_control_bar")
    }
}
