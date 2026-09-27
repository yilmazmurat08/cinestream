package com.example

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.example.player.PictureInPictureState
import com.example.player.rememberPictureInPicture
import com.example.ui.tv.TvDevice
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * PiP, uygulamanın yaptığı gibi LocalContext dil ayarlı bir bağlamla değiştirildiğinde de çalışmalı.
 * (Hata: Activity bu bağlamdan bulunamadığı için PiP hiç açılmıyordu.)
 */
@RunWith(RobolectricTestRunner::class)
class PictureInPictureTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var player: ExoPlayer? = null

    @Before
    fun setUp() {
        TvDevice.overrideForTest = false
        shadowOf(compose.activity.packageManager)
            .setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
    }

    @After
    fun tearDown() {
        TvDevice.overrideForTest = null
        compose.runOnUiThread { player?.release() }
    }

    /** MainActivity'deki gibi: LocalContext = activity.createConfigurationContext(...) (Activity değil). */
    private fun showPip(canEnter: Boolean): () -> PictureInPictureState? {
        val activity = compose.activity
        var state: PictureInPictureState? = null
        compose.runOnUiThread { player = ExoPlayer.Builder(activity).build() }
        compose.setContent {
            val localized = remember {
                activity.createConfigurationContext(Configuration(activity.resources.configuration))
            }
            CompositionLocalProvider(LocalContext provides localized) {
                state = rememberPictureInPicture(player!!, canEnter = canEnter, onDismissedInBackground = {})
            }
        }
        compose.waitForIdle()
        return { state }
    }

    private fun userLeavesApp() {
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation().callActivityOnUserLeaving(compose.activity)
        }
        compose.waitForIdle()
    }

    @Test
    @Config(sdk = [30])
    fun android8to11_entersPip_whenUserLeavesWhilePlaying() {
        showPip(canEnter = true)
        userLeavesApp()
        assertTrue(compose.activity.isInPictureInPictureMode)
    }

    @Test
    @Config(sdk = [30])
    fun android8to11_doesNotEnterPip_whenPaused() {
        showPip(canEnter = false)
        userLeavesApp()
        assertFalse(compose.activity.isInPictureInPictureMode)
    }

    @Test
    @Config(sdk = [34])
    fun android12plus_autoEnterIsArmed_whilePlaying() {
        val state = showPip(canEnter = true)
        assertTrue("Activity bulunmalı ve PiP otomatik geçişe hazır olmalı", state()!!.willEnterOnLeave)
    }

    @Test
    @Config(sdk = [34])
    fun tv_neverUsesPip() {
        TvDevice.overrideForTest = true
        val state = showPip(canEnter = true)
        assertFalse(state()!!.willEnterOnLeave)
    }

    @Test
    fun networkConfig_allowsHttpPostersFromImageTmdb() {
        // Sağlayıcılar afişleri http://image.tmdb.org/... ile verir; bu alan adı HTTPS zorunlu listesinde olmamalı.
        val xml = File("src/main/res/xml/network_security_config.xml").readText()
        val strictBlock = xml.substringAfter("<domain-config cleartextTrafficPermitted=\"false\">")
            .substringBefore("</domain-config>")
        assertFalse(strictBlock.contains("image.tmdb.org"))
        // Reklam engelleyici / VPN sertifikası kullanan cihazlarda TMDB ve yapay zekâ çağrıları kesilmesin.
        assertTrue(strictBlock.contains("<certificates src=\"user\" />"))
    }
}
