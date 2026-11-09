package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.example.data.api.XtreamCatchUpApi
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.ui.components.ChannelEpgDropdown
import com.example.util.CatchUp
import com.example.util.CatchUpProgram
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64
import java.util.TimeZone

/** Catch-up: arşiv listesini okuma, arşiv adresi, sunucudan çekme ve kanal kartındaki "Kaçırdıkların" bölümü. */
@RunWith(RobolectricTestRunner::class)
class CatchUpTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_800_000_000_000L // 15.01.2027 08:00 UTC
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun channel(url: String) = IPTVItem(
        id = 77, playlistId = 1, name = "TRT 1 HD", cleanedName = "TRT 1 HD", logoUrl = null,
        streamUrl = url, category = "Ulusal", type = "LIVE"
    )

    private fun listing(title: String, startSec: Long, stopSec: Long, archive: Any, start: String? = null) =
        """{"title":"$title","start_timestamp":"$startSec","stop_timestamp":"$stopSec","has_archive":$archive""" +
            (start?.let { ""","start":"$it"""" } ?: "") + "}"

    private val sample by lazy {
        val n = now / 1000
        """{"epg_listings":[
            ${listing(b64("Akşam Haberleri"), n - 7200, n - 5400, 1, "2027-01-15 09:00:00")},
            ${listing(b64("Dizi: Gönül Dağı"), n - 5400, n - 1800, "\"1\"", "2027-01-15 09:30:00")},
            ${listing(b64("Arşivsiz Program"), n - 1800, n - 600, 0)},
            ${listing(b64("Şu An Yayında"), n - 600, n + 1200, 1)},
            ${listing("Düz Başlık", n - 9000, n - 7200, 1)}
        ]}"""
    }

    @Test fun parse_keepsOnlyEndedArchivedPrograms_newestFirst() {
        val list = CatchUp.parse(sample, now, TimeZone.getTimeZone("UTC"))
        assertEquals(listOf("Dizi: Gönül Dağı", "Akşam Haberleri", "Düz Başlık"), list.map { it.title })
        // Sunucu saati yanıttan alınır; yoksa verilen saat diliminden hesaplanır.
        assertEquals("2027-01-15:09-30", list[0].serverStart)
        assertEquals("2027-01-15:05-30", list[2].serverStart)
        assertTrue(CatchUp.parse("bozuk", now).isEmpty())
        assertTrue(CatchUp.parse("""{"epg_listings":[]}""", now).isEmpty())
    }

    @Test fun timeshiftUrl_andPlayableItem() {
        val ch = channel("http://sunucu.tv:8080/live/ali/1234/5555.ts")
        val c = CatchUp.credentialsOf(ch)!!
        val program = CatchUpProgram("Haberler", now - 3_600_000, now - 1_800_000, "2027-01-15:10-00")
        assertEquals("http://sunucu.tv:8080/timeshift/ali/1234/30/2027-01-15:10-00/5555.ts", CatchUp.timeshiftUrl(c, program))
        val item = CatchUp.itemFor(ch, c, program)
        assertEquals(CatchUp.TYPE, item.type)
        assertTrue("kimlik gerçek kanallarla karışmamalı", item.id < 0)
        assertEquals("TRT 1 HD · Haberler", item.cleanedName)
        // Xtream olmayan (M3U) kanalda catch-up yok.
        assertNull(CatchUp.credentialsOf(channel("http://example.com/stream/trt1.m3u8")))
    }

    @Test fun fetch_fromServer() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(sample))
        server.start()
        try {
            val ch = channel(server.url("/live/ali/1234/5555.ts").toString())
            val list = runBlocking { XtreamCatchUpApi.fetch(CatchUp.credentialsOf(ch)!!, now) }
            assertEquals(3, list.size)
            val path = server.takeRequest().path!!
            assertTrue(path, path.contains("action=get_simple_data_table") && path.contains("stream_id=5555"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun channelCard_catchUpSection_opensAndPlays() {
        val upcoming = EPGProgram(
            id = "1", channelId = 77, channelName = "TRT 1", title = "Ana Haber", description = "", category = "Haber",
            startTimeFormatted = "19:00", endTimeFormatted = "20:00",
            startEpochMillis = now + 3_600_000, endEpochMillis = now + 7_200_000
        )
        val past = CatchUpProgram("Sabah Programı", now - 7_200_000, now - 3_600_000, "2027-01-15:06-00")
        val watched = mutableListOf<CatchUpProgram>()
        compose.setContent {
            ChannelEpgDropdown(
                channelId = 77, programs = listOf(upcoming), reminderKeys = emptySet(),
                onToggleReminder = {}, onWatch = {},
                catchUp = listOf(past), onWatchCatchUp = { watched += it }
            )
        }
        fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue(exists("catchup_toggle_77"))
        assertTrue("kapalıyken akış görünür", exists("epg_program_77_${upcoming.startEpochMillis}"))
        compose.onAllNodes(hasTestTag("catchup_toggle_77"), useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("catchup_watch_77_${past.startMillis}"), useUnmergedTree = true)[0].performClick()
        assertEquals(listOf(past), watched)
    }
}
