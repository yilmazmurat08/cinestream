package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.db.AppDatabase
import com.example.data.db.AppDatabase.Companion.applyMigrationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room şema sürümü 10, yayındaki ilk sürümdür. Bu testler:
 *  - 10.json şemasının dışa aktarıldığını,
 *  - v10 veritabanındaki kullanıcı verisinin (liste, favori, izleme geçmişi) uygulamanın gerçek
 *    migration politikasıyla açıldığında korunduğunu,
 *  - 10'dan güncel sürüme kadar her adım için bir Migration bulunduğunu,
 *  - yıkıcı geçişin yalnızca geliştirme sürümlerinde (1–9) kaldığını denetler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private val testDb = "migration-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        context.deleteDatabase(testDb)
    }

    @Test
    fun v10UserDataSurvivesOpeningWithAppMigrationPolicy() {
        helper.createDatabase(testDb, 10).apply {
            execSQL(
                "INSERT INTO playlists (id, name, url, isXtream, xtreamUsername, xtreamPassword, loadedAt, epgUrl) " +
                    "VALUES (7, 'Ev', 'http://example.com/list.m3u', 0, NULL, NULL, 1, NULL)"
            )
            execSQL(
                "INSERT INTO iptv_items (id, playlistId, name, cleanedName, logoUrl, streamUrl, category, type, rating, " +
                    "summary, `cast`, director, trailerUrl, isFavorite, season, episode, releaseDate, genre, tvgId) " +
                    "VALUES (42, 7, 'TRT 1 HD', 'TRT 1', NULL, 'http://example.com/1.ts', 'Ulusal', 'LIVE', 0.0, " +
                    "'', '', '', NULL, 1, NULL, NULL, '', '', NULL)"
            )
            execSQL(
                "INSERT INTO continue_watching (itemId, itemName, itemType, itemLogo, streamUrl, category, " +
                    "progressSeconds, totalSeconds, lastPlayedAt) " +
                    "VALUES (42, 'TRT 1', 'LIVE', NULL, 'http://example.com/1.ts', 'Ulusal', 120, 0, 5)"
            )
            close()
        }

        // 10 -> güncel sürüm: tüm migration'lar uygulanır ve şema doğrulanır.
        helper.runMigrationsAndValidate(testDb, AppDatabase.DATABASE_VERSION, true, *AppDatabase.MIGRATIONS).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .applyMigrationPolicy()
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val playlists = db.iptvDao().getAllPlaylists()
                assertEquals(listOf("Ev"), playlists.map { it.name })
                val items = db.iptvDao().getAllItemsDirect()
                assertEquals(1, items.size)
                assertTrue("favori korunmalı", items.single().isFavorite)
                val history = db.iptvDao().getAllContinueWatchingOnce()
                assertEquals(120L, history.single().progressSeconds)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun everyVersionAfter10HasAMigration() {
        for (from in 10 until AppDatabase.DATABASE_VERSION) {
            val found = AppDatabase.MIGRATIONS.any { it.startVersion == from && it.endVersion == from + 1 }
            assertTrue("$from -> ${from + 1} için Migration eksik; yıkıcı geçiş yok, kullanıcı verisi korunmalı", found)
        }
    }

    @Test
    fun destructiveFallbackIsLimitedToDevVersions() {
        val devVersions = AppDatabase.DEV_ONLY_VERSIONS.toList()
        assertEquals((1..9).toList(), devVersions)
        assertTrue(devVersions.none { it >= 10 })
    }

    @Test
    fun oldDevelopmentDatabaseIsRecreatedWithoutCrash() {
        // Geliştirme sürümü 9'dan kalma bir veritabanı dosyası.
        val file = context.getDatabasePath(testDb)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL("CREATE TABLE legacy (id INTEGER PRIMARY KEY)")
            raw.version = 9
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .applyMigrationPolicy()
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(AppDatabase.DATABASE_VERSION, db.openHelper.readableDatabase.version)
            runBlocking { assertTrue(db.iptvDao().getAllPlaylists().isEmpty()) }
        } finally {
            db.close()
        }
    }
}
