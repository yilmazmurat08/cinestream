package com.example

import com.example.data.model.SeriesParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSeriesParser_S01E01() {
    val result = SeriesParser.parseEpisodeInfo("From S01E01")
    assertNotNull(result)
    assertEquals("From", result?.showTitle)
    assertEquals(1, result?.season)
    assertEquals(1, result?.episode)
  }

  @Test
  fun testSeriesParser_1x03() {
    val result = SeriesParser.parseEpisodeInfo("Stranger Things 1x03")
    assertNotNull(result)
    assertEquals("Stranger Things", result?.showTitle)
    assertEquals(1, result?.season)
    assertEquals(3, result?.episode)
  }

  @Test
  fun testSeriesParser_Season_Episode() {
    val result = SeriesParser.parseEpisodeInfo("Breaking Bad - Season 5 Episode 12 - Rabid Dog")
    assertNotNull(result)
    assertEquals("Breaking Bad", result?.showTitle)
    assertEquals(5, result?.season)
    assertEquals(12, result?.episode)
    assertEquals("Rabid Dog", result?.episodeName)
  }

  @Test
  fun testSeriesParser_Turkish_Format() {
    val result = SeriesParser.parseEpisodeInfo("Lupin Sezon 1 Bölüm 2")
    assertNotNull(result)
    assertEquals("Lupin", result?.showTitle)
    assertEquals(1, result?.season)
    assertEquals(2, result?.episode)
  }

  @Test
  fun testSeriesParser_GroupItemsIntoPlatforms() {
    val item1 = com.example.data.model.IPTVItem(
        playlistId = 1,
        name = "From S01E01",
        cleanedName = "From S01E01",
        logoUrl = "logo_from",
        streamUrl = "http://stream/from_s01e01",
        category = "NETFLIX;Aksiyon",
        type = "SERIES"
    )
    val item2 = com.example.data.model.IPTVItem(
        playlistId = 1,
        name = "The Boys S01E01",
        cleanedName = "The Boys S01E01",
        logoUrl = "logo_boys",
        streamUrl = "http://stream/the_boys_s01e01",
        category = "AMAZON PRIME;Bilim Kurgu",
        type = "SERIES"
    )

    val platforms = SeriesParser.groupItemsIntoPlatforms(listOf(item1, item2))
    assertEquals(2, platforms.size)

    val netflix = platforms.find { it.name == "Netflix" }
    assertNotNull(netflix)
    assertEquals(1, netflix?.shows?.size)
    assertEquals("From", netflix?.shows?.first()?.title)
    assertEquals("Aksiyon", netflix?.shows?.first()?.category)

    val amazon = platforms.find { it.name == "Amazon Prime" }
    assertNotNull(amazon)
    assertEquals(1, amazon?.shows?.size)
    assertEquals("The Boys", amazon?.shows?.first()?.title)
    assertEquals("Bilim Kurgu", amazon?.shows?.first()?.category)
  }
}
