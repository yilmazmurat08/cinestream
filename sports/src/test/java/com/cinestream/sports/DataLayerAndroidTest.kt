package com.cinestream.sports

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cinestream.sports.data.remote.RemoteSportsDataSource
import com.cinestream.sports.data.remote.SportsJson
import com.cinestream.sports.model.CrestPattern
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.NotificationEntry
import com.cinestream.sports.model.NotificationType
import com.cinestream.sports.model.Sport
import com.cinestream.sports.settings.DataStoreSportsSettingsStore
import com.cinestream.sports.settings.LeagueFilterMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

private val SAMPLE = """
{
  "leagues": [{"id":"tr-super","sport":"football","name":"Süper Lig","country":{"code":"TR","name":"Türkiye"},"major":true,"priority":1},
              {"id":"bozuk","sport":"curling","name":"?"}],
  "teams": [{"id":"gs","name":"Galatasaray","short":"GS","league":"tr-super","leagueName":"Süper Lig","sport":"football",
             "colors":{"primary":"#FDB913","secondary":"#A90432","pattern":"halves"}},
            {"id":"fb","name":"Fenerbahçe","league":"tr-super","colors":{"primary":"yanlış"}}],
  "matches": [
    {"id":"m1","league":"tr-super","home":"gs","away":"fb","kickoff":"2026-10-12T17:00:00Z","status":"LIVE","minute":67,
     "period":null,"score":{"home":1,"away":0},"broadcasts":[{"channel":"beIN SPORTS 1","country":"TR"},{"channel":"","country":"TR"},{"channel":null,"country":"TR"}],
     "round":"8. Hafta","venue":"RAMS Park"},
    {"id":"m2","league":"yok","home":"gs","away":"fb","kickoff":"2026-10-12T17:00:00Z"},
    {"id":"m3","league":"tr-super","home":"gs","away":"fb","kickoff":"tarih değil"}
  ]
}
""".trimIndent()

/** org.json ve DataStore gerçek Android sınıfları ister; Robolectric ile çalışır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataLayerAndroidTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun jsonIsParsed_brokenRecordsAreSkipped() {
        val matches = SportsJson.parseMatches(SAMPLE)
        assertEquals(listOf("m1"), matches.map { it.id })
        val m = matches.single()
        assertEquals(MatchStatus.LIVE, m.status)
        assertEquals(67, m.minute)
        assertNull(m.period)
        assertEquals(listOf("beIN SPORTS 1"), m.broadcasts.map { it.channelName })
        assertEquals(0xFFFDB913, m.home.colors.primary)
        assertEquals(CrestPattern.HALVES, m.home.colors.pattern)
        assertEquals("yanlış renk varsayılana döner", 0xFF6B6385, m.away.colors.primary)
        assertEquals("FEN", m.away.shortName)
    }

    @Test
    fun detailIsParsed() {
        val body = SAMPLE.trimEnd('}') + """,
          "match": {"id":"m1","league":"tr-super","home":"gs","away":"fb","kickoff":"2026-10-12T17:00:00Z","status":"FINISHED","score":{"home":2,"away":1}},
          "events": [{"minute":12,"type":"GOAL","side":"home","player":"E. Kaya"},{"minute":50,"type":"UFO","side":"away"}],
          "stats": [{"key":"POSSESSION","home":55,"away":45}],
          "standings": [{"position":1,"team":"gs","played":7,"won":6,"drawn":1,"lost":0,"goalsFor":18,"goalsAgainst":4,"points":19}]
        }"""
        val d = SportsJson.parseDetail(body)!!
        assertEquals(MatchStatus.FINISHED, d.match.status)
        assertEquals(1, d.events.size)
        assertEquals(55, d.stats.single().home)
        assertEquals(19, d.standings.single().points)
    }

    @Test
    fun remoteSourceAsksTheProxyAndReportsErrors() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(SAMPLE))
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            val source = RemoteSportsDataSource(server.url("/api/").toString(), io = Dispatchers.Unconfined)
            val list = source.matches(Sport.FOOTBALL, LocalDate.of(2026, 10, 12), ZoneId.of("Europe/Istanbul"))
            assertEquals(1, list.size)
            val request = server.takeRequest()
            assertEquals("/api/v1/matches", request.requestUrl!!.encodedPath)
            assertEquals("football", request.requestUrl!!.queryParameter("sport"))
            assertEquals("2026-10-12", request.requestUrl!!.queryParameter("date"))
            assertEquals("Europe/Istanbul", request.requestUrl!!.queryParameter("tz"))
            assertNull("anahtar uygulamada yok", request.getHeader("Authorization"))
            try {
                source.matchDetail("m1")
                fail("HTTP 500 hata olarak bildirilmeli")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("500"))
            }
        } finally {
            server.shutdown()
        }
    }

    private fun store(name: String) = DataStoreSportsSettingsStore(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { tmp.root.resolve(name) }
    )

    @Test
    fun settingsAreSavedAndRead() = runBlocking {
        val s = store("a.preferences_pb")
        s.update {
            it.copy(
                sports = listOf(Sport.BASKETBALL, Sport.FOOTBALL),
                leagueMode = LeagueFilterMode.CUSTOM,
                customLeagueIds = setOf("tr-3lig"),
                followedTeamIds = setOf("gs"),
                reminderMinutes = 15,
                channelMappings = mapOf("spor tabii" to "TR | TRT 1 HD")
            )
        }
        val read = s.settings.first()
        assertEquals(listOf(Sport.BASKETBALL, Sport.FOOTBALL), read.sports)
        assertEquals(LeagueFilterMode.CUSTOM, read.leagueMode)
        assertEquals(setOf("tr-3lig"), read.customLeagueIds)
        assertEquals(15, read.reminderMinutes)
        assertEquals("TR | TRT 1 HD", read.channelMappings["spor tabii"])
    }

    @Test
    fun notificationHistoryKeepsTheLatest50() = runBlocking {
        val s = store("b.preferences_pb")
        repeat(55) { i ->
            s.addNotification(NotificationEntry("n$i", NotificationType.REMINDER, "Maç $i", "10 dakika sonra", i * 1000L, "m$i"))
        }
        val list = s.notifications.first()
        assertEquals(50, list.size)
        assertEquals("en yeni en üstte", "n54", list.first().id)
        s.clearNotifications()
        assertTrue(s.notifications.first().isEmpty())
    }

    @Test
    fun corruptedSettingsFileOpensWithDefaults() = runBlocking {
        val file = tmp.root.resolve("bozuk.preferences_pb")
        file.writeBytes(byteArrayOf(0x13, 0x37, 0x00, 0x7F, 0x55))
        val s = DataStoreSportsSettingsStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
                    androidx.datastore.preferences.core.emptyPreferences()
                },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ) { file }
        )
        assertEquals(LeagueFilterMode.MAJOR, s.settings.first().leagueMode)
    }
}
