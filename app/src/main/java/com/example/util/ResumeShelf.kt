package com.example.util

import com.example.data.model.ContinueWatching
import com.example.data.model.SeriesParser

/**
 * Kaydedilenler'deki "Nerede kalmıştık" şeridi: yarım kalan son film ve diziler (en yenisi başta).
 * Kayıtlar izleme geçmişinden (continue_watching) gelir; yeni bir izleme eklenince en eskisi şeritten düşer.
 */
object ResumeShelf {
    const val LIMIT = 5

    /** Sonuna bu kadar kala izlenen yapım bitmiş sayılır (jenerik). */
    private const val FINISHED_FRACTION = 0.95

    /** Birkaç saniyelik açıp kapatmalar "yarım kalan" sayılmaz. */
    private const val MIN_PROGRESS_SECONDS = 30L

    fun from(history: List<ContinueWatching>, limit: Int = LIMIT): List<ContinueWatching> =
        history.asSequence()
            .filter { it.itemType == "MOVIE" || it.itemType == "SERIES" }
            .filter { isUnfinished(it) }
            .sortedByDescending { it.lastPlayedAt }
            // Aynı dizinin birden çok bölümü tek kart: en son izlenen bölüm.
            .distinctBy { key(it) }
            .take(limit)
            .toList()

    fun isUnfinished(cw: ContinueWatching): Boolean {
        if (cw.progressSeconds < MIN_PROGRESS_SECONDS) return false
        if (cw.totalSeconds <= 0L) return true
        return cw.progressSeconds < cw.totalSeconds * FINISHED_FRACTION
    }

    /** Kalan dakika (süre bilinmiyorsa null). */
    fun minutesLeft(cw: ContinueWatching): Int? =
        if (cw.totalSeconds <= 0L) null else (((cw.totalSeconds - cw.progressSeconds).coerceAtLeast(0L) + 59L) / 60L).toInt()

    /** 0..1 arası ilerleme (süre bilinmiyorsa null). */
    fun progress(cw: ContinueWatching): Float? =
        if (cw.totalSeconds <= 0L) null else (cw.progressSeconds.toFloat() / cw.totalSeconds).coerceIn(0f, 1f)

    private fun key(cw: ContinueWatching): String =
        if (cw.itemType == "SERIES") {
            "S:" + (SeriesParser.parseEpisodeInfo(cw.itemName)?.showTitle ?: cw.itemName).trim().lowercase(java.util.Locale.ROOT)
        } else "M:${cw.itemId}"
}
