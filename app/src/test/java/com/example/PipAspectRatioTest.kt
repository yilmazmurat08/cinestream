package com.example

import android.util.Rational
import androidx.media3.common.VideoSize
import com.example.player.pipAspectRatio
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PiP penceresi oranı Android'in izin verdiği aralıkta kalmalı (aksi hâlde PiP'e geçiş hata verir). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PipAspectRatioTest {

    @Test
    fun normalVideo_keepsItsRatio() {
        assertEquals(Rational(1920, 1080), pipAspectRatio(VideoSize(1920, 1080)))
    }

    @Test
    fun unknownSize_defaultsTo16by9() {
        assertEquals(Rational(16, 9), pipAspectRatio(VideoSize.UNKNOWN))
    }

    @Test
    fun extremeRatios_areClamped() {
        assertEquals(Rational(239, 100), pipAspectRatio(VideoSize(4000, 1000)))
        assertEquals(Rational(100, 239), pipAspectRatio(VideoSize(500, 2000)))
    }
}
