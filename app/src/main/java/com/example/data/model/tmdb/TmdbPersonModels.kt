package com.example.data.model.tmdb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbPersonSearchResponse(
    val results: List<TmdbPersonSearchResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbPersonSearchResult(
    val id: Int,
    val name: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "known_for_department") val knownForDepartment: String? = null,
    val popularity: Double? = null
)

@JsonClass(generateAdapter = true)
data class TmdbPersonDetailsDto(
    val id: Int,
    val name: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    @Json(name = "place_of_birth") val placeOfBirth: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "known_for_department") val knownForDepartment: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbPersonCombinedCreditsResponse(
    val cast: List<TmdbCreditItem> = emptyList(),
    val crew: List<TmdbCreditItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCreditItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    val popularity: Double? = null,
    val character: String? = null,
    val job: String? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null
) {
    /** tr-TR isteğinde Türkçe başlık (film: title, dizi: name). */
    val displayTitle: String get() = title ?: name ?: ""

    /** Yapımın orijinal adı; IPTV listeleri çoğu zaman bunu kullanır. */
    val displayOriginalTitle: String get() = originalTitle ?: originalName ?: ""

    val yearInt: Int? get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
}
