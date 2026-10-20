package com.cinestream.sports.channels

import com.cinestream.sports.model.Broadcast
import com.cinestream.sports.model.PlaylistChannel
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Kanal adının karşılaştırılabilir anahtarı.
 * "TR | BEIN SPORTS 1 HD" → marka {bein, spor}, numara [1], kalite HD.
 */
data class ChannelKey(
    val brand: Set<String>,
    val numbers: List<Int>,
    /** 3 = HD/FHD, 2 = 4K ya da etiketsiz, 1 = SD. */
    val quality: Int,
    /** Adında "yedek" ya da "backup" geçiyor. */
    val backup: Boolean
) {
    /** Kalıcı eşleme anahtarı: liste yenilense de değişmez. */
    val id: String
        get() = brand.sorted().joinToString(" ") + if (numbers.isEmpty()) "" else " #" + numbers.joinToString(",")
}

object ChannelKeys {
    private val qualityTags = mapOf(
        "fhd" to 3, "hd" to 3, "1080p" to 3, "1080" to 3,
        "4k" to 2, "uhd" to 2, "2160p" to 2,
        "720p" to 1, "720" to 1, "sd" to 1
    )
    private val noise = setOf("tr", "tur", "turk", "turkey", "turkiye", "raw", "hevc", "h264", "h265", "vip", "live", "canli")
    private val backupWords = setOf("yedek", "backup")
    private val synonyms = mapOf("sports" to "spor", "sport" to "spor")
    private val brackets = Regex("\\[[^\\]]*]|\\([^)]*\\)|\\{[^}]*}")
    private val splitter = Regex("[^a-z0-9]+")
    private val backupRegex = Regex("\\b(yedek|backup)\\b")
    private val marks = Regex("\\p{Mn}+")

    /** Türkçe ve aksanlı harfleri sadeleştirip küçültür: "TRT Spor Yıldız" → "trt spor yildiz". */
    fun fold(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKD).replace(marks, "").replace('ı', 'i').lowercase(Locale.ROOT)

    fun of(raw: String): ChannelKey {
        val folded = fold(raw)
        val quality = folded.split(splitter).mapNotNull { qualityTags[it] }.maxOrNull() ?: 2
        val brand = sortedSetOf<String>()
        val numbers = mutableListOf<Int>()
        for (token in folded.replace(brackets, " ").split(splitter)) {
            if (token.isEmpty() || token in qualityTags || token in noise || token in backupWords) continue
            val number = token.toIntOrNull()
            if (number != null) numbers += number else brand += synonyms[token] ?: token
        }
        return ChannelKey(brand, numbers.sorted(), quality, backupRegex.containsMatchIn(folded))
    }
}

enum class MatchReason { MANUAL, AUTO }

/** Yayıncının kullanıcının listesindeki karşılığı. */
data class ChannelMatch(
    val broadcaster: String,
    val channel: PlaylistChannel,
    val score: Int,
    val reason: MatchReason,
    /** Aynı kanalın diğer sürümleri: yayın açılmazsa sırayla denenir. */
    val alternates: List<PlaylistChannel>
)

object ChannelMatcher {
    /** Bu puan ve üstü otomatik açılır; altı yalnızca öneri olur. */
    const val AUTO_OPEN = 85
    private val harmless = setOf("tv", "kanal", "channel")

    fun score(broadcaster: ChannelKey, channel: ChannelKey): Int {
        // Numara farklıysa asla eşleşmez: beIN 1 ≠ beIN 10 ≠ beIN MAX 1.
        if (broadcaster.numbers != channel.numbers) return 0
        return brandScore(broadcaster.brand, channel.brand)
    }

    fun brandScore(a: Set<String>, b: Set<String>): Int {
        if (a == b) return 100
        val (small, big) = if (a.size <= b.size) a to b else b to a
        if (big.containsAll(small) && (big - small).all { it in harmless }) return 90
        val union = (a union b).size
        if (union == 0) return 0
        val jaccard = (a intersect b).size.toDouble() / union
        return if (jaccard >= 0.6) (60 * jaccard).roundToInt() else 0
    }
}

