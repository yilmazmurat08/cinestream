package com.example

import com.example.util.TitleNormalizer
import org.junit.Assert.*
import org.junit.Test

class TmdbIntegrationTest {

    @Test
    fun testTitleNormalizer_SeriesWithTechnicalTags() {
        val result = TitleNormalizer.normalize("The.Last.of.Us.S02E03.1080p.WEB-DL")
        assertEquals("The Last of Us", result.cleanTitle)
        assertEquals("S02E03", result.seasonEpisode)
    }

    @Test
    fun testTitleNormalizer_MovieWithYearAnd4K() {
        val result = TitleNormalizer.normalize("Avatar (2009) 4K HDR")
        assertEquals("Avatar", result.cleanTitle)
        assertEquals("2009", result.year)
    }

    @Test
    fun testTitleNormalizer_MovieWith2160p() {
        val result = TitleNormalizer.normalize("The Batman 2022 2160p")
        assertEquals("The Batman", result.cleanTitle)
        assertEquals("2022", result.year)
    }

    @Test
    fun testTitleNormalizer_SeriesWithDotsAndEpisode() {
        val result = TitleNormalizer.normalize("Breaking.Bad.S05E10.1080p")
        assertEquals("Breaking Bad", result.cleanTitle)
        assertEquals("S05E10", result.seasonEpisode)
    }

    @Test
    fun testTitleNormalizer_GameOfThronesFormat() {
        val result = TitleNormalizer.normalize("Game of Thrones - S01 E02")
        assertEquals("Game of Thrones", result.cleanTitle)
        assertEquals("S01 E02", result.seasonEpisode)
    }

    @Test
    fun testTitleNormalizer_SimpleMovie() {
        val result = TitleNormalizer.normalize("Interstellar")
        assertEquals("Interstellar", result.cleanTitle)
        assertNull(result.year)
    }

    @Test
    fun testTitleNormalizer_InceptionWithYear() {
        val result = TitleNormalizer.normalize("Inception (2010)")
        assertEquals("Inception", result.cleanTitle)
        assertEquals("2010", result.year)
    }

    @Test
    fun testTitleNormalizer_TurkishDublajAndDualTags() {
        val result = TitleNormalizer.normalize("Oppenheimer [2023] Remux Dual Türkçe Dublaj 1080p")
        assertEquals("Oppenheimer", result.cleanTitle)
        assertEquals("2023", result.year)
    }

    @Test
    fun testTitleNormalizer_ImdbIdExtraction() {
        val result = TitleNormalizer.normalize("Dune Part Two tt15239678 (2024)")
        assertEquals("Dune Part Two", result.cleanTitle)
        assertEquals("tt15239678", result.imdbId)
        assertEquals("2024", result.year)
    }

    @Test
    fun testTitleNormalizer_LegacyCompatibilityMethods() {
        val (cleanTitle1, season1) = TitleNormalizer.cleanTitleForTMDB("Stranger Things S04E01")
        assertEquals("Stranger Things", cleanTitle1)
        assertEquals("S04E01", season1)

        val (cleanTitle2, year2) = TitleNormalizer.cleanM3UData("The Matrix (1999)", "1999-03-31")
        assertEquals("The Matrix", cleanTitle2)
        assertEquals("1999", year2)
    }
}
