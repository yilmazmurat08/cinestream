package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.epg.RealEpgProvider
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.IPTVViewModel
import com.example.ui.components.LiveNowBand
import com.example.ui.components.currentProgramOf
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import java.util.concurrent.TimeUnit

/**
 * Telefonda canlı yayın oynatıcısının üstünde o an yayında olan programı gösteren bant. Yayın bilgisi yoksa bant
 * boş kalır; tıklanmaz, pencere açmaz. Film/dizide ve TV modunda (bilgi orada alttaki kartta) gösterilmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w800dp-h360dp-land")
class LiveNowBandTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() = RealEpgProvider.clearCache()

    @After
    fun tearDown() = RealEpgProvider.clearCache()

    private fun prog(title: String, start: Long, end: Long) = EPGProgram(
        id = title, channelId = 0, channelName = "trt1", title = title, description = "", category = "",
        startTimeFormatted = "20:00", endTimeFormatted = "21:30", startEpochMillis = start, endEpochMillis = end
    )

    private fun channel(tvgId: String?, type: String = "LIVE") = IPTVItem(
        id = 61001, playlistId = 1, name = "TRT 1", cleanedName = "TRT 1", logoUrl = null,
        streamUrl = "http://10.255.255.1/live/61001.ts", category = "Ulusal", type = type, tvgId = tvgId
    )

    @Test
    fun currentProgram_isTheOneAiringNow() {
        val now = 1_000_000_000L
        val map = mapOf(
            "trt1" to listOf(
                prog("Önceki", now - 7_200_000, now - 3_600_000),
                prog("Şimdiki", now - 3_600_000, now + 600_000),
                prog("Sonraki", now + 600_000, now + 4_200_000)
            )
        )
        assertEquals("Şimdiki", currentProgramOf(channel("trt1"), map, now)?.title)
        assertEquals("program bitince sıradaki gelir", "Sonraki", currentProgramOf(channel("trt1"), map, now + 600_000)?.title)
        assertEquals("tvg-id'de büyük harf/boşluk fark etmez", "Şimdiki", currentProgramOf(channel(" TRT1 "), map, now)?.title)
        assertNull(currentProgramOf(channel(null), map, now))
        assertNull(currentProgramOf(channel("baska"), map, now))
        assertNull("akış bittiyse bilgi yok", currentProgramOf(channel("trt1"), map, now + 5_000_000))
    }

    @Test
    fun band_showsTheProgram_orStaysEmpty_andIsNotClickable() {
        var program by mutableStateOf<EPGProgram?>(prog("Akşam Haberleri", 0, 1))
        compose.setContent { MyApplicationTheme { LiveNowBand(program) } }
        compose.onNodeWithTag("live_now_band").assertExists().assertHasNoClickAction()
        compose.onNodeWithTag("live_now_title").assertTextEquals("Şimdi: Akşam Haberleri")
        compose.onNodeWithText("20:00 – 21:30").assertExists()

        program = null
        compose.waitForIdle()
        compose.onNodeWithTag("live_now_band").assertExists()
        compose.onNodeWithTag("live_now_title").assertDoesNotExist()
    }

    private fun xmltvTime(epochMs: Long): String =
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs))

    private fun waitFor(condition: () -> Boolean): Boolean {
        for (i in 0 until 150) {
            if (condition()) return true
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
        return condition()
    }

    private fun showPlayer(item: IPTVItem, epgTitle: String?): MockWebServer {
        val server = MockWebServer()
        val now = System.currentTimeMillis()
        server.enqueue(
            MockResponse().setBody(
                """<?xml version="1.0" encoding="UTF-8"?><tv>""" + (epgTitle?.let {
                    """<programme start="${xmltvTime(now - 600_000)}" stop="${xmltvTime(now + 3_600_000)}" channel="trt1"><title>$it</title></programme>"""
                } ?: """<programme start="${xmltvTime(now - 600_000)}" stop="${xmltvTime(now + 3_600_000)}" channel="baska"><title>Başka</title></programme>""") +
                    "</tv>"
            )
        )
        server.start()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            tmp.newFile("ayarlar.preferences_pb").also { it.delete() }
        }
        runBlocking {
            db.iptvDao().insertPlaylist(Playlist(name = "Liste", url = "http://h/liste.m3u", epgUrl = server.url("/xmltv.php").toString()))
            db.iptvDao().insertItems(listOf(item))
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db), SettingsRepository(app, store))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerScreen(
                item = item,
                siblingItems = emptyList(),
                onBack = {},
                onPlayItem = {},
                onProgressUpdate = { _, _, _ -> },
                iptvViewModel = vm
            )
        }
        return server
    }

    @Test
    fun phonePlayer_liveChannel_showsWhatIsAiringNowAtTheTop() {
        val server = showPlayer(channel("trt1"), epgTitle = "Akşam Haberleri")
        try {
            assertEquals(true, waitFor { compose.onAllNodes(hasTestTag("live_now_title")).fetchSemanticsNodes().isNotEmpty() })
            compose.onNodeWithTag("live_now_title").assertTextEquals("Şimdi: Akşam Haberleri")
            compose.onNodeWithTag("live_now_band").assertHasNoClickAction()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun phonePlayer_liveChannelWithoutGuide_keepsAnEmptyBand() {
        val server = showPlayer(channel("trt1"), epgTitle = null)
        try {
            assertEquals(true, waitFor { compose.onAllNodes(hasTestTag("live_now_band")).fetchSemanticsNodes().isNotEmpty() })
            waitFor { server.requestCount > 0 }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("live_now_band").assertExists()
            compose.onNodeWithTag("live_now_title").assertDoesNotExist()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun phonePlayer_movie_hasNoBand() {
        val server = showPlayer(channel("trt1", type = "MOVIE"), epgTitle = "Akşam Haberleri")
        try {
            waitFor { server.requestCount > 0 }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("live_now_band").assertDoesNotExist()
        } finally {
            server.shutdown()
        }
    }
}
