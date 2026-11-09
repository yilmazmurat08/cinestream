package com.example.util

import com.example.data.model.IPTVItem
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Kaçırılan bir program: sağlayıcının arşivinde duruyor, baştan izlenebilir. */
data class CatchUpProgram(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    /** Sunucu saatine göre başlangıç ("2026-10-29:20-00"); arşiv adresinde bu kullanılır. */
    val serverStart: String
)

/**
 * Catch-up (kaçırılan yayını izleme). Xtream sağlayıcısının arşivi: kanalın geçmiş programları
 * `get_simple_data_table` ile gelir, arşivde olanlar `has_archive = 1`. Program `timeshift` adresiyle oynatılır.
 */
object CatchUp {
    /** Arşivli oynatmanın öğe türü: canlı değil (sarılabilir), izleme geçmişine yazılmaz. */
    const val TYPE = "CATCHUP"
    const val MAX_PROGRAMS = 72

    private val liveUrl = Regex("""^(https?://[^/]+)/live/([^/]+)/([^/]+)/(\d+)(\.[a-zA-Z0-9]+)?$""", RegexOption.IGNORE_CASE)

    data class Credentials(val host: String, val user: String, val pass: String, val streamId: Int)

    /** Xtream canlı kanalının adresinden sunucu ve kimlik; Xtream değilse null. */
    fun credentialsOf(channel: IPTVItem): Credentials? {
        if (channel.type != "LIVE") return null
        val m = liveUrl.find(channel.streamUrl.trim()) ?: return null
        val id = m.groupValues[4].toIntOrNull() ?: return null
        return Credentials(m.groupValues[1], m.groupValues[2], m.groupValues[3], id)
    }

    /**
     * Arşivde olan ve bitmiş programlar, en yenisi önce. [serverZone] yalnızca yanıtta sunucu saati ("start") yoksa
     * kullanılır.
     */
    fun parse(json: String, now: Long, serverZone: TimeZone = TimeZone.getDefault()): List<CatchUpProgram> {
        val listings = try {
            JSONObject(json).optJSONArray("epg_listings")
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val result = ArrayList<CatchUpProgram>()
        for (i in 0 until listings.length()) {
            val o = listings.optJSONObject(i) ?: continue
            if (o.optString("has_archive", "0").trim() != "1") continue
            val start = o.optString("start_timestamp", "").toLongOrNull()?.times(1000) ?: continue
            val end = o.optString("stop_timestamp", "").toLongOrNull()?.times(1000) ?: continue
            if (end <= start || end > now) continue
            val title = decodeTitle(o.optString("title", ""))
            if (title.isBlank()) continue
            val serverStart = serverStartOf(o.optString("start", ""))
                ?: SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.ROOT).apply { timeZone = serverZone }.format(Date(start))
            result += CatchUpProgram(title, start, end, serverStart)
        }
        return result.distinctBy { it.startMillis }.sortedByDescending { it.startMillis }.take(MAX_PROGRAMS)
    }

    /** "2026-10-29 20:00:00" → "2026-10-29:20-00" (sunucunun kendi saati; saat dilimi hesabı gerekmez). */
    internal fun serverStartOf(raw: String): String? {
        val m = Regex("""^(\d{4}-\d{2}-\d{2})[ T](\d{2}):(\d{2})""").find(raw.trim()) ?: return null
        return "${m.groupValues[1]}:${m.groupValues[2]}-${m.groupValues[3]}"
    }

    /** Başlıklar çoğu sunucuda base64; çözülemezse olduğu gibi. */
    internal fun decodeTitle(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        if (Regex("""^[A-Za-z0-9+/=\s]+$""").matches(s) && s.length % 4 == 0) {
            val decoded = try {
                String(java.util.Base64.getMimeDecoder().decode(s), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
            if (decoded != null && decoded.isNotBlank() && decoded.none { it == '�' || (it.isISOControl() && it != '\n') }) {
                return decoded.trim()
            }
        }
        return s
    }

    fun timeshiftUrl(c: Credentials, program: CatchUpProgram): String {
        val minutes = ((program.endMillis - program.startMillis + 59_999) / 60_000).coerceAtLeast(1)
        return "${c.host}/timeshift/${c.user}/${c.pass}/$minutes/${program.serverStart}/${c.streamId}.ts"
    }

    /** Oynatıcıya verilecek öğe. Kimlik eksi sayı: gerçek kanallarla ve izleme geçmişiyle karışmaz. */
    fun itemFor(channel: IPTVItem, c: Credentials, program: CatchUpProgram): IPTVItem {
        val id = -(((channel.id.toLong() * 31 + program.startMillis / 60_000) and 0x3FFF_FFFF).toInt()) - 1
        val channelName = channel.cleanedName.ifBlank { channel.name }
        return IPTVItem(
            id = id,
            playlistId = channel.playlistId,
            name = program.title,
            cleanedName = "$channelName · ${program.title}",
            logoUrl = channel.logoUrl,
            streamUrl = timeshiftUrl(c, program),
            category = channel.category,
            type = TYPE
        )
    }
}
