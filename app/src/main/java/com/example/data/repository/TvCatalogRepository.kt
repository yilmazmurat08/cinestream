package com.example.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.data.db.IPTVDao
import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.model.TvShow
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.model.tmdb.PersonCreditsLookup
import com.example.data.model.tmdb.PersonWorkMatch
import com.example.data.model.tmdb.PersonWorksResult
import com.example.util.AdultContentFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** TV kategori listesindeki bir satır. [key] veritabanındaki değer, [name] ekranda görünen ad. */
data class TvCategory(
    val key: String,
    val name: String,
    val count: Int,
    val isAdult: Boolean,
    /** Öne Çıkanlar / Top 10 gibi özel bölümler (sayfalama yok, sabit küçük liste). */
    val special: TvSpecialSection? = null
)

enum class TvSpecialSection { TOP10, TOP_RATED }

/** Izgaradaki bir poster: film, dizi (katalog veya bölümlerden gruplanmış). */
data class TvPoster(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val rating: Double,
    val item: IPTVItem,
    /** Xtream kataloğundaki dizi (açılınca bölümler canlı çekilir). */
    val catalogShow: TvShow? = null
)

/**
 * TV modu Filmler/Diziler verisi. Telefonla aynı kaynaklar ve aynı kurallar (kategori sırası, yetişkin süzmesi,
 * Top 10 = puana göre ilk 10 uygun öğe, En yüksek puanlılar = 8.0+ yoksa 7.5+), fakat her şey veritabanından
 * sayfa sayfa okunur; kütüphanenin tamamı belleğe alınmaz.
 */
class TvCatalogRepository(private val dao: IPTVDao) {

    suspend fun hasSeriesCatalog(): Boolean = withContext(Dispatchers.IO) { dao.seriesCatalogCount() > 0 }

    /** Kategoriler: en üstte özel bölümler, sonra telefondaki sırayla sağlayıcı kategorileri. */
    suspend fun categories(type: String, rank: (String) -> Pair<Int, String>): List<TvCategory> = withContext(Dispatchers.IO) {
        val catalog = type == "SERIES" && dao.seriesCatalogCount() > 0
        val rows = if (catalog) {
            dao.seriesCatalogCategoryCounts()
                .groupBy { it.name.ifBlank { OTHER } }
                .map { (name, list) -> TvCategory(key = list.first().name, name = name, count = list.sumOf { it.count }, isAdult = AdultContentFilter.isAdult(name)) }
                .sortedBy { it.name } // telefon: toSortedMap()
        } else {
            dao.categoryCounts(type)
                .map { TvCategory(key = it.name, name = it.name.ifBlank { OTHER }, count = it.count, isAdult = AdultContentFilter.isAdult(it.name)) }
                .sortedWith(compareBy({ rank(it.name).first }, { rank(it.name).second }))
        }
        val specials = buildList {
            if (type == "MOVIE" || catalog) add(TvCategory(SPECIAL_TOP10, "", 0, false, TvSpecialSection.TOP10))
            add(TvCategory(SPECIAL_TOP_RATED, "", 0, false, TvSpecialSection.TOP_RATED))
        }
        specials + rows
    }

    /** Özel bölüm içeriği (küçük sabit liste; yetişkin içerik asla yok). */
    suspend fun special(type: String, section: TvSpecialSection): List<TvPoster> = withContext(Dispatchers.IO) {
        val catalog = type == "SERIES" && dao.seriesCatalogCount() > 0
        when (section) {
            TvSpecialSection.TOP10 -> if (catalog) {
                dao.topRatedSeriesCatalog(40).asSequence().filterNot { adult(it) }.take(10).map { catalogPoster(it) }.toList()
            } else {
                dao.topRatedByType(type, 40).asSequence().filterNot { adult(it) }.take(10).map { itemPoster(it) }.toList()
            }
            TvSpecialSection.TOP_RATED -> if (catalog) {
                val high = dao.seriesCatalogRatedAtLeast(8.0, 120).filterNot { adult(it) }
                (high.ifEmpty { dao.seriesCatalogRatedAtLeast(7.5, 120).filterNot { adult(it) } }).map { catalogPoster(it) }
            } else if (type == "SERIES") {
                // Bölümlerden: dizi başına tek poster
                val high = dao.ratedAtLeast(type, 8.0, 400).filterNot { adult(it) }
                showsOf(high.ifEmpty { dao.ratedAtLeast(type, 7.5, 400).filterNot { adult(it) } }, emptyMap())
                    .sortedByDescending { it.rating }
            } else {
                val high = dao.ratedAtLeast(type, 8.0, 120).filterNot { adult(it) }
                (high.ifEmpty { dao.ratedAtLeast(type, 7.5, 120).filterNot { adult(it) } }).map { itemPoster(it) }
            }
        }
    }

