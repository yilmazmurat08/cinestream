package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.stopsWhenDailyLimitEndsWhileWatching
import com.example.util.TrustedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Günlük süre dolunca oynatıcı kapanır ve PRO penceresi açılır (oynatıcıda "Yeniden dene" ile yayın açılamaz).
 * Canlı yayın izlerken süre dolarsa durur; film/dizi sonuna kadar oynar. PRO bilgisi kayıttan okunur: ödeme yapan
 * kullanıcı yanlışlıkla engellenmez, iade/iptal edilen PRO kapanır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProPlayerLimitTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        IPTVViewModel.freeWatchLimitOverride = true // mağaza sürümündeki gibi sınır açık
        TrustedClock.resetForTest(app)
        TrustedClock.networkTime = { System.currentTimeMillis() } // testte ağa çıkılmaz
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            tmp.newFile("ayarlar.preferences_pb").also { it.delete() }
        }
        settings = SettingsRepository(app, store)
    }

    @After
    fun tearDown() {
        IPTVViewModel.freeWatchLimitOverride = null
        TrustedClock.resetForTest(app)
        db.close()
    }

    private fun vm() = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db), settings)

    private fun item(type: String) = IPTVItem(
        id = 8800, playlistId = 1, name = "Sınır Testi", cleanedName = "Sınır Testi", logoUrl = null,
        streamUrl = "http://10.255.255.1/x/8800.ts", category = "Test", type = type
    )

    @Test
    fun limitReached_closesThePlayer_andOpensThePaywall() {
        runBlocking { settings.addWatchSeconds(IPTVViewModel.FREE_DAILY_WATCH_SECONDS) }
        val vm = vm()
        var closed = false
        compose.setContent {
            PlayerScreen(
                item = item("MOVIE"), siblingItems = emptyList(), onBack = { closed = true }, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm
            )
        }
        compose.waitUntil(10_000) { closed }
        assertTrue("oynatıcı kapanır (Yeniden dene ile açılamaz)", closed)
        assertTrue(vm.showPaywallDialog.value)
    }

    @Test
    fun underTheLimit_playerStaysOpen() {
        runBlocking { settings.addWatchSeconds(60) }
        val vm = vm()
        var closed = false
        compose.setContent {
            PlayerScreen(
                item = item("LIVE"), siblingItems = emptyList(), onBack = { closed = true }, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm
            )
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertFalse(closed)
        assertFalse(vm.showPaywallDialog.value)
    }

    @Test
    fun whileWatching_onlyLiveStops_moviesAndEpisodesFinish() {
        assertTrue(stopsWhenDailyLimitEndsWhileWatching("LIVE"))
        assertTrue(stopsWhenDailyLimitEndsWhileWatching("LIVE_TV"))
        assertFalse(stopsWhenDailyLimitEndsWhileWatching("MOVIE"))
        assertFalse(stopsWhenDailyLimitEndsWhileWatching("SERIES"))
    }

    @Test
    fun payingUser_isNeverBlocked_evenBeforeTheScreenLoadsProStatus() {
        runBlocking {
            settings.addWatchSeconds(IPTVViewModel.FREE_DAILY_WATCH_SECONDS)
            settings.setProUser(true)
        }
        val vm = vm() // ekranda kimse PRO durumunu dinlemiyor: vm.isProUser.value henüz false
        assertFalse(vm.isProUser.value)
        assertFalse(runBlocking { vm.isFreeWatchLimitReachedNow() })
    }

    @Test
    fun refundedOrCancelledPro_isTurnedOff_whenPlaySaysSo() {
        runBlocking { settings.setProUser(true) }
        val vm = vm()
        vm.syncProFromPlay(false) // Play: geçerli satın alım yok
        val end = System.currentTimeMillis() + 5_000
        while (runBlocking { settings.isProUserFlow.first() } && System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertEquals(false, runBlocking { settings.isProUserFlow.first() })
    }
}
