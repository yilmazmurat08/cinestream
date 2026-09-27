package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import coil.Coil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashType categorization for clean crash triage
 */
enum class CrashCategory {
    UI,
    PLAYER,
    NETWORK,
    OUT_OF_MEMORY,
    DATABASE_CORRUPTION,
    NULL_POINTER,
    UNKNOWN
}

/**
 * CrashRecoveryManager:
 * 1. Safe, non-blocking crash telemetry logging.
 * 2. Startup crash loop detection with progressive non-destructive recovery.
 * 3. Graceful fallback on consecutive failures without wiping user data unnecessarily.
 */
object CrashRecoveryManager {

    private const val TAG = "CrashRecoveryManager"
    private const val PREFS_NAME = "crash_recovery_prefs"
    private const val KEY_STARTUP_CRASH_COUNT = "startup_crash_count"
    private const val KEY_LAST_CRASH_TIMESTAMP = "last_crash_timestamp"
    private const val KEY_LAST_CRASH_CATEGORY = "last_crash_category"
    private const val KEY_LAST_CRASH_MESSAGE = "last_crash_message"
    private const val KEY_PENDING_REPORT_FILE = "pending_crash_report_file"
    private const val CRASH_WINDOW_MS = 15_000L // 15 seconds from launch is considered startup window

    private var appStartTimeMs: Long = 0L

    /**
     * Categorizes a Throwable into a specific CrashCategory to avoid misdiagnosing generic crashes as DB corruption.
     */
    fun categorizeThrowable(throwable: Throwable): CrashCategory {
        var current: Throwable? = throwable
        while (current != null) {
            val className = current.javaClass.name.lowercase()
            val msg = current.message?.lowercase() ?: ""

            if (current is OutOfMemoryError || msg.contains("out of memory")) {
                return CrashCategory.OUT_OF_MEMORY
            }
            if (current is android.database.sqlite.SQLiteDatabaseCorruptException ||
                (current is android.database.sqlite.SQLiteException && msg.contains("corrupt"))
            ) {
                return CrashCategory.DATABASE_CORRUPTION
            }
            if (current is NullPointerException) {
                return CrashCategory.NULL_POINTER
            }
            if (className.contains("media3") || className.contains("exoplayer") || className.contains("player")) {
                return CrashCategory.PLAYER
            }
            if (className.contains("compose") || className.contains("view") || className.contains("layout")) {
                return CrashCategory.UI
            }
            if (current is java.io.IOException || current is java.net.SocketException || current is java.net.UnknownHostException) {
                return CrashCategory.NETWORK
            }
            current = current.cause
        }
        return CrashCategory.UNKNOWN
    }

