package com.example.data.repository

import com.example.data.db.IPTVDao
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.IPTVItemStaging
import com.example.data.model.Playlist
import com.example.data.model.PersonDetailsEntity
import com.example.data.model.SearchHistory
import com.example.data.model.AiRecommendationHistory
import com.example.data.model.generateDeterministicId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import androidx.room.withTransaction
import okhttp3.Request
import okhttp3.Response
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import com.example.data.model.XtreamSeriesCatalogEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class BoundedInputStream(
    private val target: InputStream,
    private val maxBytes: Long
) : InputStream() {
    private var totalBytesRead: Long = 0L

    override fun read(): Int {
        if (totalBytesRead >= maxBytes) {
            throw IOException("M3U playlist size exceeded safety limit of ${maxBytes / (1024 * 1024)} MB")
        }
        val byte = target.read()
        if (byte != -1) {
            totalBytesRead++
        }
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (totalBytesRead >= maxBytes) {
            throw IOException("M3U playlist size exceeded safety limit of ${maxBytes / (1024 * 1024)} MB")
        }
        val maxToRead = minOf(len.toLong(), maxBytes - totalBytesRead).toInt()
        val bytesRead = target.read(b, off, maxToRead)
        if (bytesRead != -1) {
            totalBytesRead += bytesRead
        }
        return bytesRead
    }

    override fun close() {
        target.close()
    }
}

