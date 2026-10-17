package com.example.util

import android.content.Context
import androidx.core.content.edit
import com.example.data.model.tmdb.TmdbVideo
import java.util.Locale

/** Bir kaynaktan fragman arama sonucu: bulundu, yok (kaynak cevap verdi) ya da ulaşılamadı. */
sealed class TrailerLookup {
    data class Found(val videoId: String) : TrailerLookup()
    object NotFound : TrailerLookup()
    object Failed : TrailerLookup()
}

/**
 * Linkte fragmanı olmayan film/dizi için fragman bulur:
 * 1) TMDB'deki resmî YouTube fragmanı (doğru yapım, kota sorunu yok),
 * 2) yoksa YouTube araması (ad + yıl).
 * Sonuç cihazda saklanır; aynı yapım için tekrar istek atılmaz. "Bulunamadı" birkaç gün saklanır (yeni fragman
 * çıkabilir); kaynaklardan biri ulaşılamazsa saklanmaz, bir dahaki açılışta yeniden denenir.
 */
class TrailerFinder(
    context: Context,
    private val tmdb: suspend (title: String, year: String?, isMovie: Boolean) -> TrailerLookup,
    private val youtube: suspend (title: String, year: String?) -> TrailerLookup,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun find(title: String, year: String?, isMovie: Boolean): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        val cleanYear = year?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val key = cacheKey(cleanTitle, cleanYear, isMovie)

        prefs.getString(key, null)?.let { cached ->
            when {
                cached.startsWith(FOUND) -> return cached.removePrefix(FOUND)
                cached.startsWith(NONE) -> {
                    val at = cached.removePrefix(NONE).toLongOrNull() ?: 0L
                    if (now() - at < NOT_FOUND_TTL_MS) return null
                }
            }
        }

        val fromTmdb = runCatching { tmdb(cleanTitle, cleanYear, isMovie) }.getOrDefault(TrailerLookup.Failed)
        if (fromTmdb is TrailerLookup.Found) return remember(key, fromTmdb.videoId)

        val fromYoutube = runCatching { youtube(cleanTitle, cleanYear) }.getOrDefault(TrailerLookup.Failed)
        if (fromYoutube is TrailerLookup.Found) return remember(key, fromYoutube.videoId)

        if (fromTmdb == TrailerLookup.NotFound && fromYoutube == TrailerLookup.NotFound) {
            prefs.edit { putString(key, NONE + now()) }
        }
        return null
    }

    private fun remember(key: String, videoId: String): String {
        prefs.edit { putString(key, FOUND + videoId) }
        return videoId
    }

    companion object {
        const val PREFS = "trailer_cache"
        private const val FOUND = "id:"
        private const val NONE = "none:"
        const val NOT_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000

        internal fun cacheKey(title: String, year: String?, isMovie: Boolean): String =
            (if (isMovie) "MOVIE" else "SERIES") + "|" + title.lowercase(Locale.ROOT) + "|" + (year ?: "")

        /**
         * TMDB videolarından en uygun fragman: yalnızca YouTube; önce "Trailer", sonra "Teaser" (klip, kamera arkası
         * vb. alınmaz); resmî olan önde; dil sırası Türkçe, İngilizce, diğerleri.
         */
        fun pickTrailerKey(videos: List<TmdbVideo>): String? = videos
            .filter { it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank() }
            .filter { it.type.equals("Trailer", ignoreCase = true) || it.type.equals("Teaser", ignoreCase = true) }
            .sortedWith(
                compareBy<TmdbVideo> { if (it.type.equals("Trailer", ignoreCase = true)) 0 else 1 }
                    .thenBy { if (it.official) 0 else 1 }
                    .thenBy {
                        when (it.language?.lowercase(Locale.ROOT)) {
                            "tr" -> 0
                            "en" -> 1
                            else -> 2
                        }
                    }
            )
            .firstOrNull()
            ?.key
    }
}
