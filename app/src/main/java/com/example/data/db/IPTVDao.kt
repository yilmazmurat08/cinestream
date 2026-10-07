package com.example.data.db

import androidx.room.*
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.model.PersonDetailsEntity
import com.example.data.model.SearchHistory
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.model.AiRecommendationHistory
import com.example.data.model.tmdb.TmdbCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IPTVDao {

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY loadedAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY loadedAt DESC")
    suspend fun getAllPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE url = :url LIMIT 1")
    suspend fun findPlaylistByUrl(url: String): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Int): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    // IPTV Items
    // Büyük listeler @Transaction ile tek, tutarlı bir anlık görüntüden okunur. Liste 2 MB'lık okuma penceresine
    // (CursorWindow) sığmazsa Android sorguyu parça parça yeniden çalıştırır; bu sırada başka bir işlem tabloyu
    // değiştirirse (ör. liste yenilemesi) okuma "Row too big to fit into CursorWindow" hatasıyla çöküyordu.
    @Transaction
    @Query("SELECT * FROM iptv_items ORDER BY id DESC")
    fun getAllItemsFlow(): Flow<List<IPTVItem>>

    @Transaction
    @Query("SELECT * FROM iptv_items")
    suspend fun getAllItemsDirect(): List<IPTVItem>

    @Query("SELECT COUNT(*) FROM iptv_items")
    suspend fun getItemCount(): Int

    @Transaction
    @Query("SELECT * FROM iptv_items WHERE type = :type")
    suspend fun getItemsByTypeDirect(type: String): List<IPTVItem>

    @Transaction
    @Query("SELECT * FROM iptv_items WHERE type = 'SERIES' AND cleanedName LIKE '%' || :titlePattern || '%' LIMIT 500")
    suspend fun findSeriesEpisodesByTitleLike(titlePattern: String): List<IPTVItem>

    @Transaction
    @Query("SELECT * FROM iptv_items WHERE type = :type ORDER BY id DESC")
    fun getItemsByTypeFlow(type: String): Flow<List<IPTVItem>>

    @Query("SELECT DISTINCT category FROM iptv_items WHERE category != '' ORDER BY category ASC")
    fun getDistinctCategoriesFlow(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM series_covers")
    suspend fun getAllSeriesCovers(): List<com.example.data.model.SeriesCoverEntity>

    @Transaction
    @Query("SELECT * FROM series_covers")
    fun getAllSeriesCoversFlow(): Flow<List<com.example.data.model.SeriesCoverEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesCover(cover: com.example.data.model.SeriesCoverEntity)

    @Transaction
    @Query("SELECT * FROM xtream_series_catalog")
    fun getAllXtreamSeriesCatalogFlow(): Flow<List<com.example.data.model.XtreamSeriesCatalogEntity>>

    @Transaction
    @Query("SELECT * FROM xtream_series_catalog")
    suspend fun getAllXtreamSeriesCatalogDirect(): List<com.example.data.model.XtreamSeriesCatalogEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertXtreamSeriesCatalog(items: List<com.example.data.model.XtreamSeriesCatalogEntity>)

    // --- TV ana sayfası (Top Shelf) için rastgele seçim: tüm listeyi belleğe almadan, veritabanında ---
    /** Görseli olan rastgele öğeler; özeti kayıtlı olanlar önce gelir. */
    @Query("SELECT * FROM iptv_items WHERE type = :type AND logoUrl IS NOT NULL AND logoUrl != '' ORDER BY (summary != '') DESC, RANDOM() LIMIT :limit")
    suspend fun randomItemsWithImage(type: String, limit: Int): List<IPTVItem>

    /** Kapak görseli olan rastgele diziler (Xtream kataloğu); konusu kayıtlı olanlar önce. */
    @Query("SELECT * FROM xtream_series_catalog WHERE coverUrl != '' ORDER BY (plot != '') DESC, RANDOM() LIMIT :limit")
    suspend fun randomSeriesCatalog(limit: Int): List<XtreamSeriesCatalogEntity>

    @Query("SELECT * FROM xtream_series_catalog WHERE canonicalKey = :key LIMIT 1")
    suspend fun findXtreamSeriesByCanonicalKey(key: String): com.example.data.model.XtreamSeriesCatalogEntity?

    @Query("DELETE FROM xtream_series_catalog")
    suspend fun clearXtreamSeriesCatalog()

    /** Xtream dizi kataloğunu TEK işlemde yeniler: okuyan ekran kataloğu hiçbir an boş ya da yarım görmez. */
    @Transaction
    suspend fun replaceXtreamSeriesCatalog(items: List<XtreamSeriesCatalogEntity>) {
        clearXtreamSeriesCatalog()
        insertXtreamSeriesCatalog(items)
    }

    @Query("UPDATE iptv_items SET type = :newType WHERE id IN (:ids)")
    suspend fun updateItemsType(ids: List<Int>, newType: String)

    @Query("SELECT DISTINCT category FROM iptv_items WHERE type = :type AND category != '' ORDER BY category ASC")
    fun getDistinctCategoriesByTypeFlow(type: String): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM iptv_items WHERE type = :type AND category = :category ORDER BY id DESC")
    fun getItemsByTypeAndCategoryFlow(type: String, category: String): Flow<List<IPTVItem>>

    @Transaction
    @Query("SELECT * FROM iptv_items WHERE isFavorite = 1")
    fun getFavoriteItemsFlow(): Flow<List<IPTVItem>>

    @Query("SELECT * FROM iptv_items WHERE id = :id")
    fun getItemByIdFlow(id: Int): Flow<IPTVItem?>

    @Query("SELECT * FROM iptv_items WHERE id = :id")
    suspend fun getItemById(id: Int): IPTVItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<IPTVItem>)

    @Query("DELETE FROM iptv_items WHERE type = 'MOVIE'")
    suspend fun deleteAllMovies()

    @Query("DELETE FROM iptv_items WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteItemsByPlaylistAndType(playlistId: Int, type: String)

    @Query("SELECT id FROM iptv_items WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun favoriteItemIdsOfPlaylist(playlistId: Int): List<Int>

    @Query("SELECT streamUrl FROM iptv_items WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun favoriteStreamUrlsOfPlaylist(playlistId: Int): List<String>

    /**
     * Bir listenin bir türdeki öğelerini TEK işlemde yeniler (Xtream yenilemesi):
     * - Okuyan ekranlar tabloyu hiçbir an yarı boş görmez (eskiden "sil" ile "ekle" arasında okunursa uygulama çöküyordu).
     * - Favoriler korunur: yeni öğe, kimliği ya da yayın adresi eski bir favoriyle eşleşiyorsa favori kalır
     *   (eskiden her yenilemede favoriler sıfırlanıyordu).
     */
    @Transaction
    suspend fun replaceItemsOfType(playlistId: Int, type: String, items: List<IPTVItem>) {
        val favoriteIds = favoriteItemIdsOfPlaylist(playlistId).toHashSet()
        val favoriteUrls = favoriteStreamUrlsOfPlaylist(playlistId).toHashSet()
        deleteItemsByPlaylistAndType(playlistId, type)
        insertItems(items.map { item ->
            if (!item.isFavorite && (item.id in favoriteIds || item.streamUrl in favoriteUrls)) item.copy(isFavorite = true) else item
        })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: IPTVItem): Long

    @Query("UPDATE iptv_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)

    @Query("UPDATE iptv_items SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: Int, summary: String)

    @Query("UPDATE iptv_items SET summary = :summary WHERE id IN (:ids)")
    suspend fun updateSummaryForIds(ids: List<Int>, summary: String)

    @Query("UPDATE iptv_items SET summary = :summary, cast = :cast, director = :director, rating = :rating, logoUrl = :logoUrl, trailerUrl = :trailerUrl, releaseDate = :releaseDate, genre = :genre WHERE id = :id")
    suspend fun updateItemMetadata(id: Int, summary: String, cast: String, director: String, rating: Double, logoUrl: String?, trailerUrl: String?, releaseDate: String = "", genre: String = "")

    @Query("DELETE FROM iptv_items WHERE playlistId = :playlistId")
    suspend fun deleteItemsByPlaylist(playlistId: Int)

    @Query("SELECT COUNT(*) FROM iptv_items WHERE playlistId = :playlistId")
    suspend fun getItemsCountForPlaylist(playlistId: Int): Int

    // Search & Filter
    @Transaction
    @Query("SELECT * FROM iptv_items WHERE name LIKE :query OR cleanedName LIKE :query OR category LIKE :query")
    fun searchItemsFlow(query: String): Flow<List<IPTVItem>>

    @Query("""
        SELECT * FROM iptv_items
        WHERE cleanedName LIKE :query
           OR director LIKE :query
           OR `cast` LIKE :query
           OR category LIKE :query
           OR summary LIKE :query
        LIMIT :limit
    """)
    suspend fun searchCollectionItems(query: String, limit: Int = 20): List<IPTVItem>

    @Query("""
        SELECT * FROM iptv_items
        WHERE type = 'MOVIE'
          AND rating >= :minRating
        ORDER BY rating DESC
        LIMIT :limit
    """)
    suspend fun getTopRatedMovies(minRating: Double = 7.5, limit: Int = 12): List<IPTVItem>

    @Query("""
        SELECT * FROM iptv_items
        WHERE type = 'SERIES'
          AND (cleanedName LIKE :showTitlePattern OR name LIKE :showTitlePattern)
        LIMIT :limit
    """)
    suspend fun getEpisodesForShowTitle(showTitlePattern: String, limit: Int = 100): List<IPTVItem>

    @Transaction
    @Query("""
        SELECT * FROM iptv_items
        WHERE type = 'MOVIE' OR type = 'SERIES'
        ORDER BY rating DESC
        LIMIT :limit
    """)
    suspend fun getPopularCatalogPool(limit: Int = 200): List<IPTVItem>

    @Transaction
    @Query("""
        SELECT * FROM iptv_items
        WHERE type = :itemType
        ORDER BY rating DESC
        LIMIT :limit
    """)
    suspend fun getPopularCatalogPoolByType(itemType: String, limit: Int = 200): List<IPTVItem>

    @Query("""
        SELECT * FROM iptv_items
        WHERE type = :itemType
          AND (category LIKE :categoryPattern OR genre LIKE :genrePattern)
        ORDER BY rating DESC
        LIMIT :limit
    """)
    suspend fun getSimilarItemsByCategory(itemType: String, categoryPattern: String, genrePattern: String, limit: Int = 20): List<IPTVItem>

    // Continue Watching
    @Query("SELECT * FROM continue_watching ORDER BY lastPlayedAt DESC")
    fun getContinueWatchingFlow(): Flow<List<ContinueWatching>>

    @Query("SELECT * FROM continue_watching")
    suspend fun getAllContinueWatchingOnce(): List<ContinueWatching>

    @Query("SELECT * FROM continue_watching WHERE itemId = :itemId LIMIT 1")
    suspend fun getContinueWatchingForItem(itemId: Int): ContinueWatching?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContinueWatching(continueWatching: ContinueWatching)

    @Query("DELETE FROM continue_watching WHERE itemId = :itemId")
    suspend fun deleteContinueWatchingByItemId(itemId: Int)

    // Staging Table Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStagingItems(items: List<com.example.data.model.IPTVItemStaging>)

    @Query("DELETE FROM iptv_items_staging WHERE playlistId = :playlistId")
    suspend fun clearStagingItems(playlistId: Int)

    @Query("SELECT COUNT(*) FROM iptv_items_staging WHERE playlistId = :playlistId")
    suspend fun getStagingItemCount(playlistId: Int): Int

    @Query("SELECT id FROM iptv_items WHERE playlistId != :playlistId")
    suspend fun getExistingItemIdsExcludingPlaylist(playlistId: Int): List<Int>

    @Query("SELECT id FROM iptv_items")
    suspend fun getAllExistingItemIds(): List<Int>

    @Query("""
        UPDATE iptv_items_staging 
        SET isFavorite = 1 
        WHERE playlistId = :playlistId 
          AND (
            id IN (SELECT id FROM iptv_items WHERE playlistId = :playlistId AND isFavorite = 1)
            OR streamUrl IN (SELECT streamUrl FROM iptv_items WHERE playlistId = :playlistId AND isFavorite = 1)
          )
    """)
    suspend fun preserveFavoritesFromActive(playlistId: Int)

    @Query("""
        UPDATE continue_watching
        SET itemId = (
            SELECT s.id FROM iptv_items_staging s 
            WHERE s.playlistId = :playlistId AND (s.streamUrl = continue_watching.streamUrl OR s.cleanedName = continue_watching.itemName) 
            LIMIT 1
        )
        WHERE EXISTS (
            SELECT 1 FROM iptv_items_staging s 
            WHERE s.playlistId = :playlistId AND (s.streamUrl = continue_watching.streamUrl OR s.cleanedName = continue_watching.itemName)
        )
    """)
    suspend fun syncContinueWatchingWithStaging(playlistId: Int)

    @Query("UPDATE playlists SET epgUrl = :epgUrl WHERE id = :playlistId")
    suspend fun updatePlaylistEpgUrl(playlistId: Int, epgUrl: String?)

    @Query("""
        INSERT OR REPLACE INTO iptv_items (id, playlistId, name, cleanedName, logoUrl, streamUrl, category, type, rating, summary, `cast`, director, trailerUrl, isFavorite, season, episode, releaseDate, genre, tvgId)
        SELECT id, playlistId, name, cleanedName, logoUrl, streamUrl, category, type, rating, summary, `cast`, director, trailerUrl, isFavorite, season, episode, releaseDate, genre, tvgId
        FROM iptv_items_staging
        WHERE playlistId = :playlistId
    """)
    suspend fun copyStagingToActive(playlistId: Int)

    /** Birden çok öğenin detay bilgisini tek işlemde yazar: listeler bir kez yeniden okunur. */
    @Transaction
    suspend fun updateItemsMetadata(updates: List<ItemMetadataUpdate>) {
        updates.forEach { u ->
            updateItemMetadata(u.itemId, u.summary, u.cast, u.director, u.rating, u.logoUrl, u.trailerUrl, u.releaseDate, u.genre)
        }
    }

    // --- Aşırı büyük alan koruması ---
    // Bazı listeler logoyu adres yerine resmin kendisi (base64) olarak ya da çok uzun metinlerle verir.
    // Tek bir satır 2 MB'lık okuma penceresini (CursorWindow) aşarsa listeyi okumak "Couldn't read row"
    // hatasıyla çöker. Bu alanlar makul uzunlukta kırpılır (hepsi listeden yeniden gelen bilgiler).
    @Query("UPDATE iptv_items SET logoUrl = NULL WHERE length(logoUrl) > 4096")
    suspend fun dropOversizedLogos()

    @Query("UPDATE iptv_items SET trailerUrl = NULL WHERE length(trailerUrl) > 4096")
    suspend fun dropOversizedTrailers()

    @Query("""
        UPDATE iptv_items SET
            name = substr(name, 1, 500),
            cleanedName = substr(cleanedName, 1, 500),
            category = substr(category, 1, 300),
            summary = substr(summary, 1, 8000),
            `cast` = substr(`cast`, 1, 4000),
            director = substr(director, 1, 500),
            releaseDate = substr(releaseDate, 1, 50),
            genre = substr(genre, 1, 500),
            tvgId = substr(tvgId, 1, 300)
        WHERE length(name) > 500 OR length(cleanedName) > 500 OR length(category) > 300
            OR length(summary) > 8000 OR length(`cast`) > 4000 OR length(director) > 500
            OR length(releaseDate) > 50 OR length(genre) > 500 OR length(tvgId) > 300
    """)
    suspend fun truncateOversizedTextFields()

    @Transaction
    suspend fun trimOversizedItemFields() {
        dropOversizedLogos()
        dropOversizedTrailers()
        truncateOversizedTextFields()
    }

    @Transaction
    suspend fun promoteStagingToActive(playlistId: Int): Int {
        preserveFavoritesFromActive(playlistId)
        syncContinueWatchingWithStaging(playlistId)
        deleteItemsByPlaylist(playlistId)
        copyStagingToActive(playlistId)
        trimOversizedItemFields()
        val count = getStagingItemCount(playlistId)
        clearStagingItems(playlistId)
        return count
    }

    @Query("DELETE FROM continue_watching")
    suspend fun clearContinueWatching()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM iptv_items")
    suspend fun deleteAllIPTVItems()

    // Bounded queries for Search History & Continue Watching
    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimSearchHistory(limit: Int = 100)

    @Transaction
    suspend fun insertSearchQueryWithLimit(searchHistory: SearchHistory, limit: Int = 100) {
        insertSearchQuery(searchHistory)
        trimSearchHistory(limit)
    }

    @Query("DELETE FROM continue_watching WHERE id NOT IN (SELECT id FROM continue_watching ORDER BY lastPlayedAt DESC LIMIT :limit)")
    suspend fun trimContinueWatching(limit: Int = 50)

    @Transaction
    suspend fun insertContinueWatchingWithLimit(continueWatching: ContinueWatching, limit: Int = 50) {
        val existing = getContinueWatchingForItem(continueWatching.itemId)
        if (existing != null) {
            insertContinueWatching(continueWatching.copy(id = existing.id))
        } else {
            insertContinueWatching(continueWatching)
        }
        trimContinueWatching(limit)
    }

    // TMDB Cache Operations
    @Query("SELECT * FROM tmdb_cache WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getTmdbCache(sourceKey: String): TmdbCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTmdbCache(entity: TmdbCacheEntity)

    @Query("DELETE FROM tmdb_cache WHERE updatedAt < :cutoffTime")
    suspend fun deleteExpiredTmdbCache(cutoffTime: Long)

    @Query("DELETE FROM tmdb_cache")
    suspend fun clearTmdbCache()

    // TMDB Person Details Cache
    @Query("SELECT * FROM tmdb_person_cache WHERE name = :name")
    suspend fun getPersonDetails(name: String): PersonDetailsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonDetails(person: PersonDetailsEntity)

    // En son eklenen/güncellenen :limit kişi kalır (REPLACE her yazmada yeni rowid verir). Eskiden sırasız
    // LIMIT rastgele kayıtları tutuyordu; son bakılan oyuncuların fotoğrafları silinip yeniden aranıyordu.
    @Query("DELETE FROM tmdb_person_cache WHERE rowid NOT IN (SELECT rowid FROM tmdb_person_cache ORDER BY rowid DESC LIMIT :limit)")
    suspend fun trimPersonCache(limit: Int = 3000)

    @Transaction
    suspend fun insertPersonDetailsWithLimit(person: PersonDetailsEntity, limit: Int = 3000) {
        insertPersonDetails(person)
        trimPersonCache(limit)
    }

    // Search History
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearchQueriesFlow(limit: Int = 5): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(searchHistory: SearchHistory)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // AI Recommendation History
    @Query("SELECT * FROM ai_recommendation_history ORDER BY timestamp DESC")
    fun getAllAiRecommendationHistoryFlow(): Flow<List<AiRecommendationHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiRecommendationHistory(history: AiRecommendationHistory): Long

    @Query("DELETE FROM ai_recommendation_history WHERE id = :id")
    suspend fun deleteAiRecommendationHistory(id: Int)

    @Query("DELETE FROM ai_recommendation_history")
    suspend fun clearAllAiRecommendationHistory()

    // --- TV modu: sayfa sayfa gezinme (tüm kütüphane belleğe alınmaz) ---

    @Query("SELECT category AS name, COUNT(*) AS count FROM iptv_items WHERE type = :type GROUP BY category")
    suspend fun categoryCounts(type: String): List<CategoryCount>

    @Query("SELECT * FROM iptv_items WHERE type = :type AND category = :category ORDER BY cleanedName COLLATE NOCASE, id LIMIT :limit OFFSET :offset")
    suspend fun itemsInCategoryPage(type: String, category: String, limit: Int, offset: Int): List<IPTVItem>

    /** Bir kategorideki tüm öğeler (yalnızca dizi bölümlerini diziye gruplamak için; tek kategoriyle sınırlı). */
    @Transaction
    @Query("SELECT * FROM iptv_items WHERE type = :type AND category = :category")
    suspend fun itemsInCategory(type: String, category: String): List<IPTVItem>

    @Query("SELECT * FROM iptv_items WHERE type = :type ORDER BY rating DESC LIMIT :limit")
    suspend fun topRatedByType(type: String, limit: Int): List<IPTVItem>

    @Query("SELECT * FROM iptv_items WHERE type = :type AND rating >= :minRating ORDER BY rating DESC LIMIT :limit")
    suspend fun ratedAtLeast(type: String, minRating: Double, limit: Int): List<IPTVItem>

    @Query("SELECT * FROM iptv_items WHERE type = 'LIVE' AND streamUrl != '' ORDER BY category, rowid LIMIT :limit")
    suspend fun firstLiveChannels(limit: Int): List<IPTVItem>

    @Query("SELECT * FROM iptv_items WHERE type = :type AND streamUrl != '' LIMIT 1")
    suspend fun firstItemWithStream(type: String): IPTVItem?

    @Query("SELECT COUNT(*) FROM xtream_series_catalog")
    suspend fun seriesCatalogCount(): Int

    @Query("SELECT categoryId AS name, COUNT(*) AS count FROM xtream_series_catalog GROUP BY categoryId")
    suspend fun seriesCatalogCategoryCounts(): List<CategoryCount>

    @Query("SELECT * FROM xtream_series_catalog WHERE categoryId = :categoryId ORDER BY name COLLATE NOCASE, seriesId LIMIT :limit OFFSET :offset")
    suspend fun seriesCatalogPage(categoryId: String, limit: Int, offset: Int): List<XtreamSeriesCatalogEntity>

    @Query("SELECT * FROM xtream_series_catalog WHERE seriesId = :seriesId OR name = :name LIMIT 1")
    suspend fun findCatalogShow(seriesId: Int, name: String): XtreamSeriesCatalogEntity?

    @Query("SELECT * FROM xtream_series_catalog ORDER BY rating DESC LIMIT :limit")
    suspend fun topRatedSeriesCatalog(limit: Int): List<XtreamSeriesCatalogEntity>

    @Query("SELECT * FROM xtream_series_catalog WHERE rating >= :minRating ORDER BY rating DESC LIMIT :limit")
    suspend fun seriesCatalogRatedAtLeast(minRating: Double, limit: Int): List<XtreamSeriesCatalogEntity>

    /** Kişi filmografisi eşleştirmesi için aday süzme (başlık/oyuncu LIKE koşulları sorguyla kurulur). */
    @RawQuery
    suspend fun rawItems(query: androidx.sqlite.db.SupportSQLiteQuery): List<IPTVItem>

    @RawQuery
    suspend fun rawSeriesCatalog(query: androidx.sqlite.db.SupportSQLiteQuery): List<XtreamSeriesCatalogEntity>
}

/** Kategori adı ve içindeki öğe sayısı (TV kategori listesi). */
data class CategoryCount(val name: String, val count: Int)
