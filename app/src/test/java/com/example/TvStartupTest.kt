package com.example

import android.content.pm.PackageManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.example.ui.tv.TvDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Android TV'de açılış: eski Android sürümleri (TV'lerin çoğu 9–12), dokunmatik ekran ve PiP yok.
 * Uygulama açılıp giriş ekranına gelmeli, arka plana gidip dönmeli, çökmemeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television")
class TvStartupTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    private fun startOnTv() {
        TvDevice.overrideForTest = true
        shadowOf(rule.activity.packageManager).apply {
            setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
            setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
            setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        }
        rule.activityRule.scenario.recreate()
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
        rule.onNode(hasTestTag("login_screen")).assertExists()
        // Ana ekran tuşu ve geri dönüş.
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(Lifecycle.State.RESUMED, rule.activityRule.scenario.state)
    }

    @Test @Config(sdk = [26]) fun android8() = startOnTv()
    // Kullanıcının cihazı: Xiaomi Mi TV Stick, Android 10 (API 29).
    @Test @Config(sdk = [29]) fun android10_xiaomiMiTvStick() = startOnTv()
    @Test @Config(sdk = [33]) fun android13() = startOnTv()
}
