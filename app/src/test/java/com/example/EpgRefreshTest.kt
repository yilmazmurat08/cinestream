package com.example

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.epg.RealEpgProvider
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * Uygulama arka planda saatlerce/günlerce açık kalınca (TV kutuları) yayın akışı hiç yenilenmiyordu; ön plana
 * dönüşte bayat rehber yenilenmeli, yeni indirilmiş rehber ise boşuna tekrar indirilmemeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgRefreshTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer
    private val served = AtomicInteger(0)

    private fun xmltvTime(epochMs: Long): String =
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs))

    @Before
    fun setUp() {
        RealEpgProvider.clearCache()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = served.incrementAndGet()
                val now = System.currentTimeMillis()
                return MockResponse().setBody(
                    """<?xml version="1.0" encoding="UTF-8"?><tv><programme start="${xmltvTime(now - 600_000)}" """ +
                        """stop="${xmltvTime(now + 600_000)}" channel="trt1"><title>Program $n</title></programme></tv>"""
                )
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        RealEpgProvider.clearCache()
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
        return condition()
    }

    @Test
    fun returningToForeground_refreshesAStaleGuide_butNotAFreshOne() {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            tmp.newFile("ayarlar.preferences_pb").also { it.delete() }
        }
        val settings = SettingsRepository(app, store)
        try {
            runBlocking {
                db.iptvDao().insertPlaylist(
                    Playlist(name = "Liste", url = "http://h/liste.m3u", epgUrl = server.url("/xmltv.php").toString())
                )
            }
            val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db), settings)
            fun liveTitle() = vm.realEpgPrograms.value["trt1"]?.firstOrNull()?.title
            fun lastFetch() = runBlocking { settings.lastEpgFetchTimeFlow.first() }

            // Açılışta rehber bir kez indirilir.
            assertTrue("açılışta rehber indirilmeli", waitUntil { liveTitle() == "Program 1" && lastFetch() > 0 })

            // Yeni indirilmiş rehber arka plandan dönünce tekrar indirilmez.
            vm.onAppReturnedToForeground()
            waitUntil(1_000) { served.get() > 1 }
            assertEquals(1, served.get())

            // 7 saat önce indirilmiş (bayat) rehber arka plandan dönünce yenilenir.
            runBlocking { settings.setLastEpgFetchTime(System.currentTimeMillis() - 7 * 3_600_000L) }
            vm.onAppReturnedToForeground()
            assertTrue("bayat rehber yenilenmeli", waitUntil { liveTitle() == "Program 2" })
            assertTrue("yenileme zamanı kaydedilmeli", waitUntil { lastFetch() > System.currentTimeMillis() - 60_000 })
        } finally {
            db.close()
        }
    }
}
