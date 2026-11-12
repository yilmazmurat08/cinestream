package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.example.data.api.NetworkModule
import com.example.data.api.TmdbAuth
import com.example.util.TitleNormalizer
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.util.Locale

/** Kullanıcının seçtiği kapak: [original] listedeki eski adres (yoksa boş), [cover] yeni adres ya da cihazdaki dosya. */
data class CoverOverride(val original: String, val cover: String)

/**
 * "Kapak Görselini Değiştir": kullanıcının bir film ya da dizi için seçtiği kapak cihazda saklanır.
 * Liste, veritabanı ve TMDB eşleştirmesi değişmez; yalnızca görsel gösterilirken yeni kapak kullanılır.
 * Anahtar yapımın türü ve adıdır, bu yüzden liste yenilense de seçim kalır.
 */
object CoverOverrideStore {
    private const val PREFS = "cover_overrides"
    private const val KEY_DATA = "data"
    private const val DIR = "covers"
    private const val MAX_SIDE = 900

    private val _overrides = MutableStateFlow<Map<String, CoverOverride>>(emptyMap())
    val overrides: StateFlow<Map<String, CoverOverride>> = _overrides.asStateFlow()

    /** Eski adres -> yeni kapak; görsel yükleyici bununla her ekranda yeni kapağı gösterir. */
    @Volatile private var byOriginal: Map<String, String> = emptyMap()
    @Volatile private var loaded = false

    /** Her değişiklikte artar; görseller bunu okuyarak yeni kapağı hemen gösterir. */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun keyFor(isSeries: Boolean, title: String): String =
        (if (isSeries) "SERIES|" else "MOVIE|") + title.trim().lowercase(Locale.ROOT)

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val map = LinkedHashMap<String, CoverOverride>()
            try {
                val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, null)
                if (!raw.isNullOrBlank()) {
                    val json = JSONObject(raw)
                    for (key in json.keys()) {
                        val o = json.optJSONObject(key) ?: continue
                        val cover = o.optString("c")
                        if (cover.isNotBlank()) map[key] = CoverOverride(o.optString("o"), cover)
                    }
                }
            } catch (e: Exception) {
                // Bozuk kayıt: seçimler yok sayılır, uygulama çalışmaya devam eder.
            }
            publish(map)
            loaded = true
        }
    }

    private fun publish(map: Map<String, CoverOverride>) {
        _overrides.value = map
        byOriginal = map.values.filter { it.original.isNotBlank() }.associate { it.original to it.cover }
        version++
    }

    private fun persist(context: Context, map: Map<String, CoverOverride>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, JSONObject().put("o", v.original).put("c", v.cover)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DATA, json.toString()).apply()
    }

    fun coverFor(key: String): String? = _overrides.value[key]?.cover

    /** Görsel yükleyici için: bu adresin yerine kullanıcı başka kapak seçtiyse onu verir. */
    fun replacementFor(url: String): String? = byOriginal[url]

    fun set(context: Context, key: String, originalUrl: String?, cover: String) {
        init(context)
        synchronized(this) {
            val map = LinkedHashMap(_overrides.value)
            val previous = map[key]
            // İlk seçimdeki eski adres korunur (ikinci değişiklikte "eski" artık bizim kapağımız olmasın).
            val original = previous?.original?.takeIf { it.isNotBlank() } ?: originalUrl.orEmpty()
            map[key] = CoverOverride(original, cover)
            publish(map)
            persist(context, map)
            if (previous != null && previous.cover != cover) deleteLocal(context, previous.cover)
        }
    }

    fun clear(context: Context, key: String) {
        init(context)
        synchronized(this) {
            val map = LinkedHashMap(_overrides.value)
            val previous = map.remove(key) ?: return
            publish(map)
            persist(context, map)
            deleteLocal(context, previous.cover)
        }
    }

    private fun deleteLocal(context: Context, cover: String) {
        val dir = File(context.filesDir, DIR)
        val file = File(cover)
        // Yalnızca kendi klasörümüzdeki kapak dosyası silinir.
        if (file.parentFile == dir) runCatching { file.delete() }
    }

    /** Galeriden seçilen resmi küçültüp uygulamanın kendi klasörüne kopyalar; dosya yolunu döndürür. */
    suspend fun importFromGallery(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "cover_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            file.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

/** Kullanıcı kapak değiştirdiyse eski adres yerine yeni kapağı yükler (ana sayfa, listeler, TV: her yer). */
class CoverOverrideInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val replacement = (data as? String)?.let { CoverOverrideStore.replacementFor(it) }
            ?: return chain.proceed(chain.request)
        return chain.proceed(chain.request.newBuilder().data(replacement).build())
    }
}

