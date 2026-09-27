package com.example.data.model.tmdb

import androidx.compose.runtime.Immutable
import com.example.data.model.IPTVItem
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** TMDB movie/now_playing cevabı. "dates" gibi kullanılmayan alanlar yok sayılır. */
@JsonClass(generateAdapter = true)
data class TmdbNowPlayingResponse(
    val results: List<TmdbNowPlayingMovie> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbNowPlayingMovie(
    val id: Int,
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    val popularity: Double? = null,
    val adult: Boolean? = null
)

/**
 * "Sinemada Bu Hafta" kartı.
 * tmdbItem: TMDB bilgisiyle kurulan öğe (kütüphanede yoksa detayı bununla açılır).
 * libraryItem: aynı film kütüphanede varsa oynatılacak öğe, yoksa null.
 */
@Immutable
data class NowPlayingEntry(
    val tmdbItem: IPTVItem,
    val libraryItem: IPTVItem?,
    val posterUrl: String?
)

/**
 * Listenin hangi ülkenin sinemalarına ait olduğu.
 * code: kullanılan ülke (ISO 3166-1, ör. "DE"); autoCode: cihazdan otomatik bulunan ülke.
 * isAuto: kullanıcı ülke seçmediyse true. isGlobalFallback: o ülke için TMDB'de liste yoksa dünya geneli gösterildi.
 * uiLanguage: uygulama dili ("tr" ya da "en").
 */
@Immutable
data class NowPlayingRegionInfo(
    val code: String,
    val name: String,
    val autoCode: String,
    val autoName: String,
    val isAuto: Boolean,
    val isGlobalFallback: Boolean,
    val uiLanguage: String
)
