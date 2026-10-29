package com.example.util

import com.example.data.model.IPTVItem
import java.util.Locale

/**
 * Kesintisiz izleme: açılamayan ya da kurtarılamayan canlı yayın yerine aynı kanalın başka bir sürümü (HD, FHD,
 * 4K, yedek…) bulunur. Kanal adından kalite ve ülke ekleri atılır; "TRT 1 HD", "TR: TRT 1 FHD" ve "TRT 1 (Yedek)"
 * aynı kanal sayılır. Kanal numarası korunur: "beIN Sports 1" ile "beIN Sports 2" ayrı kanaldır.
 */
object BackupStreams {
    private val QUALITY = Regex(
        """\b(uhd|fhd|hd|sd|hq|lq|4k|8k|hevc|h\.?265|h\.?264|x265|1080[pi]?|720p|2160p|576p|480p|50 ?fps|60 ?fps|raw|backup|yedek)\b(\s*\d{1,2}\b)?"""
    )
    private val PREFIX = Regex("""^\s*([a-z]{2,3})\s*[:|\-]\s*""")
    private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")

    /** Kanalın kalite ve ülke eklerinden arınmış adı ("trt 1"). */
    fun baseName(name: String): String {
        var n = name.lowercase(Locale.ROOT).replace("̇", "").replace('ı', 'i')
        n = PREFIX.replace(n, "")
        n = BRACKETS.replace(n, " ")
        n = NON_WORD.replace(n, " ")
        n = QUALITY.replace(n, " ")
        return n.trim().replace(Regex("\\s+"), " ")
    }

    /** Veritabanında aday aramak için ad parçası (ilk kelime). */
    fun searchToken(name: String): String? = baseName(name).split(' ').firstOrNull { it.length >= 2 }

    /** Daha yüksek kalite önce: 4K/UHD > FHD > HD > adı eksiz > SD; yedek etiketliler sonra. */
    private fun qualityRank(name: String): Int {
        val n = name.lowercase(Locale.ROOT)
        val base = when {
            Regex("""\b(4k|uhd|2160p)\b""").containsMatchIn(n) -> 0
            Regex("""\b(fhd|1080[pi]?)\b""").containsMatchIn(n) -> 1
            Regex("""\b(hd|720p)\b""").containsMatchIn(n) -> 2
            Regex("""\b(sd|576p|480p|lq)\b""").containsMatchIn(n) -> 4
            else -> 3
        }
        return if (Regex("""\b(yedek|backup)\b""").containsMatchIn(n)) base + 10 else base
    }

    /**
     * [current] için denenmemiş yedekler, en uygunu başta: aynı adlı (eksiz) canlı kanallar; önce aynı klasördekiler,
     * sonra kaliteye göre. Aynı yayın adresi ve [tried] adresleri elenir.
     */
    fun candidates(current: IPTVItem, pool: List<IPTVItem>, tried: Set<String>): List<IPTVItem> {
        val base = baseName(current.cleanedName.ifBlank { current.name })
        if (base.isBlank()) return emptyList()
        return pool.asSequence()
            .filter { it.type == current.type && it.id != current.id }
            .filter { it.streamUrl.isNotBlank() && it.streamUrl != current.streamUrl && it.streamUrl !in tried }
            .filter { baseName(it.cleanedName.ifBlank { it.name }) == base }
            .distinctBy { it.streamUrl }
            .sortedWith(compareBy<IPTVItem>({ if (it.category == current.category) 0 else 1 }, { qualityRank(it.cleanedName.ifBlank { it.name }) }))
            .toList()
    }
}
