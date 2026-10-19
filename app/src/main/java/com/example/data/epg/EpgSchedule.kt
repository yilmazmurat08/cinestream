package com.example.data.epg

import com.example.data.model.EPGProgram

/** Kanal kartında açılan yayın akışı: şu an yayında olan program ve sonraki saatlerde başlayanlar. */
object EpgSchedule {

    /**
     * [now] anında bitmemiş ve [hours] saat içinde başlayan programlar, başlangıca göre sıralı. "Şimdi yayında" ve
     * ilerleme [now]'a göre hesaplanır (rehber saatlerce bellekte kalır; indirildiği ana göre değil).
     */
    fun window(programs: List<EPGProgram>, now: Long, hours: Int): List<EPGProgram> {
        val until = now + hours * 60L * 60L * 1000L
        return programs
            .asSequence()
            .filter { it.endEpochMillis > now && it.startEpochMillis < until && it.endEpochMillis > it.startEpochMillis }
            .sortedBy { it.startEpochMillis }
            .distinctBy { it.startEpochMillis }
            .map { p ->
                val live = now >= p.startEpochMillis && now < p.endEpochMillis
                val progress = if (live) {
                    ((now - p.startEpochMillis).toFloat() / (p.endEpochMillis - p.startEpochMillis).toFloat()).coerceIn(0f, 1f)
                } else 0f
                p.copy(isLiveNow = live, progressPercent = progress)
            }
            .toList()
    }
}
