package com.example.data.repository

import android.content.Context
import com.example.data.api.NetworkModule
import com.example.data.api.TmdbAuth
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.NowPlayingEntry
import com.example.data.model.tmdb.NowPlayingRegionInfo
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonCreditsLookup
import com.example.data.model.tmdb.TmdbNowPlayingMovie
import com.example.data.model.tmdb.TmdbNowPlayingResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Sadece bu özellik için küçük bir Retrofit arayüzü; mevcut TmdbService'e dokunmadan aynı istemciyi kullanır. */
interface TmdbCinemaService {
    @GET("movie/now_playing")
    suspend fun nowPlaying(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "tr-TR",
        @Query("region") region: String? = null,
        @Query("page") page: Int = 1
    ): TmdbNowPlayingResponse
}

/**
 * Ana sayfadaki "Sinemada Bu Hafta" bölümü. Liste kullanıcının bulunduğu ülkenin sinemalarından gelir:
 * kullanıcı bir ülke seçtiyse o, seçmediyse cihazdan otomatik bulunan ülke. TMDB'de o ülke için sinema
 * listesi yoksa dünya geneli listeye düşülür. Filmler oyuncu penceresindeki Türkçe duyarlı başlık
 * eşleştirmesiyle kütüphaneye bağlanır.
 */
object NowPlayingRepository {
    private const val CACHE_MS = 6 * 60 * 60 * 1000L
    private const val PREFS = "now_playing_prefs"
    private const val KEY_REGION = "region_override"

    /** Ülke seçicide listelenen ülkeler. "Otomatik" seçeneği her zaman en üstte ayrıca gösterilir. */
    val PICKER_REGIONS: List<String> = listOf(
        "TR", "DE", "NL", "BE", "FR", "AT", "CH", "GB", "US", "CA",
        "AZ", "CY", "IT", "ES", "SE", "DK", "NO", "AE", "SA", "AU"
    )

    data class FetchResult(val films: List<TmdbNowPlayingMovie>, val region: NowPlayingRegionInfo)

    private data class CacheEntry(val films: List<TmdbNowPlayingMovie>, val at: Long, val globalFallback: Boolean)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val service: TmdbCinemaService by lazy {
        NetworkModule.retrofit.create(TmdbCinemaService::class.java)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Kullanıcının seçtiği ülkeyi kaydeder; null = otomatik. */
    fun setRegionOverride(context: Context, code: String?) {
        val editor = prefs(context).edit()
        if (code.isNullOrBlank()) {
            editor.remove(KEY_REGION)
        } else {
            editor.putString(KEY_REGION, code.uppercase(Locale.ROOT))
        }
        editor.apply()
    }

    /**
     * Kullanıcının bulunduğu ülke. Sıra: mobil şebekenin ülkesi (yurt dışındayken de doğru ülkeyi verir),
     * SIM kartın ülkesi, cihazın sistem bölgesi. Hiçbiri yoksa Türkiye. Uygulama dili ayarı
     * Locale.getDefault()'u sadece "tr"/"en" yaptığı için bölge, sistem ayarından ayrıca okunur.
     */
    fun detectCountry(context: Context): String {
        val telephony = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        } catch (e: Exception) {
            null
        }
        val systemCountry = try {
            android.content.res.Resources.getSystem().configuration.locales[0].country
        } catch (e: Exception) {
            null
        }
        val candidates = listOf(
            try { telephony?.networkCountryIso } catch (e: Exception) { null },
            try { telephony?.simCountryIso } catch (e: Exception) { null },
            systemCountry
        )
        return candidates
            .mapNotNull { it?.trim()?.uppercase(Locale.ROOT) }
            .firstOrNull { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
            ?: "TR"
    }

    /** Ülke adını uygulama dilinde verir: "DE" → "Almanya" / "Germany". */
    fun countryName(code: String, uiLanguage: String): String =
        Locale("", code).getDisplayCountry(Locale(uiLanguage)).ifBlank { code }

