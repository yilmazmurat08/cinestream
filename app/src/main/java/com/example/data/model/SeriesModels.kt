package com.example.data.model

import androidx.compose.runtime.Immutable
import java.io.Serializable
import java.util.Locale

@Immutable
data class Episode(
    val id: Int,
    val name: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val streamUrl: String = "",
    val logoUrl: String? = null,
    val item: IPTVItem
) : Serializable

@Immutable
data class Season(
    val seasonNumber: Int,
    val episodes: List<Episode>
) : Serializable

@Immutable
data class TvShow(
    val id: Int,
    val title: String,
    val logoUrl: String?,
    val category: String,
    val rating: Double,
    val summary: String,
    val cast: String,
    val director: String,
    val isFavorite: Boolean,
    val seasons: List<Season>,
    val platformName: String = ""
) : Serializable

@Immutable
data class Platform(
    val name: String,
    val logoUrl: String? = null,
    val shows: List<TvShow>
) : Serializable

object SeriesParser {
    // Cache map for parsed results to optimize performance and completely avoid main thread regex ANR
    private val parseCache = java.util.concurrent.ConcurrentHashMap<String, ParsedEpisodeInfo>()

    // Regex patterns for seasons and episodes
    private val patterns = listOf(
        // Pattern 1: S01E01 / S1E1 / S01 E01 / S1 E1 / s01e01 (case insensitive)
        Regex("(?i)(.*?)\\bS(\\d+)\\s*E(\\d+)\\b(.*)"),
        // Pattern 2: 1x02 / 01x02 (case insensitive)
        Regex("(?i)(.*?)\\b(\\d+)\\s*x\\s*(\\d+)\\b(.*)"),
        // Pattern 3: Season 1 Episode 2 / Sezon 1 Bölüm 2 (TR/EN supports)
        Regex("(?i)(.*?)\\b(?:Season|Sezon|S)\\s*(\\d+)\\s*(?:Episode|Bölüm|E)\\s*(\\d+)\\b(.*)"),
        // Pattern 4: Sezon 1 Bölüm 2 style with prefix or space
        Regex("(?i)(.*?)\\b(?:Season|Sezon)\\s*(\\d+)\\s*(.*)") // Just season first if episode not found
    )

    data class ParsedEpisodeInfo(
        val showTitle: String,
        val season: Int,
        val episode: Int,
        val episodeName: String
    )

    private data class PlatformParsedItem(
        val item: IPTVItem,
        val parsedInfo: ParsedEpisodeInfo,
        val platformName: String,
        val category: String
    )

    fun parseEpisodeInfo(rawName: String): ParsedEpisodeInfo? {
        val cached = parseCache[rawName]
        if (cached != null) return cached

        val normalizedName = rawName.replace(Regex("^[A-ZÇĞİÖŞÜ]{2,4}:\\s*"), "").trim().ifEmpty { rawName }

        var result: ParsedEpisodeInfo? = null
        for (pattern in patterns) {
            val matchResult = pattern.matchEntire(normalizedName) ?: pattern.find(normalizedName)
            if (matchResult != null) {
                val groups = matchResult.groupValues
                if (groups.size >= 4) {
                    val showTitleRaw = groups[1].trim()
                    val showTitle = cleanTitle(showTitleRaw)
                    
                    val season = groups[2].toIntOrNull() ?: 1
                    val episode = groups[3].toIntOrNull() ?: 1
                    
                    val rest = groups.getOrNull(4)?.trim() ?: ""
                    val episodeName = if (rest.startsWith("-") || rest.startsWith(":") || rest.startsWith("/")) {
                        rest.substring(1).trim()
                    } else {
                        rest
                    }.ifEmpty { "Bölüm $episode" }

                    result = ParsedEpisodeInfo(showTitle, season, episode, episodeName)
                    break
                }
            }
        }
        
        if (result == null) {
            val simpleS = Regex("(?i)S(\\d+)").find(normalizedName)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val simpleE = Regex("(?i)E(\\d+)").find(normalizedName)?.groupValues?.getOrNull(1)?.toIntOrNull()

            if (simpleS != null || simpleE != null) {
                val cleanName = cleanTitle(normalizedName.replace(Regex("(?i)S\\d+"), "").replace(Regex("(?i)E\\d+"), ""))
                result = ParsedEpisodeInfo(cleanName, simpleS ?: 1, simpleE ?: 1, "Bölüm ${simpleE ?: 1}")
            } else {
                val trailingNumberMatch = Regex("""^(.*?)[\s\-–—:]+(\d{1,3})\s*$""").find(normalizedName)
                if (trailingNumberMatch != null) {
                    val titlePart = trailingNumberMatch.groupValues[1].trim()
                    val epNum = trailingNumberMatch.groupValues[2].toIntOrNull() ?: 1
                    val wordCount = titlePart.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    if (titlePart.isNotBlank() && wordCount >= 2) {
                        FallbackDiagnostics.recordSample(rawName)
                        result = ParsedEpisodeInfo(cleanTitle(titlePart), 1, epNum, "Bölüm $epNum")
                    }
                }
                if (result == null) {
                    val cleanName = cleanTitle(normalizedName)
                    result = ParsedEpisodeInfo(cleanName, 1, 1, "Bölüm 1")
                }
            }
        }
        
        parseCache[rawName] = result
        return result
    }

