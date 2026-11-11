package com.example.data.repository

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.data.api.NetworkModule
import com.example.data.api.TmdbAuth
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonCreditsLookup
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class TmdbAgendaResponse(val results: List<TmdbAgendaItem> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbAgendaItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    val overview: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    val popularity: Double? = null,
    val adult: Boolean? = null,
    @Json(name = "media_type") val mediaType: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeAir(
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTvAirInfo(
    val id: Int,
    @Json(name = "next_episode_to_air") val nextEpisode: TmdbEpisodeAir? = null,
    @Json(name = "last_episode_to_air") val lastEpisode: TmdbEpisodeAir? = null
)

/** Yalnızca "Sinema Gündemi" için küçük bir arayüz; mevcut TmdbService'e ve eşleştirmeye dokunmaz, aynı istemciyi kullanır. */
interface TmdbAgendaService {
    @GET("movie/upcoming")
    suspend fun upcoming(@Query("api_key") apiKey: String, @Query("language") language: String, @Query("region") region: String?): TmdbAgendaResponse

    @GET("trending/all/day")
    suspend fun trending(@Query("api_key") apiKey: String, @Query("language") language: String): TmdbAgendaResponse

    @GET("tv/on_the_air")
    suspend fun onTheAir(@Query("api_key") apiKey: String, @Query("language") language: String): TmdbAgendaResponse

    @GET("tv/{id}")
    suspend fun tvAirInfo(@Path("id") id: Int, @Query("api_key") apiKey: String, @Query("language") language: String): TmdbTvAirInfo
}

enum class AgendaKind { NOW_PLAYING, NEW_SEASON, SEASON_STARTED, TRENDING, UPCOMING }

/** Gündemin bir satırı (TMDB'den gelen ham bilgi). [date] yyyy-MM-dd. */
data class AgendaLine(
    val kind: AgendaKind,
    val tmdbId: Int,
    val isSeries: Boolean,
    val title: String,
    val originalTitle: String,
    val posterPath: String?,
    val date: String?,
    val season: Int?,
    val rating: Double,
    val overview: String
)

/** Ekrandaki satır: [libraryItem] yapım kullanıcının listesindeyse o öğe, yoksa null. */
@Immutable
data class AgendaEntry(val line: AgendaLine, val tmdbItem: IPTVItem, val libraryItem: IPTVItem?, val posterUrl: String?)

/**
 * Ana sayfadaki "Sinema Gündemi": haber yazısı değildir; TMDB'nin doğrulanmış verisinden günlük kısa satırlar
 * (bu hafta vizyonda, yeni sezon, bugün en çok konuşulan, yakında). Günde iki kez yenilenir, son hali cihazda saklanır
 * (internet yokken de görünür). Yetişkin içerik alınmaz.
 */
object CinemaAgendaRepository {
    private const val CACHE_MS = 12 * 60 * 60 * 1000L
    private const val PREFS = "cinema_agenda"
    private const val RECENT_DAYS = 14L

    private val service: TmdbAgendaService by lazy { NetworkModule.retrofit.create(TmdbAgendaService::class.java) }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun daysAgo(days: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L))

    suspend fun fetch(context: Context): List<AgendaLine> = withContext(Dispatchers.IO) {
        val uiLanguage = com.example.util.LocaleHelper.getSavedLanguage(context)
        val language = if (uiLanguage == "tr") "tr-TR" else "en-US"
        val cached = readStored(context, language)
        if (cached != null && System.currentTimeMillis() - cached.first < CACHE_MS && cached.second.isNotEmpty()) return@withContext cached.second
        val apiKey = TmdbAuth.resolve()
        if (apiKey.isBlank()) return@withContext cached?.second.orEmpty()
        try {
            val lines = build(context, apiKey, language)
            if (lines.isNotEmpty()) {
                store(context, language, lines)
                lines
            } else cached?.second.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.second.orEmpty()
        }
    }

    private suspend fun build(context: Context, apiKey: String, language: String): List<AgendaLine> {
        val used = HashSet<String>()
        val out = ArrayList<AgendaLine>()
        fun add(line: AgendaLine?) {
            if (line != null && line.title.isNotBlank() && used.add("${line.isSeries}:${line.tmdbId}")) out += line
        }
        val today = today()

        // 1. Bu hafta vizyonda: "Sinemada Bu Hafta" ile aynı liste ve ülke (ek istek yok, önbellekten).
        val nowPlaying = NowPlayingRepository.fetch(context)
        val region = nowPlaying.region.code.takeIf { !nowPlaying.region.isGlobalFallback }
        nowPlaying.films
            .filter { (it.releaseDate ?: "") >= daysAgo(RECENT_DAYS) && (it.releaseDate ?: "") <= today }
            .maxByOrNull { it.popularity ?: 0.0 }
            ?.let {
                add(AgendaLine(AgendaKind.NOW_PLAYING, it.id, false, (it.title ?: it.originalTitle).orEmpty(), (it.originalTitle ?: it.title).orEmpty(),
                    it.posterPath, it.releaseDate, null, it.voteAverage ?: 0.0, it.overview.orEmpty()))
            }

        // 2. Yeni sezon: yayındaki en popüler dizilerden, sezonu yeni başlayan ya da başlamak üzere olan.
        runCatching {
            val shows = service.onTheAir(apiKey, language).results.filter { ok(it) }.sortedByDescending { it.popularity ?: 0.0 }.take(5)
            for (show in shows) {
                val info = runCatching { service.tvAirInfo(show.id, apiKey, language) }.getOrNull() ?: continue
                val next = info.nextEpisode
                val last = info.lastEpisode
                val line = when {
                    next?.episodeNumber == 1 && (next.seasonNumber ?: 0) > 1 && (next.airDate ?: "") >= today ->
                        show.toLine(AgendaKind.NEW_SEASON, true, next.airDate, next.seasonNumber)
                    last?.episodeNumber == 1 && (last.seasonNumber ?: 0) > 1 && (last.airDate ?: "") >= daysAgo(RECENT_DAYS) ->
                        show.toLine(AgendaKind.SEASON_STARTED, true, last.airDate, last.seasonNumber)
                    else -> null
                }
                if (line != null) {
                    add(line)
                    break
                }
            }
        }

        // 3. Bugün en çok konuşulan (film ya da dizi).
        runCatching {
            service.trending(apiKey, language).results
                .filter { ok(it) && (it.mediaType == "movie" || it.mediaType == "tv") }
                .firstOrNull { "${it.mediaType == "tv"}:${it.id}" !in used }
                ?.let { add(it.toLine(AgendaKind.TRENDING, it.mediaType == "tv", it.releaseDate ?: it.firstAirDate, null)) }
        }

        // 4. Yakında vizyonda.
        runCatching {
            service.upcoming(apiKey, language, region).results
                .filter { ok(it) && (it.releaseDate ?: "") > today }
                .sortedByDescending { it.popularity ?: 0.0 }
                .firstOrNull { "false:${it.id}" !in used }
                ?.let { add(it.toLine(AgendaKind.UPCOMING, false, it.releaseDate, null)) }
        }
        return out
    }

    private fun ok(item: TmdbAgendaItem) = item.adult != true && !item.posterPath.isNullOrBlank()

    private fun TmdbAgendaItem.toLine(kind: AgendaKind, isSeries: Boolean, date: String?, season: Int?) = AgendaLine(
        kind = kind, tmdbId = id, isSeries = isSeries,
        title = (title ?: name ?: originalTitle ?: originalName).orEmpty().trim(),
        originalTitle = (originalTitle ?: originalName ?: title ?: name).orEmpty().trim(),
        posterPath = posterPath, date = date, season = season, rating = voteAverage ?: 0.0, overview = overview.orEmpty()
    )

    /** Satırları kütüphaneyle eşleştirir (oyuncu penceresi ve "Sinemada Bu Hafta" ile aynı Türkçe duyarlı eşleştirme). */
    fun matchToLibrary(lines: List<AgendaLine>, pool: List<IPTVItem>): List<AgendaEntry> {
        val credits = lines.map {
            PersonCredit(
                tmdbId = it.tmdbId, mediaType = if (it.isSeries) "tv" else "movie", title = it.title, originalTitle = it.originalTitle,
                year = it.date?.take(4)?.toIntOrNull()?.takeIf { _ -> !it.isSeries }, posterUrl = TMDBRepository.buildImageUrl(it.posterPath, "w342"), popularity = 0.0
            )
        }
        val byKey = HashMap<String, IPTVItem>()
        runCatching {
            PersonWorksMatcher.match(
                lookup = PersonCreditsLookup(personId = 0, name = "", profileUrl = null, credits = credits),
                pool = pool, excludeItemId = Int.MIN_VALUE, maxNotInLibrary = 0
            ).inLibrary.forEach { m -> m.credit?.key?.let { byKey[it] = m.item } }
        }
        return lines.mapIndexed { i, line ->
            val poster = TMDBRepository.buildImageUrl(line.posterPath, "w342")
            AgendaEntry(
                line = line,
                tmdbItem = IPTVItem(
                    id = -line.tmdbId, playlistId = 0, name = line.title, cleanedName = line.title,
                    logoUrl = TMDBRepository.buildImageUrl(line.posterPath, TMDBRepository.SIZE_POSTER), streamUrl = "",
                    category = "", type = if (line.isSeries) "SERIES" else "MOVIE", rating = line.rating,
                    summary = line.overview, releaseDate = line.date.orEmpty()
                ),
                libraryItem = byKey[credits[i].key],
                posterUrl = poster
            )
        }
    }

    // ---------------- cihazda saklama ----------------

    private fun store(context: Context, language: String, lines: List<AgendaLine>) {
        val array = JSONArray()
        lines.forEach {
            array.put(JSONObject().apply {
                put("k", it.kind.name); put("id", it.tmdbId); put("s", it.isSeries); put("t", it.title); put("o", it.originalTitle)
                put("p", it.posterPath ?: JSONObject.NULL); put("d", it.date ?: JSONObject.NULL); put("n", it.season ?: JSONObject.NULL)
                put("r", it.rating); put("v", it.overview)
            })
        }
        runCatching { prefs(context).edit().putString("lines_$language", array.toString()).putLong("at_$language", System.currentTimeMillis()).apply() }
    }

    private fun readStored(context: Context, language: String): Pair<Long, List<AgendaLine>>? = runCatching {
        val raw = prefs(context).getString("lines_$language", null) ?: return null
        val array = JSONArray(raw)
        val lines = (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val kind = runCatching { AgendaKind.valueOf(o.getString("k")) }.getOrNull() ?: return@mapNotNull null
            AgendaLine(
                kind, o.getInt("id"), o.getBoolean("s"), o.getString("t"), o.getString("o"),
                if (o.isNull("p")) null else o.getString("p"), if (o.isNull("d")) null else o.getString("d"),
                if (o.isNull("n")) null else o.getInt("n"), o.optDouble("r", 0.0), o.optString("v")
            )
        }
        prefs(context).getLong("at_$language", 0L) to lines
    }.getOrNull()

    /** "2026-11-14" → "14 Kasım" (uygulama dilinde). */
    fun displayDate(date: String?, uiLanguage: String): String? = runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date ?: return null) ?: return null
        SimpleDateFormat("d MMMM", Locale(uiLanguage)).format(parsed)
    }.getOrNull()
}
