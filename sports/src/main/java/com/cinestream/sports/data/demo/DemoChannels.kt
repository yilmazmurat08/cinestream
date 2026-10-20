package com.cinestream.sports.data.demo

import com.cinestream.sports.channels.ChannelIndex
import com.cinestream.sports.data.ChannelProvider
import com.cinestream.sports.data.EpgLookup
import com.cinestream.sports.data.EpgProgramme
import com.cinestream.sports.data.SportsDataSource
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.ZoneId

/** Örnek IPTV listesi: gerçek listelerdeki gibi önekli, kaliteli ve yedekli adlar. */
object DemoChannels : ChannelProvider {
    val list: List<PlaylistChannel> = listOf(
        "TR | BEIN SPORTS 1 HD", "TR | BEIN SPORTS 1 SD (YEDEK)", "BEIN SPORTS 1 4K",
        "TR | BEIN SPORTS 2 HD", "TR | BEIN SPORTS 3 HD", "TR | BEIN SPORTS 4 HD", "TR | BEIN SPORTS MAX 1",
        "[TURK] S SPORT FHD", "TR || S SPORT 2 4K", "TR - TIVIBU SPOR 1 HD", "TR - TIVIBU SPOR 2 HD",
        "TR | EXXEN SPORTS 1 (1080p)", "TR: TRT SPOR HD", "TR: TRT SPOR YILDIZ HD", "TR | A SPOR HD",
        "TR | TRT 1 HD", "TR | SPOR SMART HD", "TR | HABERTURK HD", "TR | NTV HD", "TR | SHOW TV HD"
    ).mapIndexed { i, name -> PlaylistChannel("c${i + 1}", name) }

    override val channels: Flow<List<PlaylistChannel>> = flowOf(list)
}

/**
 * Örnek yayın akışı: kanalı listede olan maçlar için "Canlı | Ev - Deplasman" programı üretir. Ayrıca kanal
 * bilgisi olmayan bir 3. Lig maçını TRT Spor'un akışına koyar; böylece "yayın akışında bulundu" durumu görülür.
 */
class DemoEpgLookup(private val source: SportsDataSource, private val zone: ZoneId = ZoneId.of("Europe/Istanbul")) : EpgLookup {
    private val index = ChannelIndex(DemoChannels.list)

    override suspend fun programmes(fromEpochMillis: Long, toEpochMillis: Long): List<EpgProgramme> {
        val first = Instant.ofEpochMilli(fromEpochMillis).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(toEpochMillis).atZone(zone).toLocalDate()
        val result = mutableListOf<EpgProgramme>()
        var day = first
        while (!day.isAfter(last)) {
            for (sport in Sport.entries) {
                source.matches(sport, day, zone).forEach { m ->
                    val channel = index.matchFirst(m.broadcasts, emptyMap())?.channel
                        ?: if (m.league.id == "tr-3lig" && m.broadcasts.isEmpty()) DemoChannels.list.first { it.name == "TR: TRT SPOR HD" } else null
                    if (channel != null) {
                        result += EpgProgramme(
                            channel = channel,
                            title = "Canlı | ${m.home.name} - ${m.away.name}",
                            startEpochMillis = m.kickoffEpochMillis - 5 * 60_000L,
                            endEpochMillis = m.kickoffEpochMillis + 115 * 60_000L
                        )
                    }
                }
            }
            day = day.plusDays(1)
        }
        return result.filter { it.endEpochMillis >= fromEpochMillis && it.startEpochMillis <= toEpochMillis }
    }
}
