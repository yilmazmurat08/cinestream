package com.example.data.epg

import android.util.Log
import android.util.Xml
import com.example.data.model.EPGProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

object RealEpgProvider {
    private const val TAG = "RealEpgProvider"
    private const val MAX_XMLTV_BYTES = 60L * 1024L * 1024L
    // Büyük sağlayıcıların XMLTV'si geçmiş günler dahil 7-14 günlük program içerir. Hepsi açıklamalarıyla belleğe
    // alınınca düşük bellekli TV kutularında uygulama kapanabiliyordu: yalnızca rehberde gereken aralık tutulur.
    // Ekranlar en fazla 12 saat ileriyi gösterir (kanal kartı, TV paneli); rehber 6 saatte bir yenilenir. Uygulama bir
    // gün açık kalsa da akış bitmesin diye 26 saat tutulur (eskiden 72 saat: büyük listelerde yüz binlerce program).
    private const val KEEP_PAST_MS = 1L * 60L * 60L * 1000L
    private const val KEEP_AHEAD_MS = 26L * 60L * 60L * 1000L

    private var cachedPrograms: Map<String, List<EPGProgram>> = emptyMap()
    private var cachedForUrl: String? = null
    private var lastFetchTimestamp: Long = 0L
    private val CACHE_TTL_MS = 4L * 60L * 60L * 1000L

    suspend fun fetchProgramsFromUrl(epgUrl: String): Map<String, List<EPGProgram>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val client = com.example.data.api.NetworkModule.iptvOkHttpClient
        val request = Request.Builder().url(epgUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("EPG HTTP error ${response.code}")
            }
            val body = response.body ?: throw java.io.IOException("EPG empty response")
            val rawStream = com.example.data.repository.BoundedInputStream(body.byteStream(), MAX_XMLTV_BYTES)
            val isGzip = epgUrl.endsWith(".gz", ignoreCase = true) ||
                    response.header("Content-Type")?.contains("gzip", ignoreCase = true) == true
            val inputStream = if (isGzip) GZIPInputStream(rawStream) else rawStream
            val parsed = parseXmltv(inputStream)
            if (parsed.isEmpty()) {
                throw java.io.IOException("EPG returned 0 programs")
            }
            cachedPrograms = parsed
            cachedForUrl = epgUrl
            lastFetchTimestamp = now
            Log.d(TAG, "Gerçek EPG ayrıştırıldı: ${parsed.size} kanal için program bulundu.")
            parsed
        }
    }

    suspend fun getProgramsByChannelId(epgUrl: String): Map<String, List<EPGProgram>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedForUrl == epgUrl && (now - lastFetchTimestamp) < CACHE_TTL_MS && cachedPrograms.isNotEmpty()) {
            return@withContext cachedPrograms
        }
        try {
            fetchProgramsFromUrl(epgUrl)
        } catch (e: Exception) {
            Log.w(TAG, "EPG indirme/ayrıştırma hatası: ${e.message}")
            cachedPrograms
        }
    }

    private fun parseXmltv(inputStream: java.io.InputStream): Map<String, List<EPGProgram>> {
        val result = mutableMapOf<String, MutableList<EPGProgram>>()
        val now = System.currentTimeMillis()
        val keepFrom = now - KEEP_PAST_MS
        val keepUntil = now + KEEP_AHEAD_MS
        // Biçimlendiriciler her program için yeniden oluşturulmaz (yüz binlerce programda gereksiz yük).
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val dateFmtNoSpace = SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US)
        var progCounter = 0
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            var currentChannelId: String? = null
            var currentStart: Long = 0L
            var currentStop: Long = 0L
            var currentTitle: String? = null
            var currentDesc: String? = null
            var insideProgramme = false
            var textBuffer = StringBuilder()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                insideProgramme = true
                                currentChannelId = parser.getAttributeValue(null, "channel")
                                currentStart = parseXmltvDate(parser.getAttributeValue(null, "start"), dateFmt, dateFmtNoSpace)
                                currentStop = parseXmltvDate(parser.getAttributeValue(null, "stop"), dateFmt, dateFmtNoSpace)
                                currentTitle = null
                                currentDesc = null
                            }
                            "title" -> if (insideProgramme) textBuffer = StringBuilder()
                            "desc" -> if (insideProgramme) textBuffer = StringBuilder()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideProgramme) textBuffer.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "title" -> if (insideProgramme && currentTitle == null) currentTitle = textBuffer.toString().trim()
                            "desc" -> if (insideProgramme && currentDesc == null) currentDesc = textBuffer.toString().trim()
                            "programme" -> {
                                val chId = currentChannelId
                                if (!chId.isNullOrBlank() && !currentTitle.isNullOrBlank() && currentStart > 0 && currentStop > currentStart &&
                                    currentStop > keepFrom && currentStart < keepUntil
                                ) {
                                    progCounter++
                                    val isLive = now in currentStart until currentStop
                                    val progress = when {
                                        isLive -> ((now - currentStart).toFloat() / (currentStop - currentStart).toFloat()).coerceIn(0f, 1f)
                                        now >= currentStop -> 1f
                                        else -> 0f
                                    }
                                    val program = EPGProgram(
                                        id = "real_${chId}_$progCounter",
                                        channelId = 0,
                                        channelName = chId,
                                        title = currentTitle ?: "",
                                        // Açıklama hiçbir ekranda gösterilmiyor: bellekte tutulmaz (programların en büyük alanıydı).
                                        description = "",
                                        category = "",
                                        startTimeFormatted = timeFmt.format(java.util.Date(currentStart)),
                                        endTimeFormatted = timeFmt.format(java.util.Date(currentStop)),
                                        startEpochMillis = currentStart,
                                        endEpochMillis = currentStop,
                                        rating = 0.0,
                                        isLiveNow = isLive,
                                        progressPercent = progress
                                    )
                                    result.getOrPut(chId.lowercase(Locale.ROOT).trim()) { mutableListOf() }.add(program)
                                }
                                insideProgramme = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "XMLTV ayrıştırma sırasında hata (kısmi veri korunuyor): ${e.message}")
        } catch (e: OutOfMemoryError) {
            // Rehber bu cihazın belleğine sığmadı: uygulama kapanmasın, rehber boş kalsın.
            result.clear()
            Log.w(TAG, "XMLTV belleğe sığmadı, rehber yüklenmedi")
        }
        return result
    }

    private fun parseXmltvDate(raw: String?, fmt: SimpleDateFormat, fmtNoSpace: SimpleDateFormat): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.trim()
        // parse() başarısızlıkta null değil hata döndürür: ikinci biçim (saat dilimi boşluksuz) ayrıca denenir.
        return try {
            fmt.parse(cleaned)?.time ?: 0L
        } catch (e: Exception) {
            try {
                fmtNoSpace.parse(cleaned)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    fun clearCache() {
        cachedPrograms = emptyMap()
        cachedForUrl = null
        lastFetchTimestamp = 0L
    }
}
