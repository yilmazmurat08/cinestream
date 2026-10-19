package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.util.LocaleHelper

/**
 * Hatırlatma zamanı gelince bildirimi gösterir; telefon yeniden başlayınca / uygulama güncellenince alarmları
 * yeniden kurar. Bildirime dokununca uygulama açılır ve kanal oynatılır ([MainActivity.EXTRA_OPEN_CHANNEL_ID]).
 */
class ProgramReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val key = intent.getStringExtra(EXTRA_KEY) ?: return
                // Kullanıcı kaldırdıysa (ya da program başladıysa ve silindiyse) bildirim gösterilmez.
                val reminder = ProgramReminders.find(context, key) ?: return
                // Erken tetiklenme (başka bir uygulamanın gönderdiği yayın) bildirim göstermez.
                if (System.currentTimeMillis() < reminder.notifyAtMillis - 2 * 60 * 1000L) return
                notify(context, reminder)
                ProgramReminders.remove(context, reminder.channelId, reminder.startMillis)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ProgramReminders.rescheduleAll(context)
        }
    }

    private fun notify(context: Context, reminder: ProgramReminder) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                LocaleHelper.getString(context, R.string.epg_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            runCatching { manager.createNotificationChannel(channel) }
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val open = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CHANNEL
            putExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, reminder.channelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, reminder.key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(reminder.startMillis))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(LocaleHelper.getString(context, R.string.epg_reminder_notif_title, reminder.programTitle))
            .setContentText(LocaleHelper.getString(context, R.string.epg_reminder_notif_text, reminder.channelName, time))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(reminder.key.hashCode(), notification) }
    }

    companion object {
        const val ACTION_FIRE = "com.cinestream.iptv.PROGRAM_REMINDER"
        const val EXTRA_KEY = "reminder_key"
        const val EXTRA_CHANNEL_ID = "reminder_channel_id"
        const val CHANNEL_ID = "program_reminders"
    }
}
