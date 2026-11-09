package com.example

import android.app.Application
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * Kesintisiz izleme, gerçek oynatıcı ekranıyla: canlı yayın hiç açılmıyor (sunucu 404). Denemeler bitince ekran
 * aynı kanalın yedek sürümüne kendiliğinden geçer; başka bir kanala geçmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp-land-xxhdpi")
class BackupStreamSwitchTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val server = MockWebServer().apply {
        dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setResponseCode(404)
        }
        start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun runFor(maxMs: Long, until: () -> Boolean): Boolean {
        val t0 = System.currentTimeMillis()
        var last = t0
        while (true) {
            if (until()) return true
            val now = System.currentTimeMillis()
            if (now - t0 >= maxMs) return false
            val step = (now - last).coerceAtLeast(1)
            last = now
            val before = SystemClock.uptimeMillis()
            compose.mainClock.advanceTimeBy(step)
            val moved = SystemClock.uptimeMillis() - before
            if (moved < step) ShadowLooper.idleMainLooper(step - moved, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
    }

    @Test
    fun deadLiveStream_switchesToSameChannelBackup() {
        fun live(id: Int, name: String) = IPTVItem(
            id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = null,
            streamUrl = server.url("/live/u/p/$id.ts").toString(), category = "Ulusal", type = "LIVE"
        )
        val failing = live(501, "TRT 1 HD")
        val backup = live(502, "TRT 1 FHD")
        val otherChannel = live(503, "TRT 2 HD")
        runBlocking { db.iptvDao().insertItems(listOf(failing, backup, otherChannel)) }
        var vm: IPTVViewModel? = null
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    IPTVViewModel(app, IPTVRepository(db.iptvDao(), db)) as T
            })[IPTVViewModel::class.java]
        }
        val played = mutableListOf<IPTVItem>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerScreen(
                item = failing, siblingItems = listOf(failing, backup, otherChannel), onBack = {},
                onPlayItem = { played += it }, onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm
            )
        }
        assertTrue("yedeğe geçilmedi", runFor(90_000) { played.isNotEmpty() })
        assertEquals("aynı kanalın yedeği açılmalı", backup.id, played.first().id)
    }
}
