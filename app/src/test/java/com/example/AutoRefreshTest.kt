package com.example

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel
import com.example.util.AutoRefreshPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Otomatik liste güncellemesi: eskiden her açılışta liste baştan indiriliyordu (yayınla aynı anda olunca donma).
 * Artık en fazla günde bir kez ve video/radyo çalmıyorken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoRefreshTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val server = MockWebServer()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        IPTVViewModel.autoRefreshStartupDelayMs = 0L
        server.start()
    }

    @After
    fun tearDown() {
        IPTVViewModel.autoRefreshStartupDelayMs = AutoRefreshPolicy.STARTUP_DELAY_MS
        server.shutdown()
        db.close()
        app.getSharedPreferences(IPTVViewModel.AUTO_REFRESH_PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    @Test
    fun policy_atMostOncePerDay_andAfterAClockChange() {
        val now = 10 * day
        assertTrue("hiç güncellenmediyse", AutoRefreshPolicy.isDue(0L, now))
        assertFalse("1 saat önce güncellendiyse", AutoRefreshPolicy.isDue(now - 3_600_000, now))
        assertFalse("23 saat önce güncellendiyse", AutoRefreshPolicy.isDue(now - 23 * 3_600_000, now))
        assertTrue("24 saat geçtiyse", AutoRefreshPolicy.isDue(now - day, now))
        assertTrue("saat geri alındıysa", AutoRefreshPolicy.isDue(now + day, now))
    }

    @Test
    fun policy_waitsWhilePlaying() = runBlocking {
        var checks = 0
        AutoRefreshPolicy.awaitNoPlayback(isActive = { ++checks < 4 }, pollMs = 1L)
        assertEquals("oynatma bitene kadar beklenir", 4, checks)
    }

    private fun startAppWithPlaylist(lastRefreshMs: Long?): Int {
        val url = server.url("/liste.m3u").toString()
        val id = runBlocking {
            val id = db.iptvDao().insertPlaylist(Playlist(name = "Liste", url = url, isActive = true)).toInt()
            db.iptvDao().insertItems(listOf(IPTVItem(id = 1, playlistId = id, name = "Kanal", cleanedName = "Kanal",
                logoUrl = null, streamUrl = "http://10.255.255.1/1.ts", category = "Ulusal", type = "LIVE")))
            id
        }
        if (lastRefreshMs != null) {
            app.getSharedPreferences(IPTVViewModel.AUTO_REFRESH_PREFS, Context.MODE_PRIVATE)
                .edit { putLong("last_auto_refresh_$id", lastRefreshMs) }
        }
        repeat(3) { server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1 group-title=\"Ulusal\",Kanal\nhttp://10.255.255.1/1.ts\n")) }
        IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        return id
    }

    private fun waitFor(ms: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    @Test
    fun appStart_refreshedToday_doesNotDownloadTheListAgain() {
        startAppWithPlaylist(lastRefreshMs = System.currentTimeMillis() - 3_600_000)
        assertFalse("bugün güncellenmiş liste yeniden indirilmez", waitFor(4_000) { server.requestCount > 0 })
    }

    @Test
    fun appStart_notRefreshedForADay_downloadsOnce_andRemembersIt() {
        val id = startAppWithPlaylist(lastRefreshMs = System.currentTimeMillis() - 2 * day)
        assertTrue("bir günden eski liste güncellenir", waitFor(8_000) { server.requestCount > 0 })
        val prefs = app.getSharedPreferences(IPTVViewModel.AUTO_REFRESH_PREFS, Context.MODE_PRIVATE)
        assertTrue("güncelleme zamanı kaydedilir", waitFor(8_000) {
            System.currentTimeMillis() - prefs.getLong("last_auto_refresh_$id", 0L) < 60_000
        })
    }
}
