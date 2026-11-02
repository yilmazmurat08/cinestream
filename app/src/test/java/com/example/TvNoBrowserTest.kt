package com.example

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.player.RadioPlayerManager
import com.example.ui.components.ExternalLink
import com.example.ui.components.openTrailerInYouTube
import com.example.ui.tv.TvDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Android TV kalite yönergeleri: TV'de web tarayıcısı açılmaz (TV-WB); TV'de arka planda medya kontrolü
 * olmadan ses çalınmaz (TV-NP). Telefonda davranış değişmedi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvNoBrowserTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    @Test
    fun onTvLinkIsShownNotOpened() {
        TvDevice.overrideForTest = true
        assertFalse(ExternalLink.open(app, "https://www.youtube.com/t/terms"))
        assertNull(shadowOf(app).nextStartedActivity)
        assertTrue(ShadowToast.getTextOfLatestToast().contains("youtube.com/t/terms"))
    }

    @Test
    fun onTvSubscriptionLinkShowsHintNotBrowser() {
        TvDevice.overrideForTest = true
        assertFalse(ExternalLink.openPlaySubscriptions(app, "https://play.google.com/store/account/subscriptions"))
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(app.getString(R.string.tv_manage_subscription_hint), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun onPhoneLinkOpensAsBefore() {
        TvDevice.overrideForTest = false
        assertTrue(ExternalLink.open(app, "https://aistudio.google.com"))
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://aistudio.google.com", started.dataString)
    }

    @Test
    fun onTvTrailerTargetsYouTubeAppOnly() {
        TvDevice.overrideForTest = true
        assertTrue(openTrailerInYouTube(app, "abc123"))
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertTrue(started.`package`!!.startsWith("com.google.android.youtube"))
    }

    @Test
    fun onPhoneTrailerHasNoPackageRestriction() {
        TvDevice.overrideForTest = false
        assertTrue(openTrailerInYouTube(app, "abc123"))
        assertNull(shadowOf(app).nextStartedActivity.`package`)
    }

    @Test
    fun pauseForBackgroundWithoutRadioIsSafe() {
        RadioPlayerManager.stopRadio()
        RadioPlayerManager.pauseForBackground()
        assertFalse(RadioPlayerManager.isPlaying.value)
    }
}