    /**
     * Initializes crash detection and performs progressive recovery if consecutive startup crashes are detected.
     */
    fun onAppStart(context: Context, scope: CoroutineScope) {
        appStartTimeMs = System.currentTimeMillis()
        val prefs = getPrefs(context)
        val crashCount = prefs.getInt(KEY_STARTUP_CRASH_COUNT, 0)
        val lastCategoryName = prefs.getString(KEY_LAST_CRASH_CATEGORY, CrashCategory.UNKNOWN.name)

        if (crashCount > 0) {
            Log.w(TAG, "App started with previous startup crash count: $crashCount (Last Category: $lastCategoryName)")

            // Increment startup crash count
            prefs.edit().putInt(KEY_STARTUP_CRASH_COUNT, crashCount + 1).apply()

            // Progressive, non-destructive recovery based on crash count and category
            scope.launch(Dispatchers.IO) {
                try {
                    when {
                        // Level 1 (1-2 crashes): Clear volatile image/disk caches and temp files only
                        crashCount in 1..2 -> {
                            Log.i(TAG, "Level 1 Recovery: Clearing image/disk caches and temp files...")
                            clearVolatileCaches(context)
                        }
                        // Level 2 (3+ crashes): Clear caches and corrupted temp playlist files
                        crashCount >= 3 -> {
                            Log.w(TAG, "Level 2 Recovery: Clearing volatile caches and temporary playlist caches...")
                            clearVolatileCaches(context)
                            clearTempPlaylistFiles(context)
                            
                            // If specifically DB corruption was logged, recover DB
                            if (lastCategoryName == CrashCategory.DATABASE_CORRUPTION.name) {
                                Log.w(TAG, "Level 3 Recovery: Unrecoverable DB corruption confirmed. Recovering database...")
                                com.example.data.db.AppDatabase.recoverCorruptedDatabase(context)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error executing recovery actions", e)
                }
            }
        } else {
            // First normal launch or healthy state: mark this startup
            prefs.edit().putInt(KEY_STARTUP_CRASH_COUNT, 1).apply()
        }

        // Setup global uncaught exception handler for safe telemetry ONLY (no heavy sync I/O or DB wipes on crash thread)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleUncaughtException(context, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Error in crash handler telemetry", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Schedule reset of startup crash counter after app runs successfully for 10 seconds
        scope.launch(Dispatchers.IO) {
            delay(10_000L)
            markStartupSuccessful(context)
        }
    }

    /**
     * Marks the application startup as healthy and resets consecutive crash counter.
     */
    fun markStartupSuccessful(context: Context) {
        try {
            getPrefs(context).edit()
                .putInt(KEY_STARTUP_CRASH_COUNT, 0)
                .apply()
            Log.d(TAG, "Startup marked as healthy. Startup crash counter reset.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error resetting startup crash counter", e)
        }
    }

    /**
     * Records crash metadata safely without blocking or triggering destructive actions on the dying thread.
     */
    @androidx.annotation.VisibleForTesting
    internal fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        val category = categorizeThrowable(throwable)
        val timeSinceStart = System.currentTimeMillis() - appStartTimeMs
        val isStartupCrash = timeSinceStart in 0..CRASH_WINDOW_MS

        Log.e(TAG, "FATAL CRASH on thread ${thread.name} [Category: $category, StartupCrash: $isStartupCrash, Uptime: ${timeSinceStart}ms]", throwable)

        try {
            val prefs = getPrefs(context)
            prefs.edit()
                .putLong(KEY_LAST_CRASH_TIMESTAMP, System.currentTimeMillis())
                .putString(KEY_LAST_CRASH_CATEGORY, category.name)
                .putString(KEY_LAST_CRASH_MESSAGE, throwable.localizedMessage ?: throwable.javaClass.simpleName)
                .apply()

            // Write a persistent crash log snippet to disk for diagnostics
            val logFile = writeCrashLogFile(context, category, thread.name, throwable)
            // Bir sonraki açılışta hata raporu ekranı gösterilsin (commit: süreç hemen kapanacak).
            if (logFile != null) {
                prefs.edit().putString(KEY_PENDING_REPORT_FILE, logFile.absolutePath).commit()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write crash telemetry", e)
        }
    }

    /**
     * Clears in-memory & disk caches (Coil, OkHttp, cacheDir) without deleting DB or user settings.
     */
    private fun clearVolatileCaches(context: Context) {
        try {
            // 1. Clear Coil Cache safely
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()

            // 2. Clear cache directory files safely
            context.cacheDir.listFiles()?.forEach { file ->
                try {
                    file.deleteRecursively()
                } catch (e: Exception) {
                    // Ignore individual file deletion errors
                }
            }
            Log.i(TAG, "Volatile image & network caches cleared safely.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing volatile caches", e)
        }
    }

    /**
     * Clears temporary downloaded playlist files in case an invalid playlist format caused repeated OOM / parsing crash.
     */
    private fun clearTempPlaylistFiles(context: Context) {
        try {
            val playlistDir = context.getExternalFilesDir("playlists") ?: File(context.filesDir, "playlists")
            if (playlistDir.exists()) {
                playlistDir.listFiles()?.forEach { file ->
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing temp playlist files", e)
        }
    }

    private fun writeCrashLogFile(context: Context, category: CrashCategory, threadName: String, throwable: Throwable): File? {
        return try {
            val logDir = File(context.filesDir, "crash_logs")
            if (!logDir.exists()) logDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val logFile = File(logDir, "crash_$timestamp.txt")
            val content = buildString {
                appendLine("Timestamp: $timestamp")
                appendLine("Category: $category")
                appendLine("Thread: $threadName")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("StackTrace:")
                appendLine(throwable.stackTraceToString())
            }
            logFile.writeText(DiagnosticLog.redact(content))
            logFile
        } catch (e: Throwable) {
            null
        }
    }

    /** Henüz kullanıcıya gösterilmemiş bir çökme raporu varsa dosyasını döndürür. */
    fun pendingCrashReport(context: Context): File? = try {
        getPrefs(context).getString(KEY_PENDING_REPORT_FILE, null)?.let { File(it) }?.takeIf { it.exists() }
    } catch (e: Throwable) {
        null
    }

    fun clearPendingCrashReport(context: Context) {
        try {
            getPrefs(context).edit().remove(KEY_PENDING_REPORT_FILE).apply()
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
