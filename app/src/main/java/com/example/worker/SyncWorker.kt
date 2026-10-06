package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.db.AppDatabase
import com.example.data.repository.IPTVRepository
import java.util.concurrent.TimeUnit

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val UNIQUE_WORK_NAME = "CineStreamSyncWorker"

        fun scheduleSync(context: Context, intervalSetting: String) {
            val workManager = WorkManager.getInstance(context)
            if (intervalSetting == "MANUAL") {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                Log.d(TAG, "Cancelled background sync worker (MANUAL mode)")
                return
            }

            val (repeatInterval, timeUnit) = when (intervalSetting) {
                "12_HOURS" -> Pair(12L, TimeUnit.HOURS)
                "7_DAYS" -> Pair(7L, TimeUnit.DAYS)
                else -> Pair(24L, TimeUnit.HOURS) // "24_HOURS"
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(repeatInterval, timeUnit)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            Log.d(TAG, "Scheduled background sync worker with interval: $intervalSetting")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing background sync worker for IPTV playlists...")
        return try {
            val database = AppDatabase.getDatabase(context)
            val repository = IPTVRepository(database.iptvDao(), database, context.applicationContext)

            val list: List<com.example.data.model.Playlist> = database.iptvDao().getAllPlaylists()
            if (list.isEmpty()) {
                Result.success()
            } else {
                var successCount = 0
                var failedCount = 0
                var hasTransientError = false

                for (playlist in list) {
                    if (playlist.url.startsWith("http", ignoreCase = true)) {
                        try {
                            val count = repository.syncPlaylist(playlist)
                            if (count > 0) {
                                successCount++
                            } else {
                                // Empty or 0 items, but successful HTTP parsing (old data preserved)
                                successCount++
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            val existingItems = database.iptvDao().getItemsCountForPlaylist(playlist.id)
                            if (existingItems > 0) {
                                Log.w(TAG, "Sync for ${playlist.name} encountered issue (${e.message}), but $existingItems cached items are preserved.")
                                successCount++
                            } else {
                                failedCount++
                                Log.e(TAG, "Error syncing playlist ${playlist.name}: ${e.message}", e)
                                val msg = e.message.orEmpty()
                                val isAuthOrClientError = msg.contains("401") || msg.contains("403") || msg.contains("404") || msg.contains("reddedildi", ignoreCase = true)
                                if (!isAuthOrClientError && (e is java.net.SocketTimeoutException || e is java.net.ConnectException || e is java.net.UnknownHostException)) {
                                    hasTransientError = true
                                }
                            }
                        }
                    }
                }

                if (failedCount == 0) {
                    Log.d(TAG, "Background sync finished successfully for all playlists!")
                    Result.success()
                } else if (hasTransientError && runAttemptCount < 3) {
                    Log.w(TAG, "Background sync had transient network failures, retrying... (success: $successCount, failed: $failedCount)")
                    Result.retry()
                } else if (successCount > 0) {
                    Log.w(TAG, "Background sync partially completed (success: $successCount, failed: $failedCount)")
                    Result.success()
                } else {
                    Log.e(TAG, "Background sync failed for all remote playlists.")
                    Result.failure()
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
