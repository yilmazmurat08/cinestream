package com.example

import com.example.data.model.SeriesParser
import com.example.player.ExoPlayerConfigurator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class M3UParserAndUrlTest {

    @Test
    fun testUrlSanitization_SpecialCharacters() {
        val rawUrl = "http://example.com/live/stream 1.m3u8?token=abc|123&user=john"
        val sanitized = ExoPlayerConfigurator.sanitizeUrl(rawUrl)
        assertTrue(sanitized.contains("%20"))
        assertTrue(sanitized.contains("%7C"))
        assertFalse(sanitized.contains(" "))
        assertFalse(sanitized.contains("|"))
    }

    @Test
    fun testUrlSanitization_EmptyAndBOM() {
        assertEquals("", ExoPlayerConfigurator.sanitizeUrl(""))
        assertEquals("", ExoPlayerConfigurator.sanitizeUrl("   \n\r  "))
        val bomUrl = "\uFEFFhttp://example.com/stream.m3u8"
        assertEquals("http://example.com/stream.m3u8", ExoPlayerConfigurator.sanitizeUrl(bomUrl))
    }

    @Test
    fun testBuildMediaItem_MimeTypeDetection() {
        val hlsItem = ExoPlayerConfigurator.buildMediaItemForUrl("http://server.com/live.m3u8")
        assertNotNull(hlsItem.localConfiguration)
        assertEquals("application/x-mpegURL", hlsItem.localConfiguration?.mimeType)

        val tsItem = ExoPlayerConfigurator.buildMediaItemForUrl("http://server.com/stream.ts")
        assertEquals("video/mp2t", tsItem.localConfiguration?.mimeType)

        val mp4Item = ExoPlayerConfigurator.buildMediaItemForUrl("http://server.com/vod/movie.mp4")
        assertEquals("video/mp4", mp4Item.localConfiguration?.mimeType)

        val mkvItem = ExoPlayerConfigurator.buildMediaItemForUrl("http://server.com/series/ep.mkv")
        assertEquals("video/x-matroska", mkvItem.localConfiguration?.mimeType)
    }

    @Test
    fun testSeriesParser_Robustness() {
        val s1 = SeriesParser.parseEpisodeInfo("Game of Thrones S08E06 The Iron Throne")
        assertNotNull(s1)
        assertEquals("Game of Thrones", s1?.showTitle)
        assertEquals(8, s1?.season)
        assertEquals(6, s1?.episode)

        val s2 = SeriesParser.parseEpisodeInfo("Breaking Bad 5x14 Ozymandias")
        assertNotNull(s2)
        assertEquals("Breaking Bad", s2?.showTitle)
        assertEquals(5, s2?.season)
        assertEquals(14, s2?.episode)

        val s3 = SeriesParser.parseEpisodeInfo("Kurtlar Vadisi Sezon 2 Bölüm 45")
        assertNotNull(s3)
        assertEquals("Kurtlar Vadisi", s3?.showTitle)
        assertEquals(2, s3?.season)
        assertEquals(45, s3?.episode)
    }
}
