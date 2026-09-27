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
        val currentProg = programs.firstOrNull { it.isLiveNow }
        return ChannelEPG(
            channel = channel,
            programs = programs.map { it.copy(channelId = channel.id, channelName = channel.cleanedName) },
            currentProgram = currentProg?.copy(channelId = channel.id, channelName = channel.cleanedName)
        )
    }
}
