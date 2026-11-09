package com.cinestream.sports.data.remote

import com.cinestream.sports.model.Broadcast
import com.cinestream.sports.model.Country
import com.cinestream.sports.model.CrestPattern
import com.cinestream.sports.model.EventType
import com.cinestream.sports.model.League
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
import com.cinestream.sports.model.TeamColors
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Aracı sunucunun JSON biçimi. Liste yanıtları ligleri ve takımları bir kez verir, maçlar bunlara kimlikle bağlanır:
 *
 * GET /v1/matches?sport=football&date=2026-10-12&tz=Europe/Istanbul&lang=tr
 * {
 *   "leagues": [{"id":"tr-super","sport":"football","name":"Süper Lig","country":{"code":"TR","name":"Türkiye"},"major":true,"priority":1}],
 *   "teams":   [{"id":"gs","name":"Galatasaray","short":"GS","league":"tr-super","leagueName":"Süper Lig","sport":"football",
 *                "colors":{"primary":"#FDB913","secondary":"#A90432","pattern":"HALVES"}}],
 *   "matches": [{"id":"m1","league":"tr-super","home":"gs","away":"fb","kickoff":"2026-10-12T17:00:00Z","status":"LIVE",
 *                "minute":67,"period":null,"score":{"home":1,"away":0},"broadcasts":[{"channel":"beIN SPORTS 1","country":"TR"}],
 *                "round":"8. Hafta","venue":"RAMS Park"}]
 * }
 *
 * GET /v1/matches/{id} → aynı alanlar + "match", "events", "stats", "standings".
 * GET /v1/teams?q=… ya da ?ids=a,b → {"teams":[…]}. GET /v1/teams/{id}/next → maç listesi biçimi.
 *
 * Bilinmeyen alanlar yok sayılır; eksik ya da bozuk kayıt atlanır, liste yine gösterilir.
 */
object SportsJson {

    fun parseMatches(body: String): List<Match> {
        val root = JSONObject(body)
        val leagues = leagues(root)
        val teams = teams(root)
        val arr = root.optJSONArray("matches") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { match(it, leagues, teams) } }
    }

    fun parseTeams(body: String): List<Team> = teams(JSONObject(body)).values.toList()

    fun parseDetail(body: String): MatchDetail? {
        val root = JSONObject(body)
        val leagues = leagues(root)
        val teams = teams(root)
        val match = root.optJSONObject("match")?.let { match(it, leagues, teams) } ?: return null
        val events = root.optJSONArray("events").objects().mapNotNull { e ->
            val type = enumOrNull<EventType>(e.optString("type")) ?: return@mapNotNull null
            MatchEvent(e.optInt("minute"), type, e.optString("side") == "home", e.optStringOrNull("player").orEmpty(), e.optStringOrNull("detail"))
        }
        val stats = root.optJSONArray("stats").objects().mapNotNull { s ->
            val key = enumOrNull<StatKey>(s.optString("key")) ?: return@mapNotNull null
            StatLine(key, s.optInt("home"), s.optInt("away"))
        }
        val standings = root.optJSONArray("standings").objects().mapNotNull { r ->
            val team = teams[r.optString("team")] ?: return@mapNotNull null
            StandingRow(
                r.optInt("position"), team, r.optInt("played"), r.optInt("won"), r.optInt("drawn"),
                r.optInt("lost"), r.optInt("goalsFor"), r.optInt("goalsAgainst"), r.optInt("points")
            )
        }
        return MatchDetail(match, events, stats, standings)
    }

    private fun leagues(root: JSONObject): Map<String, League> = root.optJSONArray("leagues").objects().mapNotNull { l ->
        val sport = Sport.fromId(l.optString("sport")) ?: return@mapNotNull null
        val country = l.optJSONObject("country")
        League(
            id = l.optString("id").ifEmpty { return@mapNotNull null },
            sport = sport,
            name = l.optString("name"),
            country = Country(country?.optString("code").orEmpty(), country?.optString("name").orEmpty()),
            isMajor = l.optBoolean("major"),
            priority = l.optInt("priority", 100)
        )
    }.associateBy { it.id }

    private fun teams(root: JSONObject): Map<String, Team> = root.optJSONArray("teams").objects().mapNotNull { t ->
        val colors = t.optJSONObject("colors")
        Team(
            id = t.optString("id").ifEmpty { return@mapNotNull null },
            name = t.optString("name"),
            shortName = t.optString("short").ifEmpty { t.optString("name").take(3).uppercase() },
            colors = TeamColors(
                primary = color(colors?.optString("primary"), 0xFF6B6385),
                secondary = color(colors?.optString("secondary"), 0xFFFFFFFF),
                pattern = enumOrNull<CrestPattern>(colors?.optString("pattern").orEmpty()) ?: CrestPattern.SOLID
            ),
            leagueId = t.optString("league"),
            sport = Sport.fromId(t.optString("sport")) ?: Sport.FOOTBALL,
            leagueName = t.optString("leagueName")
        )
    }.associateBy { it.id }

    private fun match(m: JSONObject, leagues: Map<String, League>, teams: Map<String, Team>): Match? {
        val league = leagues[m.optString("league")] ?: return null
        val home = teams[m.optString("home")] ?: return null
        val away = teams[m.optString("away")] ?: return null
        val kickoff = runCatching { Instant.parse(m.optString("kickoff")).toEpochMilli() }.getOrNull() ?: return null
        val score = m.optJSONObject("score")?.let { Score(it.optInt("home"), it.optInt("away")) }
        return Match(
            id = m.optString("id").ifEmpty { return null },
            sport = league.sport,
            league = league,
            home = home,
            away = away,
            kickoffEpochMillis = kickoff,
            status = enumOrNull<MatchStatus>(m.optString("status")) ?: MatchStatus.SCHEDULED,
            minute = m.optIntOrNull("minute"),
            period = m.optIntOrNull("period"),
            score = score,
            broadcasts = m.optJSONArray("broadcasts").objects().mapNotNull { b ->
                b.optStringOrNull("channel")?.takeIf { it.isNotBlank() }?.let { Broadcast(it, b.optStringOrNull("country").orEmpty()) }
            },
            round = m.optStringOrNull("round"),
            venue = m.optStringOrNull("venue")
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

    /** JSON'daki null "null" yazısı olarak okunmasın: boş ya da null değer → null. */
    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name).ifEmpty { null }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        runCatching { java.lang.Enum.valueOf(T::class.java, name.uppercase()) }.getOrNull()

    /** "#RRGGBB" → 0xFFRRGGBB. */
    internal fun color(hex: String?, fallback: Long): Long {
        val h = hex?.trim()?.removePrefix("#") ?: return fallback
        if (h.length != 6) return fallback
        return h.toLongOrNull(16)?.let { 0xFF000000 or it } ?: fallback
    }
}
