package com.cinestream.sports

import com.cinestream.sports.data.remote.RemoteSportsDataSource
import com.cinestream.sports.data.remote.SportsJson
import com.cinestream.sports.model.CrestPattern
import com.cinestream.sports.model.EventType
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.Score
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.StatKey
import com.cinestream.sports.model.StatLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aracı sunucunun (sunucu/ projesi) ürettiği gerçek yanıtlar uygulamanın okuyucusuyla açılır.
 * Dosyalar sunucuda "node --experimental-strip-types test/export-contract.ts" ile üretilir;
 * sunucu biçimi değişirse bu test uygulamanın onu okuyamadığını hemen gösterir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerContractTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("server-contract/$name")) { "$name yok" }.readText()

    @Test
    fun footballListIsReadCompletely() {
        val matches = SportsJson.parseMatches(resource("matches-football.json")).associateBy { it.id }
        assertEquals("hiçbir maç atlanmamalı", setOf("f-1001", "f-1002", "f-1003", "f-1004"), matches.keys)

        val live = matches.getValue("f-1001")
        assertEquals(Sport.FOOTBALL, live.sport)
        assertEquals(MatchStatus.LIVE, live.status)
        assertEquals(67, live.minute)
        assertEquals(Score(2, 0), live.score)
        assertEquals(Instant.parse("2026-10-12T16:00:00Z").toEpochMilli(), live.kickoffEpochMillis)
        assertEquals("8. Hafta", live.round)
        assertEquals("Gaziantep Stadyumu", live.venue)
        assertEquals("Süper Lig", live.league.name)
        assertEquals("Türkiye", live.league.country.name)
        assertTrue(live.league.isMajor)
        assertEquals(1, live.league.priority)
        assertEquals("Gaziantep FK", live.home.name)
        assertEquals("GAZ", live.home.shortName)
        assertEquals(0xFFE30613, live.home.colors.primary)
        assertEquals(CrestPattern.STRIPES, live.home.colors.pattern)
        assertTrue(live.broadcasts.isEmpty())

        val later = matches.getValue("f-1002")
        assertEquals(MatchStatus.SCHEDULED, later.status)
        assertNull(later.score)
        assertNull(later.minute)
        assertEquals(10, later.league.priority)

        assertEquals(MatchStatus.FINISHED, matches.getValue("f-1003").status)
        assertNull(matches.getValue("f-1003").venue)
        val ucl = matches.getValue("f-1004")
        assertEquals(MatchStatus.HALF_TIME, ucl.status)
        assertEquals("Avrupa", ucl.league.country.name)
        assertEquals(0xFFFDB913, ucl.home.colors.primary)
    }

    @Test
    fun basketballAndVolleyballPeriodsAreRead() {
        val basket = SportsJson.parseMatches(resource("matches-basketball.json")).associateBy { it.id }
        assertEquals(setOf("b-501", "b-502"), basket.keys)
        assertEquals(Sport.BASKETBALL, basket.getValue("b-501").sport)
        assertEquals(4, basket.getValue("b-501").period)
        assertEquals(Score(61, 74), basket.getValue("b-501").score)
        assertEquals("ABD", basket.getValue("b-502").league.country.name)
        assertNull("JSON null değeri \"null\" yazısı olmamalı", basket.getValue("b-501").round)
        assertNull(basket.getValue("b-501").venue)

        val volley = SportsJson.parseMatches(resource("matches-volleyball.json")).single()
        assertEquals(Sport.VOLLEYBALL, volley.sport)
        assertEquals(4, volley.period)
        assertEquals(Score(2, 1), volley.score)
    }

    @Test
    fun detailEventsStatsAndTableAreRead() {
        val detail = checkNotNull(SportsJson.parseDetail(resource("detail-f-1001.json"))) { "detay okunamadı" }
        assertEquals("f-1001", detail.match.id)
        assertEquals(
            listOf(EventType.GOAL, EventType.YELLOW_CARD, EventType.PENALTY_GOAL, EventType.SUBSTITUTION, EventType.RED_CARD),
            detail.events.map { it.type }
        )
        val sub = detail.events.single { it.type == EventType.SUBSTITUTION }
        assertEquals("T. Er", sub.player)
        assertEquals("E. Koç", sub.detail)
        assertEquals(false, sub.isHome)
        assertTrue(detail.events.first().isHome)
        assertNull("gol olayında ek bilgi yok", detail.events.first().detail)

        assertEquals(StatLine(StatKey.POSSESSION, 58, 42), detail.stats.first())
        assertEquals(StatKey.entries.toList(), detail.stats.map { it.key })

        assertEquals(listOf(1, 9, 12), detail.standings.map { it.position })
        assertEquals("Galatasaray", detail.standings.first().team.name)
        assertEquals(19, detail.standings.first().points)
        assertEquals(12, detail.standings.first().goalDifference)
    }

    @Test
    fun teamSearchAndNextMatchAreRead() {
        val teams = SportsJson.parseTeams(resource("teams-search.json"))
        assertEquals(listOf("f-645", "b-1268", "v-401"), teams.map { it.id })
        assertEquals(listOf(Sport.FOOTBALL, Sport.BASKETBALL, Sport.VOLLEYBALL), teams.map { it.sport })
        assertTrue(teams.all { it.colors.primary == 0xFFFDB913 })

        val next = SportsJson.parseMatches(resource("next-f-645.json")).single()
        assertEquals("Liverpool", next.home.name)
        assertEquals("Galatasaray", next.away.name)
        assertEquals(Instant.parse("2026-10-13T16:45:00Z").toEpochMilli(), next.kickoffEpochMillis)
    }

    @Test
    fun remoteSourceReadsServerResponses() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(resource("matches-football.json")))
        server.enqueue(MockResponse().setBody(resource("detail-f-1001.json")))
        server.start()
        try {
            val source = RemoteSportsDataSource(server.url("/").toString(), language = "tr", io = Dispatchers.Unconfined)
            val list = source.matches(Sport.FOOTBALL, LocalDate.of(2026, 10, 12), ZoneId.of("Europe/Istanbul"))
            assertEquals(4, list.size)
            assertEquals(
                "/v1/matches?sport=football&date=2026-10-12&tz=Europe%2FIstanbul&lang=tr",
                server.takeRequest().path
            )
            assertEquals(5, source.matchDetail("f-1001")?.events?.size)
            assertEquals("/v1/matches/f-1001?lang=tr", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}
