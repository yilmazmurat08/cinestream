package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity

class PlaybackForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "cinestream_playback"
        const val NOTIFICATION_ID = 7301
        const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, title: String) {
            val intent = Intent(context, PlaybackForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PlaybackForegroundService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "CineStream"
        startForeground(NOTIFICATION_ID, buildNotification(title))
        return START_STICKY
    }

    private fun buildNotification(title: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Oynatma", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "İçerik oynatılırken gösterilen bildirim"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Şu an oynatılıyor")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
