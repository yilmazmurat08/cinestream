package com.example.util

import java.util.Locale

/**
 * Yetişkin içerik tespiti (kategori ve ada göre). Ana sayfa vitrinleri, Öne Çıkan alanı, oynatıcıdaki
 * kanal listesi ve ebeveyn kilidi bunu kullanır.
 */
object AdultContentFilter {

    private val keywords = listOf(
        "+18", "18+", "adult", "adults", "erotik", "erotic", "xxx", "porn", "porno",
        "nsfw", "yetiskin", "yetişkin", "for adult", "mature", "hentai", "sex",
        "brazzers", "playboy", "hustler", "redlight", "strip", "babes", "onlyfans",
        "hardcore", "softcore", "sensual", "ecchi", "cams", "erotica"
    )

    // Birçok sağlayıcı yetişkin kanalları "XX:" önekiyle işaretler (ör. "XX: Türk & Altyazılı").
    private val prefixRegex = Regex("""^\s*(?:\[\s*)?xx\s*[:|\]\-]""", RegexOption.IGNORE_CASE)

    private val wordRegex = Regex(
        """(?:\b|[^a-zA-Z0-9])(\+?18\+?|xxx|adults?|porn(?:o)?|erotik|erotic|nsfw|yetiskin|yetişkin|hentai|sex|brazzers)(?:\b|[^a-zA-Z0-9])""",
        RegexOption.IGNORE_CASE
    )

    fun isAdult(category: String, name: String = ""): Boolean {
        val lowerCat = category.lowercase(Locale.ROOT)
        val lowerName = name.lowercase(Locale.ROOT)
        if (keywords.any { kw -> lowerCat.contains(kw) || lowerName.contains(kw) }) return true
        if (prefixRegex.containsMatchIn(name) || prefixRegex.containsMatchIn(category)) return true
        return wordRegex.containsMatchIn(category) || (name.isNotEmpty() && wordRegex.containsMatchIn(name))
    }
}
