package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Immutable
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val isXtream: Boolean = false,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val loadedAt: Long = System.currentTimeMillis(),
    val epgUrl: String? = null
) : Serializable

@Immutable
@Entity(
    tableName = "iptv_items",
    indices = [Index(value = ["type"]), Index(value = ["playlistId"])]
)
data class IPTVItem(
    @PrimaryKey val id: Int = 0,
    val playlistId: Int,
    val name: String,
    val cleanedName: String,
    val logoUrl: String?,
    val streamUrl: String,
    val category: String,
    val type: String, // "LIVE", "MOVIE", "SERIES"
    val rating: Double = 0.0,
    val summary: String = "",
    val cast: String = "", // Comma-separated or JSON
    val director: String = "",
    val trailerUrl: String? = null,
    val isFavorite: Boolean = false,
    val season: Int? = null,
    val episode: Int? = null,
    val releaseDate: String = "",
    val genre: String = "",
    val tvgId: String? = null
) : Serializable

@Immutable
@Entity(tableName = "iptv_items_staging")
data class IPTVItemStaging(
    @PrimaryKey val id: Int = 0,
    val playlistId: Int,
    val name: String,
    val cleanedName: String,
    val logoUrl: String?,
    val streamUrl: String,
    val category: String,
    val type: String, // "LIVE", "MOVIE", "SERIES"
    val rating: Double = 0.0,
    val summary: String = "",
    val cast: String = "",
    val director: String = "",
    val trailerUrl: String? = null,
    val isFavorite: Boolean = false,
    val season: Int? = null,
    val episode: Int? = null,
    val releaseDate: String = "",
    val genre: String = "",
    val tvgId: String? = null
) : Serializable

fun generateDeterministicId(name: String, streamUrl: String): Int {
    val input = "${name.trim()}|${streamUrl.trim()}"
    val uuid = java.util.UUID.nameUUIDFromBytes(input.toByteArray(Charsets.UTF_8))
    val hash = (uuid.mostSignificantBits xor uuid.leastSignificantBits).toInt() and 0x7FFFFFFF
    return if (hash == 0) 1 else hash
}

@Immutable
data class IPTVGroup(
    val id: String,
    val name: String,
    val type: String, // "LIVE", "MOVIE", "SERIES"
    val items: List<IPTVItem>
) : Serializable

@Immutable
@Entity(tableName = "continue_watching")
data class ContinueWatching(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val itemName: String,
    val itemType: String,
    val itemLogo: String?,
    val streamUrl: String,
    val category: String,
    val progressSeconds: Long,
    val totalSeconds: Long,
    val lastPlayedAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "tmdb_person_cache")
data class PersonDetailsEntity(
    @PrimaryKey val name: String, // Normalized lowercase query name
    val displayName: String,       // Proper formatted name
    val role: String,
    val biography: String,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val knownFor: String = "",     // Comma-separated list of known items
    val imageUrl: String? = null
) : Serializable

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "ai_recommendation_history")
data class AiRecommendationHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val reply: String,
    val detectedTitle: String = "",
    val matchedItemId: Int? = null,
    val matchedItemName: String? = null,
    val matchedItemLogoUrl: String? = null,
    val matchedItemType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "series_covers")
data class SeriesCoverEntity(
    @PrimaryKey val showTitleKey: String,
    val coverUrl: String
) : Serializable

@Entity(tableName = "xtream_series_catalog")
data class XtreamSeriesCatalogEntity(
    @PrimaryKey val seriesId: Int,
    val name: String,
    val canonicalKey: String,
    val coverUrl: String,
    val backdropUrl: String = "",
    val plot: String = "",
    val rating: Double = 0.0,
    val releaseDate: String = "",
    val genre: String = "",
    val cast: String = "",
    val director: String = "",
    val categoryId: String = ""
) : Serializable

fun IPTVItem.isPlaceholderSummary(): Boolean {
    val s = summary.trim()
    return s.isEmpty() ||
           s == "Özet bilgisi yükleniyor..." ||
           s == "Harika bir sinema deneyimi sunan, derin hikayesi ve görsel şöleniyle öne çıkan popüler bir yapım." ||
           s == "Merak uyandıran olay örgüsü ve sürükleyici karakterleriyle izleyiciyi ekran başına kilitleyen popüler dizi." ||
           s == "Bu harika IPTV sinema yapımı kesintisiz ve akıcı bir şekilde, en yüksek yayın çözünürlüğü standardı ile izlemeniz için hazır." ||
           s == "Merak uyandıran olay örgüsü ve sürükleyici karakterleriyle izleyiciyi ekran başına kilitleyen popüler dizi yapımı." ||
           s == "Harika bir sinema filmi." ||
           s == "Sürükleyici bir dizi yapımı."
}

fun IPTVItem.isPlaceholderCast(): Boolean {
    val c = cast.trim()
    return c.isEmpty() || 
           c == "Ryan Gosling, Emma Stone, Christian Bale" ||
           c == "Popüler Oyuncular" ||
           c == "Belirtilmemiş" ||
           c == "Bilinmiyor"
}

fun IPTVItem.isPlaceholderDirector(): Boolean {
    val d = director.trim()
    return d.isEmpty() || 
           d == "Christopher Nolan" ||
           d == "Bilinmiyor" ||
           d == "Showrunner"
}


