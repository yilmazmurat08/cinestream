package com.example.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.example.data.model.IPTVItem

/**
 * Uygulamada gösterilen puanların tek kaynağı: TMDB. Sağlayıcıların listedeki puanları çoğu zaman uydurma ya da
 * şişirilmiş (ör. TMDB'de 7,3 olan film listede 9,6); bu yüzden kartlarda yalnızca TMDB'den doğrulanmış puan
 * gösterilir, TMDB puanı bilinmeyen yapımda puan yazılmaz.
 *
 * Puanlar TMDB'nin popüler / en yüksek puanlı listelerinden (kütüphanede eşleşenler) ve açılan detaylardan gelir;
 * telefonda saklanır (uygulama yeniden açılınca hemen görünür).
 */
object TmdbRatings {
    private const val PREFS = "tmdb_ratings"

    /** Yapımın anahtarı: katalog dizisi (numarası kanal/film numarasıyla çakışabilir) ayrı tutulur. */
    fun key(item: IPTVItem): String =
        if (item.type == "SERIES" && item.playlistId == 0 && item.streamUrl.isBlank()) "SC:${item.id}" else "${item.type}:${item.id}"

    /** TMDB listesinden oluşturulmuş yapım (kütüphanede değil; puanı TMDB'nin). */
    fun isFromTmdb(item: IPTVItem): Boolean = item.id < 0 && item.playlistId == 0 && item.streamUrl.isBlank()

    fun load(context: Context): Map<String, Double> = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? Float)?.toDouble()?.let { k to it } }
            .toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    fun save(context: Context, ratings: Map<String, Double>) {
        if (ratings.isEmpty()) return
        try {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            ratings.forEach { (k, v) -> editor.putFloat(k, v.toFloat()) }
            editor.apply()
        } catch (e: Exception) {
            // Saklanamazsa puan yalnızca bu oturumda görünür.
        }
    }
}

/** Ekrandaki TMDB puanları (anahtar → puan); MainActivity'de sağlanır. */
val LocalTmdbRatings = compositionLocalOf<Map<String, Double>> { emptyMap() }

/**
 * Kartta gösterilecek puan: TMDB'de doğrulanmışsa o, değilse 0 (puan gösterilmez). Doğrudan TMDB listelerinden gelen
 * yapımların (ör. "Keşfet" önerileri) kendi puanı zaten TMDB puanıdır.
 */
@Composable
fun IPTVItem.displayRating(): Double =
    if (TmdbRatings.isFromTmdb(this)) RatingScale.normalize(rating)
    else LocalTmdbRatings.current[TmdbRatings.key(this)] ?: 0.0
