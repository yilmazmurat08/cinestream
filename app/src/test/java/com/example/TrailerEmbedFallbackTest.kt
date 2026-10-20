package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.TrailerEmbedBlocklist
import com.example.ui.components.handleTrailerEmbedError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Sahibi gömülü oynatmaya izin vermeyen YouTube fragmanı: hatırlanır; kullanıcı düğmeyle başlattıysa YouTube'da
 * açılır, kendiliğinden başladıysa sessizce durur (ekranda uyarı yok).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailerEmbedFallbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app get() = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

    @Test
    fun userStartedTrailer_thatCannotEmbed_opensYouTube_andIsRemembered() {
        assertFalse(TrailerEmbedBlocklist.isBlocked(context, "abc123XYZ_-"))
        handleTrailerEmbedError(context, "abc123XYZ_-", startedByUser = true)
        assertTrue(TrailerEmbedBlocklist.isBlocked(context, "abc123XYZ_-"))
        val intent = app.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.youtube.com/watch?v=abc123XYZ_-", intent.dataString)
    }

    @Test
    fun autoStartedTrailer_thatCannotEmbed_staysQuiet() {
        handleTrailerEmbedError(context, "quiet00000a", startedByUser = false)
        assertTrue(TrailerEmbedBlocklist.isBlocked(context, "quiet00000a"))
        assertNull(app.nextStartedActivity)
    }

    @Test
    fun blankIdIsNeverBlocked() {
        assertFalse(TrailerEmbedBlocklist.isBlocked(context, null))
        assertFalse(TrailerEmbedBlocklist.isBlocked(context, ""))
    }
}
