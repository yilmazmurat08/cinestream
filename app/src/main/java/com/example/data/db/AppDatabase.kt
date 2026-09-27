package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.IPTVItemStaging
import com.example.data.model.PersonDetailsEntity
import com.example.data.model.Playlist
import com.example.data.model.SearchHistory
import com.example.data.model.AiRecommendationHistory
import com.example.data.model.SeriesCoverEntity
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.model.tmdb.TmdbCacheEntity

@Database(
    entities = [
        Playlist::class,
        IPTVItem::class,
        IPTVItemStaging::class,
        ContinueWatching::class,
        PersonDetailsEntity::class,
        SearchHistory::class,
        AiRecommendationHistory::class,
        TmdbCacheEntity::class,
        SeriesCoverEntity::class,
        XtreamSeriesCatalogEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun iptvDao(): IPTVDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "cinestream_database"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `iptv_items_staging` (
                        `id` INTEGER NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `cleanedName` TEXT NOT NULL,
                        `logoUrl` TEXT,
                        `streamUrl` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `rating` REAL NOT NULL,
                        `summary` TEXT NOT NULL,
                        `cast` TEXT NOT NULL,
                        `director` TEXT NOT NULL,
                        `trailerUrl` TEXT,
                        `isFavorite` INTEGER NOT NULL,
                        `season` INTEGER,
                        `episode` INTEGER,
                        `releaseDate` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tmdb_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceKey` TEXT NOT NULL,
                        `normalizedTitle` TEXT NOT NULL,
                        `year` TEXT,
                        `mediaType` TEXT NOT NULL,
                        `tmdbId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `overview` TEXT NOT NULL,
                        `posterPath` TEXT,
                        `backdropPath` TEXT,
                        `rating` REAL NOT NULL,
                        `releaseDate` TEXT NOT NULL,
                        `genresJson` TEXT NOT NULL,
                        `castJson` TEXT NOT NULL,
                        `directorName` TEXT,
                        `directorPhoto` TEXT,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tmdb_cache_sourceKey` ON `tmdb_cache` (`sourceKey`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return try {
                val db = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.enableWriteAheadLogging()
                            } catch (e: Exception) {
                                Log.w(TAG, "WAL mode enable failed, continuing standard mode", e)
                            }
                        }
                    })
                    .build()

                // Test-open database helper to verify integrity immediately
                try {
                    db.openHelper.readableDatabase
                } catch (oe: Exception) {
                    if (isCorruptionException(oe)) {
                        throw oe
                    } else {
                        Log.w(TAG, "Non-fatal initial open notification: ${oe.message}")
                    }
                }
                db
            } catch (e: Exception) {
                Log.e(TAG, "Error building Room database instance", e)
                if (isCorruptionException(e)) {
                    Log.w(TAG, "Database corruption detected on build. Attempting safe recovery...", e)
                    recoverCorruptedDatabase(appContext)
                    val recoveredDb = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                        .fallbackToDestructiveMigration()
                        .build()
                    recoveredDb.openHelper.readableDatabase
                    recoveredDb
                } else {
                    throw e
                }
            }
        }

        private fun isCorruptionException(throwable: Throwable): Boolean {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is SQLiteDatabaseCorruptException || current is SQLiteDiskIOException) {
                    return true
                }
                if (current is SQLiteException && current.message?.contains("corrupt", ignoreCase = true) == true) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun recoverCorruptedDatabase(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                    INSTANCE = null
                    context.applicationContext.deleteDatabase(DB_NAME)
                    Log.w(TAG, "Corrupted SQLite database safely reset.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reset corrupted database", e)
                }
            }
        }

        fun clearCorruptedCache(context: Context) {
            recoverCorruptedDatabase(context)
        }
    }
}

