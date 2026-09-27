package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.IPTVItem
import com.example.data.model.isPlaceholderCast
import com.example.data.model.isPlaceholderSummary
import com.example.data.model.isPlaceholderDirector
import com.example.data.repository.dataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Data class representing the enriched metadata of a Movie or Series.
 */
data class EnrichedMetadata(
    val summary: String,
    val cast: String,
    val director: String,
    val rating: Double,
    val logoUrl: String? = null,
    val trailerUrl: String? = null,
    val releaseDate: String = "",
    val genre: String = ""
)

/**
 * Data class representing biography and filmography details of an Actor or Director.
 */
data class PersonDetails(
    val name: String,
    val role: String,
    val biography: String,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val knownFor: List<String> = emptyList(),
    val imageUrl: String? = null
)

/**
 * MetadataEnricher matches IPTV movie and series titles with online databases (TMDB / Gemini)
 * to automatically enrich their summaries, cast, director, ratings, and artwork.
 */
object MetadataEnricher {
    private const val TAG = "MetadataEnricher"

    // --- YouTube API anahtarı (test amaçlı, kod içine gizlenmiş) ---
    private const val YT_KEY_MASK = "cinestream_yt_2026"
    private const val YT_KEY_ENCODED = "IiAUBCANMRNYLBJUHy0KRgJHKiAkCiw+PUhVK28RPC9BVkRgOiVe"

