package com.cinestream.sports.model

/**
 * Spor modülünün veri modelleri. Hiçbiri veri kaynağına (örnek veri, uzak servis) bağlı değildir;
 * kaynak değişince arayüz ve iş kuralları aynı kalır.
 */

enum class Sport(val id: String) {
    FOOTBALL("football"),
    BASKETBALL("basketball"),
    VOLLEYBALL("volleyball"),
    TENNIS("tennis");

    companion object {
        fun fromId(id: String): Sport? = entries.firstOrNull { it.id == id }
    }
}

data class Country(val code: String, val name: String)

data class League(
    val id: String,
    val sport: Sport,
    val name: String,
    val country: Country,
    /** "Büyük ligler" filtresinde görünür. */
    val isMajor: Boolean,
    /** Listede sıralama: küçük olan önce gelir. */
    val priority: Int
)

/** Kulüp logosu yerine çizilen sade arma deseni (marka logosu kullanılmaz). */
enum class CrestPattern { SOLID, HALVES, STRIPES, HOOPS, SASH, QUARTERS, CHEVRON, ROUND }

data class TeamColors(val primary: Long, val secondary: Long, val pattern: CrestPattern)

data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val colors: TeamColors,
    val leagueId: String,
    val sport: Sport = Sport.FOOTBALL,
    /** Takımın kendi ligi (takip edilenler ve aramada gösterilir). */
    val leagueName: String = ""
)

enum class MatchStatus { SCHEDULED, LIVE, HALF_TIME, FINISHED, POSTPONED }

data class Score(val home: Int, val away: Int)

/** Maçı yayınlayan kanal (veri kaynağının verdiği ad, ör. "beIN SPORTS 1"). */
data class Broadcast(val channelName: String, val countryCode: String)

data class Match(
    val id: String,
    val sport: Sport,
    val league: League,
    val home: Team,
    val away: Team,
    val kickoffEpochMillis: Long,
    val status: MatchStatus,
    /** Futbolda dakika (ör. 67). */
    val minute: Int? = null,
    /** Basketbolda çeyrek, voleybol ve teniste set numarası. */
    val period: Int? = null,
    val score: Score? = null,
    val broadcasts: List<Broadcast> = emptyList(),
    val round: String? = null,
    val venue: String? = null
) {
    val isLive: Boolean get() = status == MatchStatus.LIVE || status == MatchStatus.HALF_TIME
    val isFinished: Boolean get() = status == MatchStatus.FINISHED
    fun involves(teamId: String): Boolean = home.id == teamId || away.id == teamId
}

enum class EventType { GOAL, PENALTY_GOAL, OWN_GOAL, YELLOW_CARD, RED_CARD, SUBSTITUTION }

data class MatchEvent(
    val minute: Int,
    val type: EventType,
    val isHome: Boolean,
    val player: String,
    val detail: String? = null
)

enum class StatKey { POSSESSION, SHOTS, SHOTS_ON_TARGET, CORNERS, FOULS, OFFSIDES }

data class StatLine(val key: StatKey, val home: Int, val away: Int)

data class StandingRow(
    val position: Int,
    val team: Team,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

data class MatchDetail(
    val match: Match,
    val events: List<MatchEvent>,
    val stats: List<StatLine>,
    val standings: List<StandingRow>
)

/** Kullanıcının IPTV listesindeki bir canlı kanal. CineStream tarafında IPTVItem'dan üretilir. */
data class PlaylistChannel(val id: String, val name: String)

enum class NotificationType { REMINDER, KICKOFF, GOAL, FULL_TIME }

/** Bildirim geçmişindeki bir kayıt. */
data class NotificationEntry(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timeEpochMillis: Long,
    val matchId: String?
)
