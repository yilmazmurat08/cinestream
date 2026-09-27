package com.example.data.db

/** Bir öğenin sonradan indirilen detay bilgileri (toplu yazma için). */
data class ItemMetadataUpdate(
    val itemId: Int,
    val summary: String,
    val cast: String,
    val director: String,
    val rating: Double,
    val logoUrl: String?,
    val trailerUrl: String?,
    val releaseDate: String,
    val genre: String
)
