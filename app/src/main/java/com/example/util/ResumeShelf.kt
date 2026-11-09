package com.example.util

import com.example.data.model.ContinueWatching
import com.example.data.model.SeriesParser

/** TV "Nerede kalmıştık" kartı: yarım kalan kayıt ya da ([isNext]) bitmiş bölümden sonraki bölüm. */
data class ResumeEntry(val cw: ContinueWatching, val isNext: Boolean = false)

/** [ResumeShelf.plan] çıktısı: [needsNext] ise kaydın bölümü bitmiş, yerine sıradaki bölüm aranır. */
data class ResumePlan(val cw: ContinueWatching, val needsNext: Boolean)

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

    /**
     * [hidden]: kullanıcının şeritten kaldırdığı yapımlar (anahtar → kaldırıldığı andaki son izleme zamanı). Kayıt
     * silinmez (kaldığı yer korunur); yalnızca o zamana kadarki izlemeler şeritte görünmez. Yapım yeniden izlenince
     * şeride geri gelir.
     */
    fun from(history: List<ContinueWatching>, limit: Int = LIMIT, hidden: Map<String, Long> = emptyMap()): List<ContinueWatching> =
        history.asSequence()
            .filter { it.itemType == "MOVIE" || it.itemType == "SERIES" }
            .filter { isUnfinished(it) }
            .filter { cw -> hidden[key(cw)]?.let { cw.lastPlayedAt > it } ?: true }
            .sortedByDescending { it.lastPlayedAt }
            // Aynı dizinin birden çok bölümü tek kart: en son izlenen bölüm.
            .distinctBy { key(it) }
            .take(limit)
            .toList()

    /**
     * TV şeridi: her yapımın EN SON izlenen kaydı karar verir (en yenisi başta). Yarım kalmışsa kendisi; dizi
     * bölümü bitmişse yerine sıradaki bölüm aranır ([ResumePlan.needsNext]); bitmiş film şeritte yer almaz.
     * Kaldırılanlar ([hidden]) [from] ile aynı kurala uyar.
     */
    fun plan(history: List<ContinueWatching>, limit: Int = LIMIT, hidden: Map<String, Long> = emptyMap()): List<ResumePlan> =
        history.asSequence()
            .filter { it.itemType == "MOVIE" || it.itemType == "SERIES" }
            .filter { it.progressSeconds >= MIN_PROGRESS_SECONDS }
            .filter { cw -> hidden[key(cw)]?.let { cw.lastPlayedAt > it } ?: true }
            .sortedByDescending { it.lastPlayedAt }
            .distinctBy { key(it) }
            .mapNotNull { cw ->
                when {
                    isUnfinished(cw) -> ResumePlan(cw, needsNext = false)
                    cw.itemType == "SERIES" && SeriesParser.parseEpisodeInfo(cw.itemName) != null -> ResumePlan(cw, needsNext = true)
                    else -> null
                }
            }
            .take(limit)
            .toList()

    /**
     * Dizinin bölümleri içinde [finished] bölümden sonraki bölüm (sezon/bölüm sırasıyla). Bölüm numarayla ya da
     * sezon-bölüm numarasıyla bulunur; son bölümse ya da bulunamazsa null.
     */
    fun nextEpisode(show: com.example.data.model.TvShow, finished: ContinueWatching): com.example.data.model.Episode? {
        val info = SeriesParser.parseEpisodeInfo(finished.itemName)
        val episodes = show.seasons.sortedBy { it.seasonNumber }.flatMap { s -> s.episodes.sortedBy { it.episodeNumber } }
        val index = episodes.indexOfFirst { it.item.id == finished.itemId }.takeIf { it >= 0 }
            ?: info?.let { i -> episodes.indexOfFirst { it.seasonNumber == i.season && it.episodeNumber == i.episode } }
            ?: -1
        return if (index < 0) null else episodes.getOrNull(index + 1)
    }

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

    fun key(cw: ContinueWatching): String =
        if (cw.itemType == "SERIES") {
            "S:" + (SeriesParser.parseEpisodeInfo(cw.itemName)?.showTitle ?: cw.itemName).trim().lowercase(java.util.Locale.ROOT)
        } else "M:${cw.itemId}"
}
