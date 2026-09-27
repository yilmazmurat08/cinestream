package com.example.data.model.tmdb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Oyuncu modeli (İsim, canlandırdığı karakter ve profil fotoğrafı tam URL'si)
 */
@JsonClass(generateAdapter = true)
data class CastMember(
    val name: String,
    val character: String? = null,
    val profilePath: String? = null
)

/**
 * TMDb'den dönen zenginleştirilmiş tam içerik sonucu
 */
@JsonClass(generateAdapter = true)
data class TmdbContentResult(
    val tmdbId: Int = 0,
    val title: String,
    val overview: String?,
    val backdropPath: String?,
    val posterPath: String?,
    val castList: List<CastMember> = emptyList(),
    val directorName: String?,
    val directorPhoto: String?,
    val voteAverage: Double? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val voteCount: Int = 0
)

// ==========================================
// TMDb Ham API Yanıt Modelleri (JSON Parsing)
// ==========================================

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Int = 0,
    val name: String = ""
)

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse<T>(
    val results: List<T> = emptyList(),
    @Json(name = "total_results") val totalResults: Int? = null,
    @Json(name = "total_pages") val totalPages: Int? = null,
    val page: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbFindResponse(
    @Json(name = "movie_results") val movieResults: List<TmdbMediaItem> = emptyList(),
    @Json(name = "tv_results") val tvResults: List<TmdbMediaItem> = emptyList(),
    @Json(name = "person_results") val personResults: List<TmdbMediaItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbMediaItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null, // Diziler için başlık 'name' alanında gelir
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val popularity: Double? = null
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetails(
    val id: Int = 0,
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    val genres: List<TmdbGenre>? = emptyList(),
    val credits: TmdbCreditsResponse? = null,
    val runtime: Int? = null,
    val tagline: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetails(
    val id: Int = 0,
    val name: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "created_by") val createdBy: List<TmdbCreatedBy> = emptyList(),
    val genres: List<TmdbGenre>? = emptyList(),
    val credits: TmdbCreditsResponse? = null,
    val tagline: String? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    val seasons: List<TmdbSeasonSummary>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummary(
    val id: Int = 0,
    val name: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    val overview: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCreatedBy(
    val id: Int = 0,
    val name: String = "",
    @Json(name = "profile_path") val profilePath: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsResponse(
    val id: Int = 0,
    val cast: List<TmdbCastItem> = emptyList(),
    val crew: List<TmdbCrewItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCastItem(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCrewItem(
    val id: Int = 0,
    val name: String = "",
    val job: String? = null,
    val department: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null
)
