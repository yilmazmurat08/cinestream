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

    /**
     * Kategori ya da yapım yetişkin içerik mi. Kategori adı geniş kelime listesiyle denetlenir; yapım adı yalnızca
     * kesin işaretlerle ([isAdultTitle]). Böylece "Sex Education", "Essex", "Stripes" gibi sıradan adlar yetişkin
     * sayılmaz.
     */
    fun isAdult(category: String, name: String = ""): Boolean =
        isAdultCategory(category) || (name.isNotEmpty() && isAdultTitle(name))

    /** Kategori (klasör) adı yetişkin içerik belirtiyor mu. */
    fun isAdultCategory(category: String): Boolean {
        if (category.isBlank()) return false
        val lowerCat = category.lowercase(Locale.ROOT)
        if (keywords.any { kw -> lowerCat.contains(kw) }) return true
        return prefixRegex.containsMatchIn(category) || wordRegex.containsMatchIn(category)
    }

    // Yapım adında yalnızca kesin işaretler (tek başına kelime olarak): "sex", "mature", "strip", "adult" gibi
    // sıradan film adlarında da geçen kelimeler sayılmaz.
    private val titleRegex = Regex(
        """(?:^|[^a-zA-Z0-9])(\+18|18\+|xxx|porn(?:o)?|erotik|erotic|erotica|nsfw|hentai|brazzers|onlyfans)(?:$|[^a-zA-Z0-9])""",
        RegexOption.IGNORE_CASE
    )

    /** Yapım/kanal adı kesin olarak yetişkin içerik mi ("XX:" öneki ya da açık işaretler). */
    fun isAdultTitle(name: String): Boolean =
        prefixRegex.containsMatchIn(name) || titleRegex.containsMatchIn(name)
}
