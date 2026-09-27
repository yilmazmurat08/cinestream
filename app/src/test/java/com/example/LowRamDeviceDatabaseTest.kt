package com.example

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Xiaomi Mi TV Stick (Android 10) çökmesi: düşük bellekli cihazlarda Room veritabanını WAL olmadan açar;
 * uygulama açılışta WAL'ı zorla açınca Room'un geçici takip tablosu kayboluyor ve
 * "no such table: room_table_modification_log" ile çöküyordu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LowRamDeviceDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AppDatabase.closeForTest()
        context.getDatabasePath("cinestream_database").parentFile?.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun lowRamTv_databaseOpensAndObservesWithoutCrash() = runBlocking {
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).setIsLowRamDevice(true)
        val db = AppDatabase.getDatabase(context)
        val dao = db.iptvDao()
        // Flow gözlemi takip tablosuna yazar (çökmenin olduğu yer).
        val initial = withTimeout(20_000) { dao.getItemsByTypeFlow("LIVE").first() }
        assertEquals(0, initial.size)
        dao.insertItems(listOf(IPTVItem(id = 1, playlistId = 1, name = "TRT 1", cleanedName = "TRT 1",
            logoUrl = null, streamUrl = "http://x/1.ts", category = "Ulusal", type = "LIVE")))
        assertEquals(1, withTimeout(20_000) { dao.getItemsByTypeFlow("LIVE").first() }.size)
    }

    private fun journalMode(): String = AppDatabase.getDatabase(context).openHelper.writableDatabase
        .query("PRAGMA journal_mode").use { c -> c.moveToFirst(); c.getString(0).lowercase() }

    @Test
    fun normalPhone_keepsWalForSpeed() {
        assertEquals("wal", journalMode())
    }

    @Test
    fun lowRamDevice_doesNotForceWal() {
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).setIsLowRamDevice(true)
        // Cihazda Android'in varsayılanı (TRUNCATE); Robolectric bunu "memory" olarak raporlar. Önemli olan WAL'ın
        // zorlanmaması (zorlamak düşük bellekli cihazda çökmeye yol açıyordu).
        assertNotEquals("wal", journalMode())
    }
}
