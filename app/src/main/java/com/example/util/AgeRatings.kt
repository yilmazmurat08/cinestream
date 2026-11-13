package com.example.util

import android.content.Context
import com.example.data.api.NetworkModule
import com.example.data.api.TmdbAuth
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class TmdbReleaseDates(val results: List<TmdbReleaseCountry> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbReleaseCountry(
    @Json(name = "iso_3166_1") val country: String = "",
    @Json(name = "release_dates") val releaseDates: List<TmdbReleaseCert> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseCert(val certification: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbContentRatings(val results: List<TmdbContentRating> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbContentRating(@Json(name = "iso_3166_1") val country: String = "", val rating: String? = null)

/** Yalnızca yaş sınırı için küçük bir arayüz; mevcut TmdbService'e ve eşleştirmeye dokunmaz, aynı istemciyi kullanır. */
interface TmdbAgeService {
    @GET("movie/{id}/release_dates")
    suspend fun movieReleaseDates(@Path("id") id: Int, @Query("api_key") apiKey: String): TmdbReleaseDates

    @GET("tv/{id}/content_ratings")
    suspend fun tvContentRatings(@Path("id") id: Int, @Query("api_key") apiKey: String): TmdbContentRatings
}

/**
 * Yaş sınırı (TMDB'nin ülkeye göre yaş derecelendirmesi). Detayda rozet olarak görünür; ebeveyn kilidi açıkken
 * seçilen yaşın üstündeki yapımlar PIN ister. Önce Türkiye, yoksa ABD, yoksa herhangi bir ülkenin derecelendirmesi
 * kullanılır. Sonuç cihazda saklanır (aynı yapım için tekrar istek atılmaz).
 */
object AgeRatings {
    private const val PREFS = "age_ratings"
    private const val KEY_LIMIT = "__limit"
    private const val UNKNOWN = -1

    /** Ayarda seçilebilen sınırlar (0 = kapalı). Sınır: bu yaşa kadar olanlar serbest. */
    val limitOptions = listOf(0, 7, 13, 16)

    private val _limit = MutableStateFlow(0)
    val limit: StateFlow<Int> = _limit.asStateFlow()
    @Volatile private var loaded = false

    private val service: TmdbAgeService by lazy { NetworkModule.retrofit.create(TmdbAgeService::class.java) }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (loaded) return
        _limit.value = runCatching { prefs(context).getInt(KEY_LIMIT, 0) }.getOrDefault(0)
        loaded = true
    }

    fun setLimit(context: Context, years: Int) {
        _limit.value = years
        prefs(context).edit().putInt(KEY_LIMIT, years).apply()
        loaded = true
    }

    /** Derecelendirme metnini yaşa çevirir ("13+", "PG-13", "TV-MA", "Genel İzleyici" …). Bilinmiyorsa null. */
    fun parse(raw: String?): Int? {
        val s = raw?.trim()?.uppercase(java.util.Locale.ROOT).orEmpty()
        if (s.isEmpty()) return null
        when (s) {
            "G", "TV-Y", "TV-G", "U", "GENEL İZLEYICI", "GENEL İZLEYİCİ", "GENEL IZLEYICI", "ALL", "AL", "0" -> return 0
            "PG", "TV-Y7", "TV-Y7-FV", "TV-PG" -> return 7
            "PG-13", "TV-14" -> return 13
            "R" -> return 17
            "NC-17", "TV-MA", "X", "XXX" -> return 18
        }
        return Regex("(\\d{1,2})").find(s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..21 }
    }

    /** Ülkelere göre derecelendirmelerden seçim: TR, sonra US, sonra ilk anlaşılır olan. */
    fun pick(byCountry: Map<String, String?>): Int? {
        parse(byCountry["TR"])?.let { return it }
        parse(byCountry["US"])?.let { return it }
        return byCountry.values.firstNotNullOfOrNull { parse(it) }
    }

    /** Sınır açıkken bu yaş PIN ister mi? (Yaş bilinmiyorsa istemez.) */
    fun isBlocked(age: Int?, limit: Int): Boolean = limit > 0 && age != null && age > limit

    fun cached(context: Context, isSeries: Boolean, tmdbId: Int): Int? {
        val v = runCatching { prefs(context).getInt(key(isSeries, tmdbId), Int.MIN_VALUE) }.getOrDefault(Int.MIN_VALUE)
        return if (v == Int.MIN_VALUE || v == UNKNOWN) null else v
    }

    private fun key(isSeries: Boolean, tmdbId: Int) = (if (isSeries) "T:" else "M:") + tmdbId

    /** TMDB'den yaş (önbellekli). İnternet ya da anahtar yoksa null. */
    suspend fun fetch(context: Context, isSeries: Boolean, tmdbId: Int): Int? = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext null
        val p = prefs(context)
        val k = key(isSeries, tmdbId)
        if (p.contains(k)) return@withContext p.getInt(k, UNKNOWN).takeIf { it != UNKNOWN }
        val apiKey = TmdbAuth.resolve()
        if (apiKey.isBlank()) return@withContext null
        try {
            val age = if (isSeries) {
                pick(service.tvContentRatings(tmdbId, apiKey).results.associate { it.country to it.rating })
            } else {
                pick(service.movieReleaseDates(tmdbId, apiKey).results.associate { c ->
                    c.country to c.releaseDates.firstOrNull { !it.certification.isNullOrBlank() }?.certification
                })
            }
            p.edit().putInt(k, age ?: UNKNOWN).apply()
            age
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
