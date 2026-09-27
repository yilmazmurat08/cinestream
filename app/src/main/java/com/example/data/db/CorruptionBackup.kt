package com.example.data.db

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bozuk veritabanı dosyalarını silmeden önce yedekler.
 *
 * Android/SQLite, bozulma algıladığında veritabanı dosyasını siler ve boş bir tane oluşturur
 * (kullanıcının listeleri, favorileri ve izleme geçmişi kaybolur). Burada silmeden önce dosyalar
 * `<ad>.corrupt-<zaman>` olarak kopyalanır; böylece veri kurtarma imkânı kalır.
 */
internal object CorruptionBackup {
    private const val TAG = "CorruptionBackup"
    private val SUFFIXES = listOf("", "-wal", "-shm", "-journal")

    /** [dbFile] ve yan dosyalarını kopyalar (taşımaz). Kopyalanan ana dosyayı döndürür. */
    fun copy(dbFile: File): File? = backup(dbFile, move = false)

    /** [dbFile] ve yan dosyalarını yedek adına taşır. */
    fun move(dbFile: File): File? = backup(dbFile, move = true)

    private fun backup(dbFile: File, move: Boolean): File? {
        if (!dbFile.exists()) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var main: File? = null
        for (suffix in SUFFIXES) {
            val source = File(dbFile.path + suffix)
            if (!source.exists()) continue
            val target = File(dbFile.parentFile, "${dbFile.name}.corrupt-$stamp$suffix")
            try {
                if (move) {
                    if (!source.renameTo(target)) {
                        source.copyTo(target, overwrite = true)
                        source.delete()
                    }
                } else {
                    source.copyTo(target, overwrite = true)
                }
                if (suffix.isEmpty()) main = target
            } catch (e: Exception) {
                Log.e(TAG, "Backup of ${source.name} failed", e)
            }
        }
        Log.w(TAG, "Corrupted database backed up as ${main?.name}")
        return main
    }
}

/**
 * Room için açılış yardımcısı: Framework uygulamasını kullanır, sadece SQLite bozulma algıladığında
 * (onCorruption) varsayılan silme işleminden önce dosyaları yedekler.
 */
internal class BackupOnCorruptionOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val wrapped = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) {
                db.path?.let { path ->
                    if (path != ":memory:") CorruptionBackup.copy(File(path))
                }
                original.onCorruption(db)
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(wrapped)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
                .build()
        )
    }
}
