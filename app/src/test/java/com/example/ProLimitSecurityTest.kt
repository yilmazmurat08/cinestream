package com.example

import android.app.Application
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WatchUsage
import com.example.util.TrustedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.util.TimeZone

/**
 * Günlük 60 dakikalık ücretsiz süre hileye karşı: telefonun saatini/tarihini ileri-geri almak ya da saat dilimini
 * değiştirmek süreyi yenilemez. Gün internetten alınan saatle hesaplanır, saat dilimi ilk kullanımda sabitlenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProLimitSecurityTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val originalZone = TimeZone.getDefault()
    private val day = 24L * 60 * 60 * 1000

    // 15 Ekim 2026 23:00 İstanbul (20:00 UTC): gerçek zaman. Bu anda UTC+14 bölgesinde tarih 16 Ekim'dir.
    private val realNow = java.time.ZonedDateTime.of(2026, 10, 15, 23, 0, 0, 0, ZoneId.of("Europe/Istanbul")).toInstant().toEpochMilli()
    private var elapsed = 1_000_000L

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
        TrustedClock.resetForTest(app)
        TrustedClock.elapsedRealtime = { elapsed }
        TrustedClock.networkTime = { realNow }
        TrustedClock.wallClock = { realNow }
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 5)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
        TrustedClock.resetForTest(app)
    }

    private fun settings(): SettingsRepository {
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            tmp.newFile("ayarlar.preferences_pb").also { it.delete() }
        }
        return SettingsRepository(app, store)
    }

    private fun usedToday(s: SettingsRepository) = runBlocking { s.watchUsageFlow.first().secondsOn(s.watchDayNow()) }

    @Test
    fun movingThePhoneDateForwardOrBack_doesNotRenewTheFreeMinutes() {
        val s = settings()
        runBlocking { TrustedClock.ensureSynced(app); s.addWatchSeconds(3_600) }
        assertEquals(3_600L, usedToday(s))

        TrustedClock.wallClock = { realNow + day } // tarih bir gün ileri alındı
        assertEquals("ileri almak süreyi yenilemez", 3_600L, usedToday(s))
        TrustedClock.wallClock = { realNow - day } // tarih bir gün geri alındı
        assertEquals("geri almak süreyi yenilemez", 3_600L, usedToday(s))
    }

    @Test
    fun changingTheTimeZone_doesNotRenewTheFreeMinutes() {
        val s = settings()
        runBlocking { TrustedClock.ensureSynced(app); s.addWatchSeconds(3_600) }
        // 23:00 İstanbul'da UTC+14 bölgesinde tarih ertesi gündür.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        assertEquals(3_600L, usedToday(s))
    }

    @Test
    fun withoutInternetTime_settingTheClockBack_stillDoesNotRenew() {
        TrustedClock.networkTime = { null } // internet saati alınamıyor
        val s = settings()
        runBlocking { assertFalse(TrustedClock.ensureSynced(app)); s.addWatchSeconds(3_600) }
        TrustedClock.wallClock = { realNow - day }
        assertEquals(3_600L, usedToday(s))
    }

    @Test
    fun theRealNextDay_givesFreshMinutes() {
        val s = settings()
        runBlocking { TrustedClock.ensureSynced(app); s.addWatchSeconds(3_600) }
        elapsed += day // gerçekten bir gün geçti (cihaz kapanmadan)
        assertEquals(0L, usedToday(s))
    }

    @Test
    fun internetTimeIsReusedAfterAppRestart_butNotAfterReboot() {
        runBlocking { assertTrue(TrustedClock.ensureSynced(app)) }
        TrustedClock.wallClock = { realNow + 5 * day }
        TrustedClock.forgetInMemoryForTest() // uygulama yeniden başladı, aynı açılış
        elapsed += 60_000
        assertEquals(realNow + 60_000, TrustedClock.now(app))

        TrustedClock.forgetInMemoryForTest()
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 6) // cihaz yeniden açıldı
        elapsed = 5_000
        assertFalse(TrustedClock.isSynced(app))
        assertEquals("yeniden alınana kadar telefon saati", realNow + 5 * day, TrustedClock.now(app))
    }

    @Test
    fun dayRules() {
        assertEquals("2026-10-15", WatchUsage.noEarlierThan("2026-10-14", "2026-10-15"))
        assertEquals("2026-10-16", WatchUsage.noEarlierThan("2026-10-16", "2026-10-15"))
        // Saat çok geriye düştüyse (ör. cihaz saati sıfırlandı) kullanıcı kilitli kalmaz.
        assertEquals("2026-10-01", WatchUsage.noEarlierThan("2026-10-01", "2026-10-15"))
        assertEquals("2026-10-15", WatchUsage.noEarlierThan("2026-10-15", null))
    }
}
