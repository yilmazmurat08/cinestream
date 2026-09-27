package com.example.data.repository

import com.example.data.db.IPTVDao
import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.util.AdultContentFilter

/** Hero görselinin türü: yatay arka plan, dikey poster (kendi oranında) veya kanal logosu. */
enum class ShelfImageKind { WIDE, POSTER, LOGO }

/**
 * TV ana sayfasının üst alanında (Top Shelf) gösterilen içerik. Görsel ve yazılar her zaman aynı içeriğe aittir.
 * Değeri olmayan alanlar null'dır ve gösterilmez (uydurma değer yok).
 */
data class ShelfContent(
    val key: String,
    val title: String,
    /** Sağlayıcı listesindeki puan; yoksa null. */
    val rating: Double?,
    val year: String?,
    val genre: String?,
    val overview: String?,
    val imageUrl: String?,
    val imageKind: ShelfImageKind,
    /** Canlı kanalda EPG'deki şu anki program. */
    val nowPlaying: String? = null,
    val item: IPTVItem? = null
)

/**
 * TV ana sayfası hero içeriği. Rastgele seçim veritabanında sorguyla yapılır (liste belleğe alınmaz); önce
 * görseli ve konusu kayıtlı içerikler seçilir. Konu veya yatay görsel eksikse yalnızca gösterilecek içerik için
 * önbellekli TMDB detayına bakılır. Yetişkin içerik asla seçilmez.
 */
class HeroShelfRepository(
    private val dao: IPTVDao,
    private val tmdb: TMDBRepository,
    private val tmdbApiKey: () -> String
) {
    private fun allowed(item: IPTVItem) = !AdultContentFilter.isAdult(item.category, item.cleanedName.ifBlank { item.name })

    suspend fun randomMovie(): ShelfContent? =
        dao.randomItemsWithImage("MOVIE", 10).firstOrNull { allowed(it) }?.let { fromItem(it) }

    suspend fun randomChannel(nowPlaying: (IPTVItem) -> String?): ShelfContent? =
        dao.randomItemsWithImage("LIVE", 12).firstOrNull { allowed(it) }?.let { channel ->
            ShelfContent(
                key = "live-${channel.id}",
                title = channel.cleanedName.ifBlank { channel.name },
                rating = null,
                year = null,
                genre = channel.category.takeIf { it.isNotBlank() },
                overview = null,
                imageUrl = channel.logoUrl,
                imageKind = ShelfImageKind.LOGO,
                nowPlaying = nowPlaying(channel),
                item = channel
            )
        }

    suspend fun randomSeries(): ShelfContent? {
        dao.randomSeriesCatalog(10).firstOrNull { !AdultContentFilter.isAdult(it.categoryId, it.name) }?.let { return fromSeries(it) }
        // Xtream kataloğu yoksa M3U dizi bölümlerinden bir dizi (bölüm adı yerine dizi adı gösterilir).
        val episode = dao.randomItemsWithImage("SERIES", 10).firstOrNull { allowed(it) } ?: return null
        val showTitle = (SeriesParser.parseEpisodeInfo(episode.cleanedName) ?: SeriesParser.parseEpisodeInfo(episode.name))
            ?.showTitle?.takeIf { it.isNotBlank() }
        return fromItem(episode, titleOverride = showTitle, type = "SERIES")
    }

    suspend fun fromItem(item: IPTVItem, titleOverride: String? = null, type: String = item.type): ShelfContent {
        val title = titleOverride ?: item.cleanedName.ifBlank { item.name }
        val needsDetails = item.summary.isBlank() || item.type != "LIVE"
        val details = if (needsDetails && (type == "MOVIE" || type == "SERIES")) {
            runCatching { tmdb.fetchDetailsByTitle(title, type = type, tmdbApiKey = tmdbApiKey()) }.getOrNull()
        } else {
            null
        }
        val overview = item.summary.takeIf { it.isNotBlank() && it != "Açıklama bulunamadı." }
            ?: details?.overview?.takeIf { it.isNotBlank() && it != "Açıklama bulunamadı." }
        val wide = details?.backdropUrl?.takeIf { it.isNotBlank() }
        return ShelfContent(
            key = "${type.lowercase()}-${item.id}",
            title = title,
            rating = item.rating.takeIf { it > 0.0 },
            year = yearOf(item.releaseDate) ?: yearOf(details?.releaseDate),
            genre = item.genre.takeIf { it.isNotBlank() } ?: details?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
            overview = overview,
            imageUrl = wide ?: item.logoUrl,
            imageKind = if (wide != null) ShelfImageKind.WIDE else ShelfImageKind.POSTER,
            item = item
        )
    }

    private suspend fun fromSeries(show: XtreamSeriesCatalogEntity): ShelfContent {
        val details = if (show.plot.isBlank() || show.backdropUrl.isBlank()) {
            runCatching { tmdb.fetchDetailsByTitle(show.name, type = "SERIES", tmdbApiKey = tmdbApiKey()) }.getOrNull()
        } else {
            null
        }
        val wide = show.backdropUrl.takeIf { it.isNotBlank() } ?: details?.backdropUrl?.takeIf { it.isNotBlank() }
        return ShelfContent(
            key = "series-${show.seriesId}",
            title = show.name,
            rating = show.rating.takeIf { it > 0.0 },
            year = yearOf(show.releaseDate) ?: yearOf(details?.releaseDate),
            genre = show.genre.takeIf { it.isNotBlank() } ?: details?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotBlank() },
            overview = show.plot.takeIf { it.isNotBlank() } ?: details?.overview?.takeIf { it.isNotBlank() && it != "Açıklama bulunamadı." },
            imageUrl = wide ?: show.coverUrl,
            imageKind = if (wide != null) ShelfImageKind.WIDE else ShelfImageKind.POSTER
        )
    }

    private fun yearOf(date: String?): String? =
        date?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
}