    /** Film kategorisinin bir sayfası. */
    suspend fun moviePage(category: String, offset: Int, limit: Int): List<TvPoster> = withContext(Dispatchers.IO) {
        dao.itemsInCategoryPage("MOVIE", category, limit, offset).map { itemPoster(it) }
    }

    /** Xtream dizi kataloğu kategorisinin bir sayfası. */
    suspend fun catalogPage(categoryKey: String, offset: Int, limit: Int): List<TvPoster> = withContext(Dispatchers.IO) {
        dao.seriesCatalogPage(categoryKey, limit, offset).map { catalogPoster(it) }
    }

    /** M3U dizi kategorisi: yalnızca bu kategorinin bölümleri okunur ve telefondaki gibi dizilere gruplanır. */
    suspend fun seriesInCategory(category: String, covers: Map<String, String>): List<TvPoster> = withContext(Dispatchers.Default) {
        showsOf(dao.itemsInCategory("SERIES", category), covers) // Room sorguyu kendi iş parçacığında çalıştırır
    }

    /**
     * Dizi detayında "benzer yapımlar": izlenen dizinin klasöründeki diğer diziler (bölümler değil). Xtream
     * kataloğunda dizinin kategorisi, M3U'da bölümlerin kategorisi kullanılır.
     */
    suspend fun otherShowsInFolder(show: TvShow?, episode: IPTVItem, covers: Map<String, String>, limit: Int = 30): List<TvPoster> =
        withContext(Dispatchers.Default) {
            val title = show?.title ?: SeriesParser.episodeInfoOf(episode)?.showTitle ?: episode.cleanedName
            val catalogShow = dao.findCatalogShow(show?.id ?: -1, title)
            val list = if (catalogShow != null) {
                dao.seriesCatalogPage(catalogShow.categoryId, limit + 1, 0)
                    .filter { it.seriesId != catalogShow.seriesId && !adult(it) }
                    .map { catalogPoster(it) }
            } else if (episode.category.isNotBlank()) {
                showsOf(dao.itemsInCategory("SERIES", episode.category), covers)
                    .filterNot { it.title.equals(title, ignoreCase = true) || adult(it.item) }
            } else {
                emptyList()
            }
            list.take(limit)
        }

    /** Detay ekranı için bir bölümün dizisi (yalnızca o dizinin bölümleri sorgulanır). */
    suspend fun showForEpisode(item: IPTVItem, covers: Map<String, String>): TvShow? = withContext(Dispatchers.Default) {
        val title = SeriesParser.episodeInfoOf(item)?.showTitle
            ?.takeIf { it.isNotBlank() } ?: return@withContext null
        val shows = SeriesParser.groupItemsIntoShows(dao.getEpisodesForShowTitle("%${title.trim()}%", 3000), covers)
        shows.firstOrNull { it.title.equals(title, ignoreCase = true) } ?: shows.firstOrNull()
    }

    /** İlk canlı kanal (kategori adına göre sıralı; yetişkin kanallar atlanır). */
    suspend fun firstLiveChannel(isAdult: (IPTVItem) -> Boolean): IPTVItem? = withContext(Dispatchers.IO) {
        dao.firstLiveChannels(200).firstOrNull { !isAdult(it) }
    }

    suspend fun firstItemWithStream(): IPTVItem? = withContext(Dispatchers.IO) {
        dao.firstItemWithStream("MOVIE") ?: dao.firstItemWithStream("LIVE")
    }

    // ------------------------------------------------------------------
    // Kişi filmografisi: aynı eşleştirme kuralları (PersonWorksMatcher), adaylar veritabanından süzülür.
    // ------------------------------------------------------------------

    /** Sağlayıcının oyuncu/yönetmen bilgisinde kişiyi içeren yapımlar (telefondaki "searchByCast" ile aynı kural). */
    suspend fun castPool(personName: String): List<IPTVItem> = withContext(Dispatchers.IO) {
        val name = PersonWorksMatcher.primaryName(personName).trim()
        if (name.length < 3) return@withContext emptyList()
        val patterns = likePatterns(name)
        val where = patterns.joinToString(" OR ") { "\"cast\" LIKE ? ESCAPE '\\' OR director LIKE ? ESCAPE '\\'" }
        val args = patterns.flatMap { listOf(it, it) }.toTypedArray<Any?>()
        val items = dao.rawItems(SimpleSQLiteQuery("SELECT * FROM iptv_items WHERE type IN ('MOVIE','SERIES') AND ($where) LIMIT 1500", args))
        val shows = dao.rawSeriesCatalog(SimpleSQLiteQuery("SELECT * FROM xtream_series_catalog WHERE $where LIMIT 500", args))
            .map { catalogItem(it) }
        items + shows
    }

