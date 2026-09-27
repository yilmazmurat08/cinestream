package com.example.util

import android.content.Context
import android.util.Log
import coil.Coil
import com.example.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File

/**
 * StorageOptimizer:
 * 1. Proactive Disk & Cache Size Limiting (Prevents multi-gigabyte cache bloat).
 * 2. Safe Local File Persistence (for huge M3U/JSON files instead of SharedPreferences/DataStore).
 * 3. Out-Of-Memory & Corrupted Cache Recovery System.
 */
object StorageOptimizer {

    private const val TAG = "StorageOptimizer"
    private const val MAX_IMAGE_CACHE_BYTES = 150L * 1024 * 1024 // 150 MB Max Cache Limit
    private const val TARGET_PRUNE_CACHE_BYTES = 80L * 1024 * 1024 // Prune to 80 MB when limit exceeded

    /**
     * Run proactive cache maintenance in background during app startup.
     * Trims old images, deletes orphaned temp files, and ensures total cache stays within safe boundaries.
     */
    suspend fun performStartupMaintenance(context: Context) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            val currentCacheSize = getFolderSize(cacheDir)
            Log.d(TAG, "Starting cache maintenance. Current cache size: ${currentCacheSize / (1024 * 1024)} MB")

            // If cache exceeds limit, prune oldest files
            if (currentCacheSize > MAX_IMAGE_CACHE_BYTES) {
                Log.w(TAG, "Cache size exceeded 150 MB ($currentCacheSize bytes). Pruning oldest files...")
                pruneCacheDirectory(cacheDir, TARGET_PRUNE_CACHE_BYTES)
            }

            // Clean orphaned temporary files
            cleanTempFiles(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Error performing cache maintenance", e)
        }
    }

    /**
     * Full cache cleaner: Clears Coil in-memory and disk caches, OkHttp cache, and temporary files.
     */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearAllCaches(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 1. Clear Coil Cache
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()

            // 2. Clean Cache Directory
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()

            // 3. Clean Code Cache
            context.codeCacheDir.deleteRecursively()
            context.codeCacheDir.mkdirs()

            // 4. Clean external cache if available
            context.externalCacheDir?.deleteRecursively()

            Log.i(TAG, "All application caches have been cleared successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing caches", e)
        }
    }

    /**
     * Non-destructive recovery function:
     * Clears volatile temporary files, image/network disk caches, and resets corrupted temporary buffers.
     * Preserves user settings, favorites, and watch history.
     */
    suspend fun emergencyResetCorruptedData(context: Context) = withContext(Dispatchers.IO) {
        Log.w(TAG, "Initiating safe volatile cache reset for corrupted temp state / OOM...")
        try {
            // 1. Clear temporary person/metadata cache files only
            try {
                context.getSharedPreferences("person_details_cache", Context.MODE_PRIVATE).edit().clear().apply()
            } catch (prefEx: Throwable) {
                Log.e(TAG, "Error clearing temporary cache prefs", prefEx)
            }

            // 2. Clear volatile app image & disk caches
            clearAllCaches(context)

            Log.i(TAG, "Safe cache reset completed successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "Safe cache reset failed", e)
        }
    }

    /**
     * Helper to write large playlist content to app-specific file storage instead of SharedPreferences/DataStore.
     */
    suspend fun saveLargePlaylistFile(context: Context, fileName: String, content: String): File? = withContext(Dispatchers.IO) {
        try {
            val baseDir = context.getExternalFilesDir("playlists") ?: File(context.filesDir, "playlists")
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            val targetFile = File(baseDir, fileName)
            targetFile.writeText(content, Charsets.UTF_8)
            targetFile
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving large playlist file: $fileName", e)
            null
        }
    }

    /**
     * Helper to read large playlist content from app-specific file storage via a stream block to avoid OOM.
     */
    suspend fun <T> readLargePlaylistFileStream(context: Context, fileName: String, block: (BufferedReader) -> T): T? = withContext(Dispatchers.IO) {
        try {
            val baseDir = context.getExternalFilesDir("playlists") ?: File(context.filesDir, "playlists")
            val targetFile = File(baseDir, fileName)
            if (targetFile.exists()) {
                targetFile.bufferedReader(Charsets.UTF_8).use { block(it) }
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error reading playlist file: $fileName", e)
            null
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    private fun pruneCacheDirectory(dir: File, targetSizeBytes: Long) {
        val allFiles = mutableListOf<File>()
        collectAllFiles(dir, allFiles)
        // Sort oldest modified first
        allFiles.sortBy { it.lastModified() }

        var currentSize = allFiles.sumOf { it.length() }
        for (file in allFiles) {
            if (currentSize <= targetSizeBytes) break
            val length = file.length()
            if (file.delete()) {
                currentSize -= length
            }
        }
    }

    private fun collectAllFiles(dir: File, list: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                collectAllFiles(f, list)
            } else {
                list.add(f)
            }
        }
    }

    private fun cleanTempFiles(context: Context) {
        try {
            val tempDir = File(context.cacheDir, "temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning temp files", e)
        }
    }
}
