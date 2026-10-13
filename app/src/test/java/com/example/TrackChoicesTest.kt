package com.example

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.example.player.selectableTrackIndices
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ses/altyazı listesi: cihazın çalamadığı kanallar seçenek olarak sunulmaz (seçilince ses kesiliyor / içerik oynamıyordu). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackChoicesTest {

    private fun audio(lang: String, mime: String) = Format.Builder().setSampleMimeType(mime).setLanguage(lang).build()

    private fun group(support: IntArray, vararg formats: Format) =
        Tracks.Group(TrackGroup(*formats), false, support, BooleanArray(formats.size))

    @Test
    fun unsupportedAudioTracks_areNotOffered() {
        val g = group(
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_SUBTYPE, C.FORMAT_EXCEEDS_CAPABILITIES),
            audio("tr", "audio/mp4a-latm"), audio("en", "audio/vnd.dts.hd;profile=lbr"), audio("de", "audio/eac3")
        )
        // Çalınabilenler (kapasiteyi aşsa da çözülebilenler dahil) kalır; çözücüsü olmayan kanal listeden çıkar.
        assertEquals(listOf(0, 2), selectableTrackIndices(g))
    }

    @Test
    fun allSupportedTracks_keepTheirOrder() {
        val g = group(intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED), audio("tr", "audio/ac3"), audio("en", "audio/ac3"))
        assertEquals(listOf(0, 1), selectableTrackIndices(g))
    }
}
