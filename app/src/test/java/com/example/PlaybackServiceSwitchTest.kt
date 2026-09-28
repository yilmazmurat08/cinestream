package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.player.PlaybackForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Hızlı kanal/bölüm geçişi: yeni oynatıcı servisi başlatır, eski oynatıcı hemen ardından durdurur. Servis
 * durdurulmamalı (hazır olmadan durdurulan ön plan servisi Android'de uygulamayı kapatır). Son oynatıcı da
 * kapanınca servis kısa bir beklemeden sonra durdurulur. Servis oluşturulur oluşturulmaz ön plana geçer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceSwitchTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun quickSwitch_doesNotStopService_lastStopStopsAfterDelay() {
        val shadow = shadowOf(app)
        val oldPlayer = Any()
        val newPlayer = Any()
        PlaybackForegroundService.start(app, "Kanal 1", owner = oldPlayer)
        PlaybackForegroundService.start(app, "Kanal 2", owner = newPlayer)
        PlaybackForegroundService.stop(app, owner = oldPlayer)
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertNull("Geçişte servis durdurulmamalı", shadow.nextStoppedService)

        // Aynı oynatıcı iki kez durdurulsa da (arka plan + kapanış) diğerini etkilemez
        PlaybackForegroundService.stop(app, owner = oldPlayer)
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertNull(shadow.nextStoppedService)

        PlaybackForegroundService.stop(app, owner = newPlayer)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertNull("Hemen durdurulmamalı (kısa bekleme)", shadow.nextStoppedService)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertNotNull("Son oynatıcı kapanınca servis durmalı", shadow.nextStoppedService)
    }

    @Test
    fun restartDuringPendingStop_cancelsStop() {
        val shadow = shadowOf(app)
        val a = Any()
        val b = Any()
        PlaybackForegroundService.start(app, "Film", owner = a)
        PlaybackForegroundService.stop(app, owner = a)
        PlaybackForegroundService.start(app, "Sonraki bölüm", owner = b)
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertNull(shadow.nextStoppedService)
        PlaybackForegroundService.stop(app, owner = b)
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
    }

    @Test
    fun serviceGoesForegroundInOnCreate() {
        val service = Robolectric.buildService(PlaybackForegroundService::class.java).create().get()
        assertEquals(PlaybackForegroundService.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        service.onDestroy()
    }
}
