package com.example.data.repository

import android.content.Context
import com.example.data.model.IPTVItem

/**
 * Ana sayfadaki "Öne Çıkan" alanında gösterilen film.
 */
data class FeaturedMovie(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    val overview: String?,
    val imdbRating: String? = null,
    val releaseDate: String? = null,
    val category: String? = null,
    val streamUrl: String = "",
    val iptvItem: IPTVItem? = null
)

/**
 * Öne Çıkan içerikleri artık telefona kaydedilmez; uygulamaya her girişte yeniden seçilir.
 * Eski sürümlerin 12 saatlik kaydı (featured_movie_prefs) burada silinir.
 */
object FeaturedMovieRepository {
    private const val LEGACY_PREFS = "featured_movie_prefs"

    fun clearLegacyCache(context: Context) {
        try {
            context.deleteSharedPreferences(LEGACY_PREFS)
        } catch (e: Exception) {
            // Silinemezse sadece küçük, kullanılmayan bir dosya kalır.
        }
    }
}
