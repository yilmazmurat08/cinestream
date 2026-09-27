package com.example.data.api

import com.example.data.model.tmdb.TmdbCreditsResponse
import com.example.data.model.tmdb.TmdbFindResponse
import com.example.data.model.tmdb.TmdbMediaItem
import com.example.data.model.tmdb.TmdbMovieDetails
import com.example.data.model.tmdb.TmdbSearchResponse
import com.example.data.model.tmdb.TmdbTvDetails
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbService {

    // ============================
    // EXTERNAL ID (IMDb) ENDPOINT
    // ============================

    @GET("find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: String,
        @Query("external_source") externalSource: String = "imdb_id",
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbFindResponse

    // ============================
    // FİLM ENDPOINT'LERİ
    // ============================

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("year") year: String? = null,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse<TmdbMediaItem>

    @GET("movie/{id}")
    suspend fun getMovieDetailsWithCredits(
        @Path("id") id: Int,
        @Query("language") language: String = "tr-TR",
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("api_key") apiKey: String
    ): TmdbMovieDetails

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbMovieDetails

    @GET("movie/{id}/credits")
    suspend fun getMovieCredits(
        @Path("id") id: Int,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbCreditsResponse

    // ============================
    // DİZİ (TV) ENDPOINT'LERİ
    // ============================

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("first_air_date_year") year: String? = null,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse<TmdbMediaItem>

    @GET("tv/{id}")
    suspend fun getTvDetailsWithCredits(
        @Path("id") id: Int,
        @Query("language") language: String = "tr-TR",
        @Query("append_to_response") appendToResponse: String = "credits",
        @Query("api_key") apiKey: String
    ): TmdbTvDetails

    @GET("tv/{id}")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbTvDetails

    @GET("tv/{id}/credits")
    suspend fun getTvCredits(
        @Path("id") id: Int,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbCreditsResponse

    // ============================
    // FALLBACK / MULTI / POPULAR
    // ============================

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse<TmdbMediaItem>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "tr-TR"
    ): TmdbSearchResponse<TmdbMediaItem>

    @GET("tv/popular")
    suspend fun getPopularSeries(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "tr-TR"
    ): TmdbSearchResponse<TmdbMediaItem>

    @GET("search/person")
    suspend fun searchPerson(
        @Query("query") query: String,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): com.example.data.model.tmdb.TmdbPersonSearchResponse

    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): com.example.data.model.tmdb.TmdbPersonDetailsDto

    @GET("person/{person_id}/combined_credits")
    suspend fun getPersonCombinedCredits(
        @Path("person_id") personId: Int,
        @Query("language") language: String = "tr-TR",
        @Query("api_key") apiKey: String
    ): com.example.data.model.tmdb.TmdbPersonCombinedCreditsResponse
}