    /**
     * TMDB filmografisindeki başlıklarla eşleşebilecek kütüphane öğeleri. SQLite LIKE Türkçe/aksanlı harflerde
     * büyük-küçük eşleştirmesi yapamadığı için her başlık iki desenle aranır: olduğu gibi ve harf iskeletiyle
     * (ı/i, ş/s, ğ/g, ç/c, ö/o, ü/u, é/e gibi değişebilen harfler tek karakter jokeri). Son karar yine
     * PersonWorksMatcher'dadır; burada yalnızca aday havuzu süzülür.
     */
    suspend fun creditsPool(lookup: PersonCreditsLookup): List<IPTVItem> = withContext(Dispatchers.IO) {
        val patterns = lookup.credits
            .sortedByDescending { it.popularity }
            .take(MAX_CREDITS)
            .flatMap { listOf(it.title, it.originalTitle) }
            .map { it.trim() }
            .filter { it.length >= 2 }
            .flatMap { likePatterns(it) }
            .distinct()
        if (patterns.isEmpty()) return@withContext emptyList()
        val out = LinkedHashMap<String, IPTVItem>()
        suspend fun query(group: List<String>, movieLimit: Int, seriesLimit: Int, catalogLimit: Int) {
            val where = group.joinToString(" OR ") { "cleanedName LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\'" }
            val bound = group.flatMap { listOf(it, it) }.toTypedArray<Any?>()
            dao.rawItems(SimpleSQLiteQuery("SELECT * FROM iptv_items WHERE type = 'MOVIE' AND ($where) LIMIT $movieLimit", bound))
                .forEach { out["i${it.id}"] = it }
            // dizilerde her bölüm ayrı satırdır
            dao.rawItems(SimpleSQLiteQuery("SELECT * FROM iptv_items WHERE type = 'SERIES' AND ($where) LIMIT $seriesLimit", bound))
                .forEach { out["i${it.id}"] = it }
            val catWhere = group.joinToString(" OR ") { "name LIKE ? ESCAPE '\\'" }
            dao.rawSeriesCatalog(SimpleSQLiteQuery("SELECT * FROM xtream_series_catalog WHERE $catWhere LIMIT $catalogLimit", group.toTypedArray<Any?>()))
                .forEach { out["c${it.seriesId}"] = catalogItem(it) }
        }
        // Gevşek desenler (kısa başlığın iskeleti) çok satır tutabilir; diğerlerini dışarıda bırakmasınlar diye ayrı sorgulanır.
        val (loose, strict) = patterns.partition { fixedLetters(it) < STRICT_FIXED_LETTERS }
        for (chunk in strict.chunked(LIKE_CHUNK)) query(chunk, movieLimit = 1500, seriesLimit = 4000, catalogLimit = 500)
        for (pattern in loose) query(listOf(pattern), movieLimit = 300, seriesLimit = 1500, catalogLimit = 100)
        out.values.toList()
    }

    /**
     * Asistanın tahmin ettiği yapımları kütüphanede bulur (Türkçe ve orijinal adla, Türkçe harf / etiket / yıl
     * farklarına dayanıklı; kişi filmografisindeki eşleştirmenin aynısı). Aday sırası korunur, yetişkin içerik yok.
     */
    suspend fun findTitles(candidates: List<com.example.data.api.GeminiAiService.TitleCandidate>, limit: Int = 4): List<IPTVItem> =
        withContext(Dispatchers.Default) {
            if (candidates.isEmpty()) return@withContext emptyList()
            val credits = candidates.mapIndexed { index, c ->
                com.example.data.model.tmdb.PersonCredit(
                    tmdbId = index + 1, mediaType = if (c.isSeries) "tv" else "movie", title = c.title,
                    originalTitle = c.originalTitle, year = c.year, posterUrl = null, popularity = (100 - index).toDouble()
                )
            }
            val lookup = PersonCreditsLookup(personId = 0, name = "", profileUrl = null, credits = credits)
            val pool = creditsPool(lookup)
            PersonWorksMatcher.match(lookup, pool, excludeItemId = -1).inLibrary
                .map { it.item }
                .filterNot { adult(it) }
                .take(limit)
        }

