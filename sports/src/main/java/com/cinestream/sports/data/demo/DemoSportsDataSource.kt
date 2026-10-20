package com.cinestream.sports.data.demo

import com.cinestream.sports.channels.ChannelKeys
import com.cinestream.sports.data.SportsDataSource
import com.cinestream.sports.model.Broadcast
import com.cinestream.sports.model.EventType
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchDetail
import com.cinestream.sports.model.MatchEvent
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.Score
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.StandingRow
import com.cinestream.sports.model.StatKey
import com.cinestream.sports.model.StatLine
import com.cinestream.sports.model.Team
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Örnek veri kaynağı: gerçek sonuçlar değildir. Ekranları, testleri ve tanıtım görsellerini veri servisi olmadan
 * çalıştırır. Program Türkiye saatine göre tanımlıdır; maçın durumu (başlamadı, canlı, bitti) ve skoru verilen
 * saate göre hesaplanır, böylece demo saat ilerledikçe "canlı" görünür.
 */
class DemoSportsDataSource(private val clock: Clock) : SportsDataSource {

    private val homeZone: ZoneId = ZoneId.of("Europe/Istanbul")
    private val cache = ConcurrentHashMap<String, List<Fixture>>()
    private val roundOrder = listOf(
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
    )

    private class Fixture(
        val id: String,
        val schedule: DemoCatalog.Schedule,
        val home: Team,
        val away: Team,
        val kickoff: Long,
        val broadcasts: List<Broadcast>,
        val round: String,
        val seed: Int
    ) {
        val sport: Sport get() = schedule.league.sport
    }

    private class LiveState(val status: MatchStatus, val minute: Int?, val period: Int?, val progress: Double)

    override suspend fun matches(sport: Sport, day: LocalDate, zone: ZoneId): List<Match> {
        val now = clock.millis()
        return DemoCatalog.schedules.asSequence()
            .filter { it.league.sport == sport }
            .flatMap { s -> (-1L..1L).asSequence().flatMap { fixtures(s, day.plusDays(it)).asSequence() } }
            .filter { Instant.ofEpochMilli(it.kickoff).atZone(zone).toLocalDate() == day }
            .map { toMatch(it, now) }
            .sortedWith(compareBy<Match>({ it.kickoffEpochMillis }, { it.league.priority }))
            .toList()
    }

    override suspend fun matchDetail(matchId: String): MatchDetail? {
        val parts = matchId.split('_')
        if (parts.size != 3) return null
        val schedule = DemoCatalog.schedule(parts[0]) ?: return null
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return null
        val fixture = fixtures(schedule, date).firstOrNull { it.id == matchId } ?: return null
        val now = clock.millis()
        val state = liveState(fixture.sport, fixture.kickoff, now)
        val match = toMatch(fixture, now)
        val events = if (fixture.sport == Sport.FOOTBALL && state.status != MatchStatus.SCHEDULED) {
            footballEvents(fixture).filter { state.status == MatchStatus.FINISHED || it.minute <= (state.minute ?: 0) }
        } else {
            emptyList()
        }
        val stats = if (fixture.sport == Sport.FOOTBALL && state.status != MatchStatus.SCHEDULED) {
            footballStats(fixture, state.progress, match.score ?: Score(0, 0))
        } else {
            emptyList()
        }
        return MatchDetail(match, events, stats, standings(schedule))
    }

    override suspend fun searchTeams(query: String): List<Team> {
        val q = ChannelKeys.fold(query).trim()
        if (q.isEmpty()) return emptyList()
        return DemoCatalog.allTeams
            .filter { ChannelKeys.fold(it.name).contains(q) || ChannelKeys.fold(it.shortName) == q }
            .sortedWith(compareBy<Team>({ it.sport.ordinal }, { it.name }))
            .take(30)
    }

    override suspend fun teams(ids: Set<String>): List<Team> = DemoCatalog.allTeams.filter { it.id in ids }

