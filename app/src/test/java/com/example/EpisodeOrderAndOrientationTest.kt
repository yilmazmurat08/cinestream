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
