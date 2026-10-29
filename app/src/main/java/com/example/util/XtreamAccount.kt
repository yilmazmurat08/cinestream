package com.example.util

import android.content.Context
import org.json.JSONObject

/**
 * Xtream hesabının bilgisi (player_api.php'nin user_info / server_info bölümü): aboneliğin bitiş zamanı, durum,
 * en fazla eşzamanlı bağlantı ve sunucunun saat dilimi (catch-up adresinde gerekir).
 */
data class XtreamAccountInfo(
    val expiresAtMillis: Long?,
    val status: String?,
    val maxConnections: Int?,
    val serverTimezone: String?,
    val fetchedAt: Long
)

object XtreamAccount {
    private const val PREFS = "xtream_account"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Bitişe bu kadar gün kala uyarı çıkar. */
    const val WARN_DAYS = 7

    /** Sunucudan yeniden sorulmadan önce bekleme süresi. */
    const val REFRESH_MS = 12L * 60L * 60L * 1000L

    /** player_api.php cevabını okur (eylemsiz istek). Hesap bilgisi yoksa null. */
    fun parse(json: String, now: Long): XtreamAccountInfo? = try {
        val root = JSONObject(json)
        val user = root.optJSONObject("user_info") ?: return null
        val server = root.optJSONObject("server_info")
        // exp_date saniye cinsinden, metin ya da sayı olabilir; süresiz hesaplarda null/boş gelir.
        val exp = user.opt("exp_date")?.toString()?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.let { it * 1000L }
        XtreamAccountInfo(
            expiresAtMillis = exp,
            status = user.optString("status").takeIf { it.isNotBlank() && it != "null" },
            maxConnections = user.opt("max_connections")?.toString()?.toIntOrNull(),
            serverTimezone = server?.optString("timezone")?.takeIf { it.isNotBlank() && it != "null" },
            fetchedAt = now
        )
    } catch (e: Exception) {
        null
    }

    /** Bitişe kalan gün (bugün biterse 0, geçtiyse eksi). Süresiz hesapta null. */
    fun daysLeft(info: XtreamAccountInfo?, now: Long): Int? {
        val exp = info?.expiresAtMillis ?: return null
        val diff = exp - now
        return if (diff <= 0) -1 else ((diff + DAY_MS - 1) / DAY_MS - 1).toInt()
    }

    /** Uyarı gösterilmeli mi: süre dolmuş ya da [WARN_DAYS] gün içinde doluyor. */
    fun shouldWarn(info: XtreamAccountInfo?, now: Long): Boolean {
        val days = daysLeft(info, now) ?: return false
        return days <= WARN_DAYS
    }

    fun load(context: Context, playlistId: Int): XtreamAccountInfo? = runCatching {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "p_$playlistId"
        if (!p.contains("${key}_at")) return null
        XtreamAccountInfo(
            expiresAtMillis = p.getLong("${key}_exp", 0L).takeIf { it > 0 },
            status = p.getString("${key}_status", null),
            maxConnections = p.getInt("${key}_max", -1).takeIf { it >= 0 },
            serverTimezone = p.getString("${key}_tz", null),
            fetchedAt = p.getLong("${key}_at", 0L)
        )
    }.getOrNull()

    fun save(context: Context, playlistId: Int, info: XtreamAccountInfo) {
        runCatching {
            val key = "p_$playlistId"
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("${key}_exp", info.expiresAtMillis ?: 0L)
                .putString("${key}_status", info.status)
                .putInt("${key}_max", info.maxConnections ?: -1)
                .putString("${key}_tz", info.serverTimezone)
                .putLong("${key}_at", info.fetchedAt)
                .apply()
        }
    }

    /** Uyarı bugün kapatıldı mı (her gün en fazla bir kez gösterilir). */
    fun dismissedToday(context: Context, today: String): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("dismissed_day", null) == today
    }.getOrDefault(false)

    fun dismissToday(context: Context, today: String) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("dismissed_day", today).apply()
        }
    }
}