    /** Seçili/otomatik ülkede vizyondaki ilk 10 film (ülke ve dil başına 6 saat önbellekli). */
    suspend fun fetch(context: Context): FetchResult = withContext(Dispatchers.IO) {
        val uiLanguage = com.example.util.LocaleHelper.getSavedLanguage(context)
        val tmdbLanguage = if (uiLanguage == "tr") "tr-TR" else "en-US"
        val autoCode = detectCountry(context)
        val override = prefs(context).getString(KEY_REGION, null)?.takeIf { it.length == 2 }
        val region = override ?: autoCode

        fun info(globalFallback: Boolean) = NowPlayingRegionInfo(
            code = region,
            name = countryName(region, uiLanguage),
            autoCode = autoCode,
            autoName = countryName(autoCode, uiLanguage),
            isAuto = override == null,
            isGlobalFallback = globalFallback,
            uiLanguage = uiLanguage
        )

        val cacheKey = "$region|$tmdbLanguage"
        val now = System.currentTimeMillis()
        val cachedEntry = cache[cacheKey]
        if (cachedEntry != null && cachedEntry.films.isNotEmpty() && now - cachedEntry.at < CACHE_MS) {
            return@withContext FetchResult(cachedEntry.films, info(cachedEntry.globalFallback))
        }
        val apiKey = TmdbAuth.resolve()
        if (apiKey.isBlank()) {
            return@withContext FetchResult(cachedEntry?.films.orEmpty(), info(cachedEntry?.globalFallback ?: false))
        }
        try {
            var globalFallback = false
            var films = topTen(service.nowPlaying(apiKey = apiKey, language = tmdbLanguage, region = region).results)
            if (films.isEmpty()) {
                // Bazı ülkeler için TMDB'de sinema tarihi az olabiliyor: dünya geneli listeye düş.
                films = topTen(service.nowPlaying(apiKey = apiKey, language = tmdbLanguage, region = null).results)
                globalFallback = true
            }
            if (films.isNotEmpty()) {
                cache[cacheKey] = CacheEntry(films, now, globalFallback)
                FetchResult(films, info(globalFallback))
            } else {
                FetchResult(cachedEntry?.films.orEmpty(), info(cachedEntry?.globalFallback ?: false))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult(cachedEntry?.films.orEmpty(), info(cachedEntry?.globalFallback ?: false))
        }
    }

    private fun topTen(results: List<TmdbNowPlayingMovie>): List<TmdbNowPlayingMovie> =
        results
            .filter { !it.posterPath.isNullOrBlank() && it.adult != true }
            .distinctBy { it.id } // aynı film iki kez gelirse liste anahtarı tekrarlanıp ekran çökmesin
            .sortedByDescending { it.popularity ?: 0.0 }
            .take(10)

    /** Filmleri kütüphanedeki filmlerle eşleştirir; sıra TMDB popülerlik sırası olarak kalır. */
    fun matchToLibrary(films: List<TmdbNowPlayingMovie>, moviePool: List<IPTVItem>): List<NowPlayingEntry> {
        val credits = films.map { it.toCredit() }
        val result = PersonWorksMatcher.match(
            lookup = PersonCreditsLookup(personId = 0, name = "", profileUrl = null, credits = credits),
            pool = moviePool,
            excludeItemId = Int.MIN_VALUE,
            maxNotInLibrary = 0
        )
        val libraryByKey = HashMap<String, IPTVItem>()
        for (match in result.inLibrary) {
            val key = match.credit?.key ?: continue
            libraryByKey[key] = match.item
        }
        return films.mapIndexed { index, film ->
            NowPlayingEntry(
                tmdbItem = film.toIptvItem(),
                libraryItem = libraryByKey[credits[index].key],
                posterUrl = TMDBRepository.buildImageUrl(film.posterPath, "w342")
            )
        }
    }

    private fun TmdbNowPlayingMovie.toCredit(): PersonCredit = PersonCredit(
        tmdbId = id,
        mediaType = "movie",
        title = (title ?: originalTitle).orEmpty(),
        originalTitle = (originalTitle ?: title).orEmpty(),
        year = releaseDate?.take(4)?.toIntOrNull(),
        posterUrl = TMDBRepository.buildImageUrl(posterPath, "w342"),
        popularity = popularity ?: 0.0
    )

    private fun TmdbNowPlayingMovie.toIptvItem(): IPTVItem {
        val displayName = (title ?: originalTitle ?: "Film").trim()
        return IPTVItem(
            id = -id,
            playlistId = 0,
            name = displayName,
            cleanedName = displayName,
            logoUrl = TMDBRepository.buildImageUrl(posterPath ?: backdropPath, TMDBRepository.SIZE_POSTER),
            streamUrl = "",
            category = "Sinemada",
            type = "MOVIE",
            rating = voteAverage ?: 0.0,
            summary = overview.orEmpty(),
            releaseDate = releaseDate.orEmpty()
        )
    }
}
