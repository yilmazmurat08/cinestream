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
        private const val STOP_DELAY_MS = 1_500L

        /**
         * Servisi kullanan oynatıcılar. Kanal/bölüm hızlıca değişince yeni oynatıcı başlatır, eski oynatıcı hemen
         * ardından durdurur; servis hazır olmadan durdurulursa Android uygulamayı kapatır
         * (ForegroundServiceDidNotStartInTimeException). Bu yüzden servis ancak hiçbir kullanıcı kalmayınca ve
         * kısa bir beklemeden sonra durdurulur.
         */
        private val owners = HashSet<Int>()
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var pendingStop: Runnable? = null

        fun start(context: Context, title: String, owner: Any = DEFAULT_OWNER) {
            val app = context.applicationContext
            synchronized(owners) {
                owners.add(System.identityHashCode(owner))
                pendingStop?.let { mainHandler.removeCallbacks(it) }
                pendingStop = null
            }
            val intent = Intent(app, PlaybackForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
            }
            try {
                app.startForegroundService(intent)
            } catch (e: Exception) {
                // Arka planda başlatma izni yoksa (Android 12+) oynatma bildirimsiz devam eder.
            }
        }

        fun stop(context: Context, owner: Any = DEFAULT_OWNER) {
            val app = context.applicationContext
            synchronized(owners) {
                owners.remove(System.identityHashCode(owner))
                if (owners.isNotEmpty()) return
                pendingStop?.let { mainHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    val stillUnused = synchronized(owners) { owners.isEmpty() }
                    if (stillUnused) {
                        try {
                            app.stopService(Intent(app, PlaybackForegroundService::class.java))
                        } catch (e: Exception) {
                        }
                    }
                }
                pendingStop = runnable
                mainHandler.postDelayed(runnable, STOP_DELAY_MS)
            }
        }

        private val DEFAULT_OWNER = Any()
    }

    override fun onCreate() {
        super.onCreate()
        // startForegroundService sonrası ilk iş: ön plana geç. Servis onStartCommand'dan önce durdurulsa bile
        // Android'in "zamanında ön plana geçmedi" hatası oluşmaz.
        startForeground(NOTIFICATION_ID, buildNotification("CineStream"))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "CineStream"
        startForeground(NOTIFICATION_ID, buildNotification(title))
        // Süreç öldürülürse oynatıcı olmadan yeniden başlayıp boş bir "oynatılıyor" bildirimi bırakmasın.
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, com.example.util.LocaleHelper.getString(this, com.example.R.string.notif_playback_channel), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = com.example.util.LocaleHelper.getString(this@PlaybackForegroundService, com.example.R.string.notif_playback_channel_desc)
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
            .setContentTitle(com.example.util.LocaleHelper.getString(this, com.example.R.string.notif_playback_title))
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
