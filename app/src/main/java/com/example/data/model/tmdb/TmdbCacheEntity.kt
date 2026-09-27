package com.example.data.model.tmdb

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * Room Database Entity for persistent TMDB metadata caching.
 * Prevents redundant TMDB API calls across app sessions with a 30-day TTL.
 */
@Entity(
    tableName = "tmdb_cache",
    indices = [Index(value = ["sourceKey"], unique = true)]
)
data class TmdbCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceKey: String, // e.g. "MOVIE|avatar|2009", "SERIES|the last of us|2023", "IMDB|tt1234567"
    val normalizedTitle: String,
    val year: String? = null,
    val mediaType: String, // "MOVIE" or "SERIES" or "TV"
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double = 0.0,
    val releaseDate: String = "",
    val genresJson: String = "", // Comma-separated or JSON list of genres
    val castJson: String = "",   // JSON or serialized list of CastMembers
    val directorName: String? = null,
    val directorPhoto: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable
