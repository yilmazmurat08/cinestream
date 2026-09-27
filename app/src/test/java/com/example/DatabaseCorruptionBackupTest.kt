package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.AppDatabase.Companion.applyMigrationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Bozuk veritabanında kullanıcı verisi silinmez: dosyalar önce `.corrupt-<zaman>` olarak yedeklenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseCorruptionBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "cinestream_database"

    @After
    fun cleanUp() {
        context.getDatabasePath(dbName).parentFile?.listFiles()?.forEach { it.delete() }
    }

    private fun backups(): List<File> =
        context.getDatabasePath(dbName).parentFile?.listFiles()
            ?.filter { it.name.startsWith("$dbName.corrupt-") }
            .orEmpty()

    @Test
    fun recoverCorruptedDatabase_movesFilesAside_insteadOfDeleting() {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("kullanıcının verisi")
        File(dbFile.path + "-wal").writeText("wal")

        AppDatabase.recoverCorruptedDatabase(context)

        assertFalse("Asıl dosya temiz açılış için yerinden alınmalı", dbFile.exists())
        val saved = backups()
        assertEquals(setOf("", "-wal"), saved.map { it.name.substringAfter(".corrupt-").dropWhile { c -> c != '-' } }.toSet())
        assertTrue(saved.any { it.readText() == "kullanıcının verisi" })
    }

    @Test
    fun sqliteCorruption_onOpen_isBackedUpBeforeReset() {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        // Veritabanı olmayan (bozuk) bir dosya.
        dbFile.writeBytes(ByteArray(8192) { (it % 251).toByte() })

        val db = AppDatabase.getDatabase(context)
        try {
            runBlocking { db.iptvDao().getAllPlaylists() }
        } catch (_: Exception) {
            // Açılış hatası olsa bile önemli olan verinin yedeklenmiş olması.
        } finally {
            db.close()
        }

        assertTrue("Bozuk dosya silinmeden önce yedeklenmeli", backups().isNotEmpty())
        assertTrue(backups().any { it.length() == 8192L })
    }

    @Test
    fun healthyDatabase_isNotBackedUp() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .applyMigrationPolicy()
            .allowMainThreadQueries()
            .build()
        runBlocking { db.iptvDao().getAllPlaylists() }
        db.close()
        assertTrue(backups().isEmpty())
    }
}
