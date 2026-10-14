package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
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
import com.example.data.model.IPTVItemParked
import com.example.data.model.XtreamSeriesCatalogParked
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
        XtreamSeriesCatalogEntity::class,
        IPTVItemParked::class,
        XtreamSeriesCatalogParked::class
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
         * Güncel şema sürümü (yayındaki ilk sürüm 10). Şema dosyaları app/schemas/ altında dışa aktarılır.
         *
         * KURAL: Bundan sonraki her şema değişikliğinde DATABASE_VERSION bir artırılır ve
         * [MIGRATIONS] listesine bir Migration eklenir. Yıkıcı geçiş (tabloları silip yeniden
         * oluşturma) SADECE yayın öncesi geliştirme sürümleri (1–9) için geçerlidir; 10 ve
         * sonrasında migration bulunamazsa Room hata verir, kullanıcının listeleri, favorileri ve
         * izleme geçmişi sessizce silinmez. AppDatabaseMigrationTest bu kuralı denetler.
         */
        const val DATABASE_VERSION = 12

        /** Yayın öncesi geliştirme sürümleri; bu sürümlerdeki veritabanları yeniden oluşturulur. */
        internal val DEV_ONLY_VERSIONS: IntArray = (1..9).toList().toIntArray()

        /**
         * 10 → 11: Çoklu liste. Aynı anda tek liste ekranda görünür.
         * - `playlists.isActive` eklenir; en son yüklenen liste aktif olur (eskiden ayarlarda ilk sıradaki liste).
         * - Aktif olmayan listelerin öğeleri `iptv_items_parked` tablosuna taşınır (silinmez, favoriler dahil korunur).
         * - Hiçbir listeye ait olmayan öğeler olduğu yerde kalır.
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `iptv_items_parked` (`id` INTEGER NOT NULL, `playlistId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `cleanedName` TEXT NOT NULL, `logoUrl` TEXT, `streamUrl` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `type` TEXT NOT NULL, `rating` REAL NOT NULL, `summary` TEXT NOT NULL, " +
                        "`cast` TEXT NOT NULL, `director` TEXT NOT NULL, `trailerUrl` TEXT, `isFavorite` INTEGER NOT NULL, " +
                        "`season` INTEGER, `episode` INTEGER, `releaseDate` TEXT NOT NULL, `genre` TEXT NOT NULL, `tvgId` TEXT, " +
                        "PRIMARY KEY(`playlistId`, `id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `xtream_series_catalog_parked` (`playlistId` INTEGER NOT NULL, " +
                        "`seriesId` INTEGER NOT NULL, `name` TEXT NOT NULL, `canonicalKey` TEXT NOT NULL, `coverUrl` TEXT NOT NULL, " +
                        "`backdropUrl` TEXT NOT NULL, `plot` TEXT NOT NULL, `rating` REAL NOT NULL, `releaseDate` TEXT NOT NULL, " +
                        "`genre` TEXT NOT NULL, `cast` TEXT NOT NULL, `director` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `seriesId`))"
                )
                db.execSQL(
                    "UPDATE `playlists` SET `isActive` = 1 WHERE `id` = " +
                        "(SELECT `id` FROM `playlists` ORDER BY `loadedAt` DESC, `id` DESC LIMIT 1)"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `iptv_items_parked` (`id`, `playlistId`, `name`, `cleanedName`, `logoUrl`, `streamUrl`, " +
                        "`category`, `type`, `rating`, `summary`, `cast`, `director`, `trailerUrl`, `isFavorite`, `season`, `episode`, " +
                        "`releaseDate`, `genre`, `tvgId`) SELECT `id`, `playlistId`, `name`, `cleanedName`, `logoUrl`, `streamUrl`, " +
                        "`category`, `type`, `rating`, `summary`, `cast`, `director`, `trailerUrl`, `isFavorite`, `season`, `episode`, " +
                        "`releaseDate`, `genre`, `tvgId` FROM `iptv_items` " +
                        "WHERE `playlistId` IN (SELECT `id` FROM `playlists` WHERE `isActive` = 0)"
                )
                db.execSQL("DELETE FROM `iptv_items` WHERE `playlistId` IN (SELECT `id` FROM `playlists` WHERE `isActive` = 0)")
            }
        }

        /**
         * 11 → 12: Kanalların listedeki sırası (`sortOrder`). Kanallar sağlayıcının sırasıyla gösterilir.
         * Mevcut öğelerde sıra 0 olur (bilinmiyor); liste ilk yenilendiğinde gerçek sıra yazılır. Veri silinmez.
         */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `iptv_items` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `iptv_items_staging` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `iptv_items_parked` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 10'dan sonraki şema değişiklikleri için migration'lar; her yeni sürüm buraya bir adım ekler. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_10_11, MIGRATION_11_12)

        /** Uygulamanın ve testlerin kullandığı tek migration politikası. */
        fun <T : RoomDatabase> RoomDatabase.Builder<T>.applyMigrationPolicy(): RoomDatabase.Builder<T> =
            addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(true, *DEV_ONLY_VERSIONS)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Yalnızca testler: açık veritabanını kapatıp tekil örneği sıfırlar. */
        @androidx.annotation.VisibleForTesting
        fun closeForTest() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

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
                    // SQLite bozulma algılarsa dosyalar silinmeden önce yedeklenir.
                    .openHelperFactory(BackupOnCorruptionOpenHelperFactory())
                    // Kayıt (journal) modunu Room seçer: normal cihazlarda WAL, düşük bellekli cihazlarda (ör. Xiaomi
                    // Mi TV Stick) TRUNCATE. Eskiden burada açılışta WAL zorla açılıyordu; düşük bellekli cihazlarda
                    // bu bağlantıyı yeniden kurup Room'un geçici takip tablosunu siliyor ve uygulama açılışta
                    // "no such table: room_table_modification_log" ile çöküyordu.
                    .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
                    .build()

                // Test-open database helper to verify integrity immediately. Ana iş parçacığında
                // (ViewModel fabrikası) disk açılışı yapılmaz; Room ilk sorguda arka planda açar.
                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) try {
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
                        .openHelperFactory(BackupOnCorruptionOpenHelperFactory())
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
                // SQLiteDiskIOException (ör. disk dolu) geçicidir; bozulma sayılıp veri silinmez.
                if (current is SQLiteDatabaseCorruptException) {
                    return true
                }
                if (current is SQLiteException && current.message?.contains("corrupt", ignoreCase = true) == true) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        /** Gerçekten bozulmuş veritabanını yedeğe taşır (silmez). DB_NAME.corrupt-<zaman> olarak kalır. */
        fun recoverCorruptedDatabase(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                    INSTANCE = null
                    // Silmek yerine yedeğe taşı: kullanıcı verisi kurtarılabilir kalır, uygulama temiz bir
                    // veritabanıyla açılır.
                    val backup = CorruptionBackup.move(context.applicationContext.getDatabasePath(DB_NAME))
                    Log.w(TAG, "Corrupted SQLite database moved aside: ${backup?.name}")
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

