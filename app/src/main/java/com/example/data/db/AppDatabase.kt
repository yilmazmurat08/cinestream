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
    version = AppDatabase.DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun iptvDao(): IPTVDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "cinestream_database"

        /**
         * Yayındaki ilk şema sürümü. Şema dosyası app/schemas/ altında dışa aktarılır.
         *
         * KURAL: Bundan sonraki her şema değişikliğinde DATABASE_VERSION bir artırılır ve
         * [MIGRATIONS] listesine bir Migration eklenir. Yıkıcı geçiş (tabloları silip yeniden
         * oluşturma) SADECE yayın öncesi geliştirme sürümleri (1–9) için geçerlidir; 10 ve
         * sonrasında migration bulunamazsa Room hata verir, kullanıcının listeleri, favorileri ve
         * izleme geçmişi sessizce silinmez. AppDatabaseMigrationTest bu kuralı denetler.
         */
        const val DATABASE_VERSION = 10

        /** Yayın öncesi geliştirme sürümleri; bu sürümlerdeki veritabanları yeniden oluşturulur. */
        internal val DEV_ONLY_VERSIONS: IntArray = (1..9).toList().toIntArray()

        /** 10'dan sonraki şema değişiklikleri için migration'lar (ör. Migration(10, 11)). */
        val MIGRATIONS: Array<Migration> = arrayOf()

        /** Uygulamanın ve testlerin kullandığı tek migration politikası. */
        fun <T : RoomDatabase> RoomDatabase.Builder<T>.applyMigrationPolicy(): RoomDatabase.Builder<T> =
            addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(true, *DEV_ONLY_VERSIONS)

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
                    .applyMigrationPolicy()
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
                        .applyMigrationPolicy()
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

