package com.example.data.epg

import com.example.data.model.ChannelEPG
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem

object EPGProvider {
    fun generateEPGForChannels(
        channels: List<IPTVItem>,
        realProgramsByChannelId: Map<String, List<EPGProgram>> = emptyMap()
    ): List<ChannelEPG> {
        return channels.map { channel ->
            generateEPGForSingleChannel(channel, realProgramsByChannelId)
        }
    }

    fun generateEPGForSingleChannel(
        channel: IPTVItem,
        realProgramsByChannelId: Map<String, List<EPGProgram>> = emptyMap()
    ): ChannelEPG {
        val matchKey = channel.tvgId?.lowercase(java.util.Locale.ROOT)?.trim()
        val programs = if (!matchKey.isNullOrBlank()) {
            realProgramsByChannelId[matchKey] ?: emptyList()
        } else {
            emptyList()
        }
        // "Şimdi canlı" ve ilerleme rehberin indirildiği ana göre değil, şu ana göre hesaplanır (rehber saatlerce
        // bellekte kalıyor; eskiden biten program canlı, yayındaki program "başlamadı" görünüyordu).
        val now = System.currentTimeMillis()
        val current = programs.map { program ->
            val live = now >= program.startEpochMillis && now < program.endEpochMillis
            val progress = when {
                live -> ((now - program.startEpochMillis).toFloat() /
                    (program.endEpochMillis - program.startEpochMillis).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                now >= program.endEpochMillis -> 1f
                else -> 0f
            }
            program.copy(channelId = channel.id, channelName = channel.cleanedName, isLiveNow = live, progressPercent = progress)
        }
        return ChannelEPG(
            channel = channel,
            programs = current,
            currentProgram = current.firstOrNull { it.isLiveNow }
        )
    }
}
