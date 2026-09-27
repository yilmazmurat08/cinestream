package com.example.util

import java.util.Locale

/**
 * TitleNormalizer parses and cleans IPTV M3U stream titles for accurate TMDB searches.
 * Handles technical tags (1080p, WEB-DL, etc.), Season/Episode patterns, Year extraction,
 * and IMDb IDs.
 */
object TitleNormalizer {

    data class NormalizedTitleResult(
        val cleanTitle: String,
        val year: String? = null,
        val seasonEpisode: String? = null,
        val imdbId: String? = null
    )

    // Regex to match technical quality, encoding, resolution, and audio tags
    private val TECHNICAL_TAGS_REGEX = Regex(
        "(?i)\\b(" +
        "1080p|720p|2160p|4k|8k|uhd|fhd|hd|sd|" +
        "hdr|hdr10|hdr10\\+|dv|dolby[ ._-]?vision|imax|" +
        "web-?dl|web-?rip|bluray|blu-?ray|bdrip|brrip|hdtv|dvdrip|remux|camrip|cam|telesync|ts|" +
        "x264|x265|h\\.?264|h\\.?265|hevc|avc|10bit|8bit|" +
        "aac|ddp5?\\.?1?|dd5?\\.?1?|atmos|dts(-hd)?|ac3|mp3|5\\.1|7\\.1|" +
        "multi|dual|tur|tr|en|eng|turkce|türkçe|dublaj|altyazi|altyazili|sub|dub|" +
        "extended|unrated|directors[ ._-]?cut|remastered|proper|repack" +
        ")\\b"
    )

    // Regex to match Season and Episode patterns
    private val SEASON_EPISODE_REGEX = Regex(
        "(?i)\\b(" +
        "s\\d{1,2}\\s*[-_.]?\\s*e\\d{1,2}|" +
        "\\d{1,2}\\s*x\\s*\\d{1,2}|" +
        "(?:season|sezon)\\s*\\d{1,2}\\s*(?:episode|bölüm|bolum|e)?\\s*\\d{0,2}|" +
        "(?:bölüm|bolum|episode)\\s*\\d{1,2}" +
        ")\\b"
    )

    // Regex to match 4-digit Year (between 1900 and 2099)
    private val YEAR_REGEX = Regex(
        "[\\[\\(]?\\b(19\\d\\d|20\\d\\d)\\b[\\]\\)]?"
    )

    // Regex to match IMDb IDs (e.g. tt1234567)
    private val IMDB_REGEX = Regex(
        "(?i)\\b(tt\\d{7,8})\\b"
    )

    /**
     * Cleans and normalizes a raw title string.
     */
    fun normalize(rawTitle: String, rawDate: String? = null): NormalizedTitleResult {
        if (rawTitle.isBlank()) {
            val fallbackYear = rawDate?.takeIf { it.length >= 4 }?.substring(0, 4)
            return NormalizedTitleResult(cleanTitle = "", year = fallbackYear)
        }

        var working = rawTitle.trim()

        // 1. Extract IMDb ID if present
        val imdbMatch = IMDB_REGEX.find(working)
        val imdbId = imdbMatch?.groupValues?.getOrNull(1)
        if (imdbMatch != null) {
            working = working.replace(imdbMatch.value, " ")
        }

        // 2. Extract Season / Episode info
        val seasonEpisodeMatch = SEASON_EPISODE_REGEX.find(working)
        val seasonEpisode = seasonEpisodeMatch?.value?.trim()
        if (seasonEpisodeMatch != null) {
            working = working.replace(seasonEpisodeMatch.value, " ")
        }

        // 3. Extract Year from title or fallback to rawDate
        var year: String? = null
        val yearMatches = YEAR_REGEX.findAll(working).toList()
        if (yearMatches.isNotEmpty()) {
            val lastYearMatch = yearMatches.last()
            year = lastYearMatch.groupValues.getOrNull(1)
            // Remove year from title query so TMDB query is clean
            working = working.removeRange(lastYearMatch.range)
        }

        if (year.isNullOrBlank() && !rawDate.isNullOrBlank() && rawDate.length >= 4) {
            year = rawDate.substring(0, 4)
        }

        // 4. Clean technical keywords & tags
        working = TECHNICAL_TAGS_REGEX.replace(working, " ")

        // 5. Replace dot, underscore, dash, brackets, parentheses used as delimiters with space
        working = working.replace(Regex("[._\\[\\](){}|/\\\\]"), " ")
        working = working.replace(Regex("\\s*-\\s*"), " ")

        // 6. Clean punctuation at ends and collapse multiple whitespace
        working = working.replace(Regex("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ\\s]"), " ")
        working = working.replace(Regex("\\s+"), " ").trim()

        return NormalizedTitleResult(
            cleanTitle = working,
            year = year,
            seasonEpisode = seasonEpisode,
            imdbId = imdbId
        )
    }

    /**
     * Legacy helper method matching previous method signatures
     */
    fun cleanTitleForTMDB(rawTitle: String): Pair<String, String?> {
        val result = normalize(rawTitle)
        return Pair(result.cleanTitle, result.seasonEpisode)
    }

    /**
     * Legacy helper method matching previous method signatures
     */
    fun cleanM3UData(rawTitle: String, rawDate: String?): Pair<String, String?> {
        val result = normalize(rawTitle, rawDate)
        return Pair(result.cleanTitle, result.year)
    }
}