    override suspend fun nextMatch(teamId: String, fromEpochMillis: Long): Match? {
        val startDay = Instant.ofEpochMilli(fromEpochMillis).atZone(homeZone).toLocalDate()
        val schedules = DemoCatalog.schedules.filter { s -> s.teams.any { it.id == teamId } }
        for (d in 0L..21L) {
            val date = startDay.plusDays(d)
            val next = schedules.asSequence()
                .flatMap { fixtures(it, date).asSequence() }
                .filter { (it.home.id == teamId || it.away.id == teamId) && it.kickoff + durationMinutes(it.sport) * 60_000L > fromEpochMillis }
                .minByOrNull { it.kickoff }
            if (next != null) return toMatch(next, clock.millis())
        }
        return null
    }

    // ---- Program üretimi ----

    private fun fixtures(s: DemoCatalog.Schedule, date: LocalDate): List<Fixture> =
        cache.getOrPut("${s.league.id}|$date") { generate(s, date) }

    private fun currentRoundStart(): LocalDate {
        val today = Instant.ofEpochMilli(clock.millis()).atZone(homeZone).toLocalDate()
        return today.minusDays(roundOrder.indexOf(today.dayOfWeek).toLong())
    }

    private fun generate(s: DemoCatalog.Schedule, date: LocalDate): List<Fixture> {
        val count = s.perDay[date.dayOfWeek] ?: 0
        val n = s.teams.size - s.teams.size % 2
        if (count <= 0 || n < 2) return emptyList()
        // Bir "hafta" cumadan perşembeye sürer; o haftanın eşleşmeleri günlere sırayla dağıtılır.
        val dayPos = roundOrder.indexOf(date.dayOfWeek)
        val roundStart = date.minusDays(dayPos.toLong())
        val roundNo = Math.floorMod(roundStart.toEpochDay() / 7, (n - 1).toLong()).toInt()
        val teams = s.teams.take(n)
        // Çember yöntemi: ilk takım sabit, diğerleri her hafta bir sıra kayar.
        val rotated = listOf(teams[0]) + (0 until n - 1).map { teams[1 + (it + roundNo) % (n - 1)] }
        val pairs = (0 until n / 2).map { i ->
            if ((i + roundNo) % 2 == 0) rotated[i] to rotated[n - 1 - i] else rotated[n - 1 - i] to rotated[i]
        }
        val offset = roundOrder.take(dayPos).sumOf { s.perDay[it] ?: 0 }
        val week = s.week + ChronoUnit.WEEKS.between(currentRoundStart(), roundStart).toInt()
        return (0 until count).map { k ->
            val (home, away) = pairs[(offset + k) % pairs.size]
            val time = s.times[k % s.times.size]
            val id = "${s.league.id}_${date}_$k"
            val broadcasts = if (s.channels.isEmpty()) emptyList() else listOf(Broadcast(s.channels[(k + roundNo) % s.channels.size], "TR"))
            val round = when (s.league.sport) {
                Sport.TENNIS -> listOf("2. Tur", "Çeyrek final", "Yarı final")[k % 3]
                else -> "${week.coerceAtLeast(1)}. Hafta"
            }
            Fixture(
                id = id,
                schedule = s,
                home = home,
                away = away,
                kickoff = ZonedDateTime.of(date, time, homeZone).toInstant().toEpochMilli(),
                broadcasts = broadcasts,
                round = round,
                seed = id.hashCode()
            )
        }
    }

    // ---- Durum ve skor ----

    private fun durationMinutes(sport: Sport): Int = when (sport) {
        Sport.FOOTBALL -> 110
        Sport.BASKETBALL -> 120
        Sport.VOLLEYBALL -> 110
        Sport.TENNIS -> 120
    }

