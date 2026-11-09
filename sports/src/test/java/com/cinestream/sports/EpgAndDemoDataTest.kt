package com.cinestream.sports

import com.cinestream.sports.channels.EpgMatchFinder
import com.cinestream.sports.data.EpgProgramme
import com.cinestream.sports.data.demo.DemoSportsDataSource
import com.cinestream.sports.model.EventType
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class EpgMatchFinderTest {

    @Test
    fun titlesAreParsed() {
        assertEquals(EpgMatchFinder.Teams("galatasaray", "fenerbahce"), EpgMatchFinder.parseTitle("Canlı | Galatasaray - Fenerbahçe"))
        assertEquals(EpgMatchFinder.Teams("vakifbank", "eczacibasi dynavit"), EpgMatchFinder.parseTitle("Sultanlar Ligi: VakıfBank - Eczacıbaşı Dynavit"))
        assertEquals(EpgMatchFinder.Teams("arsenal", "chelsea"), EpgMatchFinder.parseTitle("LIVE: Arsenal vs Chelsea"))
        assertNull(EpgMatchFinder.parseTitle("Akşam Haberleri"))
    }

    @Test
    fun sameTeamToleratesSponsorNames() {
        assertTrue(EpgMatchFinder.sameTeam("Eczacıbaşı Dynavit", "Eczacıbaşı"))
        assertTrue(EpgMatchFinder.sameTeam("Fenerbahçe Beko", "Fenerbahçe"))
        assertTrue(EpgMatchFinder.sameTeam("Vakifbank", "VakıfBank"))
        assertFalse(EpgMatchFinder.sameTeam("Galatasaray", "Fenerbahçe"))
    }

    @Test
    fun programmeMustStartNearKickoff() = runBlocking {
        val source = DemoSportsDataSource(TestEnv.clock)
        val match = source.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone).first { it.home.name == "Gaziantep FK" }
        val channel = PlaylistChannel("c1", "TR | BEIN SPORTS 2 HD")
        fun programme(offsetMin: Long) = EpgProgramme(
            channel, "Canlı | Gaziantep FK - Alanyaspor",
            match.kickoffEpochMillis + offsetMin * 60_000, match.kickoffEpochMillis + 120 * 60_000
        )
        assertNotNull(EpgMatchFinder.find(match, listOf(programme(-5))))
        assertNull("2 saat önceki program başka maçtır", EpgMatchFinder.find(match, listOf(programme(-120))))
    }
}

class DemoDataTest {
    private val source = DemoSportsDataSource(TestEnv.clock)

    @Test
    fun mondayHasLiveFinishedAndUpcomingMatches() = runBlocking {
        val list = source.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone)
        assertEquals(11, list.size)
        assertEquals(3, list.count { it.isLive })
        assertTrue(list.any { it.status == MatchStatus.FINISHED })
        assertTrue(list.any { it.status == MatchStatus.SCHEDULED })
        assertEquals("saate göre sıralı", list.sortedBy { it.kickoffEpochMillis }, list)
    }

    @Test
    fun scoreMatchesTheGoalsInTheDetail() = runBlocking {
        val all = source.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone) +
            source.matches(Sport.FOOTBALL, TestEnv.today.minusDays(1), TestEnv.zone)
        all.filter { it.score != null }.forEach { m ->
            val d = source.matchDetail(m.id)!!
            val goals = d.events.filter { it.type == EventType.GOAL || it.type == EventType.PENALTY_GOAL || it.type == EventType.OWN_GOAL }
            assertEquals(m.id, m.score, d.match.score)
            assertEquals(m.id, m.score!!.home, goals.count { it.isHome })
            assertEquals(m.id, m.score!!.away, goals.count { !it.isHome })
            if (m.isLive) assertTrue("canlı maçta gelecekteki olay olmaz", d.events.all { it.minute <= (m.minute ?: 0) })
        }
    }

    @Test
    fun statsAndSubstitutionsAreConsistent() = runBlocking {
        val all = source.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone) +
            source.matches(Sport.FOOTBALL, TestEnv.today.minusDays(1), TestEnv.zone)
        all.filter { it.score != null }.forEach { m ->
            val d = source.matchDetail(m.id)!!
            val stat = d.stats.associateBy { it.key }
            val onTarget = stat.getValue(com.cinestream.sports.model.StatKey.SHOTS_ON_TARGET)
            val shots = stat.getValue(com.cinestream.sports.model.StatKey.SHOTS)
            assertTrue("${m.id}: isabetli şut gol sayısından az olamaz", onTarget.home >= m.score!!.home && onTarget.away >= m.score!!.away)
            assertTrue("${m.id}: şut isabetli şuttan az olamaz", shots.home >= onTarget.home && shots.away >= onTarget.away)
            d.events.filter { it.type == EventType.SUBSTITUTION }.forEach {
                assertTrue("${m.id}: giren ve çıkan aynı oyuncu", it.player != it.detail)
            }
        }
    }

    @Test
    fun standingsAreOrdered() = runBlocking {
        val m = source.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone).first { it.league.id == "tr-super" }
        val table = source.matchDetail(m.id)!!.standings
        assertEquals(18, table.size)
        assertEquals((1..18).toList(), table.map { it.position })
        assertTrue(table.zipWithNext().all { (a, b) -> a.points >= b.points })
        table.forEach { assertEquals(it.played, it.won + it.drawn + it.lost) }
    }

    @Test
    fun dayIsCountedInTheRequestedTimeZone() = runBlocking {
        val utc = ZoneId.of("UTC")
        val list = source.matches(Sport.BASKETBALL, TestEnv.today, utc)
        assertTrue(list.isNotEmpty())
        list.forEach { assertEquals(TestEnv.today, Instant.ofEpochMilli(it.kickoffEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()) }
    }

    @Test
    fun teamSearchIgnoresTurkishLetters() = runBlocking {
        val found = source.searchTeams("fenerbahce").map { it.name }
        assertTrue(found.toString(), found.contains("Fenerbahçe"))
        assertTrue(found.toString(), found.contains("Fenerbahçe Beko"))
    }

    @Test
    fun nextMatchOfGalatasarayIsTomorrowsChampionsLeagueGame() = runBlocking {
        val next = source.nextMatch("gs", TestEnv.clock.millis())!!
        assertEquals("uefa-cl", next.league.id)
        assertEquals("Liverpool", next.home.name)
    }

    @Test
    fun dataIsDeterministic() = runBlocking {
        val again = DemoSportsDataSource(TestEnv.clock)
        assertEquals(
            source.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone),
            again.matches(Sport.FOOTBALL, TestEnv.today, TestEnv.zone)
        )
    }
}
