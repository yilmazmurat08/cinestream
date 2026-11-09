package com.example

import android.app.Application
import android.view.KeyEvent
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
import androidx.compose.ui.test.requestFocus
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvSavedScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kullanıcı testi (9 Kasım 2026, Mi TV Stick):
 * 1) "Nerede kalmıştık"tan açılan yapımda "İzlemeye devam et?" penceresine kumandayla gelinemiyordu: pencere kodda iki
 *    kez çiziliyordu (üst üste) ve oynatıcı odağı kendine alıyordu. Artık tek pencere, odak "Devam Et"te, Sol/Sağ ile
 *    düğmeler arasında gezilir.
 * 2) "Nerede kalmıştık"ta OK'e basılı tutmak kartı şeritten kaldırmıyordu (yalnızca alttaki kayıtlı listede vardı).
 *    Artık basılı tutunca "Listeden kaldır" penceresi açılır (kullanıcı isteği).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvResumeAndShelfTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() {
        TvDevice.overrideForTest = null
        db.close()
        app.getSharedPreferences("resume_shelf_hidden", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun advance(ms: Long) {
        repeat((ms / 50).toInt()) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(50)
        }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun resumeDialog_single_focusOnContinue_navigableWithRemote() {
        TvDevice.overrideForTest = true
        val movie = IPTVItem(
            id = 8801, playlistId = 1, name = "Devam Filmi", cleanedName = "Devam Filmi", logoUrl = null,
            streamUrl = "http://10.255.255.1/movie/8801.mp4", category = "Film", type = "MOVIE"
        )
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent {
            PlayerScreen(
                item = movie, siblingItems = emptyList(), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm,
                initialProgressSeconds = 60L, tvMode = true, tvUiState = com.example.ui.tv.TvPlayerUiState()
            )
        }
        advance(1_500)
        assertEquals("tek pencere olmalı", 1, count("resume_continue"))
        compose.onNodeWithTag("resume_continue").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("resume_start_over").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("resume_continue").assertIsFocused()
        press(Key.DirectionCenter)
        advance(500)
        assertEquals(0, count("resume_continue"))
    }

    private fun sendOk(repeat: Int, down: Boolean) {
        val now = android.os.SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, repeat)
        compose.runOnUiThread { compose.activity.window.decorView.dispatchKeyEvent(event) }
        compose.waitForIdle()
    }

    @Test
    fun resumeShelf_shortOkPlays_longOkRemovesFromShelfOnly() {
        TvDevice.overrideForTest = true
        runBlocking {
            listOf(1, 2).forEach { i ->
                db.iptvDao().insertContinueWatching(
                    ContinueWatching(
                        itemId = 8900 + i, itemName = "Yarım Film $i", itemType = "MOVIE", itemLogo = null,
                        streamUrl = "http://s/$i.mp4", category = "Film", progressSeconds = 600, totalSeconds = 6000,
                        lastPlayedAt = 1_000L + i
                    )
                )
            }
        }
        runBlocking {
            db.iptvDao().insertItems((1..8).map { i ->
                IPTVItem(
                    id = 8950 + i, playlistId = 1, name = "Kayıtlı $i", cleanedName = "Kayıtlı $i", logoUrl = null,
                    streamUrl = "http://s/k$i.mp4", category = "Film", type = "MOVIE", isFavorite = true
                )
            })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        var played: IPTVItem? = null
        compose.setContent { TvSavedScreen(viewModel = vm, onPlayItem = { played = it }) }
        compose.waitUntil(10_000) { count("tv_resume_8902") > 0 && count("tv_saved_8951") > 0 }
        // Şerit ve kayıtlı liste aynı kaydırılan ızgarada (eskiden şerit sabitti, liste altta kesik görünüyordu).
        assertEquals(
            1,
            compose.onAllNodes(
                hasTestTag("tv_resume_shelf") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("tv_saved_grid")),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
        )

        // Kısa OK: oynatır.
        compose.onNodeWithTag("tv_resume_8902").requestFocus()
        compose.waitForIdle()
        sendOk(0, down = true)
        sendOk(0, down = false)
        assertEquals(8902, played?.id)

        // Basılı tut (tuş tekrarı): "Listeden kaldır" penceresi açılır, oynatmaz; kart henüz durur.
        played = null
        sendOk(0, down = true)
        sendOk(1, down = true)
        sendOk(2, down = true)
        sendOk(0, down = false)
        compose.waitUntil(5_000) { count("tv_remove_dialog") > 0 }
        assertNull(played)
        assertEquals(1, count("tv_resume_8902"))
        // Pencerede OK (odak "Listeden kaldır"da): kart şeritten kalkar.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("tv_remove_confirm").assertIsFocused() }.isSuccess
        }
        @OptIn(ExperimentalTestApi::class)
        compose.onNodeWithTag("tv_remove_dialog").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5_000) { count("tv_resume_8902") == 0 }
        assertEquals(0, count("tv_remove_dialog"))
        assertNull(played)
        assertEquals(1, count("tv_resume_8901"))
        // İzleme kaydı silinmedi (kaldığı yer korunur).
        assertTrue(runBlocking { db.iptvDao().getContinueWatchingForItem(8902) } != null)
    }

    @Test
    fun seriesPanelTitle_isEpisodesAndSeasons() {
        TvDevice.overrideForTest = true
        fun ep(n: Int) = IPTVItem(
            id = -(77000 + n), playlistId = 1, name = "Panel Dizi - Bölüm $n", cleanedName = "Panel Dizi", logoUrl = null,
            streamUrl = "http://10.255.255.1/series/u/p/${77000 + n}.mp4", category = "Dizi", type = "SERIES", season = 1, episode = n
        )
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val ui = com.example.ui.tv.TvPlayerUiState().apply { panelOpen = true }
        compose.setContent {
            PlayerScreen(
                item = ep(1), siblingItems = listOf(ep(1), ep(2)), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm, tvMode = true, tvUiState = ui
            )
        }
        advance(1_000)
        val title = app.getString(R.string.player_episodes_seasons)
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText(title), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText(app.getString(R.string.player_lists)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