    private fun liveState(sport: Sport, kickoff: Long, now: Long): LiveState {
        val e = (now - kickoff) / 60_000L
        if (e < 0) return LiveState(MatchStatus.SCHEDULED, null, null, 0.0)
        if (e >= durationMinutes(sport)) return LiveState(MatchStatus.FINISHED, null, null, 1.0)
        return when (sport) {
            Sport.FOOTBALL -> when {
                e < 46 -> min(45, e.toInt() + 1).let { LiveState(MatchStatus.LIVE, it, null, it / 90.0) }
                e < 61 -> LiveState(MatchStatus.HALF_TIME, 45, null, 0.5)
                else -> min(90, 46 + (e - 61).toInt()).let { LiveState(MatchStatus.LIVE, it, null, it / 90.0) }
            }
            Sport.BASKETBALL -> LiveState(MatchStatus.LIVE, null, min(4, 1 + (e / 30).toInt()), e / 120.0)
            Sport.VOLLEYBALL -> LiveState(MatchStatus.LIVE, null, min(5, 1 + (e / 27).toInt()), e / 110.0)
            Sport.TENNIS -> LiveState(MatchStatus.LIVE, null, min(3, 1 + (e / 45).toInt()), e / 120.0)
        }
    }

    private fun toMatch(f: Fixture, now: Long): Match {
        val state = liveState(f.sport, f.kickoff, now)
        val score = if (state.status == MatchStatus.SCHEDULED) null else scoreAt(f, state)
        return Match(
            id = f.id,
            sport = f.sport,
            league = f.schedule.league,
            home = f.home,
            away = f.away,
            kickoffEpochMillis = f.kickoff,
            status = state.status,
            minute = state.minute,
            period = state.period,
            score = score,
            broadcasts = f.broadcasts,
            round = f.round
        )
    }

    private fun scoreAt(f: Fixture, state: LiveState): Score {
        val finished = state.status == MatchStatus.FINISHED
        return when (f.sport) {
            Sport.FOOTBALL -> {
                val goals = footballEvents(f).filter {
                    it.type == EventType.GOAL || it.type == EventType.PENALTY_GOAL || it.type == EventType.OWN_GOAL
                }.filter { finished || it.minute <= (state.minute ?: 0) }
                Score(goals.count { it.isHome }, goals.count { !it.isHome })
            }
            Sport.BASKETBALL -> {
                val r = Random(f.seed)
                var home = 72 + r.nextInt(27)
                val away = 70 + r.nextInt(27)
                if (home == away) home += 3
                if (finished) Score(home, away) else Score((home * state.progress).roundToInt(), (away * state.progress).roundToInt())
            }
            Sport.VOLLEYBALL, Sport.TENNIS -> {
                val r = Random(f.seed)
                val toWin = if (f.sport == Sport.VOLLEYBALL) 3 else 2
                val loser = r.nextInt(toWin)
                val final = if (r.nextBoolean()) Score(toWin, loser) else Score(loser, toWin)
                if (finished) final else setsSoFar(final, (state.period ?: 1) - 1)
            }
        }
    }

    /** Canlı set skoru: tamamlanan setler, maçın sonucuyla tutarlı dağıtılır. */
    private fun setsSoFar(final: Score, completed: Int): Score {
        val total = final.home + final.away
        if (completed <= 0 || total == 0) return Score(0, 0)
        var home = min(final.home, ceil(completed * final.home / total.toDouble()).toInt())
        val away = min(final.away, completed - home)
        if (home + away < completed) home = min(final.home, completed - away)
        return Score(home, away)
    }

    // ---- Maç detayı (futbol) ----

    private fun goalCount(r: Random, mean: Double): Int {
        val x = r.nextDouble()
        var p = exp(-mean)
        var cumulative = p
        var k = 0
        while (x > cumulative && k < 5) {
            k++
            p *= mean / k
            cumulative += p
        }
        return k
    }