    /** Aynı eşleştirme: TMDB eşleşmeleri + sağlayıcı oyuncu eşleşmeleri; kütüphanede olmayanlar ayrı. */
    suspend fun personWorks(lookup: PersonCreditsLookup?, personName: String, excludeItemId: Int, current: IPTVItem?): PersonWorksResult =
        withContext(Dispatchers.Default) {
            val castPool = castPool(personName)
            val castMatches = PersonWorksMatcher.searchByCast(personName, castPool, excludeItemId)
                .filterNot { PersonWorksMatcher.sameContent(it.item, current) || adult(it.item) }
            if (lookup == null) return@withContext PersonWorksResult(castMatches, emptyList())
            val pool = creditsPool(lookup)
            val matched = PersonWorksMatcher.match(lookup, pool, excludeItemId)
            PersonWorksResult(
                inLibrary = PersonWorksMatcher.merge(matched.inLibrary, castMatches)
                    .filterNot { PersonWorksMatcher.sameContent(it.item, current) || adult(it.item) },
                notInLibrary = matched.notInLibrary
            )
        }

    private fun adult(item: IPTVItem) = AdultContentFilter.isAdult(item.category, item.cleanedName.ifBlank { item.name })
    private fun adult(show: XtreamSeriesCatalogEntity) = AdultContentFilter.isAdult(show.categoryId, show.name)

    private fun itemPoster(item: IPTVItem) = TvPoster(
        key = "${item.type}_${item.id}", title = item.cleanedName.ifBlank { item.name }, posterUrl = item.logoUrl,
        rating = item.rating, item = item
    )

    private fun catalogShow(e: XtreamSeriesCatalogEntity) = TvShow(
        id = e.seriesId, title = e.name, logoUrl = e.coverUrl.ifEmpty { e.backdropUrl }.ifEmpty { null },
        category = e.genre.ifEmpty { "Dizi" }, rating = e.rating, summary = e.plot, cast = e.cast, director = e.director,
        isFavorite = false, seasons = emptyList(), platformName = e.categoryId.ifBlank { OTHER }
    )

    private fun catalogItem(e: XtreamSeriesCatalogEntity) = IPTVItem(
        id = e.seriesId, playlistId = 0, name = e.name, cleanedName = e.name,
        logoUrl = e.coverUrl.ifEmpty { e.backdropUrl }.ifEmpty { null }, streamUrl = "",
        category = e.genre.ifEmpty { "Dizi" }, type = "SERIES", rating = e.rating, cast = e.cast, director = e.director
    )

    private fun catalogPoster(e: XtreamSeriesCatalogEntity): TvPoster {
        val show = catalogShow(e)
        return TvPoster(key = "catalog_${e.seriesId}", title = e.name, posterUrl = show.logoUrl, rating = e.rating,
            item = catalogItem(e), catalogShow = show)
    }

    private fun showsOf(episodes: List<IPTVItem>, covers: Map<String, String>): List<TvPoster> =
        SeriesParser.groupItemsIntoShows(episodes, covers).mapNotNull { show ->
            val first = show.seasons.sortedBy { it.seasonNumber }.firstOrNull()?.episodes?.minByOrNull { it.episodeNumber }?.item
                ?: return@mapNotNull null
            TvPoster(key = "show_${show.title}", title = show.title, posterUrl = show.logoUrl, rating = show.rating, item = first)
        }

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Başlığın olduğu gibi deseni + harf iskeleti deseni (en az 2 sabit harf kalırsa). */
    private fun likePatterns(raw: String): List<String> {
        val out = ArrayList<String>(2)
        out.add("%${escapeLike(raw)}%")
        val folded = PersonWorksMatcher.fold(raw)
        val skeleton = buildString {
            for (ch in folded) {
                when {
                    ch == ' ' -> if (isNotEmpty() && last() != '%') append('%')
                    ch in VARIABLE_LETTERS -> append('_')
                    else -> append(ch)
                }
            }
        }.trim('%')
        if (fixedLetters(skeleton) >= 2) out.add("%$skeleton%")
        return out
    }

    private fun fixedLetters(pattern: String) = pattern.count { it != '_' && it != '%' && it != '\\' }

    companion object {
        const val SPECIAL_TOP10 = "__tv_top10"
        const val SPECIAL_TOP_RATED = "__tv_top_rated"
        const val OTHER = "Diğer"
        private const val MAX_CREDITS = 120
        private const val LIKE_CHUNK = 30
        /** Türkçe/aksanlı karşılığı olabilen harfler (LIKE'ta tek karakter jokeri olur). */
        private const val VARIABLE_LETTERS = "aeioucgs"
        private const val STRICT_FIXED_LETTERS = 4
    }
}