    object FallbackDiagnostics {
        private val samples = java.util.Collections.synchronizedList(mutableListOf<String>())
        fun recordSample(rawName: String) {
            if (samples.size < 40) samples.add(rawName)
        }
        fun getSamples(): List<String> = samples.toList()
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("^[-:\\s|/]+"), "")
            .replace(Regex("[-:\\s|/]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun groupItemsIntoPlatforms(items: List<IPTVItem>, seriesCovers: Map<String, String> = emptyMap()): List<Platform> {
        val seriesItems = items.filter { it.type == "SERIES" }

        val canonicalNamesLongestFirst = seriesCovers.keys.sortedByDescending { it.length }
        fun titleCase(s: String): String = s.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        val parsedList = seriesItems.map { item ->
            var parsedInfo = parseEpisodeInfo(item.cleanedName) ?: parseEpisodeInfo(item.name) ?: ParsedEpisodeInfo(
                showTitle = item.cleanedName,
                season = 1,
                episode = 1,
                episodeName = "Bölüm 1"
            )

            if (canonicalNamesLongestFirst.isNotEmpty()) {
                val normalizedItemName = (item.cleanedName.ifEmpty { item.name })
                    .lowercase(Locale.ROOT)
                    .replace(Regex("^[a-zçğıöşü]{2,4}:\\s*"), "")
                    .trim()
                val matchedCanonical = canonicalNamesLongestFirst.firstOrNull { canonical ->
                    canonical.length >= 3 && (
                        normalizedItemName == canonical ||
                        normalizedItemName.startsWith("$canonical ") ||
                        normalizedItemName.startsWith("$canonical:") ||
                        Regex("(?:^|\\s)${Regex.escape(canonical)}(?:\\s|$)").containsMatchIn(normalizedItemName)
                    )
                }
                if (matchedCanonical != null) {
                    parsedInfo = parsedInfo.copy(showTitle = titleCase(matchedCanonical))
                }
            }
            
            val categoryParts = item.category.split(";")
            var platformName = item.category.trim()
            var cleanCategory = item.category.trim()
            
            if (categoryParts.size >= 2) {
                platformName = categoryParts[0].trim()
                cleanCategory = categoryParts[1].trim()
            } else if (categoryParts.isNotEmpty()) {
                platformName = categoryParts[0].trim()
                cleanCategory = categoryParts[0].trim()
            }
            
            val normalizedPlatform = when {
                platformName.lowercase(Locale.ROOT).contains("netflix") -> "Netflix"
                platformName.lowercase(Locale.ROOT).contains("amazon") || platformName.lowercase(Locale.ROOT).contains("prime") -> "Amazon Prime"
                platformName.lowercase(Locale.ROOT).contains("disney") -> "Disney+"
                platformName.lowercase(Locale.ROOT).contains("blu") -> "BluTV"
                platformName.lowercase(Locale.ROOT).contains("exxen") -> "Exxen"
                platformName.lowercase(Locale.ROOT).contains("gain") -> "GAİN"
                platformName.lowercase(Locale.ROOT).contains("hbo") -> "HBO Max"
                platformName.lowercase(Locale.ROOT).contains("apple") -> "Apple TV+"
                platformName.lowercase(Locale.ROOT).contains("bein") -> "beIN Connect"
                else -> platformName
            }
            
            PlatformParsedItem(
                item = item,
                parsedInfo = parsedInfo,
                platformName = normalizedPlatform,
                category = cleanCategory
            )
        }

        val groupedByPlatform = parsedList.groupBy { it.platformName.lowercase(Locale.ROOT) }
        
        return groupedByPlatform.map { (lowerPlatform, platformItems) ->
            val platformName = platformItems.first().platformName
            
            val groupedByShow = platformItems.groupBy { it.parsedInfo.showTitle.lowercase(Locale.ROOT) }
            
            val shows = groupedByShow.map { (lowerShowTitle, showParsedItems) ->
                val showTitle = showParsedItems.map { it.parsedInfo.showTitle }.groupBy { it }.maxByOrNull { it.value.size }?.key ?: showParsedItems.first().parsedInfo.showTitle
                
                val firstItemParsed = showParsedItems.first()
                val firstItem = firstItemParsed.item
                val logoUrl = seriesCovers[showTitle.lowercase(Locale.ROOT).trim()]
                val category = showParsedItems.map { it.category }.distinct().joinToString(" / ")
                val ratedEpisodes = showParsedItems.map { it.item.rating }.filter { it > 0.0 }
                val rating = if (ratedEpisodes.isNotEmpty()) {
                    Math.round(ratedEpisodes.average() * 10) / 10.0
                } else {
                    0.0
                }
                val summary = showParsedItems.map { it.item.summary }.firstOrNull { it.isNotBlank() } ?: firstItem.summary
                val cast = showParsedItems.map { it.item.cast }.firstOrNull { it.isNotBlank() } ?: firstItem.cast
                val director = showParsedItems.map { it.item.director }.firstOrNull { it.isNotBlank() } ?: firstItem.director
                val isFavorite = showParsedItems.any { it.item.isFavorite }
                
                val seasonsGrouped = showParsedItems.groupBy { it.parsedInfo.season }
                val seasons = seasonsGrouped.map { (seasonNum, seasonItems) ->
                    val episodes = seasonItems.map { parsedItem ->
                        Episode(
                            id = parsedItem.item.id,
                            name = parsedItem.parsedInfo.episodeName.ifEmpty { "${seasonNum}. Sezon ${parsedItem.parsedInfo.episode}. Bölüm" },
                            seasonNumber = seasonNum,
                            episodeNumber = parsedItem.parsedInfo.episode,
                            streamUrl = parsedItem.item.streamUrl,
                            logoUrl = parsedItem.item.logoUrl,
                            item = parsedItem.item
                        )
                    }.sortedBy { it.episodeNumber }
                    
                    Season(
                        seasonNumber = seasonNum,
                        episodes = episodes
                    )
                }.sortedBy { it.seasonNumber }
                
                TvShow(
                    id = (showTitle + platformName).hashCode(),
                    title = showTitle,
                    logoUrl = logoUrl,
                    category = category,
                    rating = rating,
                    summary = summary,
                    cast = cast,
                    director = director,
                    isFavorite = isFavorite,
                    seasons = seasons,
                    platformName = platformName
                )
            }.sortedBy { it.title }
            
            Platform(
                name = platformName,
                logoUrl = null,
                shows = shows
            )
        }.sortedBy { it.name }
    }

    fun groupItemsIntoShows(items: List<IPTVItem>, seriesCovers: Map<String, String> = emptyMap()): List<TvShow> {
        return groupItemsIntoPlatforms(items, seriesCovers).flatMap { it.shows }.sortedBy { it.title }
    }

    // ÖNEMLİ: extractShowPosterUrl kaldırıldı — dizi kapağı artık SADECE
    // series_covers tablosundan (Xtream'in doğrulanmış get_series_info API'si)
    // geliyor, hiçbir bölümün görseline bakılmıyor.
}

fun TvShow.isPlaceholderSummary(): Boolean {
    val s = summary.trim()
    return s.isEmpty() ||
           s == "Özet bilgisi yükleniyor..." ||
           s == "Harika bir sinema deneyimi sunan, derin hikayesi ve görsel şöleniyle öne çıkan popüler bir yapım." ||
           s == "Merak uyandıran olay örgüsü ve sürükleyici karakterleriyle izleyiciyi ekran başına kilitleyen popüler dizi." ||
           s == "Bu harika IPTV sinema yapımı kesintisiz ve akıcı bir şekilde, en yüksek yayın çözünürlüğü standardı ile izlemeniz için hazır." ||
           s == "Merak uyandıran olay örgüsü ve sürükleyici karakterleriyle izleyiciyi ekran başına kilitleyen popüler dizi yapımı." ||
           s == "Harika bir sinema filmi." ||
           s == "Sürükleyici bir dizi yapımı."
}

fun TvShow.isPlaceholderCast(): Boolean {
    val c = cast.trim()
    return c.isEmpty() || 
           c == "Ryan Gosling, Emma Stone, Christian Bale" ||
           c == "Popüler Oyuncular" ||
           c == "Belirtilmemiş" ||
           c == "Bilinmiyor"
}

fun TvShow.isPlaceholderDirector(): Boolean {
    val d = director.trim()
    return d.isEmpty() || 
           d == "Christopher Nolan" ||
           d == "Bilinmiyor" ||
           d == "Showrunner"
}

fun TvShow.toBrowsableItem(): IPTVItem = IPTVItem(
    id = this.id,
    playlistId = 0,
    name = this.title,
    cleanedName = this.title,
    logoUrl = this.logoUrl,
    streamUrl = "",
    category = this.category,
    type = "SERIES",
    rating = this.rating
)

