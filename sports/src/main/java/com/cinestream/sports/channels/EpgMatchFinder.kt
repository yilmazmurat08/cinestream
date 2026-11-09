package com.cinestream.sports.channels

import com.cinestream.sports.data.EpgProgramme
import com.cinestream.sports.model.Match
import kotlin.math.abs

/**
 * Yayın akışındaki "Canlı | Galatasaray - Fenerbahçe" gibi programları maçla eşler.
 * Maç verisinde kanal yazmasa bile kanal bulunabilir; yazıyorsa doğrulama olarak gösterilir.
 */
object EpgMatchFinder {
    private val livePrefix = Regex("^\\s*(canli|live|naklen)\\s*[:|\\-–]?\\s*")
    private val leaguePrefix = Regex("^[^:|]{2,40}[:|]\\s*(?=[^:|]+\\s(?:-|–|vs\\.?|v)\\s)")
    private val separator = Regex("\\s+(?:-|–|vs\\.?|v)\\s+")
    private val parens = Regex("\\([^)]*\\)")
    private val splitter = Regex("[^a-z0-9]+")
    private val generic = setOf("fk", "sk", "jk", "cf", "fc", "afc")

    data class Teams(val home: String, val away: String)

    /** "Sultanlar Ligi: VakıfBank - Eczacıbaşı Dynavit" → (vakifbank, eczacibasi dynavit). */
    fun parseTitle(title: String): Teams? {
        val t = ChannelKeys.fold(title).replace(livePrefix, "").replace(leaguePrefix, "")
        val parts = t.split(separator)
        if (parts.size != 2) return null
        val home = parts[0].replace(parens, "").trim()
        val away = parts[1].replace(parens, "").trim()
        return if (home.isEmpty() || away.isEmpty()) null else Teams(home, away)
    }

    private fun tokens(name: String): List<String> =
        ChannelKeys.fold(name).split(splitter).filter { it.length >= 3 && it !in generic }

    /** "Eczacıbaşı" ile "Eczacıbaşı Dynavit", "Fenerbahçe" ile "Fenerbahçe Beko" aynı takım sayılır. */
    fun sameTeam(a: String, b: String): Boolean {
        val x = tokens(a)
        val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x[0] == y[0] || x.any { it.length >= 5 && it in y }
    }

    fun find(match: Match, programmes: List<EpgProgramme>, windowMillis: Long = 30 * 60_000L): EpgProgramme? =
        programmes.firstOrNull { p ->
            abs(p.startEpochMillis - match.kickoffEpochMillis) <= windowMillis &&
                parseTitle(p.title)?.let { sameTeam(it.home, match.home.name) && sameTeam(it.away, match.away.name) } == true
        }
}
