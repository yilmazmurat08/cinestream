package com.example.util

import android.content.Context
import androidx.core.content.edit
import com.example.data.model.tmdb.TmdbVideo
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * çıkabilir); kaynaklardan biri ulaşılamazsa saklanmaz, bir dahaki açılışta yeniden denenir. Kayıt sayısı sınırlıdır
 * ([MAX_ENTRIES]); dosya şişmez.
 */
class TrailerFinder(
    context: Context,
    private val tmdb: suspend (title: String, year: String?, isMovie: Boolean) -> TrailerLookup,
    private val youtube: suspend (title: String, year: String?) -> TrailerLookup,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Dosya okuma/yazma ana iş parçacığında yapılmaz (kart açılırken takılma olmasın).
    suspend fun find(title: String, year: String?, isMovie: Boolean): String? = withContext(Dispatchers.IO) { findOnIo(title, year, isMovie) }

    private suspend fun findOnIo(title: String, year: String?, isMovie: Boolean): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        val cleanYear = year?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        val key = cacheKey(cleanTitle, cleanYear, isMovie)

        when (val cached = runCatching { prefs.getString(key, null) }.getOrNull()?.let(::parse)) {
            is Entry.Found -> return cached.videoId
            is Entry.None -> if (now() - cached.at < NOT_FOUND_TTL_MS) return null
            null -> {}
        }

        val fromTmdb = runCatching { tmdb(cleanTitle, cleanYear, isMovie) }.getOrDefault(TrailerLookup.Failed)
        if (fromTmdb is TrailerLookup.Found) return remember(key, fromTmdb.videoId)

        val fromYoutube = runCatching { youtube(cleanTitle, cleanYear) }.getOrDefault(TrailerLookup.Failed)
        if (fromYoutube is TrailerLookup.Found) return remember(key, fromYoutube.videoId)

        if (fromTmdb == TrailerLookup.NotFound && fromYoutube == TrailerLookup.NotFound) {
            write(key, NONE + now())
        }
        return null
    }

    private fun remember(key: String, videoId: String): String {
        write(key, FOUND + now() + ":" + videoId)
        return videoId
    }

    /**
     * Kayıt yazılır; kayıt sayısı [MAX_ENTRIES]'ı aşarsa en eskiler silinip [TRIM_TO] kayda inilir. Böylece dosya
     * en fazla birkaç yüz KB olur, ne kadar kart açılırsa açılsın şişmez. Yazılamazsa (disk dolu vb.) yalnızca
     * saklanmaz; fragman yine oynar.
     */
    private fun write(key: String, value: String) {
        runCatching {
            val all = prefs.all
            prefs.edit {
                putString(key, value)
                if (all.size + 1 > MAX_ENTRIES) {
                    all.entries
                        .filter { it.key != key }
                        .sortedBy { (it.value as? String)?.let(::parse)?.at ?: 0L }
                        .take(all.size + 1 - TRIM_TO)
                        .forEach { remove(it.key) }
                }
            }
        }
    }

    private sealed class Entry(val at: Long) {
        class Found(at: Long, val videoId: String) : Entry(at)
        class None(at: Long) : Entry(at)
    }

    /** "id:<zaman>:<video>" ya da "none:<zaman>". Bozuk kayıt yok sayılır. */
    private fun parse(value: String): Entry? = when {
        value.startsWith(FOUND) -> {
            val rest = value.removePrefix(FOUND)
            val at = rest.substringBefore(':', "").toLongOrNull()
            val id = if (at != null) rest.substringAfter(':') else rest
            id.takeIf { it.isNotBlank() }?.let { Entry.Found(at ?: 0L, it) }
        }
        value.startsWith(NONE) -> value.removePrefix(NONE).toLongOrNull()?.let { Entry.None(it) }
        else -> null
    }

    companion object {
        const val PREFS = "trailer_cache"
        private const val FOUND = "id:"
        private const val NONE = "none:"
        const val NOT_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000

        /** Saklanan en fazla kayıt (~100 bayt/kayıt → en fazla ~200 KB). */
        const val MAX_ENTRIES = 2_000
        const val TRIM_TO = 1_500

        internal fun cacheKey(title: String, year: String?, isMovie: Boolean): String =
            (if (isMovie) "MOVIE" else "SERIES") + "|" + title.lowercase(Locale.ROOT).take(120) + "|" + (year ?: "")

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