    private fun resolveEmbeddedYoutubeKey(): String {
        return try {
            val decodedBytes = android.util.Base64.decode(YT_KEY_ENCODED, android.util.Base64.DEFAULT)
            val maskBytes = YT_KEY_MASK.toByteArray(Charsets.UTF_8)
            val resultBytes = ByteArray(decodedBytes.size) { i ->
                (decodedBytes[i].toInt() xor maskBytes[i % maskBytes.size].toInt()).toByte()
            }
            String(resultBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    // ---------------------------------------------------------
    // METHOD 1: GEMINI AI METADATA ENRICHMENT (Fully Functional & Zero-Config)
    // ---------------------------------------------------------
    
    private val knownTitleRatingsAndTrailers = mapOf(
        "interstellar" to Pair(8.7, "https://www.youtube.com/watch?v=zSWdZVtXT7E"),
        "inception" to Pair(8.8, "https://www.youtube.com/watch?v=YoHD9XEInc0"),
        "the dark knight" to Pair(9.0, "https://www.youtube.com/watch?v=EXeTwQWrcwY"),
        "spirited away" to Pair(8.6, "https://www.youtube.com/watch?v=ByXuk9QqQkk"),
        "stranger things" to Pair(8.7, "https://www.youtube.com/watch?v=b9EkMc79ZSU"),
        "breaking bad" to Pair(9.5, "https://www.youtube.com/watch?v=HhesaQXLuRY")
    )

    fun extractJson(text: String): String {
        if (text.isBlank()) return "{}"
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        clean = clean.trim()

        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return clean.substring(firstBrace, lastBrace + 1)
        }
        val firstBracket = clean.indexOf('[')
        val lastBracket = clean.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return clean.substring(firstBracket, lastBracket + 1)
        }
        return clean
    }

    private val clientByTimeout by lazy { NetworkModule.okHttpClient }

    private val apiService: TMDBApiService by lazy { NetworkModule.tmdbApiService }

    private val requestSemaphore = Semaphore(3)

    private val dynamicMetadataCache: MutableMap<String, EnrichedMetadata> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, EnrichedMetadata>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EnrichedMetadata>?): Boolean {
                return size > 250
            }
        }
    )

    // ---------------------------------------------------------
    // METHOD 0: XTREAM CODES VOD & SERIES INFO SERVICE
    // ---------------------------------------------------------
    data class XtreamInfoCredentials(
        val baseUrl: String,
        val user: String,
        val pass: String,
        val streamId: String,
        val isSeries: Boolean
    )

    fun parseXtreamCredentials(item: IPTVItem): XtreamInfoCredentials? {
        val streamUrl = item.streamUrl.trim()
        if (streamUrl.isEmpty()) return null
        
        val isSeries = item.type == "SERIES" || streamUrl.lowercase(java.util.Locale.ROOT).contains("/series/")
        
        val regex = Regex("""(https?://[^/]+)/(movie|series|live)/([^/]+)/([^/]+)/(\d+)(\.[a-zA-Z0-9]+)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(streamUrl)
        if (match != null) {
            val host = match.groupValues[1]
            val user = match.groupValues[3]
            val pass = match.groupValues[4]
            val id = match.groupValues[5]
            return XtreamInfoCredentials(host, user, pass, id, isSeries)
        }

        val shortRegex = Regex("""(https?://[^/]+)/([^/]+)/([^/]+)/(\d+)(\.[a-zA-Z0-9]+)?$""", RegexOption.IGNORE_CASE)
        val shortMatch = shortRegex.find(streamUrl)
        if (shortMatch != null) {
            val host = shortMatch.groupValues[1]
            val user = shortMatch.groupValues[2]
            val pass = shortMatch.groupValues[3]
            val id = shortMatch.groupValues[4]
            return XtreamInfoCredentials(host, user, pass, id, isSeries)
        }
        return null
    }

    /**
     * Fetches VOD info or Series info directly from Xtream Codes player_api.php
     */
    suspend fun fetchXtreamMetadata(context: android.content.Context, item: IPTVItem): EnrichedMetadata? = withContext(Dispatchers.IO) {
        val creds = parseXtreamCredentials(item)
        if (creds == null) {
            Log.d(TAG, "Item ${item.name} does not match direct Xtream URL pattern. Skipping Xtream Info fetch.")
            if (com.example.util.DiagnosticLog.enabled) try {
                com.example.util.DiagnosticLog.write(context, "xtream_cover_diag", 
                    "KİMLİK BİLGİSİ ÇIKARILAMADI\nÖğe: ${item.name}\nlogoUrl: ${item.logoUrl}\nstreamUrl: ${item.streamUrl}\n" +
                    "-> Bu URL beklenen Xtream kalıbına (http(s)://host/movie|series|live/kullanici/sifre/id) uymuyor."
                )
            } catch (e: Exception) { }
            return@withContext null
        }

        val action = if (creds.isSeries) "get_series_info" else "get_vod_info"
        val idParam = if (creds.isSeries) "series_id" else "vod_id"
        val apiUrl = "${creds.baseUrl}/player_api.php?username=${creds.user}&password=${creds.pass}&action=$action&$idParam=${creds.streamId}"

        Log.d(TAG, "Calling Xtream API GET request: $apiUrl")

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .build()

            val response = clientByTimeout.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Xtream API call failed code=${response.code} for ${item.name}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            if (responseBody.isBlank() || responseBody == "[]" || !responseBody.contains("{")) {
                Log.w(TAG, "Xtream API returned invalid/empty body for ${item.name}")
                return@withContext null
            }

            val root = JSONObject(responseBody)
            val info = root.optJSONObject("info") ?: root

            val rawPlot = info.optString("plot", "").ifEmpty { info.optString("description", "") }.trim()

            // Fallback rule: If plot is empty or blank, return null to trigger Gemini AI motor!
            if (rawPlot.isEmpty() || rawPlot == "null") {
                Log.w(TAG, "Xtream API returned empty plot for ${item.name}. Triggering Gemini AI fallback motor!")
                return@withContext null
            }

            val ratingStr = info.optString("rating", "").ifEmpty { info.optString("rating_5based", "") }
            var ratingVal = ratingStr.toDoubleOrNull() ?: info.optDouble("rating", 0.0)
            if (ratingVal > 0.0 && ratingVal <= 5.0 && info.has("rating_5based")) {
                ratingVal *= 2.0
            }

            val releaseDate = info.optString("releasedate", "").ifEmpty {
                info.optString("release_date", "").ifEmpty {
                    info.optString("year", "")
                }
            }

            val genre = info.optString("genre", "")
            val cast = info.optString("cast", "").ifEmpty { info.optString("actors", "") }
            val director = info.optString("director", "")
            val backdropFromArray = info.optJSONArray("backdrop_path")?.let { arr ->
                if (arr.length() > 0) arr.optString(0, "") else ""
            } ?: ""
            val cover = backdropFromArray.ifEmpty {
                info.optString("cover_big", "").ifEmpty {
                    info.optString("movie_image", "").ifEmpty {
                        info.optString("cover", "")
                    }
                }
            }

            var trailer = info.optString("youtube_trailer", "")
            if (trailer.isNotEmpty() && !trailer.startsWith("http")) {
                trailer = "https://www.youtube.com/watch?v=$trailer"
            }

            Log.d(TAG, "Successfully parsed Xtream VOD Info for ${item.name}: plot=${rawPlot.take(30)}..., rating=$ratingVal")

            if (com.example.util.DiagnosticLog.enabled) try {
                com.example.util.DiagnosticLog.write(context, "xtream_cover_diag", 
                    "BAŞARILI ÇAĞRI\nÖğe: ${item.name}\nAPI URL: $apiUrl\n" +
                    "rating (ham): '${info.optString("rating", "YOK")}'\n" +
                    "rating_5based (ham): '${info.optString("rating_5based", "YOK")}'\n" +
                    "hesaplanan ratingVal: $ratingVal\n" +
                    "cover_big: '${info.optString("cover_big", "YOK")}'\n" +
                    "movie_image: '${info.optString("movie_image", "YOK")}'\n" +
                    "cover: '${info.optString("cover", "YOK")}'\n" +
                    "backdrop_path (ham): '${info.optJSONArray("backdrop_path")?.toString() ?: "YOK"}'\n" +
                    "SEÇİLEN kapak: '$cover'\n" +
                    "item.logoUrl (M3U'dan, karşılaştırma için): '${item.logoUrl}'"
                )
            } catch (e: Exception) { }

            return@withContext EnrichedMetadata(
                summary = rawPlot,
                cast = cast,
                director = director,
                rating = if (ratingVal > 0.0) ratingVal else item.rating,
                logoUrl = cover.ifEmpty { item.logoUrl },
                trailerUrl = trailer.ifEmpty { item.trailerUrl },
                releaseDate = releaseDate,
                genre = genre
            )

        } catch (e: Exception) {
            Log.e(TAG, "Xtream API call exception for ${item.name}", e)
            return@withContext null
        }
    }

    /**
     * ÖNEMLİ (yeni mimari): Xtream'in get_series (toplu liste) uç noktası zaten
     * cover/backdrop_path/plot/rating içeriyor — her diziyi ayrı ayrı sorgulamaya
     * gerek yok. Profesyonel oynatıcılar (IPTV Smarters, TiviMate) kütüphaneyi
     * böyle eşitliyor. TEK ağ isteğiyle TÜM dizilerin kapağını döndürür.
     */
    suspend fun fetchAllSeriesCoversFromXtreamBulk(context: android.content.Context, anySeriesItem: IPTVItem): Map<String, String> = withContext(Dispatchers.IO) {
        fun writeDiag(msg: String) = com.example.util.DiagnosticLog.write(context, "bulk_series_cover_diag", msg)

        val creds = parseXtreamCredentials(anySeriesItem)
        if (creds == null) {
            writeDiag("TOPLU ÇEKİM: Kimlik bilgisi çıkarılamadı.\nÖrnek öğe: ${anySeriesItem.name}\nstreamUrl: ${anySeriesItem.streamUrl}")
            return@withContext emptyMap()
        }
        try {
            val apiUrl = "${creds.baseUrl}/player_api.php?username=${creds.user}&password=${creds.pass}&action=get_series"
            Log.d(TAG, "Xtream toplu dizi listesi çağrılıyor: $apiUrl")

            val request = Request.Builder().url(apiUrl).get().build()
            val response = clientByTimeout.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Xtream get_series çağrısı başarısız: code=${response.code}")
                writeDiag("TOPLU ÇEKİM BAŞARISIZ\nAPI URL: $apiUrl\nHTTP kodu: ${response.code}")
                return@withContext emptyMap()
            }

            val responseBody = response.body?.string() ?: return@withContext emptyMap()
            if (responseBody.isBlank() || !responseBody.trim().startsWith("[")) {
                Log.w(TAG, "Xtream get_series geçersiz/boş yanıt döndürdü")
                writeDiag("TOPLU ÇEKİM GEÇERSİZ YANIT\nAPI URL: $apiUrl\nYanıtın ilk 300 karakteri: ${responseBody.take(300)}")
                return@withContext emptyMap()
            }

            val jsonArray = JSONArray(responseBody)
            val result = mutableMapOf<String, String>()
            val rawNameSamples = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").ifEmpty { obj.optString("title", "") }.trim()
                if (name.isEmpty()) continue
                if (rawNameSamples.size < 15) rawNameSamples.add(name)

                val coverBig = obj.optString("cover_big", "").trim()
                val coverStd = obj.optString("cover", "").trim()
                val coverUrl = obj.optString("cover_url", "").trim()
                val cover = when {
                    coverBig.isNotEmpty() -> coverBig
                    coverStd.isNotEmpty() -> coverStd
                    coverUrl.isNotEmpty() -> coverUrl
                    else -> ""
                }

                if (cover.isNotBlank()) {
                    result[name.lowercase(java.util.Locale.ROOT)] = cover
                }
            }
            Log.d(TAG, "Xtream toplu dizi listesi: ${jsonArray.length()} dizi bulundu, ${result.size} tanesinde kapak var")
            writeDiag(
                "TOPLU ÇEKİM BAŞARILI\nAPI URL: $apiUrl\n" +
                "Xtream'in döndürdüğü toplam dizi sayısı: ${jsonArray.length()}\n" +
                "İçlerinden kapağı olan: ${result.size}\n" +
                "Xtream'in kendi dizi adlarından örnekler (bizim hesapladığımız başlıklarla KARŞILAŞTIRMAK için):\n" +
                rawNameSamples.joinToString("\n") { "  - $it" }
            )
            result
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllSeriesCoversFromXtreamBulk hata: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Xtream get_series uç noktasından tüm dizi kataloğunu çeker.
     * Kapak, arka plan, özet, puan, tür vb. zengin alanları XtreamSeriesCatalogEntity
     * listesi olarak döndürür. Bu liste Room veritabanında saklanır.
     */
    private suspend fun fetchCategoryNameMap(host: String, user: String, pass: String, action: String): Map<String, String> {
        return try {
            val apiUrl = "$host/player_api.php?username=$user&password=$pass&action=$action"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            val response = clientByTimeout.newCall(request).execute()
            if (!response.isSuccessful) return emptyMap()
            val body = response.body?.string() ?: return emptyMap()
            if (body.isBlank() || !body.trim().startsWith("[")) return emptyMap()
            val arr = JSONArray(body)
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("category_id", "")
                val catName = obj.optString("category_name", "").trim()
                if (id.isNotEmpty() && catName.isNotEmpty()) map[id] = catName
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchXtreamLiveStreamsAsItems(context: android.content.Context, host: String, user: String, pass: String, playlistId: Int): List<IPTVItem> = withContext(Dispatchers.IO) {
        fun writeDiag(msg: String) = com.example.util.DiagnosticLog.write(context, "xtream_live_streams_diag", msg)
        try {
            val categoryNames = fetchCategoryNameMap(host, user, pass, "get_live_categories")
            val apiUrl = "$host/player_api.php?username=$user&password=$pass&action=get_live_streams"
            val startTime = System.currentTimeMillis()
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            val response = clientByTimeout.newCall(request).execute()
            val elapsedMs = System.currentTimeMillis() - startTime
            if (!response.isSuccessful) {
                writeDiag("CANLI LİSTESİ BAŞARISIZ\nHTTP kodu: ${response.code}\nSüre: ${elapsedMs}ms")
                return@withContext emptyList()
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            if (responseBody.isBlank() || !responseBody.trim().startsWith("[")) {
                writeDiag("CANLI LİSTESİ GEÇERSİZ YANIT\nSüre: ${elapsedMs}ms\nİlk 300 karakter: ${responseBody.take(300)}")
                return@withContext emptyList()
            }
            val jsonArray = JSONArray(responseBody)
            val result = mutableListOf<IPTVItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val streamId = obj.optInt("stream_id", -1)
                if (streamId <= 0) continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                val icon = obj.optString("stream_icon", "").trim()
                val categoryId = obj.optString("category_id", "")
                val epgChannelId = obj.optString("epg_channel_id", "").trim()
                val playUrl = "$host/live/$user/$pass/$streamId.ts"

                val categoryName = categoryNames[categoryId] ?: "Diğer"
                val lowerCat = categoryName.lowercase()
                val lowerName = name.lowercase()
                val isRadioChannel = lowerCat.contains("radyo") || lowerCat.contains("radio") || lowerCat.contains("radyolar") ||
                        lowerName.contains("radyo") || lowerName.contains("radio") || lowerName.contains(" fm") || lowerName.endsWith("fm")

                result.add(
                    IPTVItem(
                        id = streamId,
                        playlistId = playlistId,
                        name = name,
                        cleanedName = name,
                        logoUrl = icon.ifBlank { null },
                        streamUrl = playUrl,
                        category = categoryName,
                        type = if (isRadioChannel) "RADIO" else "LIVE",
                        tvgId = epgChannelId.ifBlank { null }
                    )
                )
            }
            writeDiag(
                "CANLI LİSTESİ BAŞARILI\nAPI URL: $apiUrl\n" +
                "Ağ isteği süresi: ${elapsedMs}ms\n" +
                "Toplam kanal sayısı: ${jsonArray.length()}\n" +
                "Geçerli üretilen: ${result.size}\n" +
                "Logosu olan: ${result.count { !it.logoUrl.isNullOrBlank() }}"
            )
            result
        } catch (e: Exception) {
            writeDiag("CANLI LİSTESİ HATA: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchXtreamVodStreamsAsItems(context: android.content.Context, anyMovieItem: IPTVItem): List<IPTVItem> = withContext(Dispatchers.IO) {
        fun writeDiag(msg: String) = com.example.util.DiagnosticLog.write(context, "xtream_vod_streams_diag", msg)
        val creds = parseXtreamCredentials(anyMovieItem)
        if (creds == null) {
            writeDiag("VOD LİSTESİ: Kimlik bilgisi çıkarılamadı.\nstreamUrl: ${anyMovieItem.streamUrl}")
            return@withContext emptyList()
        }
        try {
            val categoryNames = fetchCategoryNameMap(creds.baseUrl, creds.user, creds.pass, "get_vod_categories")
            val apiUrl = "${creds.baseUrl}/player_api.php?username=${creds.user}&password=${creds.pass}&action=get_vod_streams"
            val startTime = System.currentTimeMillis()
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            val response = clientByTimeout.newCall(request).execute()
            val elapsedMs = System.currentTimeMillis() - startTime
            if (!response.isSuccessful) {
                writeDiag("VOD LİSTESİ BAŞARISIZ\nHTTP kodu: ${response.code}\nSüre: ${elapsedMs}ms")
                return@withContext emptyList()
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            if (responseBody.isBlank() || !responseBody.trim().startsWith("[")) {
                writeDiag("VOD LİSTESİ GEÇERSİZ YANIT\nSüre: ${elapsedMs}ms\nİlk 300 karakter: ${responseBody.take(300)}")
                return@withContext emptyList()
            }
            val jsonArray = JSONArray(responseBody)
            val result = mutableListOf<IPTVItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val streamId = obj.optInt("stream_id", -1)
                if (streamId <= 0) continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                val icon = obj.optString("stream_icon", "").trim()
                val containerExt = obj.optString("container_extension", "mp4").ifEmpty { "mp4" }
                val playUrl = "${creds.baseUrl}/movie/${creds.user}/${creds.pass}/$streamId.$containerExt"
                val rating = obj.optDouble("rating", 0.0).let { if (it.isNaN()) 0.0 else it }
                val categoryId = obj.optString("category_id", "")

                result.add(
                    IPTVItem(
                        id = streamId,
                        playlistId = anyMovieItem.playlistId,
                        name = name,
                        cleanedName = name,
                        logoUrl = icon.ifBlank { null },
                        streamUrl = playUrl,
                        category = categoryNames[categoryId] ?: "Diğer",
                        type = "MOVIE",
                        rating = rating
                    )
                )
            }
            writeDiag(
                "VOD LİSTESİ BAŞARILI (tam çekim)\nAPI URL: $apiUrl\n" +
                "Ağ isteği süresi: ${elapsedMs}ms\n" +
                "Toplam film sayısı: ${jsonArray.length()}\n" +
                "Geçerli (id+isim dolu) IPTVItem üretilen: ${result.size}\n" +
                "Posteri olan: ${result.count { !it.logoUrl.isNullOrBlank() }}"
            )
            result
        } catch (e: Exception) {
            writeDiag("VOD LİSTESİ HATA: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchXtreamVodStreamsDiagnostic(context: android.content.Context, anyMovieItem: IPTVItem): Int = withContext(Dispatchers.IO) {
        fun writeDiag(msg: String) = com.example.util.DiagnosticLog.write(context, "xtream_vod_streams_diag", msg)
        val creds = parseXtreamCredentials(anyMovieItem)
        if (creds == null) {
            writeDiag("VOD LİSTESİ: Kimlik bilgisi çıkarılamadı.\nstreamUrl: ${anyMovieItem.streamUrl}")
            return@withContext 0
        }
        try {
            val apiUrl = "${creds.baseUrl}/player_api.php?username=${creds.user}&password=${creds.pass}&action=get_vod_streams"
            val startTime = System.currentTimeMillis()
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            val response = clientByTimeout.newCall(request).execute()
            val elapsedMs = System.currentTimeMillis() - startTime
            if (!response.isSuccessful) {
                writeDiag("VOD LİSTESİ BAŞARISIZ\nHTTP kodu: ${response.code}\nSüre: ${elapsedMs}ms")
                return@withContext 0
            }
            val responseBody = response.body?.string() ?: return@withContext 0
            if (responseBody.isBlank() || !responseBody.trim().startsWith("[")) {
                writeDiag("VOD LİSTESİ GEÇERSİZ YANIT\nSüre: ${elapsedMs}ms\nİlk 300 karakter: ${responseBody.take(300)}")
                return@withContext 0
            }
            val jsonArray = JSONArray(responseBody)
            val withPoster = (0 until jsonArray.length()).count { i ->
                !jsonArray.optJSONObject(i)?.optString("stream_icon", "").isNullOrBlank()
            }
            val sampleNames = (0 until minOf(10, jsonArray.length())).map {
                jsonArray.optJSONObject(it)?.optString("name", "") ?: ""
            }
            writeDiag(
                "VOD LİSTESİ BAŞARILI\nAPI URL: $apiUrl\n" +
                "Yanıt boyutu: ${responseBody.length} karakter\n" +
                "Ağ isteği süresi: ${elapsedMs}ms\n" +
                "Toplam film sayısı: ${jsonArray.length()}\n" +
                "Posteri (stream_icon) olan: $withPoster\n" +
                "İlk 10 örnek isim:\n" + sampleNames.joinToString("\n") { "  - $it" }
            )
            jsonArray.length()
        } catch (e: Exception) {
            writeDiag("VOD LİSTESİ HATA: ${e.message}")
            0
        }
    }

    suspend fun fetchSeriesInfoLive(
        context: android.content.Context,
        seriesId: Int,
        showTitle: String,
        showCover: String?,
        showRating: Double,
        showSummary: String,
        showCast: String,
        showDirector: String,
        anyItemForCredentials: IPTVItem
    ): com.example.data.model.TvShow? = withContext(Dispatchers.IO) {
        fun writeDiag(msg: String) = com.example.util.DiagnosticLog.write(context, "series_info_live_diag", msg)
        val creds = parseXtreamCredentials(anyItemForCredentials) ?: run {
            writeDiag("CANLI BÖLÜM ÇEKME: Kimlik bilgisi çıkarılamadı.\nDizi: $showTitle")
            return@withContext null
        }
        try {
            val apiUrl = "${creds.baseUrl}/player_api.php?username=${creds.user}&password=${creds.pass}&action=get_series_info&series_id=$seriesId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            val response = clientByTimeout.newCall(request).execute()
            if (!response.isSuccessful) {
                writeDiag("CANLI BÖLÜM ÇEKME BAŞARISIZ\nDizi: $showTitle (id=$seriesId)\nHTTP kodu: ${response.code}")
                return@withContext null
            }
            val responseBody = response.body?.string() ?: return@withContext null
            val root = org.json.JSONObject(responseBody)
            val episodesObj = root.optJSONObject("episodes") ?: run {
                writeDiag("CANLI BÖLÜM ÇEKME: 'episodes' alanı yok\nDizi: $showTitle (id=$seriesId)\nYanıtın ilk 300 karakteri: ${responseBody.take(300)}")
                return@withContext null
            }

            val seasons = mutableListOf<com.example.data.model.Season>()
            var totalEpisodes = 0
            val seasonKeys = episodesObj.keys()
            while (seasonKeys.hasNext()) {
                val seasonKey = seasonKeys.next()
                val seasonNum = seasonKey.toIntOrNull() ?: continue
                val episodeArray = episodesObj.optJSONArray(seasonKey) ?: continue
                val episodes = mutableListOf<com.example.data.model.Episode>()
                for (i in 0 until episodeArray.length()) {
                    val epObj = episodeArray.optJSONObject(i) ?: continue
                    val epId = epObj.optString("id", "").ifEmpty { epObj.optInt("id", -1).toString() }
                    val epIdInt = epId.toIntOrNull() ?: continue
                    val epTitle = epObj.optString("title", "Bölüm ${i + 1}")
                    val containerExt = epObj.optString("container_extension", "mp4").ifEmpty { "mp4" }
                    val epNum = epObj.optJSONObject("info")?.optInt("episode_num", i + 1) ?: (epObj.optInt("episode_num", i + 1))
                    val playUrl = "${creds.baseUrl}/series/${creds.user}/${creds.pass}/$epIdInt.$containerExt"
                    val epCover = epObj.optJSONObject("info")?.optString("movie_image", "") ?: ""
                    val syntheticId = -epIdInt

                    val syntheticItem = IPTVItem(
                        id = syntheticId,
                        playlistId = anyItemForCredentials.playlistId,
                        name = "$showTitle - $epTitle",
                        cleanedName = showTitle,
                        logoUrl = epCover.ifBlank { showCover },
                        streamUrl = playUrl,
                        category = anyItemForCredentials.category,
                        type = "SERIES",
                        rating = showRating,
                        summary = showSummary,
                        cast = showCast,
                        director = showDirector,
                        season = seasonNum,
                        episode = epNum
                    )
                    episodes.add(
                        com.example.data.model.Episode(
                            id = syntheticId,
                            name = epTitle,
                            seasonNumber = seasonNum,
                            episodeNumber = epNum,
                            streamUrl = playUrl,
                            logoUrl = epCover.ifBlank { showCover },
                            item = syntheticItem
                        )
                    )
                    totalEpisodes++
                }
                if (episodes.isNotEmpty()) {
                    seasons.add(com.example.data.model.Season(seasonNum, episodes.sortedBy { it.episodeNumber }))
                }
            }

            writeDiag(
                "CANLI BÖLÜM ÇEKME BAŞARILI\nDizi: $showTitle (id=$seriesId)\n" +
                "Sezon sayısı: ${seasons.size}\nToplam bölüm: $totalEpisodes"
            )

            if (seasons.isEmpty()) return@withContext null

            com.example.data.model.TvShow(
                id = seriesId,
                title = showTitle,
                logoUrl = showCover,
                category = "",
                rating = showRating,
                summary = showSummary,
                cast = showCast,
                director = showDirector,
                isFavorite = false,
                seasons = seasons.sortedBy { it.seasonNumber }
            )
        } catch (e: Exception) {
            writeDiag("CANLI BÖLÜM ÇEKME HATA\nDizi: $showTitle (id=$seriesId)\nHata: ${e.message}")
            null
        }
    }

    suspend fun fetchFullXtreamSeriesCatalog(
        context: android.content.Context,
        anySeriesItem: IPTVItem
    ): List<com.example.data.model.XtreamSeriesCatalogEntity> = withContext(Dispatchers.IO) {
        val creds = parseXtreamCredentials(anySeriesItem) ?: return@withContext emptyList()
        try {
            val categoryNames = fetchCategoryNameMap(creds.baseUrl, creds.user, creds.pass, "get_series_categories")
            val apiUrl = "${creds.baseUrl}/player_api.php?username=${creds.user}&password=${creds.pass}&action=get_series"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()
                .build()
            val response = clientByTimeout.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            if (responseBody.isBlank() || !responseBody.trim().startsWith("[")) return@withContext emptyList()

            val jsonArray = JSONArray(responseBody)
            val list = ArrayList<com.example.data.model.XtreamSeriesCatalogEntity>(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val seriesId = obj.optInt("series_id", 0).let { if (it != 0) it else obj.optInt("id", 0) }
                val rawName = obj.optString("name", "").ifEmpty { obj.optString("title", "") }.trim()
                if (rawName.isEmpty()) continue

                val coverBig = obj.optString("cover_big", "").trim()
                val coverStd = obj.optString("cover", "").trim()
                val coverUrl = obj.optString("cover_url", "").trim()
                val chosenCover = when {
                    coverBig.isNotEmpty() -> coverBig
                    coverStd.isNotEmpty() -> coverStd
                    coverUrl.isNotEmpty() -> coverUrl
                    else -> ""
                }

                val backdropArray = obj.optJSONArray("backdrop_path")
                val backdrop = if (backdropArray != null && backdropArray.length() > 0) {
                    backdropArray.optString(0, "").trim()
                } else {
                    obj.optString("backdrop", "").trim()
                }

                val plot = obj.optString("plot", "").trim()
                val rating = obj.optDouble("rating", 0.0).let { r ->
                    if (r > 0.0) r else obj.optDouble("rating_5based", 0.0) * 2.0
                }
                val releaseDate = obj.optString("releaseDate", "").ifEmpty { obj.optString("release_date", "") }.trim()
                val genre = obj.optString("genre", "").trim()
                val cast = obj.optString("cast", "").trim()
                val director = obj.optString("director", "").trim()
                val categoryIdRaw = obj.optString("category_id", "").trim()
                val categoryId = categoryNames[categoryIdRaw] ?: "Diğer"

                val canonicalKey = rawName.lowercase(java.util.Locale.ROOT).trim()

                list.add(
                    com.example.data.model.XtreamSeriesCatalogEntity(
                        seriesId = if (seriesId != 0) seriesId else (canonicalKey.hashCode() and 0x7FFFFFFF),
                        name = rawName,
                        canonicalKey = canonicalKey,
                        coverUrl = chosenCover,
                        backdropUrl = backdrop,
                        plot = plot,
                        rating = rating,
                        releaseDate = releaseDate,
                        genre = genre,
                        cast = cast,
                        director = director,
                        categoryId = categoryId
                    )
                )
            }
            if (com.example.util.DiagnosticLog.enabled) try {
                val distinctCategories = list.map { it.categoryId }.toSet()
                val digerCount = list.count { it.categoryId == "Diğer" }
                com.example.util.DiagnosticLog.write(context, "series_category_diag", 
                    "Xtream'in kategori uç noktasından (get_series_categories) dönen kategori sayısı: ${categoryNames.size}\n" +
                    "Kataloğa aktarılan toplam dizi sayısı: ${list.size}\n" +
                    "Sonuçtaki BENZERSİZ kategori adı sayısı (Diğer dahil): ${distinctCategories.size}\n" +
                    "\"Diğer\"e düşen dizi sayısı: $digerCount\n" +
                    "Benzersiz kategori adlarının TAMAMI:\n" +
                    distinctCategories.sorted().joinToString("\n") { "  - $it" }
                )
            } catch (e: Exception) { }
            Log.d(TAG, "fetchFullXtreamSeriesCatalog: ${list.size} dizi kataloğa aktarıldı")
            list
        } catch (e: Exception) {
            Log.w(TAG, "fetchFullXtreamSeriesCatalog hata: ${e.message}")
            emptyList()
        }
    }

    /**
     * Primary entry point for metadata enrichment:
     * 1. Tries Xtream Codes API get_vod_info / get_series_info (both for movies and series)
     * 2. If plot is empty or call fails -> Fallback Circuit: Gemini AI Motor
     * 3. Fallback to local offline metadata generator
     * TMDB is completely bypassed and disabled for speed and zero-crash reliability.
     */
    suspend fun enrichWithXtreamOrFallback(context: android.content.Context, item: IPTVItem): EnrichedMetadata = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            try {
                val xtreamMeta = try {
                    fetchXtreamMetadata(context, item)
                } catch (e: Exception) {
                    Log.w(TAG, "Xtream metadata fetch failed for ${item.name}: ${e.message}")
                    null
                }
                if (xtreamMeta != null && (xtreamMeta.summary.isNotBlank() || !xtreamMeta.logoUrl.isNullOrBlank())) {
                    return@withPermit xtreamMeta
                }

                Log.d(TAG, "Xtream metadata missing. Trying real TMDB database lookup for ${item.name}...")
                val tmdbMeta = try {
                    val tmdbRepo = com.example.data.repository.TMDBRepository(context.applicationContext)
                    val tmdbType = if (item.type == "SERIES") "SERIES" else "MOVIE"
                    tmdbRepo.fetchDetailsByTitle(item.cleanedName.ifEmpty { item.name }, type = tmdbType)
                } catch (e: Exception) {
                    Log.w(TAG, "TMDB lookup failed for ${item.name}: ${e.message}")
                    null
                }
                if (tmdbMeta != null && (tmdbMeta.overview.isNotBlank() || tmdbMeta.castString.isNotBlank() || !tmdbMeta.posterUrl.isNullOrBlank())) {
                    return@withPermit EnrichedMetadata(
                        summary = tmdbMeta.overview.ifBlank { item.summary },
                        cast = tmdbMeta.castString.ifBlank { item.cast },
                        director = tmdbMeta.director.ifBlank { item.director },
                        rating = if (tmdbMeta.rating > 0.0) tmdbMeta.rating else item.rating,
                        logoUrl = item.logoUrl,
                        trailerUrl = item.trailerUrl
                    )
                }

                Log.d(TAG, "TMDB lookup empty for ${item.name}. Running Gemini AI fallback motor...")
                val geminiMeta = try {
                    enrichWithGemini(context, item)
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini metadata enrichment failed for ${item.name}: ${e.message}")
                    null
                }
                if (geminiMeta != null && (geminiMeta.cast.isNotBlank() || geminiMeta.director.isNotBlank() || geminiMeta.trailerUrl != null)) {
                    return@withPermit geminiMeta.copy(summary = item.summary)
                }

                generateLocalMovieMetadata(item)
            } catch (e: Throwable) {
                Log.e(TAG, "enrichWithXtreamOrFallback safe catch for ${item.name}", e)
                try {
                    generateLocalMovieMetadata(item)
                } catch (_: Throwable) {
                    EnrichedMetadata(
                        summary = item.summary,
                        cast = item.cast,
                        director = item.director,
                        rating = item.rating,
                        logoUrl = item.logoUrl,
                        trailerUrl = item.trailerUrl,
                        releaseDate = item.releaseDate,
                        genre = item.genre
                    )
                }
            }
        }
    }

    suspend fun enrichWithGemini(context: android.content.Context, item: IPTVItem): EnrichedMetadata? = withContext(Dispatchers.IO) {
        val queryName = item.cleanedName.lowercase().trim()
        val cached = knownTitleRatingsAndTrailers.entries.find { queryName.contains(it.key) || it.key.contains(queryName) }?.value
        if (cached != null) {
            Log.d(TAG, "Found known rating/trailer for $queryName. Using offline mode.")
            return@withContext EnrichedMetadata(
                summary = "",
                cast = "",
                director = "",
                rating = cached.first,
                logoUrl = null,
                trailerUrl = cached.second
            )
        }

        val savedKey = context.dataStore.data.firstOrNull()?.get(stringPreferencesKey("gemini_api_key"))
        val apiKey = if (!savedKey.isNullOrEmpty()) savedKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "placeholder") {
            Log.w(TAG, "Gemini API Key is missing or default. Using local generator fallback.")
            return@withContext generateLocalMovieMetadata(item)
        }

        val typeLabel = if (item.type == "SERIES") "Dizi (TV Series)" else "Film (Movie)"
        val prompt = """
            Sana bir IPTV yayınının adını vereceğim. Bu yapımın gerçek bir film veya dizi olduğunu biliyoruz.
            Yapım Adı: "${item.cleanedName}"
            Türü: $typeLabel
            
            Lütfen bu yapım hakkında araştırma yap ve aşağıdaki bilgileri Türkçe olarak JSON formatında döndür:
            1. "cast": Başrol oyuncuları (en fazla 4 isim, virgülle ayrılmış, örn: "Leonardo DiCaprio, Joseph Gordon-Levitt").
            2. "director": Yönetmen adı (örn: "Christopher Nolan" veya diziyse yaratıcısı / yapımcısı).
            3. "rating": IMDb veya genel izleyici puanı (0.0 - 10.0 arasında ondalıklı sayı, örn: 8.8).
            4. "trailerUrl": Bu film veya dizinin resmi Türkçe veya İngilizce YouTube fragmanının/fragman tanıtımının tam YouTube URL'si (örn. 'https://www.youtube.com/watch?v=...' veya 'https://youtu.be/...'). Kesinlikle boş bırakma, en uygun YouTube fragman linkini bulup ekle.
            
            Sadece geçerli bir JSON objesi döndür. Markdown kod blokları veya 'json' kelimesi dahil hiçbir ek açıklama ekleme.
            Format:
            {
              "cast": "...",
              "director": "...",
              "rating": 8.2,
              "trailerUrl": "https://www.youtube.com/watch?v=..."
            }
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val models = listOf("gemini-2.5-flash", "gemini-1.5-flash")
            var responseBody: String? = null
            var lastError: String? = null

            for (model in models) {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    .post(requestBody)
                    .build()

                try {
                    val response = clientByTimeout.newCall(request).execute()
                    if (response.isSuccessful) {
                        responseBody = response.body?.string()
                        Log.d(TAG, "Model $model response successful")
                        break
                    } else {
                        val err = response.body?.string()
                        lastError = "Model $model failed with code ${response.code}: $err"
                        Log.w(TAG, lastError)
                    }
                } catch (e: Exception) {
                    lastError = "Model $model failed with exception: ${e.message}"
                    Log.w(TAG, lastError, e)
                }
            }

            if (responseBody == null) {
                Log.w(TAG, "All Gemini attempts failed. Last error: $lastError. Using local metadata generator fallback.")
                return@withContext generateLocalMovieMetadata(item)
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: return@withContext generateLocalMovieMetadata(item)

            val cleanedJsonText = extractJson(rawText)
            val jsonOutput = JSONObject(cleanedJsonText)
            
            return@withContext EnrichedMetadata(
                summary = "",
                cast = jsonOutput.optString("cast", item.cast).ifEmpty { generateLocalMovieMetadata(item).cast },
                director = jsonOutput.optString("director", item.director).ifEmpty { generateLocalMovieMetadata(item).director },
                rating = jsonOutput.optDouble("rating", item.rating).let { if (it <= 0.0) 8.2 else it },
                logoUrl = null,
                trailerUrl = jsonOutput.optString("trailerUrl", "").ifEmpty { null }
            )

        } catch (e: Exception) {
            System.err.println("--- enrichWithGemini EXCEPTION: ${e.message} ---")
            e.printStackTrace()
            Log.w(TAG, "Failed to enrich metadata using Gemini API. Falling back to local generator.", e)
            generateLocalMovieMetadata(item)
        }
    }

    fun generateLocalMovieMetadata(item: IPTVItem): EnrichedMetadata {
        val queryName = item.cleanedName.lowercase(java.util.Locale.ROOT).trim()
        
        when {
            queryName.contains("interstellar") || queryName.contains("yıldızlararası") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.7,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=zSWdZVtXT7E"
                )
            }
            queryName.contains("inception") || queryName.contains("başlangıç") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.8,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=YoHD9XEInc0"
                )
            }
            queryName.contains("the dark knight") || queryName.contains("kara şövalye") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 9.0,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=EXeTwQWrcwY"
                )
            }
            queryName.contains("spirited away") || queryName.contains("ruhların kaçışı") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.6,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=ByXuk9QqQkk"
                )
            }
            queryName.contains("stranger things") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.7,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=b9EkMc79ZSU"
                )
            }
            queryName.contains("breaking bad") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 9.5,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=HhesaQXLuRY"
                )
            }
            queryName.contains("godfather") || queryName.contains("baba") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 9.2,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=sY1S34973zA"
                )
            }
            queryName.contains("pulp fiction") || queryName.contains("ucuz roman") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.9,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=s7Eg0456_Yg"
                )
            }
            queryName.contains("fight club") || queryName.contains("dövüş kulübü") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.8,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=qtRKdVHc-cE"
                )
            }
            queryName.contains("matrix") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.7,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=m8e-FF8MqbU"
                )
            }
            queryName.contains("gladiator") || queryName.contains("gladyatör") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.5,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=owK1axD_180"
                )
            }
            queryName.contains("lord of the rings") || queryName.contains("yüzüklerin efendisi") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.9,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=V75dMMIW2B4"
                )
            }
            queryName.contains("game of thrones") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 9.2,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=KPLYYLDt_Vk"
                )
            }
            queryName.contains("dağ") || queryName.contains("dag") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.8,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=kYJzX2e3qN0"
                )
            }
            queryName.contains("kurtlar vadisi") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.9,
                    logoUrl = item.logoUrl,
                    trailerUrl = "https://www.youtube.com/watch?v=9_d8_2Y"
                )
            }
            queryName.contains("çukur") || queryName.contains("cukur") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.5,
                    logoUrl = item.logoUrl
                )
            }
            queryName.contains("ezel") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 9.1,
                    logoUrl = item.logoUrl
                )
            }
            queryName.contains("oppenheimer") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.9,
                    logoUrl = item.logoUrl
                )
            }
            queryName.contains("dune") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.6,
                    logoUrl = item.logoUrl
                )
            }
            queryName.contains("avatar") -> {
                return EnrichedMetadata(
                    summary = item.summary,
                    cast = item.cast,
                    director = item.director,
                    rating = 8.1,
                    logoUrl = item.logoUrl
                )
            }
        }
        
        val fallbackSummary = item.summary
        val fallbackCast = if (item.cast.isNotBlank() && !item.isPlaceholderCast()) item.cast else ""
        val fallbackDirector = if (item.director.isNotBlank() && !item.isPlaceholderDirector()) item.director else ""
        val fallbackRating = if (item.rating > 0.0) item.rating else 0.0
        
        return EnrichedMetadata(
            summary = fallbackSummary,
            cast = fallbackCast,
            director = fallbackDirector,
            rating = fallbackRating,
            logoUrl = item.logoUrl,
            trailerUrl = item.trailerUrl
        )
    }

    /**
     * Unified Gemini API caller. Handles key fetching, headers, request serialization, model fallbacks, and response parsing.
     */
    suspend fun callGeminiApi(
        context: android.content.Context,
        prompt: String
    ): String? = withContext(Dispatchers.IO) {
        val savedKey = context.dataStore.data.firstOrNull()?.get(stringPreferencesKey("gemini_api_key"))
        val apiKey = if (!savedKey.isNullOrEmpty()) savedKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "placeholder") {
            Log.w(TAG, "Gemini API Key is missing, placeholder, or default.")
            return@withContext null
        }

        val requestJson = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val models = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest")
        var responseBody: String? = null
        var lastError: String? = null

        for (model in models) {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            try {
                val response = clientByTimeout.newCall(request).execute()
                if (response.isSuccessful) {
                    responseBody = response.body?.string()
                    Log.d(TAG, "Model $model response successful")
                    break
                } else {
                    val err = response.body?.string()
                    lastError = "Model $model failed with code ${response.code}: $err"
                    Log.w(TAG, lastError)
                }
            } catch (e: Exception) {
                lastError = "Model $model failed with exception: ${e.message}"
                Log.w(TAG, lastError, e)
            }
        }

        if (responseBody == null) {
            return@withContext null
        }

        try {
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            return@withContext parts?.optJSONObject(0)?.optString("text")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemini API response", e)
            null
        }
    }

    /**
     * Recommends items from the library based on the user's watch history.
     * Uses Gemini API to analyze the watched items and selects the most relevant recommendations from the available items.
     * Returns a list of recommended IPTVItem IDs.
     */
    suspend fun getRecommendationsWithGemini(
        context: android.content.Context,
        watchHistory: List<com.example.data.model.ContinueWatching>,
        availableItems: List<IPTVItem>
    ): List<Int> = withContext(Dispatchers.IO) {
        if (watchHistory.isEmpty() || availableItems.isEmpty()) {
            return@withContext emptyList<Int>()
        }

        val historyStr = watchHistory.joinToString("\n") { cw ->
            "- ${cw.itemName} (Tür: ${cw.itemType}, Kategori: ${cw.category})"
        }

        val candidateItems = availableItems.filter { it.type == "MOVIE" || it.type == "SERIES" }
        if (candidateItems.isEmpty()) {
            return@withContext emptyList<Int>()
        }

        val candidatesStr = candidateItems.joinToString("\n") { item ->
            "- ID ${item.id}: ${item.cleanedName} (Tür: ${item.type}, Kategori: ${item.category})"
        }

        val prompt = """
            Sana kullanıcının izleme geçmişini ve kütüphanemizdeki mevcut film/dizi seçeneklerini vereceğim.
            Lütfen kullanıcının izleme geçmişini ve ilgi alanlarını (kategori, tür vb.) analiz et. Ardından, kütüphanedeki seçenekler arasından bu kullanıcının en çok ilgisini çekebilecek 3 ile 5 arasındaki yapımı seç.
            
            Kullanıcının İzleme Geçmişi:
            ${historyStr}
            
            Kütüphanedeki Seçenekler:
            ${candidatesStr}
            
            Lütfen seçtiğin yapımların sadece ID numaralarını içeren bir JSON listesi döndür. Kesinlikle başka bir açıklama, giriş veya çıkış cümlesi yazma. Sadece geçerli bir JSON array döndür.
            Örnek Çıktı:
            [6, 7, 10]
        """.trimIndent()

        val rawText = callGeminiApi(context, prompt) ?: return@withContext emptyList<Int>()
        try {
            val cleanedJsonText = extractJson(rawText).trim()
            val jsonArray = org.json.JSONArray(cleanedJsonText)
            val recommendedIds = mutableListOf<Int>()
            for (i in 0 until jsonArray.length()) {
                recommendedIds.add(jsonArray.getInt(i))
            }
            recommendedIds
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse recommended IDs from Gemini text: $rawText", e)
            emptyList()
        }
    }

    /**
     * AI Sinema Gurusu: Filters local items using NLP intent matching via Gemini.
     */
    suspend fun nlpSearchWithGemini(
        context: android.content.Context,
        query: String,
        availableItems: List<IPTVItem>
    ): List<Int> = withContext(Dispatchers.IO) {
        if (query.isBlank() || availableItems.isEmpty()) return@withContext emptyList<Int>()

        val candidateItems = availableItems.filter { it.type == "MOVIE" || it.type == "SERIES" }
        if (candidateItems.isEmpty()) return@withContext emptyList<Int>()

        val candidatesStr = candidateItems.take(50).joinToString("\n") { item ->
            "ID ${item.id}: ${item.cleanedName} (Kategori: ${item.category}, Konu: ${item.summary})"
        }

        val prompt = """
            Kullanıcı kütüphanede şu doğal dil sorgusunu yaptı: "$query"
            Lütfen bu sorgunun anlamını ve kullanıcının ruh halini, zaman tercihini veya tür beklentisini analiz et.
            Ardından, kütüphanemizdeki mevcut yapımlar arasından bu sorguya EN UYGUN olan en fazla 8 yapımı seç.
            
            Kütüphanedeki Seçenekler:
            $candidatesStr
            
            Lütfen seçtiğin yapımların sadece ID numaralarını içeren bir JSON listesi döndür. Kesinlikle başka bir açıklama, giriş veya çıkış cümlesi yazma. Sadece geçerli bir JSON array döndür.
            Örnek Çıktı:
            [12, 15]
        """.trimIndent()

        val rawText = callGeminiApi(context, prompt) ?: return@withContext emptyList<Int>()
        try {
            val cleanedJsonText = extractJson(rawText).trim()
            val jsonArray = org.json.JSONArray(cleanedJsonText)
            val matchedIds = mutableListOf<Int>()
            for (i in 0 until jsonArray.length()) {
                matchedIds.add(jsonArray.getInt(i))
            }
            matchedIds
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse matched IDs from NLP Search: $rawText", e)
            emptyList()
        }
    }

    /**
     * Spoiler-free Summary: "Önceki Bölümlerde Neler Oldu?"
     */
    suspend fun getSpoilerFreePreviousEpisodesSummary(
        context: android.content.Context,
        showTitle: String,
        currentSeason: Int,
        currentEpisode: Int
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Dizi Adı: $showTitle
            Hedef Bölüm: Sezon $currentSeason, Bölüm $currentEpisode
            
            Lütfen kullanıcının Sezon $currentSeason Bölüm $currentEpisode'i izlemeden ÖNCE bilmesi gereken önemli olayları (önceki bölümlerde yaşananları) özetle.
            Görevin, kesinlikle Sezon $currentSeason Bölüm $currentEpisode ve sonrasına dair hiçbir spoiler (gelecek olay bilgisi) VERMEDEN, heyecan verici, akıcı ve Türkçe bir "Önceki Bölümlerde Neler Oldu?" bülteni hazırlamaktır.
            En fazla 3-4 cümle olsun. Samimi, sürükleyici ve heyecanlandırıcı bir anlatım kullan.
        """.trimIndent()

        callGeminiApi(context, prompt) ?: "Önceki bölümlerin spoiler-suz özeti şu anda hazırlanamıyor."
    }

    /**
     * Grounded "Önceki Bölümlerde Neler Oldu?" bülteni (Gemini AI + Local Fallback, TMDB disabled).
     */
    suspend fun getGroundedPreviousEpisodesRecap(
        context: android.content.Context,
        showTitle: String,
        watchedEpisodes: List<Pair<Int, Int>>,
        tmdbApiKey: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (watchedEpisodes.isEmpty()) {
            return@withContext "Bu dizide henüz izlediğiniz bir bölüm bulunamadı."
        }

        try {
            val (lastSeason, lastEpisode) = watchedEpisodes.last()
            val geminiPrompt = """
                Dizi Adı: $showTitle
                İzlenen Son Bölümler: Sezon $lastSeason Bölüm $lastEpisode'a kadar.

                Lütfen kullanıcının Sezon $lastSeason Bölüm $lastEpisode ve önceki bölümlerde izlediği olayları spoiler içermeden, merak uyandırıcı, akıcı ve Türkçe bir "Önceki Bölümlerde Neler Oldu?" bülteni olarak özetle (en fazla 3-4 cümle).
            """.trimIndent()

            val recap = callGeminiApi(context, geminiPrompt)
            recap ?: "Sezon $lastSeason Bölüm $lastEpisode'a kadar yaşanan olaylar heyecan verici gelişmelerle devam ediyor. Keyifli seyirler!"
        } catch (e: Exception) {
            Log.w(TAG, "getGroundedPreviousEpisodesRecap error for '$showTitle': ${e.message}")
            "Özet şu anda hazırlanamıyor, lütfen tekrar deneyin."
        }
    }

    /**
     * AI Parental Control Warning
     */
    suspend fun getParentalControlWarning(
        context: android.content.Context,
        title: String,
        summary: String,
        cast: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Yapım Adı: $title
            Özet/Konu: $summary
            Oyuncular: $cast
            
            Lütfen bu yapımın içeriğini hassas temalar açısından analiz et (Şiddet, Korku, Cinsellik, Küfür, Alkol/Sigara vb.).
            Ebeveynlerin çocukları için bilmesi gereken olası riskli unsurları tespit et ve kısa, net, uyarıcı bir Türkçe ebeveyn kontrolü uyarısı/analizi oluştur.
            Eğer içerik tamamen güvenliyse "Genel İzleyici kitlesine uygundur." yaz.
            Toplam uzunluk 2 kısa cümleyi geçmesin. Başına ebeveyn uyarı emojisi (örneğin ⚠️ veya 🚫) koy.
        """.trimIndent()

        callGeminiApi(context, prompt) ?: "⚠️ İçerik ebeveyn uyarısı şu anda alınamıyor."
    }

    /**
     * AI Bülteni Widget Text
     */
    suspend fun getAIBulletin(
        context: android.content.Context,
        watchHistory: List<com.example.data.model.ContinueWatching>
    ): String = withContext(Dispatchers.IO) {
        val historyStr = watchHistory.take(3).joinToString("\n") { cw ->
            "- ${cw.itemName} (Kategori: ${cw.category})"
        }

        val prompt = """
            Kullanıcının son izlediği yapımlar:
            $historyStr
            
            Lütfen kullanıcıya hitaben bugün için kısa, sinematik, çok motive edici ve Türkçe bir sinema bülteni / günün öneri sözü (1 veya 2 cümlelik) hazırla.
            Onların izleme alışkanlıklarına tatlı bir atıfta bulunabilirsin. Başına ilgi çekici bir emoji koy.
        """.trimIndent()

        callGeminiApi(context, prompt) ?: "🍿 Bugün ne izlemek istersiniz? İlgi alanlarınıza göre harika içerikler keşfetmek için takipte kalın!"
    }

    // ---------------------------------------------------------
    // METHOD 2: TMDB (The Movie Database) API ARCHITECTURE
    // ---------------------------------------------------------
    
    /*
     * KULLANIM REHBERİ (TMDB API):
     * 1. https://www.themoviedb.org/ adresinden ücretsiz geliştirici hesabı oluşturup API Anahtarı alın.
     * 2. Bu anahtarı .env dosyanızda 'TMDB_API_KEY' olarak tanımlayın.
     * 3. Aşağıdaki Retrofit modellerini ve çağrı mantığını kullanarak dinamik aramalar gerçekleştirebilirsiniz.
     */

    // --- TMDB API Retrofit Arayüzü Tanımlaması ---
    interface TMDBApiService {
        // Filmleri isme göre arar
        @retrofit2.http.GET("search/movie")
        suspend fun searchMovie(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("query") query: String,
            @retrofit2.http.Query("language") language: String = "tr-TR",
            @retrofit2.http.Query("year") year: Int? = null
        ): TMDBResponse<TMDBMovieResult>

        // Dizileri (TV Shows) isme göre arar
        @retrofit2.http.GET("search/tv")
        suspend fun searchSeries(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("query") query: String,
            @retrofit2.http.Query("language") language: String = "tr-TR",
            @retrofit2.http.Query("first_air_date_year") firstAirDateYear: Int? = null
        ): TMDBResponse<TMDBSeriesResult>

        // Film oyuncu/yönetmen kadrosunu getirir
        @retrofit2.http.GET("movie/{movie_id}/credits")
        suspend fun getMovieCredits(
            @retrofit2.http.Path("movie_id") movieId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBCreditsResponse

        // Dizi oyuncu/yönetmen kadrosunu getirir
        @retrofit2.http.GET("tv/{tv_id}/credits")
        suspend fun getSeriesCredits(
            @retrofit2.http.Path("tv_id") tvId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBCreditsResponse

        // Kişileri isme göre arar
        @retrofit2.http.GET("search/person")
        suspend fun searchPerson(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("query") query: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBPersonResult>

        // Kişi detaylarını (biyografi, doğum tarihi vb) getirir
        @retrofit2.http.GET("person/{person_id}")
        suspend fun getPersonDetails(
            @retrofit2.http.Path("person_id") personId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBPersonDetailsResponse

        // Trend Filmler
        @retrofit2.http.GET("trending/movie/day")
        suspend fun getTrendingMovies(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBMovieResult>

        // Trend Diziler
        @retrofit2.http.GET("trending/tv/day")
        suspend fun getTrendingSeries(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBSeriesResult>

        // Günlük Trend İçerikler (All)
        @retrofit2.http.GET("trending/all/day")
        suspend fun getTrendingAllDay(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBTrendingResult>

        // Multi Arama (Film / Dizi M3U Eşleşmesi İçin)
        @retrofit2.http.GET("search/multi")
        suspend fun searchMulti(
            @retrofit2.http.Query("query") query: String,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBTrendingResult>

        // Popüler Filmler
        @retrofit2.http.GET("movie/popular")
        suspend fun getPopularMovies(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBMovieResult>

        // Popüler Diziler
        @retrofit2.http.GET("tv/popular")
        suspend fun getPopularSeries(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBSeriesResult>

        // En Yüksek Puan Alan Diziler (Top Rated Series)
        @retrofit2.http.GET("tv/top_rated")
        suspend fun getTopRatedSeries(
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBSeriesResult>

        // Film Önerileri
        @retrofit2.http.GET("movie/{movie_id}/recommendations")
        suspend fun getMovieRecommendations(
            @retrofit2.http.Path("movie_id") movieId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBMovieResult>

        // Film Benzeri İçerikler
        @retrofit2.http.GET("movie/{movie_id}/similar")
        suspend fun getSimilarMovies(
            @retrofit2.http.Path("movie_id") movieId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBMovieResult>

        // Dizi Önerileri
        @retrofit2.http.GET("tv/{tv_id}/recommendations")
        suspend fun getSeriesRecommendations(
            @retrofit2.http.Path("tv_id") tvId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBSeriesResult>

        // Dizi Benzeri İçerikler
        @retrofit2.http.GET("tv/{tv_id}/similar")
        suspend fun getSimilarSeries(
            @retrofit2.http.Path("tv_id") tvId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBResponse<TMDBSeriesResult>

        // Film Detayları
        @retrofit2.http.GET("movie/{movie_id}")
        suspend fun getMovieDetails(
            @retrofit2.http.Path("movie_id") movieId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBMovieDetailsResponse

        // Bölüm Detayları (GERÇEK, editör onaylı TMDB bölüm özeti — "Önceki Bölümlerde
        // Neler Oldu?" bülteninin uydurma değil gerçek veriye dayanması için kullanılır)
        @retrofit2.http.GET("tv/{tv_id}/season/{season_number}/episode/{episode_number}")
        suspend fun getEpisodeDetails(
            @retrofit2.http.Path("tv_id") tvId: Int,
            @retrofit2.http.Path("season_number") seasonNumber: Int,
            @retrofit2.http.Path("episode_number") episodeNumber: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBEpisodeDetailsResponse

        // Sezon Detayları (Bölüm Listesi ve Özetleri)
        @retrofit2.http.GET("tv/{tv_id}/season/{season_number}")
        suspend fun getSeasonDetails(
            @retrofit2.http.Path("tv_id") tvId: Int,
            @retrofit2.http.Path("season_number") seasonNumber: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBSeasonDetailsResponse

        // Dizi Detayları
        @retrofit2.http.GET("tv/{tv_id}")
        suspend fun getSeriesDetails(
            @retrofit2.http.Path("tv_id") tvId: Int,
            @retrofit2.http.Query("api_key") apiKey: String,
            @retrofit2.http.Query("language") language: String = "tr-TR"
        ): TMDBSeriesDetailsResponse
    }

    // --- TMDB Veri Modelleri ---
    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBMovieDetailsResponse(
        val id: Int? = null,
        val title: String? = null,
        val overview: String? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null,
        val release_date: String? = null,
        val vote_average: Double? = null,
        val vote_count: Int? = null,
        val tagline: String? = null,
        val runtime: Int? = null,
        val genres: List<TMDBGenre>? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBEpisodeDetailsResponse(
        val id: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        val season_number: Int? = null,
        val episode_number: Int? = null,
        val still_path: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBSeasonDetailsResponse(
        val id: Int? = null,
        val name: String? = null,
        val season_number: Int? = null,
        val overview: String? = null,
        val episodes: List<TMDBSeasonEpisode>? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBSeasonEpisode(
        val id: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        val season_number: Int? = null,
        val episode_number: Int? = null,
        val still_path: String? = null,
        val vote_average: Double? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBCreatedBy(
        val id: Int? = null,
        val name: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBSeasonSummary(
        val id: Int? = null,
        val name: String? = null,
        val season_number: Int? = null,
        val episode_count: Int? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBSeriesDetailsResponse(
        val id: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null,
        val first_air_date: String? = null,
        val vote_average: Double? = null,
        val vote_count: Int? = null,
        val tagline: String? = null,
        val number_of_seasons: Int? = null,
        val number_of_episodes: Int? = null,
        val genres: List<TMDBGenre>? = null,
        val created_by: List<TMDBCreatedBy>? = null,
        val seasons: List<TMDBSeasonSummary>? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBGenre(
        val id: Int? = null,
        val name: String? = null
    )
    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBResponse<T>(
        val results: List<T>? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBTrendingResult(
        val id: Int? = null,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        val vote_average: Double? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBMovieResult(
        val id: Int? = null,
        val title: String? = null,
        val overview: String? = null,
        val release_date: String? = null,
        val vote_average: Double? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBSeriesResult(
        val id: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        val first_air_date: String? = null,
        val vote_average: Double? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBCreditsResponse(
        val cast: List<TMDBPerson>? = null,
        val crew: List<TMDBPerson>? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBPerson(
        val name: String? = null,
        val character: String? = null,
        val job: String? = null,
        val profile_path: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBPersonResult(
        val id: Int? = null,
        val name: String? = null,
        val profile_path: String? = null,
        val known_for: List<TMDBKnownFor>? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBKnownFor(
        val title: String? = null,
        val name: String? = null
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class TMDBPersonDetailsResponse(
        val id: Int? = null,
        val name: String? = null,
        val biography: String? = null,
        val birthday: String? = null,
        val place_of_birth: String? = null,
        val profile_path: String? = null
    )

    /**
     * M3U / Xtream'den gelen film/dizi adını TMDB'de aratmadan önce temizleyen fonksiyon.
     */
    fun sanitizeTitle(rawName: String): String {
        if (rawName.isBlank()) return ""
        var cleaned = rawName
        // TR |, EN |, DUAL, 4K, 1080p, 720p, DUAL, TR-EN, AAC, x264, x265, HEVC vb. temizleme
        cleaned = cleaned.replace(Regex("""(?i)\b(TR|EN|DUAL|TR-EN|TR-ENG|4K|1080P|720P|AAC|X264|X265|HEVC|WEB-DL|HDR|REPACK|BLURAY|HD)\b\s*\|?"""), " ")
        // Parantez içindeki yılları temizleme: (2024), [2023]
        cleaned = cleaned.replace(Regex("""[\(\[]\d{4}[\)\]]"""), " ")
        // Sezon/bölüm kodlarını temizleme: S01E01, 1.Sezon
        cleaned = cleaned.replace(Regex("""(?i)\bS\d+E\d+\b|\b\d+\.\s*Sezon\b"""), " ")
        // Özel karakterleri temizleme (harf, rakam, tire ve boşluk hariç)
        cleaned = cleaned.replace(Regex("""[^\w\s\-\u00C0-\u024F\u1E00-\u1EFF]"""), " ")
        // Fazla boşlukları daraltma
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return if (cleaned.isNotBlank()) cleaned else rawName.trim()
    }

    // ÖNEMLİ: buildTmdbImageUrl (stok fotoğraf yedekli TMDB görsel tamamlayıcı)
    // tamamen kaldırıldı — hiçbir yerden çağrılmıyordu, ölü kod.

    /**
     * TMDB API'sini kullanarak verilen dizi başlığı için kısa özet (plot summary / overview) metni çeker.
     * Türkçe dilinde (tr-TR) arama yapar; sonuç boş veya overview yoksa İngilizce (en-US) dener.
     * Hata durumunda veya özet bulunamazsa null döndürür (asla çökmez).
     */
    private val seriesOverviewMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun fetchSeriesPlotSummaryFromTMDB(
        seriesTitle: String,
        tmdbApiKey: String = ""
    ): String? = withContext(Dispatchers.IO) {
        val sanitized = sanitizeTitle(seriesTitle).trim()
        if (sanitized.isBlank()) return@withContext null

        val cacheKey = sanitized.lowercase(java.util.Locale.ROOT)
        seriesOverviewMemoryCache[cacheKey]?.let { cached ->
            return@withContext cached.ifBlank { null }
        }

        try {
            val apiKey = tmdbApiKey.ifBlank {
                com.example.data.repository.TMDBRepository.resolveEmbeddedTmdbKey()
            }.ifBlank {
                BuildConfig.TMDB_API_KEY
            }

            if (apiKey.isBlank() || apiKey == "placeholder" || apiKey == "MY_TMDB_API_KEY") {
                Log.w(TAG, "fetchSeriesPlotSummaryFromTMDB: TMDB API key bulunamadı.")
                return@withContext null
            }

            // 1. Adım: Türkçe dille dizi ara
            val searchResponseTr = try {
                NetworkModule.tmdbService.searchTv(
                    query = sanitized,
                    language = "tr-TR",
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                Log.w(TAG, "fetchSeriesPlotSummaryFromTMDB tr search hatası: ${e.message}")
                null
            }

            val bestMatchTr = searchResponseTr?.results?.firstOrNull { !it.name.isNullOrBlank() }
            if (bestMatchTr != null) {
                // Eğer doğrudan arama sonucunda dolu overview varsa
                val overviewTr = bestMatchTr.overview?.trim()
                if (!overviewTr.isNullOrBlank()) {
                    seriesOverviewMemoryCache[cacheKey] = overviewTr
                    return@withContext overviewTr
                }

                // Arama özetinde yoksa detay endpoint'inden Türkçe iste
                if (bestMatchTr.id != null) {
                    try {
                        val detailsTr = NetworkModule.tmdbService.getTvDetails(id = bestMatchTr.id, language = "tr-TR", apiKey = apiKey)
                        val detailOverviewTr = detailsTr.overview?.trim()
                        if (!detailOverviewTr.isNullOrBlank()) {
                            seriesOverviewMemoryCache[cacheKey] = detailOverviewTr
                            return@withContext detailOverviewTr
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchSeriesPlotSummaryFromTMDB tr details hatası: ${e.message}")
                    }

                    // Türkçe özet yoksa İngilizce detayları dene
                    try {
                        val detailsEn = NetworkModule.tmdbService.getTvDetails(id = bestMatchTr.id, language = "en-US", apiKey = apiKey)
                        val detailOverviewEn = detailsEn.overview?.trim()
                        if (!detailOverviewEn.isNullOrBlank()) {
                            seriesOverviewMemoryCache[cacheKey] = detailOverviewEn
                            return@withContext detailOverviewEn
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchSeriesPlotSummaryFromTMDB en details hatası: ${e.message}")
                    }
                }
            }

            // 2. Adım: Türkçe aramada sonuç çıkmadıysa İngilizce ara
            val searchResponseEn = try {
                NetworkModule.tmdbService.searchTv(
                    query = sanitized,
                    language = "en-US",
                    apiKey = apiKey
                )
            } catch (e: Exception) {
                Log.w(TAG, "fetchSeriesPlotSummaryFromTMDB en search hatası: ${e.message}")
                null
            }

            val bestMatchEn = searchResponseEn?.results?.firstOrNull { !it.name.isNullOrBlank() }
            val overviewEn = bestMatchEn?.overview?.trim()
            if (!overviewEn.isNullOrBlank()) {
                seriesOverviewMemoryCache[cacheKey] = overviewEn
                return@withContext overviewEn
            }

            seriesOverviewMemoryCache[cacheKey] = ""
            null
        } catch (e: Exception) {
            Log.w(TAG, "fetchSeriesPlotSummaryFromTMDB genel hata for '$seriesTitle': ${e.message}")
            null
        }
    }

    /**
     * TMDB API is disabled. Bypasses TMDB completely and redirects safely.
     */
    suspend fun enrichWithTMDB(context: android.content.Context, item: IPTVItem, tmdbApiKey: String): EnrichedMetadata? = withContext(Dispatchers.IO) {
        // TMDB is disabled per stability requirements. Forward to Xtream/Gemini/Offline chain.
        try {
            enrichWithXtreamOrFallback(context, item)
        } catch (_: Throwable) {
            generateLocalMovieMetadata(item)
        }
    }

    /**
     * TMDB person details fallback removed - uses Gemini and local templates.
     */
    suspend fun fetchPersonDetailsFromTMDB(
        context: android.content.Context,
        name: String,
        role: String,
        tmdbApiKey: String
    ): PersonDetails? = withContext(Dispatchers.IO) {
        fun writeDiag(msg: String) = com.example.util.DiagnosticLog.write(context, "person_tmdb_diag", msg)
        try {
            val apiKey = tmdbApiKey.ifBlank {
                com.example.data.repository.TMDBRepository.resolveEmbeddedTmdbKey()
            }
            if (apiKey.isBlank() || apiKey == "placeholder") {
                writeDiag("KİŞİ ARAMA: API anahtarı boş/placeholder.\nKişi: $name")
                return@withContext null
            }

            val searchResponse = NetworkModule.tmdbService.searchPerson(query = name, apiKey = apiKey)
            val bestMatch = searchResponse.results
                .filter { !it.name.isNullOrBlank() }
                .maxByOrNull { it.popularity ?: 0.0 }
            if (bestMatch == null) {
                writeDiag("KİŞİ ARAMA: TMDB'de sonuç bulunamadı.\nKişi: $name\nSonuç sayısı: ${searchResponse.results.size}")
                return@withContext null
            }

            val details = NetworkModule.tmdbService.getPersonDetails(personId = bestMatch.id, apiKey = apiKey)
            val finalImageUrl = com.example.data.repository.TMDBRepository.buildImageUrl(
                details.profilePath ?: bestMatch.profilePath,
                com.example.data.repository.TMDBRepository.SIZE_PROFILE
            )
            writeDiag(
                "KİŞİ ARAMA SONUCU\n" +
                "Kişi: $name\n" +
                "Eşleşen TMDB ismi: ${bestMatch.name} (id=${bestMatch.id})\n" +
                "Biyografi uzunluğu: ${details.biography?.length ?: 0}\n" +
                "profilePath (detaydan): ${details.profilePath}\n" +
                "profilePath (arama sonucundan): ${bestMatch.profilePath}\n" +
                "SONUÇ imageUrl: $finalImageUrl"
            )
            if (details.biography.isNullOrBlank() && details.profilePath.isNullOrBlank()) {
                return@withContext null
            }

            val formattedRole = if (role.lowercase().contains("yönetmen") || role.lowercase().contains("director")) "Yönetmen" else "Oyuncu"
            PersonDetails(
                name = details.name ?: name,
                role = formattedRole,
                biography = details.biography ?: "",
                birthDate = details.birthday ?: "",
                birthPlace = details.placeOfBirth ?: "",
                knownFor = emptyList(),
                imageUrl = finalImageUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchPersonDetailsFromTMDB failed for $name: ${e.message}")
            null
        }
    }

    // In-memory cache for person details to make clicking on the same person multiple times INSTANT
    private val personMemoryCache = java.util.concurrent.ConcurrentHashMap<String, PersonDetails>()

    fun savePersonToCache(context: android.content.Context, details: PersonDetails) {
        val cacheKey = details.name.lowercase().trim()
        val existing = personMemoryCache[cacheKey]
        val finalDetails = if (existing != null && details.imageUrl.isNullOrBlank()) {
            existing
        } else if (existing != null && !existing.imageUrl.isNullOrBlank() && details.imageUrl.isNullOrBlank()) {
            details.copy(imageUrl = existing.imageUrl)
        } else {
            details
        }

        // Keep memory cache size bounded (max 100 entries)
        if (personMemoryCache.size > 100) {
            personMemoryCache.clear()
        }
        personMemoryCache[cacheKey] = finalDetails

        // Save to Room DB instead of bloating SharedPreferences
        try {
            com.example.IPTVApplication.applicationScope.launch {
                try {
                    val db = com.example.data.db.AppDatabase.getDatabase(context)
                    db.iptvDao().insertPersonDetails(
                        com.example.data.model.PersonDetailsEntity(
                            name = cacheKey,
                            displayName = finalDetails.name,
                            role = finalDetails.role,
                            biography = finalDetails.biography,
                            birthDate = finalDetails.birthDate,
                            birthPlace = finalDetails.birthPlace,
                            knownFor = finalDetails.knownFor.joinToString("|||"),
                            imageUrl = finalDetails.imageUrl?.takeIf { it.isNotBlank() } ?: db.iptvDao().getPersonDetails(cacheKey)?.imageUrl
                        )
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, "Error saving person details to Room DB", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error launching person cache save coroutine", e)
        }
    }

    /**
     * Gemini API ile bir oyuncu veya yönetmenin kısa biyografisini, doğum tarihi/yerini ve filmografisini çeker.
     * Includes memory and persistent Room DB caches for maximum speed.
     */
    suspend fun fetchPersonDetails(context: android.content.Context, name: String, role: String): PersonDetails? = withContext(Dispatchers.IO) {
        val cacheKey = name.lowercase().trim()

        // 1. Check in-memory cache
        personMemoryCache[cacheKey]?.let { cached ->
            if (cached.biography.isNotBlank() || !cached.imageUrl.isNullOrBlank()) {
                Log.d(TAG, "Cache HIT (Memory) for actor/director: $name")
                return@withContext cached
            }
        }

        // 2. Check Room Database cache
        try {
            val db = com.example.data.db.AppDatabase.getDatabase(context)
            val cachedEntity = db.iptvDao().getPersonDetails(cacheKey)
            if (cachedEntity != null && (cachedEntity.biography.isNotBlank() || !cachedEntity.imageUrl.isNullOrBlank())) {
                val knownForList = if (cachedEntity.knownFor.isNotBlank()) {
                    cachedEntity.knownFor.split("|||")
                } else emptyList()
                val details = PersonDetails(
                    name = cachedEntity.name,
                    role = cachedEntity.role,
                    biography = cachedEntity.biography,
                    birthDate = cachedEntity.birthDate,
                    birthPlace = cachedEntity.birthPlace,
                    knownFor = knownForList,
                    imageUrl = cachedEntity.imageUrl
                )
                personMemoryCache[cacheKey] = details
                Log.d(TAG, "Cache HIT (Room DB) for actor/director: $name")
                return@withContext details
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error reading cached person from Room DB", e)
        }

        val savedKey = context.dataStore.data.firstOrNull()?.get(stringPreferencesKey("gemini_api_key"))
        val apiKey = if (!savedKey.isNullOrEmpty()) savedKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "placeholder") {
            Log.w(TAG, "Gemini API Key is missing or default. Using local generator fallback.")
            return@withContext generateLocalPersonDetails(name, role)
        }

        // Optimized concise prompt for maximum Gemini generation speed
        val prompt = """
            Sana bir film/dizi oyuncusu veya yönetmeninin adını vereceğim.
            Adı: "$name"
            Görevi/Rolü: $role
            
            Lütfen bu kişi hakkında çok kısa ve öz araştırma yapıp aşağıdaki bilgileri Türkçe JSON olarak döndür:
            1. "name": Kişinin tam adı.
            2. "role": Görevi ("Oyuncu" veya "Yönetmen").
            3. "biography": Türkçe kısa özgeçmişi (en fazla 3 net cümle, doğum, kariyer ve başarılarından bahseden, akıcı dil).
            4. "birthDate": Doğum tarihi (örn: "11 Kasım 1974" veya null).
            5. "birthPlace": Doğum yeri (örn: "Los Angeles, Kaliforniya" veya null).
            6. "knownFor": En popüler 3-4 yapımı içeren dizi (örn: ["Inception", "Titanic"]).
            7. "imageUrl": Boş bırak veya null yap.

            Sadece geçerli bir JSON objesi döndür. Markdown kod blokları veya 'json' kelimesi dahil hiçbir ek açıklama ekleme.
            {
              "name": "...",
              "role": "...",
              "biography": "...",
              "birthDate": "...",
              "birthPlace": "...",
              "knownFor": ["...", "..."],
              "imageUrl": null
            }
        """.trimIndent()

        try {
            // Build Gemini REST Request Payload
            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val models = listOf("gemini-2.5-flash", "gemini-1.5-flash")
            var responseBody: String? = null
            var lastError: String? = null

            for (model in models) {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    .post(requestBody)
                    .build()

                try {
                    val response = clientByTimeout.newCall(request).execute()
                    if (response.isSuccessful) {
                        responseBody = response.body?.string()
                        Log.d(TAG, "Model $model response successful for person details")
                        break
                    } else {
                        val err = response.body?.string()
                        lastError = "Model $model failed with code ${response.code}: $err"
                        Log.w(TAG, lastError)
                    }
                } catch (e: Exception) {
                    lastError = "Model $model failed with exception: ${e.message}"
                    Log.w(TAG, lastError, e)
                }
            }

            if (responseBody == null) {
                Log.w(TAG, "All Gemini attempts for person details failed. Last error: $lastError. Falling back to local generator.")
                return@withContext generateLocalPersonDetails(name, role)
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: return@withContext generateLocalPersonDetails(name, role)

            val cleanJson = extractJson(rawText)
            val jsonOutput = JSONObject(cleanJson)
            val knownForArray = jsonOutput.optJSONArray("knownFor")
            val knownForList = mutableListOf<String>()
            if (knownForArray != null) {
                for (i in 0 until knownForArray.length()) {
                    knownForList.add(knownForArray.getString(i))
                }
            }

            val fetchedDetails = PersonDetails(
                name = jsonOutput.optString("name", name),
                role = jsonOutput.optString("role", role),
                biography = jsonOutput.optString("biography", "Bilgi bulunamadı."),
                birthDate = jsonOutput.optString("birthDate").let { if (it == "null" || it.isBlank()) null else it },
                birthPlace = jsonOutput.optString("birthPlace").let { if (it == "null" || it.isBlank()) null else it },
                knownFor = knownForList,
                imageUrl = jsonOutput.optString("imageUrl").let { if (it == "null" || it.isBlank()) null else it }
            )

            // Save to Cache & Room DB
            savePersonToCache(context, fetchedDetails)

            return@withContext fetchedDetails

        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch person details from Gemini API. Falling back to local generator.", e)
            return@withContext generateLocalPersonDetails(name, role)
        }
    }

    fun generateLocalPersonDetails(name: String, role: String): PersonDetails {
        val formattedRole = if (role.lowercase().contains("yönetmen") || role.lowercase().contains("director")) "Yönetmen" else "Oyuncu"
        return PersonDetails(
            name = name,
            role = formattedRole,
            biography = "",
            birthDate = "",
            birthPlace = "",
            knownFor = emptyList(),
            imageUrl = null
        )
    }

    suspend fun getMoodBasedRecommendation(
        context: android.content.Context,
        moodQuery: String,
        availableItems: List<com.example.data.model.IPTVItem>
    ): Int? = withContext(Dispatchers.IO) {
        if (moodQuery.isBlank() || availableItems.isEmpty()) return@withContext null

        val candidateItems = availableItems.filter { it.type == "MOVIE" || it.type == "SERIES" }
            .ifEmpty { availableItems }
        if (candidateItems.isEmpty()) return@withContext null

        // Pass a subset of candidate items to keep prompt size reasonable
        val candidatesStr = candidateItems.shuffled().take(50).joinToString("\n") { item ->
            "ID ${item.id}: ${item.cleanedName} (Kategori: ${item.category}, Konu: ${item.summary})"
        }

        val prompt = """
            Kullanıcının ruh hali ve o akşamki izleme arzusu şu şekildedir: "$moodQuery"
            Lütfen bu ruh halini analiz et ve aşağıdaki kütüphanemizde bulunan yapımlar arasından bu hisse, atmosfere veya yorgunluk/canlılık durumuna EN ÇOK uyan TEK bir yapımı seç.
            
            Kütüphanedeki Seçenekler:
            $candidatesStr
            
            Lütfen seçtiğin yapımın sadece ve sadece ID numarasını döndür. Başka hiçbir açıklama, kelime, işaret veya cümle yazma. Sadece tek bir tamsayı döndür.
            Örnek Çıktı:
            42
        """.trimIndent()

        val rawText = callGeminiApi(context, prompt)
        var matchedId: Int? = null

        if (!rawText.isNullOrBlank()) {
            val digitMatches = Regex("""\b(\d+)\b""").findAll(rawText).mapNotNull { it.value.toIntOrNull() }.toList()
            for (id in digitMatches) {
                if (candidateItems.any { it.id == id }) {
                    matchedId = id
                    break
                }
            }
        }

        if (matchedId == null) {
            val normQuery = moodQuery.lowercase(java.util.Locale.ROOT)
            val keywords = normQuery.split(" ", ",", ".", "-", "!", "?").filter { it.length >= 3 }
            
            val keywordMatch = candidateItems.firstOrNull { item ->
                val name = item.cleanedName.lowercase(java.util.Locale.ROOT)
                val cat = item.category.lowercase(java.util.Locale.ROOT)
                val sum = item.summary.lowercase(java.util.Locale.ROOT)
                keywords.any { kw -> name.contains(kw) || cat.contains(kw) || sum.contains(kw) }
            }
            
            matchedId = keywordMatch?.id ?: candidateItems.randomOrNull()?.id
        }

        matchedId
    }

    /**
     * Gemini AI "Günün Seçkisi": Requests Gemini model (gemini-1.5-flash / gemini-2.5-flash)
     * to generate 5 popular movies and 5 popular series formatted as JSON.
     */
    suspend fun getGeminiDailyPicks(
        context: android.content.Context
    ): List<IPTVItem> = withContext(Dispatchers.IO) {
        val prompt = """
            Bana bugün izlenebilecek en iyi 5 popüler film ve 5 popüler diziden oluşan bir liste ver.
            Yanıtı sadece geçerli JSON array formatında döndür:
            [
              {
                "title": "Interstellar",
                "type": "MOVIE",
                "genre": "Bilim Kurgu",
                "short_description": "İnsanlığın geleceğini kurtarmak için solucan deliğinden geçen astronotların epik hikayesi.",
                "rating": 8.7
              }
            ]
            
            Kurallar:
            1. Toplam 10 öge olsun (5 MOVIE, 5 SERIES).
            2. "type" değeri ya "MOVIE" ya da "SERIES" olmalı.
            3. "rating" 0.0 - 10.0 arası ondalıklı sayı olmalı.
            4. Sadece geçerli JSON array döndür. Giriş/çıkış kelimeleri veya markdown kod blokları ekleme.
        """.trimIndent()

        val rawText = callGeminiApi(context, prompt)
        if (rawText.isNullOrBlank()) {
            return@withContext getFallbackGeminiPicks()
        }

        try {
            val jsonString = extractJson(rawText).trim()
            val jsonArray = org.json.JSONArray(jsonString)
            val result = mutableListOf<IPTVItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val title = obj.optString("title", "Popüler Yapım")
                val type = obj.optString("type", "MOVIE").uppercase(java.util.Locale.ROOT)
                val genre = obj.optString("genre", if (type == "MOVIE") "Film" else "Dizi")
                val desc = obj.optString("short_description", "Gemini AI tarafından önerilen popüler içerik.")
                val rating = obj.optDouble("rating", 8.5)
                val imgUrl = obj.optString("imageUrl", "").ifEmpty { null }

                result.add(
                    IPTVItem(
                        id = 8000 + i,
                        playlistId = 0,
                        name = title,
                        cleanedName = title,
                        logoUrl = imgUrl,
                        streamUrl = "",
                        category = genre,
                        type = if (type == "SERIES") "SERIES" else "MOVIE",
                        rating = rating,
                        summary = desc,
                        cast = "",
                        director = "",
                        trailerUrl = null
                    )
                )
            }

            if (result.isNotEmpty()) result else getFallbackGeminiPicks()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemini Daily Picks JSON: $rawText", e)
            getFallbackGeminiPicks()
        }
    }

    private fun getFallbackGeminiPicks(): List<IPTVItem> {
        return emptyList()
    }

    /**
     * Verilen film/dizi adı için YouTube Data API v3 üzerinden gerçek bir fragman videosu arar.
     * API anahtarı yoksa veya sonuç bulunamazsa null döner (asla ilgisiz bir filmin
     * fragmanını yerine koymaz).
     */
    suspend fun fetchYoutubeTrailerId(context: android.content.Context, title: String): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val savedKey = context.dataStore.data.firstOrNull()?.get(stringPreferencesKey("youtube_api_key"))
        val embeddedKey = resolveEmbeddedYoutubeKey()
        val apiKey = when {
            !savedKey.isNullOrEmpty() -> savedKey
            embeddedKey.isNotEmpty() -> embeddedKey
            else -> BuildConfig.YOUTUBE_API_KEY
        }
        if (apiKey.isEmpty() || apiKey == "placeholder") {
            Log.w(TAG, "YouTube API Key is missing or placeholder; trailer search skipped.")
            return@withContext null
        }
        try {
            val query = java.net.URLEncoder.encode("$title resmi fragman trailer", "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&type=video&videoEmbeddable=true&q=$query&key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            val response = clientByTimeout.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "YouTube search failed: ${response.code}")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val items = json.optJSONArray("items")
            if (items == null || items.length() == 0) return@withContext null
            val videoId = items.getJSONObject(0).optJSONObject("id")?.optString("videoId")
            videoId?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "fetchYoutubeTrailerId error for '$title'", e)
            null
        }
    }

    /**
     * Gemini AI Spoiler-Free Summary Generator for missing M3U descriptions.
     */
    suspend fun getSpoilerFreeSummary(
        context: android.content.Context,
        title: String
    ): String = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext "Özet bulunamadı."

        val prompt = "Sana adı verilen film veya dizinin konusunu analiz et: '$title'. Bu içerik hakkında kesinlikle sürpriz gelişmeleri (spoiler) açık etmeden; akıcı, merak uyandıran ve maksimum 3-4 cümlelik Türkçe bir özet yaz. Yanıt olarak sadece özet metnini döndür."

        try {
            val result = callGeminiApi(context, prompt)
            if (!result.isNullOrBlank() && !result.contains("şu an oluşturulamadı", ignoreCase = true)) {
                return@withContext result.trim()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error generating spoiler free summary for $title: ${e.message}")
        }
        return@withContext try {
            val localItem = IPTVItem(
                id = 0,
                playlistId = 0,
                name = title,
                cleanedName = title,
                logoUrl = null,
                streamUrl = "",
                category = "SERIES",
                type = "SERIES",
                summary = ""
            )
            generateLocalMovieMetadata(localItem).summary
        } catch (e: Throwable) {
            "Bu içerik için özet bilgisi bulunamadı."
        }
    }

}
