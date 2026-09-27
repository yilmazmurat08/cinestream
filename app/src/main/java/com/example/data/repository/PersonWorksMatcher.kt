package com.example.data.repository

import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.model.isPlaceholderCast
import com.example.data.model.isPlaceholderDirector
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonCreditsLookup
import com.example.data.model.tmdb.PersonWorkMatch
import com.example.data.model.tmdb.PersonWorksResult
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * "Bu oyuncunun kütüphanemdeki diğer yapımları" aramasını yapar. İki kaynak kullanır:
 *
 * 1) Sağlayıcının oyuncu listesi (M3UAndroid gibi açık kaynak IPTV oynatıcılarının "oyuncuya göre ara"
 *    yaklaşımı): Xtream get_series her dizi için cast döndürüyor, filmlerde de detayı açılmış
 *    öğelerin cast alanı dolu. Ağ gerektirmez, anında sonuç verir, TMDB çalışmasa bile işler.
 *
 * 2) TMDB filmografisi (person/{id}/combined_credits) + başlık eşleştirme.
 *    Eski eşleştirme ham başlıkları lowercase(Locale.ROOT) + contains ile kıyaslıyordu:
 *     - "KARADAYI" → "karadayi" ama TMDB "Karadayı" → "karadayı" (ı ≠ i) → eşleşmez
 *     - "ESARETİN BEDELİ" → "esareti̇n" (İ, i + birleşik nokta olur) → eşleşmez
 *     - yalnızca Türkçe başlık kıyaslanıyordu, "The Shawshank Redemption" gibi orijinal adlar kaçıyordu
 *     - contains() kısa başlıklarda yanlış eşleşme üretiyordu ("Red" → "Predator")
 *    Burada iki taraf da aynı katlamadan geçer (Türkçe harfler ve aksanlar sadeleşir), baş/sondaki
 *    "TR:", "4K", "(1994)", "Türkçe Dublaj" gibi etiketler atılır ve TAM eşitlikle, yıl kontrolüyle
 *    kıyaslanır. Türkçe + orijinal ad ve "Türkçe Ad - Orijinal Ad" biçimli çift dilli adlar desteklenir.
 */
object PersonWorksMatcher {

    private val APOSTROPHES = Regex("['’`´]")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val YEAR_TOKEN = Regex("(19|20)\\d\\d")
    private val PARENTHESES = Regex("\\([^)]*\\)")
    private val NAME_SEPARATORS = Regex("[,;/|&]")

    /** Sağlayıcıların başlığın başına/sonuna eklediği kalite, dil ve platform etiketleri (katlanmış halde). */
    private val TAG_TOKENS: Set<String> = setOf(
        "tr", "trk", "tur", "turk", "turkce", "en", "eng", "english",
        "dublaj", "dublajli", "dub", "dubbed", "altyazi", "altyazili", "sub", "subs", "subbed",
        "multi", "dual", "4k", "8k", "uhd", "fhd", "hd", "sd", "hq",
        "480p", "720p", "1080p", "2160p", "720", "1080", "2160",
        "hdr", "hdr10", "dv", "imax", "x264", "x265", "h264", "h265", "hevc", "avc", "10bit",
        "aac", "ac3", "dts", "atmos", "remux", "bluray", "bdrip", "brrip", "hdtv", "dvdrip",
        "webdl", "webrip", "hdrip", "camrip", "cam", "ts", "tc",
        "extended", "unrated", "remastered", "proper", "repack",
        "vod", "vizyon", "netflix", "nf", "amzn", "dsnp", "hbo", "exxen", "blutv"
    )
    private val TAG_PAIRS: Set<String> = setOf(
        "web dl", "web rip", "blu ray", "h 264", "h 265", "5 1", "7 1",
        "dolby vision", "hdr 10", "full hd", "tr dublaj", "tr altyazi"
    )

    // ------------------------------------------------------------------
    // Katlama ve isim yardımcıları
    // ------------------------------------------------------------------

    /** Türkçe duyarlı katlama: "KARADAYI", "Karadayı" ve "karadayi" aynı anahtara iner. */
    fun fold(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val lower = raw.lowercase(Locale.ROOT).replace('ı', 'i')
        val withoutMarks = COMBINING_MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(APOSTROPHES.replace(withoutMarks, ""), " ").trim()
    }

    /** "Sema Ergenekon, Eylem Canpolat" gibi çoklu isimlerde aramayı ilk isimle yapar. */
    fun primaryName(raw: String): String =
        raw.split(NAME_SEPARATORS).map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: raw.trim()

    private val castNamesCache = ConcurrentHashMap<String, Set<String>>()

    private fun foldedNames(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        castNamesCache[raw]?.let { return it }
        val names = raw.split(NAME_SEPARATORS)
            .map { fold(PARENTHESES.replace(it, " ")) }
            .filter { it.length >= 3 }
            .toSet()
        if (castNamesCache.size > 50_000) castNamesCache.clear()
        castNamesCache[raw] = names
        return names
    }