@JsonClass(generateAdapter = true)
data class TmdbCoverSearchResponse(val results: List<TmdbCoverSearchItem> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbCoverSearchItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    val adult: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCoverImages(val posters: List<TmdbCoverImage> = emptyList())

@JsonClass(generateAdapter = true)
data class TmdbCoverImage(@Json(name = "file_path") val filePath: String? = null)

/** Yalnızca kapak önerileri için küçük bir arayüz; mevcut TmdbService'e ve eşleştirmeye dokunmaz, aynı istemciyi kullanır. */
interface TmdbCoverService {
    @GET("search/movie")
    suspend fun searchMovie(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("language") language: String, @Query("include_adult") adult: Boolean = false): TmdbCoverSearchResponse

    @GET("search/tv")
    suspend fun searchTv(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("language") language: String, @Query("include_adult") adult: Boolean = false): TmdbCoverSearchResponse

    @GET("movie/{id}/images")
    suspend fun movieImages(@Path("id") id: Int, @Query("api_key") apiKey: String, @Query("include_image_language") languages: String): TmdbCoverImages

    @GET("tv/{id}/images")
    suspend fun tvImages(@Path("id") id: Int, @Query("api_key") apiKey: String, @Query("include_image_language") languages: String): TmdbCoverImages
}

/** Seçilebilecek bir kapak: [label] hangi yapıma ait olduğunu söyler (ad ve yıl). */
data class CoverSuggestion(val thumbUrl: String, val coverUrl: String, val label: String)

object CoverSuggestions {
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    private const val MAX_TITLES = 6
    private const val MAX_EXTRA = 8

    private val service: TmdbCoverService by lazy { NetworkModule.retrofit.create(TmdbCoverService::class.java) }

    /** Adla aranan yapımların kapakları, ardından en yakın yapımın öteki afişleri. İnternet ya da anahtar yoksa boş liste. */
    suspend fun fetch(context: Context, title: String, isSeries: Boolean): List<CoverSuggestion> = withContext(Dispatchers.IO) {
        val apiKey = TmdbAuth.resolve()
        val query = TitleNormalizer.cleanTitleForTMDB(title).first.ifBlank { title }.trim()
        if (apiKey.isBlank() || query.isBlank()) return@withContext emptyList()
        val language = if (com.example.util.LocaleHelper.getSavedLanguage(context) == "tr") "tr-TR" else "en-US"
        try {
            val found = (if (isSeries) service.searchTv(apiKey, query, language) else service.searchMovie(apiKey, query, language)).results
                .filter { it.adult != true }
            val extra = found.firstOrNull()?.let { top ->
                runCatching {
                    (if (isSeries) service.tvImages(top.id, apiKey, "tr,en,null") else service.movieImages(top.id, apiKey, "tr,en,null")).posters
                }.getOrDefault(emptyList())
            }.orEmpty()
            build(found, extra)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun build(found: List<TmdbCoverSearchItem>, extraPosters: List<TmdbCoverImage>): List<CoverSuggestion> {
        val seen = HashSet<String>()
        val out = ArrayList<CoverSuggestion>()
        fun labelOf(item: TmdbCoverSearchItem): String {
            val name = (item.title ?: item.name).orEmpty()
            val year = (item.releaseDate ?: item.firstAirDate).orEmpty().take(4)
            return if (year.length == 4) "$name ($year)" else name
        }
        fun add(path: String?, label: String) {
            if (path.isNullOrBlank() || !seen.add(path)) return
            out += CoverSuggestion("${IMAGE_BASE}w342$path", "${IMAGE_BASE}w780$path", label)
        }
        found.take(MAX_TITLES).forEach { add(it.posterPath, labelOf(it)) }
        val top = found.firstOrNull()
        if (top != null) extraPosters.take(MAX_EXTRA + 1).forEach { if (out.size < MAX_TITLES + MAX_EXTRA) add(it.filePath, labelOf(top)) }
        return out
    }
}