class IPTVRepository(
    private val iptvDao: IPTVDao,
    private val database: com.example.data.db.AppDatabase? = null,
    /** Xtream hesaplarını player_api ile yenilemek için (bkz. syncPlaylist). Testlerde boş olabilir. */
    private val appContext: android.content.Context? = null
) {

    companion object {
        private const val TAG = "IPTVRepository"
        const val MAX_M3U_DOWNLOAD_BYTES = 200L * 1024L * 1024L // 200 MB Safety Limit
        const val BATCH_SIZE = 250
        private val syncMutex = Mutex()
        val ambiguousMovieClassificationSamples = java.util.Collections.synchronizedList(mutableListOf<String>())
    }

    val allPlaylistsFlow: Flow<List<Playlist>> = iptvDao.getAllPlaylistsFlow()
    val allItemsFlow: Flow<List<IPTVItem>> = iptvDao.getAllItemsFlow()
    val favoriteItemsFlow: Flow<List<IPTVItem>> = iptvDao.getFavoriteItemsFlow()
    val continueWatchingFlow: Flow<List<ContinueWatching>> = iptvDao.getContinueWatchingFlow()

    /**
     * Türe göre tüm öğeler. Okuma hatası (ör. aşırı büyük bir satır, "Couldn't read row") uygulamayı
     * kapatmasın: önce alanlar kırpılıp birkaç kez yeniden denenir; yine olmazsa hata kaydedilir ve
     * ekrandaki mevcut liste korunur.
     */
    fun getItemsByType(type: String): Flow<List<IPTVItem>> = iptvDao.getItemsByTypeFlow(type)
        .retryWhen { cause, attempt ->
            if (attempt < 3 && (cause is IllegalStateException || cause is android.database.sqlite.SQLiteException)) {
                Log.w("IPTVRepository", "Reading $type items failed (attempt ${attempt + 1}), trimming oversized rows", cause)
                try { iptvDao.trimOversizedItemFields() } catch (e: Exception) { Log.w("IPTVRepository", "Trim failed", e) }
                kotlinx.coroutines.delay(500L * (attempt + 1))
                true
            } else {
                false
            }
        }
        .catch { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("IPTVRepository", "Reading $type items failed", e)
        }

    /** Aşırı büyük alanları kırpar (açılışta ve doğrudan eklemelerden sonra). */
    suspend fun trimOversizedItemFields() {
        try {
            iptvDao.trimOversizedItemFields()
        } catch (e: Exception) {
            Log.w("IPTVRepository", "Trimming oversized item fields failed", e)
        }
    }

    fun getDistinctCategoriesFlow(): Flow<List<String>> = iptvDao.getDistinctCategoriesFlow()

    suspend fun getAllSeriesCovers(): Map<String, String> =
        iptvDao.getAllSeriesCovers().associate { it.showTitleKey to it.coverUrl }

    fun getAllSeriesCoversFlow(): Flow<Map<String, String>> =
        iptvDao.getAllSeriesCoversFlow().map { list -> list.associate { it.showTitleKey to it.coverUrl } }

    suspend fun saveSeriesCover(showTitleKey: String, coverUrl: String) {
        iptvDao.upsertSeriesCover(com.example.data.model.SeriesCoverEntity(showTitleKey, coverUrl))
    }

    val xtreamSeriesCatalogFlow: Flow<List<com.example.data.model.XtreamSeriesCatalogEntity>> =
        iptvDao.getAllXtreamSeriesCatalogFlow()

    suspend fun getAllXtreamSeriesCatalog(): List<com.example.data.model.XtreamSeriesCatalogEntity> =
        iptvDao.getAllXtreamSeriesCatalogDirect()

    suspend fun saveXtreamSeriesCatalog(items: List<com.example.data.model.XtreamSeriesCatalogEntity>) {
        if (items.isNotEmpty()) {
            iptvDao.insertXtreamSeriesCatalog(items)
        }
    }

    suspend fun clearXtreamSeriesCatalog() {
        iptvDao.clearXtreamSeriesCatalog()
    }

    suspend fun updateItemsType(ids: List<Int>, newType: String) {
        if (ids.isNotEmpty()) {
            iptvDao.updateItemsType(ids, newType)
        }
    }
    fun getDistinctCategoriesByTypeFlow(type: String): Flow<List<String>> = iptvDao.getDistinctCategoriesByTypeFlow(type)
    suspend fun getAllItemsDirect(): List<IPTVItem> = try {
        iptvDao.getAllItemsDirect()
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching all items direct", e)
        emptyList()
    }

    // Cache-First Architecture: Checks local Room database first.
    // If local DB contains data, returns local data immediately and bypasses network requests (0-second instant startup).
    suspend fun getLocalChannels(): List<IPTVItem> = try {
        iptvDao.getAllItemsDirect()
    } catch (e: Exception) {
        Log.e(TAG, "Database access exception in getLocalChannels", e)
        emptyList()
    }

    suspend fun getHomeData(): List<IPTVItem> = withContext(Dispatchers.IO) {
        val cachedItems = getLocalChannels()
        if (cachedItems.isNotEmpty()) {
            cachedItems
        } else {
            fetchFromNetwork()
        }
    }

    /** Önce uygulamanın kendi kimliği, kabul edilmezse genel tarayıcı kimliği denenir (bkz. AppUserAgent). */
    private val IPTV_USER_AGENTS = listOf(com.example.util.AppUserAgent.app, com.example.util.AppUserAgent.BROWSER)

    /**
     * Safely synchronizes an individual playlist from remote HTTP/HTTPS stream
     * using OkHttp with SSL bypass, multiple IPTV User-Agent fallback attempts,
     * BoundedInputStream, line-by-line parsing, staging table, and atomic swap.
     */
    suspend fun syncPlaylist(playlist: Playlist, onProgress: (Map<String, Int>) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        if (!playlist.url.startsWith("http", ignoreCase = true)) return@withContext 0

        // Xtream hesapları her zaman Xtream API'siyle (player_api) yenilenir. Sağlayıcının get.php M3U dosyası
        // indirilmez: çoğu sağlayıcı bunu reddeder (HTTP 403) ve indirilse bile kanallar farklı kimliklerle
        // yeniden yazılıp canlı kanallar, EPG ve izleme geçmişi bozuluyordu.
        val xtream = xtreamCredentialsOf(playlist)
        val context = appContext
        if (xtream != null && context != null) {
            val count = syncMutex.withLock {
                importXtreamDirectly(context, playlist.name, xtream.first, xtream.second, xtream.third, existingPlaylistId = playlist.id, onProgress = onProgress)
            }
            if (count > 0) return@withContext count
            // API hiçbir şey döndürmediyse eski yola (M3U) düşülür; o yol da başarısızsa mevcut kayıtlar korunur.
            Log.w(TAG, "Xtream API yenilemesi boş döndü, M3U yoluna geçiliyor")
        }

        syncMutex.withLock {
            val urlClean = playlist.url.trim()
            val client = com.example.data.api.NetworkModule.iptvOkHttpClient
            var lastResponseCode = 0
            var lastErrorMessage: String? = null
            var parsedCount = 0

            for (userAgent in IPTV_USER_AGENTS) {
                var response: Response? = null
                try {
                    val requestBuilder = Request.Builder()
                        .url(urlClean)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,application/x-mpegURL,audio/x-mpegurl,*/*;q=0.8")
                        .header("Accept-Language", "tr-TR,tr;q=0.9,en-US,en;q=0.8")
                        .header("Connection", "keep-alive")


                    val request = requestBuilder.build()
                    response = client.newCall(request).execute()
                    val code = response.code
                    lastResponseCode = code

                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            BoundedInputStream(body.byteStream(), MAX_M3U_DOWNLOAD_BYTES).bufferedReader(Charsets.UTF_8).use { reader ->
                                parsedCount = parseAndSaveM3UFromReader(playlist.id, reader, onProgress)
                            }
                            if (parsedCount > 0) {
                                Log.d(TAG, "Successfully synced ${playlist.name} with UA '$userAgent': $parsedCount items")
                                return@withLock parsedCount
                            } else {
                                Log.w(TAG, "Playlist ${playlist.name} parsed 0 items with UA '$userAgent'")
                            }
                        }
                    } else {
                        lastErrorMessage = "HTTP $code ${response.message}"
                        Log.w(TAG, "Sync attempt for ${playlist.name} with UA '$userAgent' returned HTTP $code")
                    }
                } catch (ce: CancellationException) {
                    try {
                        iptvDao.clearStagingItems(playlist.id)
                    } catch (_: Exception) {}
                    throw ce
                } catch (e: Exception) {
                    lastErrorMessage = e.message
                    Log.w(TAG, "Sync attempt for ${playlist.name} with UA '$userAgent' failed: ${e.message}")
                    val currentCount = iptvDao.getItemsCountForPlaylist(playlist.id)
                    if (currentCount > 0) {
                        Log.d(TAG, "Sync partially completed with $currentCount items. Returning saved content without retry.")
                        return@withLock currentCount
                    }
                } finally {
                    try {
                        response?.close()
                    } catch (_: Exception) {}
                }
            }

            // Fallback check: if server returned 403 or client error, check if existing items exist in DB
            val existingCount = iptvDao.getItemsCountForPlaylist(playlist.id)
            if (existingCount > 0) {
                Log.w(TAG, "Remote sync for '${playlist.name}' failed ($lastErrorMessage). Preserving $existingCount cached channels.")
                return@withLock existingCount
            } else {
                Log.w(TAG, "Sync for ${playlist.name} could not reach remote stream ($lastErrorMessage).")
                throw IOException("Playlist erişim reddi veya bağlantı hatası ($lastErrorMessage). Lütfen IPTV bağlantınızı kontrol edin.")
            }
        }
    }

    suspend fun fetchFromNetwork(): List<IPTVItem> = withContext(Dispatchers.IO) {
        val userPlaylists = iptvDao.getAllPlaylists()
        if (userPlaylists.isNotEmpty()) {
            var firstException: Exception? = null
            for (playlist in userPlaylists) {
                try {
                    syncPlaylist(playlist)
                } catch (e: Exception) {
                    Log.w(TAG, "Sync for ${playlist.name} failed during fetchFromNetwork: ${e.message}")
                    if (firstException == null) firstException = e
                }
            }
            val newlyLoaded = iptvDao.getAllItemsDirect()
            if (newlyLoaded.isNotEmpty()) {
                return@withContext newlyLoaded
            }
            if (firstException != null) {
                throw firstException
            }
            return@withContext emptyList()
        }
        emptyList()
    }

    suspend fun getItemsByTypeDirect(type: String): List<IPTVItem> = iptvDao.getItemsByTypeDirect(type)

    suspend fun findSeriesEpisodesByTitleLike(titlePattern: String): List<IPTVItem> =
        iptvDao.findSeriesEpisodesByTitleLike(titlePattern)

    /**
     * Xtream hesabının (sunucu, kullanıcı, şifre) bilgisi: kayıtta işaretliyse ya da adres bir Xtream
     * get.php adresiyse. Örn. http://host:8080/get.php?username=u&password=p&type=m3u_plus
     */
    internal fun xtreamCredentialsOf(playlist: Playlist): Triple<String, String, String>? {
        val uri = try { java.net.URI(playlist.url.trim()) } catch (e: Exception) { return null }
        val isXtreamUrl = uri.path?.endsWith("/get.php", ignoreCase = true) == true
        if (!playlist.isXtream && !isXtreamUrl) return null
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val user = playlist.xtreamUsername?.takeIf { it.isNotBlank() } ?: query["username"] ?: return null
        val pass = playlist.xtreamPassword?.takeIf { it.isNotBlank() } ?: query["password"] ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return Triple("$scheme://$host$port", user, pass)
    }

    suspend fun importXtreamDirectly(
        context: android.content.Context,
        playlistName: String,
        hostRaw: String,
        user: String,
        pass: String,
        /** Yenilemede mevcut liste kaydı (adresi farklı biçimde kaydedilmiş olsa da yeni liste açılmaz). */
        existingPlaylistId: Int? = null,
        onProgress: (Map<String, Int>) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val host = hostRaw.trim().let {
            val withProtocol = if (it.startsWith("http", ignoreCase = true)) it else "http://$it"
            withProtocol.trimEnd('/')
        }
        val markerUrl = "$host/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
        val xtreamEpgUrl = "$host/xmltv.php?username=$user&password=$pass"

        val existing = existingPlaylistId?.let { iptvDao.getPlaylistById(it) } ?: iptvDao.findPlaylistByUrl(markerUrl)
        val playlistId = existing?.id ?: iptvDao.insertPlaylist(
            Playlist(name = playlistName, url = markerUrl, isXtream = true, xtreamUsername = user, xtreamPassword = pass, epgUrl = xtreamEpgUrl)
        ).toInt()
        if (existing != null && (!existing.isXtream || existing.xtreamUsername != user || existing.epgUrl != xtreamEpgUrl)) {
            iptvDao.updatePlaylist(existing.copy(isXtream = true, xtreamUsername = user, xtreamPassword = pass, epgUrl = xtreamEpgUrl))
        }

        val credCarrier = IPTVItem(
            id = 0, playlistId = playlistId, name = "", cleanedName = "",
            logoUrl = null, category = "",
            streamUrl = "$host/movie/$user/$pass/1", type = "MOVIE"
        )

        var totalCount = 0
        val typeCounts = mutableMapOf<String, Int>()

        try {
            val liveItems = com.example.data.api.MetadataEnricher.fetchXtreamLiveStreamsAsItems(context, host, user, pass, playlistId)
            if (liveItems.isNotEmpty()) {
                iptvDao.deleteItemsByPlaylistAndType(playlistId, "LIVE")
                iptvDao.insertItems(liveItems)
                trimOversizedItemFields()
                totalCount += liveItems.size
                typeCounts["LIVE"] = liveItems.size
                onProgress(typeCounts.toMap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "importXtreamDirectly canlı hata: ${e.message}")
        }

        try {
            val movieItems = com.example.data.api.MetadataEnricher.fetchXtreamVodStreamsAsItems(context, credCarrier)
            if (movieItems.isNotEmpty()) {
                val moviesWithPlaylist = movieItems.map { it.copy(playlistId = playlistId) }
                iptvDao.deleteItemsByPlaylistAndType(playlistId, "MOVIE")
                iptvDao.insertItems(moviesWithPlaylist)
                trimOversizedItemFields()
                totalCount += moviesWithPlaylist.size
                typeCounts["MOVIE"] = moviesWithPlaylist.size
                onProgress(typeCounts.toMap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "importXtreamDirectly film hata: ${e.message}")
        }

        try {
            val seriesCatalog = com.example.data.api.MetadataEnricher.fetchFullXtreamSeriesCatalog(context, credCarrier)
            if (seriesCatalog.isNotEmpty()) {
                iptvDao.clearXtreamSeriesCatalog()
                iptvDao.insertXtreamSeriesCatalog(seriesCatalog)
                totalCount += seriesCatalog.size
                typeCounts["SERIES"] = seriesCatalog.size
                onProgress(typeCounts.toMap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "importXtreamDirectly dizi kataloğu hata: ${e.message}")
        }

        totalCount
    }

    suspend fun replaceAllMovies(items: List<IPTVItem>) {
        iptvDao.deleteAllMovies()
        iptvDao.insertItems(items)
        trimOversizedItemFields()
    }
    fun getItemById(id: Int): Flow<IPTVItem?> = iptvDao.getItemByIdFlow(id)
    suspend fun getItemByIdDirect(id: Int): IPTVItem? = iptvDao.getItemById(id)
    fun searchItems(query: String): Flow<List<IPTVItem>> = iptvDao.searchItemsFlow("%$query%")

    suspend fun searchCollectionItems(query: String, limit: Int = 20): List<IPTVItem> {
        val wildcard = "%${query.trim()}%"
        // Eşleşme yoksa artık alakasız "en yüksek puanlı filmler" listesine sessizce
        // düşülmüyor; boş liste dönerse çağıran taraf bunu dürüstçe gösterir.
        return iptvDao.searchCollectionItems(wildcard, limit)
    }

    suspend fun getTopRatedMovies(minRating: Double = 7.5, limit: Int = 12): List<IPTVItem> {
        return iptvDao.getTopRatedMovies(minRating, limit)
    }

    suspend fun getEpisodesForShowTitle(showTitle: String, limit: Int = 100): List<IPTVItem> {
        return iptvDao.getEpisodesForShowTitle("%${showTitle.trim()}%", limit)
    }

    suspend fun getPopularCatalogPool(limit: Int = 200): List<IPTVItem> {
        return iptvDao.getPopularCatalogPool(limit)
    }

    suspend fun getPopularCatalogPoolByType(itemType: String, limit: Int = 200): List<IPTVItem> {
        return iptvDao.getPopularCatalogPoolByType(itemType, limit)
    }

    suspend fun getSimilarItemsByCategory(itemType: String, category: String, genre: String, limit: Int = 20): List<IPTVItem> {
        return iptvDao.getSimilarItemsByCategory(itemType, "%${category.trim()}%", "%${genre.trim()}%", limit)
    }

    suspend fun findPlaylistByUrl(url: String): Playlist? = iptvDao.findPlaylistByUrl(url)

    suspend fun importRemotePlaylist(
        name: String,
        url: String,
        isXtream: Boolean = false,
        xtreamUsername: String? = null,
        xtreamPassword: String? = null,
        onProgress: (Map<String, Int>) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val temporaryPlaylist = Playlist(
            name = name,
            url = url,
            isXtream = isXtream,
            xtreamUsername = xtreamUsername,
            xtreamPassword = xtreamPassword
        )

        val existing = iptvDao.findPlaylistByUrl(url)
        if (existing != null && (existing.isXtream != isXtream || existing.xtreamUsername != xtreamUsername)) {
            iptvDao.updatePlaylist(existing.copy(isXtream = isXtream, xtreamUsername = xtreamUsername, xtreamPassword = xtreamPassword))
        }

        val playlistId = existing?.id
            ?: iptvDao.insertPlaylist(temporaryPlaylist).toInt()

        val playlist = if (existing != null) {
            existing.copy(isXtream = isXtream, xtreamUsername = xtreamUsername, xtreamPassword = xtreamPassword)
        } else {
            temporaryPlaylist.copy(id = playlistId)
        }

        try {
            val count = syncPlaylist(playlist, onProgress)

            if (count <= 0 && existing == null) {
                iptvDao.clearStagingItems(playlistId)
                iptvDao.deletePlaylist(playlistId)
            }

            count
        } catch (ce: CancellationException) {
            if (existing == null) {
                try {
                    iptvDao.clearStagingItems(playlistId)
                    iptvDao.deletePlaylist(playlistId)
                } catch (_: Exception) {}
            }
            throw ce
        } catch (e: Exception) {
            if (existing == null) {
                try {
                    iptvDao.clearStagingItems(playlistId)
                    iptvDao.deletePlaylist(playlistId)
                } catch (_: Exception) {}
            }
            throw e
        }
    }

    suspend fun insertPlaylist(playlist: Playlist): Long = iptvDao.insertPlaylist(playlist)
    suspend fun deletePlaylist(playlistId: Int) {
        iptvDao.deleteItemsByPlaylist(playlistId)
        iptvDao.clearStagingItems(playlistId)
        iptvDao.deletePlaylist(playlistId)
    }

    suspend fun clearPlaylistItems(playlistId: Int) {
        iptvDao.deleteItemsByPlaylist(playlistId)
        iptvDao.clearStagingItems(playlistId)
    }

    suspend fun clearPlaylists() {
        try {
            iptvDao.deleteAllPlaylists()
        } catch (e: Exception) {
            Log.e(TAG, "clearPlaylists: deleteAllPlaylists başarısız oldu", e)
        }
        try {
            iptvDao.deleteAllIPTVItems()
        } catch (e: Exception) {
            Log.e(TAG, "clearPlaylists: deleteAllIPTVItems başarısız oldu", e)
        }
    }

    suspend fun clearCorruptedCache(context: android.content.Context) = withContext(Dispatchers.IO) {
        try {
            com.example.util.StorageOptimizer.emergencyResetCorruptedData(context)
        } catch (e: Throwable) {
            Log.e(TAG, "Error during clearCorruptedCache", e)
        }
    }

    suspend fun toggleFavorite(itemId: Int, isFavorite: Boolean) {
        iptvDao.updateFavorite(itemId, isFavorite)
    }

    suspend fun saveContinueWatching(item: IPTVItem, progressSeconds: Long, totalSeconds: Long) {
        // Xtream bölümlerinde ad yalnızca dizi adıdır ("4400"); sezon/bölüm ayrı alanlarda. İzlemeye devam
        // listesinde doğru bölümün görünmesi için bu durumda ada "S01E03" eklenir.
        val season = item.season
        val episode = item.episode
        val needsEpisodeTag = item.type == "SERIES" && season != null && episode != null && episode > 0 &&
            com.example.data.model.SeriesParser.parseEpisodeInfo(item.cleanedName).let { it == null || it.season != season || it.episode != episode }
        val savedName = if (needsEpisodeTag) "%s S%02dE%02d".format(item.cleanedName.ifBlank { item.name }, season, episode) else item.cleanedName
        val continueWatching = ContinueWatching(
            itemId = item.id,
            itemName = savedName,
            itemType = item.type,
            itemLogo = item.logoUrl,
            streamUrl = item.streamUrl,
            category = item.category,
            progressSeconds = progressSeconds,
            totalSeconds = totalSeconds,
            lastPlayedAt = System.currentTimeMillis()
        )
        iptvDao.insertContinueWatchingWithLimit(continueWatching, 50)
    }

    suspend fun deleteContinueWatching(itemId: Int) {
        iptvDao.deleteContinueWatchingByItemId(itemId)
    }

    suspend fun saveSearchQuery(query: String) {
        if (query.isNotBlank()) {
            iptvDao.insertSearchQueryWithLimit(SearchHistory(query = query.trim()), 100)
        }
    }

    fun getRecentSearchQueriesFlow(limit: Int = 5): Flow<List<SearchHistory>> = iptvDao.getRecentSearchQueriesFlow(limit)
    suspend fun deleteSearchQuery(query: String) = iptvDao.deleteSearchQuery(query)
    suspend fun clearSearchHistory() = iptvDao.clearSearchHistory()

    // AI Recommendation History
    fun getAiRecommendationHistoryFlow(): Flow<List<AiRecommendationHistory>> = iptvDao.getAllAiRecommendationHistoryFlow()
    suspend fun saveAiRecommendationHistory(history: AiRecommendationHistory) = iptvDao.insertAiRecommendationHistory(history)
    suspend fun deleteAiRecommendationHistory(id: Int) = iptvDao.deleteAiRecommendationHistory(id)
    suspend fun clearAllAiRecommendationHistory() = iptvDao.clearAllAiRecommendationHistory()

    /**
     * "AI ile Önceki Bölümleri Özetle" için: aynı dizinin, kullanıcının GERÇEKTEN
     * izlediği (progresi %70'i geçmiş) ve hedef bölümden ÖNCE gelen (sezon, bölüm)
     * çiftlerini döndürür. Spoiler sızmasın diye hedef bölüm ve sonrası hariç tutulur.
     */
    suspend fun getWatchedEpisodesForShow(
        showTitle: String,
        beforeSeason: Int,
        beforeEpisode: Int
    ): List<Pair<Int, Int>> {
        val allWatched = iptvDao.getAllContinueWatchingOnce()
        return allWatched.mapNotNull { cw ->
            val parsed = com.example.data.model.SeriesParser.parseEpisodeInfo(cw.itemName)
                ?: return@mapNotNull null
            if (!parsed.showTitle.equals(showTitle, ignoreCase = true)) return@mapNotNull null

            val watchedEnough = cw.totalSeconds <= 0L ||
                (cw.progressSeconds.toDouble() / cw.totalSeconds.toDouble()) > 0.7
            if (!watchedEnough) return@mapNotNull null

            val isBeforeTarget = parsed.season < beforeSeason ||
                (parsed.season == beforeSeason && parsed.episode < beforeEpisode)
            if (!isBeforeTarget) return@mapNotNull null

            Pair(parsed.season, parsed.episode)
        }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }

    suspend fun updateItemMetadata(itemId: Int, summary: String, cast: String, director: String, rating: Double, logoUrl: String?, trailerUrl: String?, releaseDate: String = "", genre: String = "") {
        iptvDao.updateItemMetadata(itemId, summary, cast, director, rating, logoUrl, trailerUrl, releaseDate, genre)
    }

    suspend fun updateItemsMetadata(updates: List<com.example.data.db.ItemMetadataUpdate>) {
        if (updates.isNotEmpty()) iptvDao.updateItemsMetadata(updates)
    }

    suspend fun updateSummary(itemId: Int, summary: String) {
        iptvDao.updateSummary(itemId, summary)
    }

    suspend fun updateSummaryForIds(ids: List<Int>, summary: String) {
        if (ids.isNotEmpty()) {
            iptvDao.updateSummaryForIds(ids, summary)
        }
    }

    suspend fun getCachedPersonDetails(name: String): PersonDetailsEntity? {
        return iptvDao.getPersonDetails(name.lowercase(Locale.ROOT).trim())
    }

    suspend fun savePersonDetails(person: PersonDetailsEntity) {
        iptvDao.insertPersonDetailsWithLimit(person, 3000)
    }

    suspend fun getCachedTmdb(sourceKey: String): com.example.data.model.tmdb.TmdbCacheEntity? {
        return try {
            iptvDao.getTmdbCache(sourceKey)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching cached TMDB entity: ${e.message}")
            null
        }
    }

    /**
     * TMDB önbelleklerinin birikmesini önler (kullanıcı verisine dokunmaz):
     * süresi dolmuş film bilgilerini siler, oyuncu/yönetmen önbelleğini [personCacheLimit] kayda indirir
     * (bazı kayıt yolları sınırı atlıyordu).
     */
    suspend fun trimTmdbCaches(cutoffTime: Long, personCacheLimit: Int = 3000) {
        try {
            iptvDao.deleteExpiredTmdbCache(cutoffTime)
            iptvDao.trimPersonCache(personCacheLimit)
        } catch (e: Exception) {
            Log.w("IPTVRepository", "TMDB cache cleanup failed", e)
        }
    }

    suspend fun saveTmdbCache(entity: com.example.data.model.tmdb.TmdbCacheEntity) {
        try {
            iptvDao.insertTmdbCache(entity)
        } catch (e: Exception) {
            Log.w(TAG, "Error saving TMDB cache entity: ${e.message}")
        }
    }

    private fun isSeriesName(rawName: String, categoryStronglyIndicatesMovie: Boolean = false): Boolean {
        val strongPatterns = listOf(
            Regex("(?i)\\bS(\\d+)\\s*E(\\d+)\\b"),
            Regex("(?i)\\b(\\d+)\\s*x\\s*(\\d+)\\b"),
            Regex("(?i)\\b(?:Season|Sezon|S)\\s*(\\d+)\\s*(?:Episode|Bölüm|E)\\s*(\\d+)\\b")
        )
        if (strongPatterns.any { it.containsMatchIn(rawName) }) return true
        if (categoryStronglyIndicatesMovie) return false
        return Regex("(?i)\\b(?:Bölüm|Episode|Part)\\s*(\\d+)\\b").containsMatchIn(rawName)
    }

    /**
     * Parses M3U string content by wrapping in a StringReader to reuse the streaming staging pipeline.
     */
    suspend fun parseAndSaveM3U(playlistId: Int, m3uContent: String): Int = withContext(Dispatchers.IO) {
        if (m3uContent.isBlank()) return@withContext 0
        StringReader(m3uContent).buffered().use { reader ->
            parseAndSaveM3UFromReader(playlistId, reader)
        }
    }

    suspend fun parseAndSaveM3UStream(playlistId: Int, inputStream: InputStream): Int = withContext(Dispatchers.IO) {
        inputStream.bufferedReader().use { reader ->
            parseAndSaveM3UFromReader(playlistId, reader)
        }
    }

    /**
     * Staging Table Based Streaming Parser:
     * 1. Clears staging table for this playlistId.
     * 2. Streams line-by-line, parsing entries without accumulating entire dataset in RAM.
     * 3. Inserts in batches (250 items) into staging table.
     * 4. Only if parsing completes successfully and item count > 0, atomically promotes staging to active table.
     * 5. If 0 items parsed or error occurs, staging is cleaned up and old active data is preserved intact.
     */
    suspend fun parseAndSaveM3UFromReader(
        playlistId: Int,
        reader: BufferedReader,
        onProgress: (Map<String, Int>) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        iptvDao.clearStagingItems(playlistId)

        val otherPlaylistIds = iptvDao.getExistingItemIdsExcludingPlaylist(playlistId).toHashSet()
        val usedIdsInPlaylist = HashSet<Int>()

        var currentExtInf: String? = null
        var currentExtGrp: String? = null
        val stagingBatch = ArrayList<IPTVItemStaging>(BATCH_SIZE)

        val typeCounts = mutableMapOf("LIVE" to 0, "MOVIE" to 0, "SERIES" to 0, "RADIO" to 0)
        var sinceLastReport = 0
        var discoveredEpgUrl: String? = null

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
                    discoveredEpgUrl = parseAttributeFast(trimmed, "url-tvg")
                        ?: parseAttributeFast(trimmed, "x-tvg-url")
                } else if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    currentExtInf = trimmed
                } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                    val grp = trimmed.substringAfter(":").trim()
                    if (grp.isNotEmpty()) {
                        currentExtGrp = grp
                    }
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentExtInf != null) {
                    val streamUrl = trimmed
                    val extInf = currentExtInf
                    val extGrp = currentExtGrp
                    currentExtInf = null
                    currentExtGrp = null
                    val stagingItem = parseM3uEntryFastToStaging(extInf, streamUrl, playlistId, otherPlaylistIds, usedIdsInPlaylist)
                    if (stagingItem != null) {
                        typeCounts[stagingItem.type] = (typeCounts[stagingItem.type] ?: 0) + 1
                        sinceLastReport++
                        if (sinceLastReport >= 25) {
                            onProgress(typeCounts.toMap())
                            sinceLastReport = 0
                        }
                        stagingBatch.add(stagingItem)
                        if (stagingBatch.size >= BATCH_SIZE) {
                            iptvDao.insertStagingItems(stagingBatch)
                            stagingBatch.clear()
                        }
                    }
                }
                line = reader.readLine()
            }
        } catch (ce: CancellationException) {
            try {
                iptvDao.clearStagingItems(playlistId)
            } catch (_: Exception) {}
            throw ce
        } catch (e: Exception) {
            // Bağlantı yarıda koptu ya da boyut sınırı aşıldı: yarım listeyi aktif tabloya taşıma,
            // kullanıcının mevcut listesini koru. syncPlaylist bu hatayı yakalayıp eski kanalları döndürür.
            Log.w(TAG, "M3U stream interrupted, keeping existing items: ${e.message}")
            try {
                iptvDao.clearStagingItems(playlistId)
            } catch (_: Exception) {}
            throw e
        }

        if (stagingBatch.isNotEmpty()) {
            try {
                iptvDao.insertStagingItems(stagingBatch)
                stagingBatch.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Final staging batch insert error: ${e.message}")
            }
        }
        onProgress(typeCounts.toMap())

        if (!discoveredEpgUrl.isNullOrBlank()) {
            try {
                iptvDao.updatePlaylistEpgUrl(playlistId, discoveredEpgUrl)
            } catch (e: Exception) {
                Log.w(TAG, "EPG url kaydedilemedi: ${e.message}")
            }
        }

        val stagingCount = iptvDao.getStagingItemCount(playlistId)
        return@withContext if (stagingCount > 0) {
            val promotedCount = iptvDao.promoteStagingToActive(playlistId)
            Log.d(TAG, "Promoted $promotedCount items from staging to active database for playlist $playlistId")
            promotedCount
        } else {
            Log.w(TAG, "Staging count is 0 for playlist $playlistId. Preserving existing database records.")
            iptvDao.clearStagingItems(playlistId)
            0
        }
    }

    private fun parseM3uEntryFastToStaging(
        extInf: String,
        streamUrl: String,
        playlistId: Int,
        otherPlaylistIds: Set<Int> = emptySet(),
        usedIdsInPlaylist: MutableSet<Int> = mutableSetOf()
    ): IPTVItemStaging? {
        val item = parseM3uEntryFast(extInf, streamUrl, playlistId, otherPlaylistIds, usedIdsInPlaylist) ?: return null
        return IPTVItemStaging(
            id = item.id,
            playlistId = item.playlistId,
            name = item.name,
            cleanedName = item.cleanedName,
            logoUrl = item.logoUrl,
            streamUrl = item.streamUrl,
            category = item.category,
            type = item.type,
            rating = item.rating,
            summary = item.summary,
            cast = item.cast,
            director = item.director,
            trailerUrl = item.trailerUrl,
            isFavorite = item.isFavorite,
            season = item.season,
            episode = item.episode,
            releaseDate = item.releaseDate,
            genre = item.genre,
            tvgId = item.tvgId
        )
    }

    private fun parseM3uEntryFast(
        extInf: String,
        streamUrl: String,
        playlistId: Int,
        otherPlaylistIds: Set<Int> = emptySet(),
        usedIdsInPlaylist: MutableSet<Int> = mutableSetOf()
    ): IPTVItem? {
        val trimmedUrl = streamUrl.trim()
        if (trimmedUrl.isBlank() || !trimmedUrl.contains("://")) {
            return null
        }

        val commaIndex = extInf.lastIndexOf(',')
        val rawName = if (commaIndex != -1 && commaIndex < extInf.length - 1) {
            extInf.substring(commaIndex + 1).trim()
        } else {
            "Kanal"
        }

        val tvgLogo = parseAttributeFast(extInf, "tvg-logo")
        val tvgId = parseAttributeFast(extInf, "tvg-id")
        val groupTitle = parseAttributeFast(extInf, "group-title") ?: "Diğer"
        val cleanedName = cleanChannelName(rawName)

        val lowerCat = groupTitle.lowercase(Locale.ROOT)
        val lowerName = rawName.lowercase(Locale.ROOT)
        val lowerUrl = streamUrl.lowercase(Locale.ROOT)

        val isRad = lowerCat.contains("radyo") || lowerCat.contains("radio") || lowerCat.contains("radyolar") ||
                lowerName.contains("radyo") || lowerName.contains("radio") || lowerName.contains(" fm") || lowerName.endsWith("fm")

        val hasLiveIndicatorInName = lowerName.contains("7/24") || lowerName.contains("7-24") ||
                lowerName.contains("24 saat") || lowerName.startsWith("canlı") || lowerName.contains(" canlı") ||
                lowerName.contains("sport") || lowerName.contains("spor ") || lowerName.endsWith("spor")

        val hasSeriesUrlPattern = lowerUrl.contains("/series/") || lowerUrl.contains("/series?")
        val hasVodUrlPattern = lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mkv") || lowerUrl.endsWith(".avi") ||
                lowerUrl.contains("/movie/") || lowerUrl.contains("/vod/")

        val categoryStronglyIndicatesMovie = (lowerCat.contains("film") || lowerCat.contains("sinema") || lowerCat.contains("movie") || lowerCat.contains("cinema")) &&
                !lowerCat.contains("dizi") && !lowerCat.contains("series")

        val isSeries = !hasLiveIndicatorInName && (
            isSeriesName(rawName, categoryStronglyIndicatesMovie) || hasSeriesUrlPattern ||
            lowerCat.contains("dizi") || lowerCat.contains("series") || lowerCat.contains("season")
        )

        val isPlatformFolder = lowerCat.contains("amazon") || lowerCat.contains("prime") || lowerCat.contains("netflix") || lowerCat.contains("disney") || lowerCat.contains("exxen") || lowerCat.contains("blutv") || lowerCat.contains("gain") || lowerCat.contains("tod") || lowerCat.contains("apple") || lowerCat.contains("hbo")

        val isVod = !hasLiveIndicatorInName && (
            hasVodUrlPattern || lowerCat.contains("sinema") || lowerCat.contains("film") || lowerCat.contains("movie") || lowerCat.contains("vod") || lowerCat.contains("cinema") ||
            (isPlatformFolder && !isSeries)
        )

        if (!hasVodUrlPattern && !lowerCat.contains("sinema") && !lowerCat.contains("film") &&
            !lowerCat.contains("movie") && !lowerCat.contains("vod") && !lowerCat.contains("cinema") &&
            isPlatformFolder && !isSeries && !hasLiveIndicatorInName
        ) {
            if (ambiguousMovieClassificationSamples.size < 30) {
                ambiguousMovieClassificationSamples.add("$rawName [kategori: $groupTitle]")
            }
        }

        val type = when {
            isRad -> "RADIO"
            isSeries -> "SERIES"
            isVod -> "MOVIE"
            else -> "LIVE"
        }

        val rating = 0.0
        val seriesCover = if (isSeries) parseAttributeFast(extInf, "series-cover") else null
        val finalLogoUrl = ChannelLogoMapper.getLogoForChannel(cleanedName, groupTitle, seriesCover ?: tvgLogo)

        var parsedSeason: Int? = null
        var parsedEpisode: Int? = null
        if (type == "SERIES") {
            val parsedInfo = com.example.data.model.SeriesParser.parseEpisodeInfo(rawName)
            if (parsedInfo != null) {
                parsedSeason = parsedInfo.season
                parsedEpisode = parsedInfo.episode
            }
        }

        val baseId = generateDeterministicId(rawName, streamUrl)
        var finalId = baseId

        // Resolve ID collision: only alter ID if it conflicts with another playlist or an already-used ID in this playlist
        if (otherPlaylistIds.contains(finalId) || usedIdsInPlaylist.contains(finalId)) {
            var salt = 1
            while (otherPlaylistIds.contains(finalId) || usedIdsInPlaylist.contains(finalId)) {
                finalId = generateDeterministicId("${playlistId}_${salt}_${rawName}", streamUrl)
                if (finalId == 0) finalId = 1
                salt++
            }
        }
        usedIdsInPlaylist.add(finalId)

        return IPTVItem(
            id = finalId,
            playlistId = playlistId,
            name = rawName,
            cleanedName = cleanedName,
            logoUrl = finalLogoUrl,
            streamUrl = streamUrl,
            category = groupTitle,
            type = type,
            rating = rating,
            summary = "",
            cast = "",
            director = "",
            trailerUrl = null,
            season = parsedSeason,
            episode = parsedEpisode,
            tvgId = tvgId
        )
    }

    private fun parseAttributeFast(line: String, attribute: String): String? {
        val keys = when (attribute) {
            "group-title" -> listOf("group-title=", "group=")
            "tvg-logo" -> listOf("tvg-logo=", "logo=", "url-logo=")
            "series-cover" -> listOf("series-cover=", "cover=", "series_cover=", "series-logo=", "tvg-cover=", "poster=")
            "tvg-name" -> listOf("tvg-name=", "name=")
            else -> listOf("$attribute=")
        }
        for (key in keys) {
            val idx = line.indexOf(key, ignoreCase = true)
            if (idx != -1) {
                val afterKey = idx + key.length
                if (afterKey < line.length) {
                    val firstChar = line[afterKey]
                    if (firstChar == '"' || firstChar == '\'') {
                        val endQuote = line.indexOf(firstChar, afterKey + 1)
                        if (endQuote != -1) {
                            return line.substring(afterKey + 1, endQuote)
                        }
                    } else {
                        var spaceIdx = line.indexOf(' ', afterKey)
                        if (spaceIdx == -1) spaceIdx = line.length
                        return line.substring(afterKey, spaceIdx)
                    }
                }
            }
        }
        return null
    }

    // Akıllı M3U Eşleştirme (Smart IPTV Channel Name Cleaner)
    fun cleanChannelName(rawName: String): String {
        var name = rawName

        // 1. Remove brackets and content inside brackets, e.g. [TR] or (FHD)
        name = name.replace(Regex("\\[.*?\\]"), "")
        name = name.replace(Regex("\\(.*?\\)"), "")

        // 2. Remove common country-prefixes like "TR |", "DE :", "FR -", "TR:", "EN |", "TR - "
        val countryPrefixRegex = Regex("^(?i)(TR|EN|DE|FR|NL|ES|IT|AR|RU|PL|US|UK|GR|EX-YU)\\s*[:\\|\\-]\\s*")
        name = name.replace(countryPrefixRegex, "")

        // 3. Remove common quality suffixes like HD, FHD, UHD, SD, 4K, 1080p, 720p, HEVC, H265, H.264
        val qualityRegex = Regex("(?i)\\b(HD|FHD|UHD|SD|4K|1080P|720P|HEVC|H265|H264|RAW|BACKUP|VIP|PREMIUM)\\b")
        name = name.replace(qualityRegex, "")

        // 4. Strip extra whitespace or double spacing
        name = name.replace(Regex("\\s+"), " ").trim()

        // 5. Title casing / clean casing adjustments (e.g. BEIN SPORTS -> BeIN Sports)
        name = cleanChannelCasing(name)

        return name.ifEmpty { rawName }
    }

    /**
     * Yalnızca küçük harfle başlayan kelimelerin ilk harfini büyütür. Ad içinde geçen kelimeye göre kanal/marka
     * adına çevirme yapılmaz: bu kural film ve dizi adlarını da bozuyordu ("Now You See Me" → "NOW").
     */
    private fun cleanChannelCasing(input: String): String =
        input.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
}
