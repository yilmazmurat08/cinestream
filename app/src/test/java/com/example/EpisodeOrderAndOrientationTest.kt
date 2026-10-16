package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.example.data.model.SeriesParser
import com.example.ui.screens.physicalOrientationOf
import com.example.ui.screens.playerRequestedOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeOrderAndOrientationTest {

    @Test
    fun xtreamEpisodeNumber_prefersTopLevelField() {
        // Standart Xtream yanıtı: numara bölüm nesnesinde; dizideki sıra (index) farklı olabilir.
        assertEquals(7, SeriesParser.resolveXtreamEpisodeNumber("7", null, "Dizi - S01E07", 1, index = 0))
        assertEquals(7, SeriesParser.resolveXtreamEpisodeNumber("7", "", "Bölüm", 1, index = 3))
    }

    @Test
    fun xtreamEpisodeNumber_fallsBackToInfoThenTitleThenIndex() {
        assertEquals(4, SeriesParser.resolveXtreamEpisodeNumber("", "4", "x", 1, index = 0))
        assertEquals(12, SeriesParser.resolveXtreamEpisodeNumber(null, null, "Dizi - S02E12 - Başlık", 2, index = 0))
        assertEquals(5, SeriesParser.resolveXtreamEpisodeNumber(null, null, "Dizi 2x05", 2, index = 0))
        // Başlıktaki etiket başka sezona aitse güvenilmez; sıra kullanılır.
        assertEquals(3, SeriesParser.resolveXtreamEpisodeNumber(null, null, "Dizi - S01E09", 2, index = 2))
        assertEquals(1, SeriesParser.resolveXtreamEpisodeNumber("0", "abc", "Bölüm", 1, index = 0))
    }

    private fun src(top: String?, title: String, index: Int) = SeriesParser.XtreamEpisodeNumberSource(top, null, title, index)

    @Test
    fun xtreamSeason_trustsTheEpisodeTagInTheTitle_whenTheFieldIsJustTheOrder() {
        // Sağlayıcıda 2. bölüm yok; episode_num eklenme sırası: 3. bölüm "2" yazılmış (La Brea'daki durum).
        val season = listOf(
            src("1", "La Brea - S01E01 - The Fall", 0),
            src("2", "La Brea - S01E03 - The Way Back", 1),
            src("3", "La Brea - S01E04 - Weapon", 2)
        )
        assertEquals(listOf(1, 3, 4), SeriesParser.resolveXtreamSeasonEpisodeNumbers(season, 1))
    }

    @Test
    fun xtreamSeason_keepsTheField_whenTitleTagsAreMissingOrUnreliable() {
        // Etiket yok: alan kullanılır.
        assertEquals(listOf(5, 6), SeriesParser.resolveXtreamSeasonEpisodeNumbers(listOf(src("5", "Bölüm", 0), src("6", "Bölüm", 1)), 1))
        // Bütün bölümlerin adında aynı etiket (sağlayıcı hatası): alan kullanılır.
        assertEquals(listOf(1, 2, 3), SeriesParser.resolveXtreamSeasonEpisodeNumbers(
            listOf(src("1", "Dizi S01E01", 0), src("2", "Dizi S01E01", 1), src("3", "Dizi S01E01", 2)), 1))
        // Etiket başka sezona ait: alan kullanılır.
        assertEquals(listOf(7), SeriesParser.resolveXtreamSeasonEpisodeNumbers(listOf(src("7", "Dizi - S01E02", 0)), 2))
        // Alan da etiket de yok: sıradaki yeri.
        assertEquals(listOf(1, 2), SeriesParser.resolveXtreamSeasonEpisodeNumbers(listOf(src(null, "x", 0), src("", "y", 1)), 1))
    }

    @Test
    fun player_followsSensorUnlessForced() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, playerRequestedOrientation(null))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, playerRequestedOrientation(Configuration.ORIENTATION_LANDSCAPE))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, playerRequestedOrientation(Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    fun physicalOrientation_fromSensorDegrees() {
        assertEquals(Configuration.ORIENTATION_PORTRAIT, physicalOrientationOf(0))
        assertEquals(Configuration.ORIENTATION_PORTRAIT, physicalOrientationOf(350))
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, physicalOrientationOf(90))
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, physicalOrientationOf(270))
        assertNull(physicalOrientationOf(45))
        assertNull(physicalOrientationOf(180))
    }
}