    private fun footballEvents(f: Fixture): List<MatchEvent> {
        val r = Random(f.seed)
        val events = mutableListOf<MatchEvent>()
        // 1-11 ilk on bir, 12-23 yedekler: oyuna giren ve çıkan aynı kişi olmaz.
        fun player(home: Boolean) = DemoCatalog.playerName(if (home) f.home else f.away, r.nextInt(1, 12))
        fun substitute(home: Boolean, out: String): String {
            val team = if (home) f.home else f.away
            var slot = r.nextInt(12, 24)
            var name = DemoCatalog.playerName(team, slot)
            while (name == out && slot < 40) name = DemoCatalog.playerName(team, ++slot)
            return name
        }
        fun goal(home: Boolean) {
            val roll = r.nextInt(100)
            val type = when {
                roll < 8 -> EventType.PENALTY_GOAL
                roll < 11 -> EventType.OWN_GOAL
                else -> EventType.GOAL
            }
            // Kendi kalesine gol: gol yazılan taraf "home", atan oyuncu rakipten.
            val scorer = player(if (type == EventType.OWN_GOAL) !home else home)
            events += MatchEvent(1 + r.nextInt(90), type, home, scorer)
        }
        repeat(goalCount(r, 1.45)) { goal(true) }
        repeat(goalCount(r, 1.15)) { goal(false) }
        repeat(2 + r.nextInt(4)) {
            val home = r.nextBoolean()
            events += MatchEvent(10 + r.nextInt(80), EventType.YELLOW_CARD, home, player(home))
        }
        if (r.nextInt(10) == 0) {
            val home = r.nextBoolean()
            events += MatchEvent(50 + r.nextInt(40), EventType.RED_CARD, home, player(home))
        }
        for (home in listOf(true, false)) {
            repeat(3) {
                val out = player(home)
                events += MatchEvent(58 + r.nextInt(28), EventType.SUBSTITUTION, home, substitute(home, out), out)
            }
        }
        return events.sortedBy { it.minute }
    }

    /** İstatistik skorla tutarlı: isabetli şut en az gol kadar, şut en az isabetli şut kadar. */
    private fun footballStats(f: Fixture, progress: Double, score: Score): List<StatLine> {
        val r = Random(f.seed xor 0x5bd1e995)
        fun scaled(minValue: Int, maxValue: Int) = ((minValue + r.nextInt(maxValue - minValue + 1)) * progress).roundToInt()
        val possession = 38 + r.nextInt(25)
        val homeOnTarget = maxOf(score.home, scaled(1, 7))
        val awayOnTarget = maxOf(score.away, scaled(1, 6))
        val homeShots = homeOnTarget + scaled(2, 10)
        val awayShots = awayOnTarget + scaled(2, 9)
        return listOf(
            StatLine(StatKey.POSSESSION, possession, 100 - possession),
            StatLine(StatKey.SHOTS, homeShots, awayShots),
            StatLine(StatKey.SHOTS_ON_TARGET, homeOnTarget, awayOnTarget),
            StatLine(StatKey.CORNERS, scaled(1, 9), scaled(1, 8)),
            StatLine(StatKey.FOULS, scaled(6, 16), scaled(6, 16)),
            StatLine(StatKey.OFFSIDES, scaled(0, 4), scaled(0, 4))
        )
    }

    private fun standings(s: DemoCatalog.Schedule): List<StandingRow> {
        if (s.league.sport == Sport.TENNIS) return emptyList()
        val played = (s.week - 1).coerceAtLeast(0)
        val n = s.teams.size
        val rows = s.teams.mapIndexed { i, team ->
            val r = Random(team.id.hashCode() * 31 + s.league.id.hashCode())
            val strength = (n - i).toDouble() / n
            val wins = (played * (0.15 + 0.55 * strength) + r.nextDouble() * 1.5).toInt().coerceIn(0, played)
            val draws = if (s.league.sport == Sport.FOOTBALL) min(3, r.nextInt(0, played - wins + 1)) else 0
            val losses = played - wins - draws
            val goalsFor = wins * 2 + draws + r.nextInt(0, 4)
            val goalsAgainst = losses * 2 + draws + r.nextInt(0, 3)
            val points = when (s.league.sport) {
                Sport.FOOTBALL -> wins * 3 + draws
                Sport.BASKETBALL -> wins * 2 + losses
                else -> wins * 3
            }
            StandingRow(0, team, played, wins, draws, losses, goalsFor, goalsAgainst, points)
        }.sortedWith(
            compareByDescending<StandingRow> { it.points }.thenByDescending { it.goalDifference }.thenByDescending { it.goalsFor }
        )
        return rows.mapIndexed { index, row -> row.copy(position = index + 1) }
    }
}
