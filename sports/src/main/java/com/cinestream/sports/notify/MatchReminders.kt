package com.cinestream.sports.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cinestream.sports.R
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.NotificationEntry
import com.cinestream.sports.model.NotificationType
import com.cinestream.sports.settings.DataStoreSportsSettingsStore
import java.util.concurrent.TimeUnit

/** Maç hatırlatmalarını kurar ve iptal eder. Testlerde sahtesi kullanılır. */
interface ReminderScheduler {
    fun schedule(match: Match, minutesBefore: Int)
    fun cancel(matchId: String)

    object None : ReminderScheduler {
        override fun schedule(match: Match, minutesBefore: Int) = Unit
        override fun cancel(matchId: String) = Unit
    }
}

/**
 * WorkManager ile hatırlatma: tam zamanlı alarm izni istemez. Telefon uykudaysa birkaç dakika gecikebilir.
 * Bildirime dokununca uygulama "sports_match_id" ekiyle açılır; uygulama maç detayını gösterebilir.
 */
class WorkManagerReminderScheduler(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) : ReminderScheduler {

    override fun schedule(match: Match, minutesBefore: Int) {
        val delay = match.kickoffEpochMillis - minutesBefore * 60_000L - clock()
        if (delay <= 0) return
        val title = "${match.home.name} - ${match.away.name}"
        val body = context.getString(R.string.sports_reminder_body, minutesBefore, match.league.name)
        val request = OneTimeWorkRequestBuilder<MatchReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    MatchReminderWorker.KEY_MATCH to match.id,
                    MatchReminderWorker.KEY_TITLE to title,
                    MatchReminderWorker.KEY_BODY to body
                )
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(match.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(matchId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(matchId))
    }

    companion object {
        const val TAG = "sports_reminder"
        fun workName(matchId: String) = "sports_reminder_$matchId"
    }
}

class MatchReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val matchId = inputData.getString(KEY_MATCH) ?: return Result.success()
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val body = inputData.getString(KEY_BODY).orEmpty()
        SportsNotifier.show(applicationContext, matchId, title, body)
        DataStoreSportsSettingsStore(applicationContext).addNotification(
            NotificationEntry(
                id = "reminder_$matchId",
                type = NotificationType.REMINDER,
                title = title,
                body = body,
                timeEpochMillis = System.currentTimeMillis(),
                matchId = matchId
            )
        )
        return Result.success()
    }

    companion object {
        const val KEY_MATCH = "match_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}

object SportsNotifier {
    const val CHANNEL_ID = "sports_reminders"
    const val EXTRA_MATCH_ID = "sports_match_id"

    fun show(context: Context, matchId: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_MATCH_ID, matchId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = launch?.let {
            PendingIntent.getActivity(context, matchId.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sports_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(matchId.hashCode(), notification)
        } catch (_: SecurityException) {
            // İzin bu arada geri alındıysa bildirim gösterilmez; uygulama çökmez.
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.sports_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
