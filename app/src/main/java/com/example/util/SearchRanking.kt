package com.example.util

import com.example.data.model.IPTVItem
import java.util.Locale

/**
 * Arama sonuçlarını yakınlığa göre sıralar: adı aranan sözle aynı olanlar en üstte, sonra adı onunla başlayanlar,
 * sonra bir kelimesi onunla başlayanlar, sonra adının ortasında geçenler. Yalnızca klasör adı eşleşen yapımlar
 * (adında aranan söz yok) en sonda ve yalnızca adı eşleşen hiçbir sonuç yoksa gösterilir; böylece aranan yapımın
 * yerine alakasız yapımlar çıkmaz. Türkçe harfler (ş/s, ı/i…) ve büyük-küçük harf fark etmez.
 */
object SearchRanking {

    private const val EXACT = 0
    private const val PREFIX = 1
    private const val WORD_PREFIX = 2
    private const val CONTAINS = 3
    private const val CATEGORY_ONLY = 4
    private const val NONE = 5

    fun rank(query: String, items: List<IPTVItem>, limit: Int = 200): List<IPTVItem> {
        val q = fold(query)
        if (q.isEmpty()) return items.take(limit)
        val scored = items.map { it to score(q, it) }.filter { it.second != NONE }
        val titleMatches = scored.any { it.second <= CONTAINS }
        return scored
            .filter { !titleMatches || it.second <= CONTAINS }
            .sortedWith(compareBy({ it.second }, { typeOrder(it.first.type) }, { title(it.first).length }))
            .take(limit)
            .map { it.first }
    }

    private fun score(q: String, item: IPTVItem): Int {
        val names = listOf(title(item), fold(item.name)).distinct()
        var best = NONE
        for (name in names) {
            val s = when {
                name == q -> EXACT
                name.startsWith(q) -> PREFIX
                (" $name").contains(" $q") -> WORD_PREFIX
                name.contains(q) -> CONTAINS
                else -> NONE
            }
            if (s < best) best = s
        }
        if (best == NONE && fold(item.category).contains(q)) best = CATEGORY_ONLY
        return best
    }

    private fun typeOrder(type: String): Int = when (type) {
        "MOVIE" -> 0
        "SERIES" -> 0
        "LIVE" -> 1
        else -> 2
    }

    /** Yapımın sade adı: "TR: Oppenheimer (2023) 4K" → "oppenheimer 2023 4k" (ön ek atılır, işaretler boşluk olur). */
    private fun title(item: IPTVItem): String {
        var name = item.cleanedName.ifBlank { item.name }.trim()
        val colon = name.indexOf(':')
        if (colon in 1..4) name = name.substring(colon + 1)
        return fold(name)
    }

    /** Türkçe karşılığı olan harfler: veritabanında her biri tek karakter joker ("_") olur (bkz. [sqlPattern]). */
    private const val TURKISH_PAIRS = "ıİiIşŞsSçÇcCğĞgGöÖoOüÜuU"

    /**
     * Veritabanı ön süzgeci için LIKE kalıbı. SQLite büyük/küçük harfi yalnızca İngilizce harflerde eşler: "şerbet"
     * araması "ŞERBET"i, "kizilcik" "KIZILCIK"ı bulmuyordu. Türkçe karşılığı olan harfler tek karakter joker, kelime
     * araları ve işaretler ("la.brea", "spider-man") herhangi bir şey olur. Kalıp fazlasını getirir; kesin eşleşme ve
     * sıralama [rank] ile yapılır.
     */
    fun sqlPattern(query: String): String {
        val out = StringBuilder("%")
        for (ch in query.trim()) {
            when {
                !ch.isLetterOrDigit() -> if (out.last() != '%') out.append('%')
                ch in TURKISH_PAIRS || ch.code > 127 -> out.append('_')
                else -> out.append(ch)
            }
        }
        if (out.last() != '%') out.append('%')
        return out.toString()
    }

    fun fold(text: String): String =
        text.lowercase(Locale.forLanguageTag("tr"))
            .replace('ş', 's').replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ü', 'u')
            .replace('â', 'a').replace('î', 'i').replace('û', 'u')
            .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}
