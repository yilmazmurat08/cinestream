package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.IPTVItem

/**
 * 12 Saatte bir güncellenen öne çıkan film modeli
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
 * 12 Saatte bir M3U'dan rastgele seçilen filmi önbelleğe alan ve yöneten Repository
 */
class FeaturedMovieRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("featured_movie_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TITLE = "featured_title"
        private const val KEY_POSTER = "featured_poster"
        private const val KEY_BACKDROP = "featured_backdrop"
        private const val KEY_OVERVIEW = "featured_overview"
        private const val KEY_RATING = "featured_rating"
        private const val KEY_RELEASE_DATE = "featured_release_date"
        private const val KEY_CATEGORY = "featured_category"
        private const val KEY_STREAM_URL = "featured_stream_url"
        private const val KEY_UPDATE_TIME = "featured_update_time"
        private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L // 12 Saat
    }

    fun saveFeaturedMovie(movie: FeaturedMovie) {
        prefs.edit().apply {
            putString(KEY_TITLE, movie.title)
            putString(KEY_POSTER, movie.posterUrl)
            putString(KEY_BACKDROP, movie.backdropUrl)
            putString(KEY_OVERVIEW, movie.overview)
            putString(KEY_RATING, movie.imdbRating)
            putString(KEY_RELEASE_DATE, movie.releaseDate)
            putString(KEY_CATEGORY, movie.category)
            putString(KEY_STREAM_URL, movie.streamUrl)
            putLong(KEY_UPDATE_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getCachedFeaturedMovie(): FeaturedMovie? {
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        return FeaturedMovie(
            title = title,
            posterUrl = prefs.getString(KEY_POSTER, null),
            backdropUrl = prefs.getString(KEY_BACKDROP, null),
            overview = prefs.getString(KEY_OVERVIEW, null),
            imdbRating = prefs.getString(KEY_RATING, null),
            releaseDate = prefs.getString(KEY_RELEASE_DATE, null),
            category = prefs.getString(KEY_CATEGORY, null),
            streamUrl = prefs.getString(KEY_STREAM_URL, "") ?: ""
        )
    }

    fun shouldUpdate(): Boolean {
        val lastTime = prefs.getLong(KEY_UPDATE_TIME, 0L)
        return (System.currentTimeMillis() - lastTime) > TWELVE_HOURS_MS
    }
}
