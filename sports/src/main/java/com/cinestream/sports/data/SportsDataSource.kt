package com.cinestream.sports.data

import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchDetail
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.Team
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Maç verisinin geldiği yer. Arayüz yalnızca bu sözleşmeyi bilir; örnek veri ([demo.DemoSportsDataSource])
 * ile lisanslı servise bağlanan uzak kaynak ([remote.RemoteSportsDataSource]) birbirinin yerine takılabilir.
 */
interface SportsDataSource {
    /** [day] gününde, [zone] saat dilimine göre başlayan maçlar. */
    suspend fun matches(sport: Sport, day: LocalDate, zone: ZoneId): List<Match>

    suspend fun matchDetail(matchId: String): MatchDetail?

    /** Takip etmek için takım arama (ad ya da kısa ad, Türkçe harf duyarsız). */
    suspend fun searchTeams(query: String): List<Team>

    suspend fun teams(ids: Set<String>): List<Team>

    /** Takımın [fromEpochMillis] anından sonraki ilk maçı. */
    suspend fun nextMatch(teamId: String, fromEpochMillis: Long): Match?
}

/** Kullanıcının IPTV listesindeki canlı kanallar. CineStream'de veritabanındaki canlı kanallardan beslenir. */
interface ChannelProvider {
    val channels: Flow<List<PlaylistChannel>>
}

/** Kanalın yayın akışındaki bir program (CineStream'deki EPG verisinden). */
data class EpgProgramme(
    val channel: PlaylistChannel,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long
)

/** Yayın akışına erişim. İsteğe bağlıdır; verilmezse "yayın akışında da var" doğrulaması yapılmaz. */
fun interface EpgLookup {
    suspend fun programmes(fromEpochMillis: Long, toEpochMillis: Long): List<EpgProgramme>
}
