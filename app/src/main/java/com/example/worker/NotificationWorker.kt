package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        sendReminderNotification()
        return Result.success()
    }

    private fun sendReminderNotification() {
        val channelId = "cinestream_reminders"
        val channelName = "CineStream Hatırlatıcıları"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "CineStream premium reminder notifications"
                enableLights(true)
                lightColor = Color.parseColor("#EC4899") // Neon Pink
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Open app PendingIntent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification builders
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play) // Sinematik play butonu
            .setContentTitle("🎬 CINESTREAM Işıklarını Yaktı!")
            .setContentText("Seni özledik! Trend listesindeki en yeni filmler ve canlı yayın kanalları seni bekliyor. Bu akşam ekran başına geçmeye ne dersin? 🍿")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Seni özledik! Trend listesindeki en yeni filmler ve canlı yayın kanalları seni bekliyor. Bu akşam ekran başına geçmeye ne dersin? 🍿")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(Color.parseColor("#EC4899")) // Premium neon pink color
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(4899, notification)
    }
}
