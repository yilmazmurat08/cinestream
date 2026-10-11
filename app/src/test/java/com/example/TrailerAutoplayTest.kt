package com.example

import com.example.ui.components.shouldAutoplayTrailer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fragman kart açılınca kendiliğinden başlar; kapatılınca/bitince/hata verince aynı kartta tekrar başlamaz. */
class TrailerAutoplayTest {
    @Test fun startsWhenLinkFound_andNotDismissed_andNotPlaying() = assertTrue(shouldAutoplayTrailer("abc123", dismissed = false, playing = false))
    @Test fun doesNotStart_withoutTrailerLink() {
        assertFalse(shouldAutoplayTrailer(null, dismissed = false, playing = false))
        assertFalse(shouldAutoplayTrailer("", dismissed = false, playing = false))
    }
    @Test fun doesNotRestart_afterUserClosedOrEndedOrFailed() = assertFalse(shouldAutoplayTrailer("abc123", dismissed = true, playing = false))
    @Test fun doesNotStart_whileAlreadyPlaying() = assertFalse(shouldAutoplayTrailer("abc123", dismissed = false, playing = true))
}
