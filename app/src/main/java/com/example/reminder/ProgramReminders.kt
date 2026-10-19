package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import org.json.JSONObject

/** Kurulmuş bir program hatırlatması. [key] kanal ve başlangıç saatinden oluşur; aynı program iki kez kurulmaz. */
data class ProgramReminder(
    val channelId: Int,
    val channelName: String,
    val programTitle: String,
    val startMillis: Long
) {
    val key: String get() = key(channelId, startMillis)
    val notifyAtMillis: Long get() = startMillis - ProgramReminders.LEAD_TIME_MS

    companion object {
        fun key(channelId: Int, startMillis: Long) = "$channelId|$startMillis"
    }
}

/**
 * Program hatırlatmaları (ücretsiz): program başlamadan 5 dakika önce bildirim gelir; bildirime dokununca kanal açılır.
 * Hatırlatmalar cihazda saklanır (en fazla [MAX_REMINDERS]); başlamış olanlar kendiliğinden silinir. Zamanı Android'in
 * alarm sistemi tutar, böylece uygulama kapalıyken de bildirim gelir; telefon yeniden başlayınca alarmlar yeniden kurulur.
 */
object ProgramReminders {
    const val PREFS = "program_reminders"
    const val LEAD_TIME_MS = 5 * 60 * 1000L
    const val MAX_REMINDERS = 100

    fun all(context: Context, now: Long = System.currentTimeMillis()): List<ProgramReminder> {
        val prefs = prefs(context)
        val entries = runCatching { prefs.all }.getOrDefault(emptyMap())
        val parsed = entries.mapNotNull { (k, v) -> (v as? String)?.let { parse(it) }?.let { k to it } }
        val expired = entries.keys.filter { k -> parsed.none { it.first == k && it.second.startMillis > now } }
        if (expired.isNotEmpty()) runCatching { prefs.edit { expired.forEach { remove(it) } } }
        return parsed.map { it.second }.filter { it.startMillis > now }.sortedBy { it.startMillis }
    }

    fun keys(context: Context): Set<String> = all(context).mapTo(HashSet()) { it.key }

    fun isSet(context: Context, channelId: Int, startMillis: Long): Boolean =
        runCatching { prefs(context).contains(ProgramReminder.key(channelId, startMillis)) }.getOrDefault(false)

    /** Kurar; başlamış program ya da dolu liste için false döner. */
    fun add(context: Context, reminder: ProgramReminder, now: Long = System.currentTimeMillis()): Boolean {
        if (reminder.startMillis <= now) return false
        val current = all(context, now)
        if (current.none { it.key == reminder.key } && current.size >= MAX_REMINDERS) return false
        val saved = runCatching { prefs(context).edit(commit = true) { putString(reminder.key, encode(reminder)) } }.isSuccess
        if (saved) schedule(context, reminder, now)
        return saved
    }

    fun remove(context: Context, channelId: Int, startMillis: Long) {
        val key = ProgramReminder.key(channelId, startMillis)
        runCatching { prefs(context).edit { remove(key) } }
        runCatching { alarmManager(context)?.cancel(pendingIntent(context, key, null)) }
    }

    fun find(context: Context, key: String): ProgramReminder? =
        runCatching { prefs(context).getString(key, null) }.getOrNull()?.let { parse(it) }

    /** Telefon yeniden başlayınca ya da uygulama güncellenince bütün alarmlar yeniden kurulur. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        all(context, now).forEach { schedule(context, it, now) }
    }

    private fun schedule(context: Context, reminder: ProgramReminder, now: Long) {
        val alarms = alarmManager(context) ?: return
        val at = maxOf(reminder.notifyAtMillis, now + 1_000L)
        val pi = pendingIntent(context, reminder.key, reminder)
        runCatching {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms() ->
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                // Tam zamanlı alarm izni yoksa Android birkaç dakika kaydırabilir; bildirim yine program başlamadan gelir.
                else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    private fun pendingIntent(context: Context, key: String, reminder: ProgramReminder?): PendingIntent {
        val intent = Intent(context, ProgramReminderReceiver::class.java).apply {
            action = ProgramReminderReceiver.ACTION_FIRE
            putExtra(ProgramReminderReceiver.EXTRA_KEY, key)
            if (reminder != null) {
                putExtra(ProgramReminderReceiver.EXTRA_CHANNEL_ID, reminder.channelId)
            }
        }
        return PendingIntent.getBroadcast(
            context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmManager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(r: ProgramReminder): String = JSONObject()
        .put("cid", r.channelId)
        .put("ch", r.channelName)
        .put("t", r.programTitle)
        .put("s", r.startMillis)
        .toString()

    private fun parse(json: String): ProgramReminder? = runCatching {
        val o = JSONObject(json)
        ProgramReminder(o.getInt("cid"), o.optString("ch"), o.optString("t"), o.getLong("s"))
    }.getOrNull()
}