    // ------------------------------------------------------------------
    // Başlık varyantları
    // ------------------------------------------------------------------

    private data class TitleVariant(val key: String, val year: Int?)

    private fun tokens(folded: String): List<String> =
        if (folded.isEmpty()) emptyList() else folded.split(' ').filter { it.isNotEmpty() }

    private fun isYear(token: String): Boolean = YEAR_TOKEN.matches(token)

    /** Baştaki/sondaki etiketleri atar; geriye başlık kalmıyorsa (ör. "Dual", "Cam") dokunmaz. */
    private fun stripTags(tokens: List<String>): List<String> {
        var start = 0
        var end = tokens.size
        while (end > start) {
            if (end - start >= 2 && "${tokens[start]} ${tokens[start + 1]}" in TAG_PAIRS) {
                start += 2
                continue
            }
            if (end - start >= 2 && "${tokens[end - 2]} ${tokens[end - 1]}" in TAG_PAIRS) {
                end -= 2
                continue
            }
            if (tokens[start] in TAG_TOKENS) {
                start++
                continue
            }
            if (tokens[end - 1] in TAG_TOKENS) {
                end--
                continue
            }
            break
        }
        val stripped = tokens.subList(start, end).toList()
        return if (stripped.any { !isYear(it) }) stripped else tokens
    }

    private fun withoutLastYear(tokens: List<String>): Pair<List<String>, Int>? {
        val index = tokens.indexOfLast { isYear(it) }
        if (index < 0) return null
        val rest = tokens.filterIndexed { i, _ -> i != index }
        if (rest.isEmpty()) return null
        return rest to tokens[index].toInt()
    }

    private val libraryVariantCache = ConcurrentHashMap<String, List<TitleVariant>>()

    private fun libraryVariants(rawTitle: String): List<TitleVariant> {
        libraryVariantCache[rawTitle]?.let { return it }
        val all = tokens(fold(rawTitle))
        val result = ArrayList<TitleVariant>(4)
        fun add(parts: List<String>, year: Int?) {
            if (parts.isEmpty()) return
            val variant = TitleVariant(parts.joinToString(" "), year)
            if (variant !in result) result.add(variant)
        }
        if (all.isNotEmpty()) {
            val cleaned = stripTags(all)
            withoutLastYear(cleaned)?.let { (rest, year) -> add(stripTags(rest), year) }
            add(cleaned, null)
            withoutLastYear(all)?.let { (rest, year) -> add(rest, year) }
            add(all, null)
        }
        if (libraryVariantCache.size > 100_000) libraryVariantCache.clear()
        libraryVariantCache[rawTitle] = result
        return result
    }

    private fun creditVariants(credit: PersonCredit): Set<String> {
        val localized = fold(credit.title)
        val original = fold(credit.originalTitle)
        val out = LinkedHashSet<String>()
        for (title in listOf(localized, original)) {
            if (title.isEmpty()) continue
            out.add(title)
            val parts = tokens(title)
            val stripped = stripTags(parts)
            if (stripped.size >= 2) out.add(stripped.joinToString(" "))
            if (parts.size >= 3 && parts[0] == "the") out.add(parts.drop(1).joinToString(" "))
        }
        if (localized.isNotEmpty() && original.isNotEmpty() && localized != original) {
            out.add("$localized $original")   // "Esaretin Bedeli - The Shawshank Redemption"
            out.add("$original $localized")
        }
        return out
    }

    /** Dizi bölümlerinde ("Ezel S01 E05") dizi adını, diğerlerinde başlığın kendisini döndürür. */
    private fun libraryTitle(item: IPTVItem): String {
        if (item.type == "SERIES" && item.streamUrl.isNotBlank()) {
            val show = (SeriesParser.parseEpisodeInfo(item.cleanedName) ?: SeriesParser.parseEpisodeInfo(item.name))
                ?.showTitle
            if (!show.isNullOrBlank()) return show
        }
        return item.cleanedName.ifBlank { item.name }
    }

    /** Aynı yapımın farklı sürümlerini ("Yedi 1995" / "Yedi 4K") ve bölümlerini tek kayda indirger. */
    private fun contentKey(item: IPTVItem): String {
        val key = libraryVariants(libraryTitle(item)).firstOrNull()?.key
        return if (key.isNullOrEmpty()) "${item.type}_${item.id}" else "${item.type}:$key"
    }

    private fun yearCompatible(libraryYear: Int?, credit: PersonCredit): Boolean {
        if (libraryYear == null || credit.year == null) return true
        if (credit.mediaType != "movie") return true // dizilerde listedeki yıl sonraki bir sezona ait olabilir
        return abs(libraryYear - credit.year) <= 1
    }

