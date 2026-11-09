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

    private fun holdOk() {
        sendOk(0, down = true)
        sendOk(1, down = true)
        sendOk(2, down = true)
        sendOk(0, down = false)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun pressOn(tag: String, key: Key) {
        compose.onNodeWithTag(tag).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun insertHistory() = runBlocking {
        listOf(1, 2).forEach { i ->
            db.iptvDao().insertContinueWatching(
                ContinueWatching(
                    itemId = 8900 + i, itemName = if (i == 1) "Yarım Dizi S02E05" else "Yarım Film $i",
                    itemType = if (i == 1) "SERIES" else "MOVIE", itemLogo = null,
                    streamUrl = "http://s/$i.mp4", category = "Film", progressSeconds = 600, totalSeconds = 6000,
                    lastPlayedAt = 1_000L + i
                )
            )
        }
    }

    /**
     * "Nerede kalmıştık" artık TV ana ekranında (bölüm kartlarının altında) yatay şerit: kısa OK soru sormadan kaldığı
     * yerden oynatır; basılı tutunca menü (Listeden kaldır / Baştan başlat / Vazgeç). Kaldırmak izleme kaydını silmez.
     */
    @Test
    fun homeResumeRow_okResumes_holdOpensMenu_removeAndStartOver() {
        TvDevice.overrideForTest = true
        insertHistory()
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val resumed = mutableListOf<Pair<Int, Boolean>>()
        compose.setContent {
            com.example.ui.tv.TvHomeScreen(
                viewModel = vm,
                initialFocus = com.example.ui.tv.TvSection.LIVE,
                onFocusChanged = {},
                onOpenSection = {},
                onOpenAssistant = {},
                onSearchResult = {},
                onPlayContinue = { resumed += it.itemId to false },
                onResume = { cw, startOver -> resumed += cw.itemId to startOver }
            )
        }
        compose.waitUntil(10_000) { count("tv_home_resume_8902") > 0 && count("tv_home_resume_8901") > 0 }
        compose.waitForIdle()
        // Dizide ad ve bölüm ayrı: "Yarım Dizi" / "Sezon 2 · Bölüm 5 · 90 dk kaldı".
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Yarım Dizi"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        // Bölüm kartlarından Aşağı: şeridin ilk kartı (en son izlenen).
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_home_resume_8902").assertIsFocused()

        // Kısa OK: kaldığı yerden.
        sendOk(0, down = true)
        sendOk(0, down = false)
        assertEquals(listOf(8902 to false), resumed)

        // Basılı tut: menü açılır, oynatmaz; ilk odak "Listeden kaldır".
        holdOk()
        compose.waitUntil(5_000) { count("tv_resume_menu") > 0 }
        assertEquals(1, resumed.size)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_resume_menu_remove").assertIsFocused() }.isSuccess }
        pressOn("tv_resume_menu", Key.DirectionCenter)
        compose.waitUntil(5_000) { count("tv_home_resume_8902") == 0 }
        assertEquals(0, count("tv_resume_menu"))
        assertEquals(1, resumed.size)
        // İzleme kaydı silinmedi (kaldığı yer korunur).
        assertTrue(runBlocking { db.iptvDao().getContinueWatchingForItem(8902) } != null)

        // Kalan kartta menüden "Baştan başlat".
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_home_resume_8901").assertIsFocused() }.isSuccess }
        holdOk()
        compose.waitUntil(5_000) { count("tv_resume_menu") > 0 }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_resume_menu_remove").assertIsFocused() }.isSuccess }
        pressOn("tv_resume_menu", Key.DirectionDown)
        compose.onNodeWithTag("tv_resume_menu_start_over").assertIsFocused()
        pressOn("tv_resume_menu", Key.DirectionCenter)
        assertEquals(8901 to true, resumed.last())
    }

    /** Kaydedilenler yalnızca kullanıcının kaydettikleri; basılı tutunca "Listeden kaldır". */
    @Test
    fun savedScreen_onlySavedItems_holdRemoves() {
        TvDevice.overrideForTest = true
        insertHistory()
        runBlocking {
            db.iptvDao().insertItems((1..8).map { i ->
                IPTVItem(
                    id = 8950 + i, playlistId = 1, name = "Kayıtlı $i", cleanedName = "Kayıtlı $i", logoUrl = null,
                    streamUrl = "http://s/k$i.mp4", category = "Film", type = "MOVIE", isFavorite = true
                )
            })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent { TvSavedScreen(viewModel = vm, onPlayItem = {}) }
        compose.waitUntil(10_000) { count("tv_saved_8951") > 0 }
        assertEquals(0, count("tv_resume_8902"))
        compose.onNodeWithTag("tv_saved_8951").requestFocus()
        compose.waitForIdle()
        holdOk()
        compose.waitUntil(5_000) { count("tv_remove_dialog") > 0 }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_remove_confirm").assertIsFocused() }.isSuccess }
        pressOn("tv_remove_dialog", Key.DirectionCenter)
        compose.waitUntil(5_000) { count("tv_saved_8951") == 0 }
    }

    /** Nerede kalmıştık'tan açılınca "İzlemeye devam et?" sorulmaz. */
    @Test
    fun resumeLaunch_skipsQuestion() {
        TvDevice.overrideForTest = true
        val movie = IPTVItem(
            id = 8802, playlistId = 1, name = "Soru Yok", cleanedName = "Soru Yok", logoUrl = null,
            streamUrl = "http://10.255.255.1/movie/8802.mp4", category = "Film", type = "MOVIE"
        )
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val ui = com.example.ui.tv.TvPlayerUiState().apply { launch = com.example.ui.tv.TvPlayerUiState.LAUNCH_RESUME }
        compose.setContent {
            PlayerScreen(
                item = movie, siblingItems = emptyList(), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm,
                initialProgressSeconds = 60L, tvMode = true, tvUiState = ui
            )
        }
        advance(1_000)
        assertEquals(0, count("resume_continue"))
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

    @Test
    fun moviePanelTitle_isAiRecommendations() {
        TvDevice.overrideForTest = true
        fun film(n: Int) = IPTVItem(
            id = 78000 + n, playlistId = 1, name = "Panel Film $n", cleanedName = "Panel Film $n", logoUrl = null,
            streamUrl = "http://10.255.255.1/movie/${78000 + n}.mp4", category = "Panel Kat", type = "MOVIE"
        )
        runBlocking { db.iptvDao().insertItems((1..6).map { film(it) }) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val ui = com.example.ui.tv.TvPlayerUiState().apply { panelOpen = true }
        compose.setContent {
            PlayerScreen(
                item = film(1), siblingItems = (2..6).map { film(it) }, onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm, tvMode = true, tvUiState = ui
            )
        }
        advance(1_000)
        val title = app.getString(R.string.detail_ai_recommendations)
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText(title), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText(app.getString(R.string.player_lists)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    /** Şerit sağa kaydırılıp ilk kart çizilmez olunca kartlardan Aşağı uygulamayı kapatmamalı. */
    @Test
    fun homeResumeRow_scrolledRight_thenDownFromCards_doesNotCrash() {
        TvDevice.overrideForTest = true
        runBlocking {
            (1..5).forEach { i ->
                db.iptvDao().insertContinueWatching(
                    ContinueWatching(
                        itemId = 8700 + i, itemName = "Şerit Film $i", itemType = "MOVIE", itemLogo = null,
                        streamUrl = "http://s/r$i.mp4", category = "Film", progressSeconds = 600, totalSeconds = 6000,
                        lastPlayedAt = 2_000L + i
                    )
                )
            }
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent {
            com.example.ui.tv.TvHomeScreen(
                viewModel = vm, initialFocus = com.example.ui.tv.TvSection.LIVE, onFocusChanged = {}, onOpenSection = {},
                onOpenAssistant = {}, onSearchResult = {}, onPlayContinue = {}
            )
        }
        compose.waitUntil(10_000) { count("tv_home_resume_8705") > 0 }
        compose.waitForIdle()
        press(Key.DirectionDown)
        repeat(4) { press(Key.DirectionRight) }
        compose.onNodeWithTag("tv_home_resume_8701").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        press(Key.DirectionDown)
    }
}