/**
 * Kullanıcının kanal listesinin dizini. Anahtarlar bir kez hesaplanır ve marka kelimesine göre dizinlenir;
 * 20 bin kanallık listede de her maç için yalnızca ilgili kanallara bakılır.
 */
class ChannelIndex(channels: List<PlaylistChannel>) {

    private class Entry(val channel: PlaylistChannel, val key: ChannelKey) {
        val rank: Int get() = key.quality - if (key.backup) 10 else 0
    }

    private val items = channels.map { Entry(it, ChannelKeys.of(it.name)) }
    private val byToken: Map<String, List<Entry>> = buildMap<String, MutableList<Entry>> {
        items.forEach { e -> e.key.brand.forEach { token -> getOrPut(token) { mutableListOf() }.add(e) } }
    }

    val size: Int get() = items.size

    private fun candidates(key: ChannelKey): Collection<Entry> =
        if (key.brand.isEmpty()) items else key.brand.flatMap { byToken[it].orEmpty() }.toSet()

    /** [manual]: yayıncı anahtarı → kanal adı (kullanıcının elle eşlemesi, her zaman önce gelir). */
    fun match(broadcaster: String, manual: Map<String, String> = emptyMap()): ChannelMatch? {
        val key = ChannelKeys.of(broadcaster)
        manual[key.id]?.let { name ->
            items.firstOrNull { it.channel.name == name }?.let {
                return ChannelMatch(broadcaster, it.channel, 100, MatchReason.MANUAL, emptyList())
            }
        }
        val auto = candidates(key)
            .map { it to ChannelMatcher.score(key, it.key) }
            .filter { it.second >= ChannelMatcher.AUTO_OPEN }
            .sortedWith(
                compareByDescending<Pair<Entry, Int>> { it.second }
                    .thenByDescending { it.first.rank }
                    .thenBy { it.first.channel.name.length }
            )
        val best = auto.firstOrNull() ?: return null
        return ChannelMatch(broadcaster, best.first.channel, best.second, MatchReason.AUTO, auto.drop(1).map { it.first.channel })
    }

    /** Maçın yayıncıları arasından listede bulunan ilki (önce kullanıcının ülkesi). */
    fun matchFirst(broadcasts: List<Broadcast>, manual: Map<String, String>, country: String = "TR"): ChannelMatch? =
        broadcasts.sortedBy { if (it.countryCode == country) 0 else 1 }
            .firstNotNullOfOrNull { match(it.channelName, manual) }

    /** Otomatik açılmayacak kadar benzeyen kanallar ("Benzer: …"). */
    fun suggestions(broadcaster: String, limit: Int = 3): List<PlaylistChannel> {
        val key = ChannelKeys.of(broadcaster)
        return candidates(key)
            .map { it to ChannelMatcher.score(key, it.key) }
            .filter { it.second in 1 until ChannelMatcher.AUTO_OPEN }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.channel }
    }

    /** Kanal atama penceresi: aramaya uyan kanallar, yayıncıya en çok benzeyenler üstte. */
    fun search(query: String, broadcaster: String?, limit: Int = 60): List<Pair<PlaylistChannel, Boolean>> {
        val q = ChannelKeys.fold(query).trim()
        val key = broadcaster?.let { ChannelKeys.of(it) }
        return items.asSequence()
            .filter { q.isEmpty() || ChannelKeys.fold(it.channel.name).contains(q) }
            .map { e ->
                val s = if (key == null) 0 else maxOf(ChannelMatcher.score(key, e.key), ChannelMatcher.brandScore(key.brand, e.key.brand) / 2)
                Triple(e.channel, s, s >= 30)
            }
            .sortedWith(compareByDescending<Triple<PlaylistChannel, Int, Boolean>> { it.second }.thenBy { it.first.name })
            .take(limit)
            .map { it.first to it.third }
            .toList()
    }
}
