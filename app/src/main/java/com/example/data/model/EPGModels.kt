package com.example.data.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class EPGProgram(
    val id: String,
    val channelId: Int,
    val channelName: String,
    val title: String,
    val description: String,
    val category: String, // "Haber", "Spor", "Sinema", "Belgesel", "Dizi", "Eğlence", "Çocuk"
    val startTimeFormatted: String, // e.g. "20:00"
    val endTimeFormatted: String,   // e.g. "22:30"
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val rating: Double = 8.0,
    val isLiveNow: Boolean = false,
    val progressPercent: Float = 0f
) : Serializable

@Immutable
data class ChannelEPG(
    val channel: IPTVItem,
    val programs: List<EPGProgram>,
    val currentProgram: EPGProgram? = null
) : Serializable
