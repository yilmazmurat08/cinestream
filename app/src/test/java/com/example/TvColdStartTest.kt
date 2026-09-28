package com.example

import android.content.pm.PackageManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.ui.tv.TvDevice
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Android TV'de SOĞUK açılış: uygulama en baştan TV olarak açılır (açılış animasyonu dahil).
 * Önceki TV testleri telefon olarak açıp sonra TV'ye geçtiği için açılış animasyonu TV'de hiç denenmemişti.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television")
class TvColdStartTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun tv() {
        TvDevice.overrideForTest = true
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>().packageManager).apply {
            setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
            setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
            setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        }
    }

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    private fun coldStart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(6_000)
            compose.waitForIdle()
            // İlk açılışta mod seçimi sorulur (aynı test sürecinde daha önce seçildiyse sorulmaz); TV seçilir.
            compose.chooseViewModeIfAsked(tag = "mode_card_tv", clock = compose.mainClock)
            compose.waitForIdle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            compose.onNode(hasTestTag("login_screen")).assertExists()
        }
    }

    // Kullanıcının cihazı: Xiaomi Mi TV Stick, Android 10 (API 29), düşük bellekli cihaz. Bu profil
    // "no such table: room_table_modification_log" çökmesini veriyordu.
    @Test @Config(sdk = [29]) fun android10_xiaomiMiTvStick_lowRam_coldStart() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).setIsLowRamDevice(true)
        coldStart()
        // Açılıştan sonra veritabanı gözlemleri de çalışmalı (çökme burada oluyordu).
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(20_000) {
                com.example.data.db.AppDatabase.getDatabase(ApplicationProvider.getApplicationContext())
                    .iptvDao().getItemsByTypeFlow("LIVE").first()
            }
        }
    }
    @Test @Config(sdk = [31]) fun android12_coldStart() = coldStart()
    @Test @Config(sdk = [34]) fun android14_coldStart() = coldStart()
}