    private fun episodeOrder(item: IPTVItem): Int {
        if (item.type != "SERIES" || item.streamUrl.isBlank()) return 0
        val info = SeriesParser.parseEpisodeInfo(item.cleanedName) ?: SeriesParser.parseEpisodeInfo(item.name)
        return if (info == null) Int.MAX_VALUE / 2 else info.season * 10_000 + info.episode
    }

    private fun isBetterRepresentative(candidate: IPTVItem, current: IPTVItem): Boolean {
        val candidateHasPoster = !candidate.logoUrl.isNullOrBlank()
        val currentHasPoster = !current.logoUrl.isNullOrBlank()
        if (candidateHasPoster != currentHasPoster) return candidateHasPoster
        return episodeOrder(candidate) < episodeOrder(current)
    }

    // ------------------------------------------------------------------
    // 1) Sağlayıcının oyuncu listesinde arama
    // ------------------------------------------------------------------

    fun searchByCast(personName: String, pool: List<IPTVItem>, excludeItemId: Int): List<PersonWorkMatch> {
        val wanted = fold(primaryName(personName))
        if (wanted.length < 3) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<PersonWorkMatch>()
        for (item in pool) {
            if (item.id == excludeItemId) continue
            if (item.type != "MOVIE" && item.type != "SERIES") continue
            val inCast = !item.isPlaceholderCast() && wanted in foldedNames(item.cast)
            val inDirector = !item.isPlaceholderDirector() && wanted in foldedNames(item.director)
            if (!inCast && !inDirector) continue
            if (seen.add(contentKey(item))) result.add(PersonWorkMatch(item, null))
        }
        return result.sortedByDescending { it.item.rating }
    }

    // ------------------------------------------------------------------
    // 2) TMDB filmografisini kütüphaneyle eşleştirme
    // ------------------------------------------------------------------

    fun match(
        lookup: PersonCreditsLookup,
        pool: List<IPTVItem>,
        excludeItemId: Int,
        maxNotInLibrary: Int = 30
    ): PersonWorksResult {
        val creditsByKey = HashMap<String, MutableList<PersonCredit>>()
        for (credit in lookup.credits) {
            for (key in creditVariants(credit)) {
                creditsByKey.getOrPut(key) { mutableListOf() }.add(credit)
            }
        }

        fun creditsFor(item: IPTVItem): List<PersonCredit> {
            val title = libraryTitle(item)
            if (title.isBlank()) return emptyList()
            val found = ArrayList<PersonCredit>(1)
            for (variant in libraryVariants(title)) {
                val candidates = creditsByKey[variant.key] ?: continue
                for (credit in candidates) {
                    if (credit in found) continue
                    if (!yearCompatible(variant.year, credit)) continue
                    found.add(credit)
                }
            }
            return found
        }

        // Şu an açık olan yapımın TMDB kaydını hiçbir listede göstermeyiz.
        val currentCreditKeys = HashSet<String>()
        for (item in pool) {
            if (item.id == excludeItemId) creditsFor(item).forEach { currentCreditKeys.add(it.key) }
        }

        val bestByCredit = LinkedHashMap<String, PersonWorkMatch>()
        for (item in pool) {
            if (item.id == excludeItemId) continue
            if (item.type != "MOVIE" && item.type != "SERIES") continue
            for (credit in creditsFor(item)) {
                if (credit.key in currentCreditKeys) continue
                val existing = bestByCredit[credit.key]
                if (existing == null || isBetterRepresentative(item, existing.item)) {
                    bestByCredit[credit.key] = PersonWorkMatch(item, credit)
                }
            }
        }

        val inLibrary = bestByCredit.values
            .sortedByDescending { it.credit?.popularity ?: 0.0 }
            .distinctBy { it.uiKey }
        val notInLibrary = lookup.credits
            .asSequence()
            .filter { it.key !in bestByCredit && it.key !in currentCreditKeys }
            .filter { !it.posterUrl.isNullOrBlank() }
            .sortedByDescending { it.popularity }
            .take(maxNotInLibrary)
            .toList()

        return PersonWorksResult(inLibrary = inLibrary, notInLibrary = notInLibrary)
    }

    /** Şu an açık olan yapımla aynı içerik mi? Farklı kaynaktan açılsa bile id yerine başlıkla karşılaştırır. */
    fun sameContent(item: IPTVItem, current: IPTVItem?): Boolean =
        current != null && contentKey(item) == contentKey(current)

    /** TMDB eşleşmeleri önce; sağlayıcı listesinden gelip TMDB'de karşılığı bulunmayanlar sonra. */
    fun merge(tmdbMatches: List<PersonWorkMatch>, castMatches: List<PersonWorkMatch>): List<PersonWorkMatch> {
        val seenContent = HashSet<String>()
        val seenUi = HashSet<String>()
        val out = ArrayList<PersonWorkMatch>(tmdbMatches.size + castMatches.size)
        for (match in tmdbMatches + castMatches) {
            if (!seenUi.add(match.uiKey)) continue
            if (!seenContent.add(contentKey(match.item))) continue
            out.add(match)
        }
        return out
    }
}
