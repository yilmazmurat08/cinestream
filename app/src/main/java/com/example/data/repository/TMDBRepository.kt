package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.api.MetadataEnricher
import com.example.data.api.NetworkModule
import com.example.data.api.PersonDetails
import com.example.data.api.TmdbService
import com.example.data.model.IPTVItem
import com.example.data.model.PersonDetailsEntity
import com.example.data.model.isPlaceholderCast
import com.example.data.model.isPlaceholderDirector
import com.example.data.model.isPlaceholderSummary
import com.example.data.model.tmdb.CastMember
import com.example.data.model.tmdb.TmdbCacheEntity
import com.example.data.model.tmdb.TmdbContentResult
import com.example.data.model.tmdb.TmdbMediaItem
import com.example.util.TitleNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Production-ready TMDB Repository for CineStream IPTV.
 * Features:
 * - Room-based 30-day persistent caching (TmdbCacheEntity)
 * - In-flight request deduplication (ConcurrentHashMap)
 * - Search by Movie/TV with year filtering & candidate scoring
 * - IMDb / External ID matching via find endpoint
 * - Combined details + credits using append_to_response=credits
 * - Turkish overview with English fallback for blank descriptions
 * - Strict fallback preservation (TMDB -> Local IPTV -> Safe Defaults)
 * - Never selects random trending items on search failure
 */
class TMDBRepository(
    private val context: Context,
    private val iptvRepository: IPTVRepository? = null,
    private val tmdbService: TmdbService = NetworkModule.tmdbService
) {
    companion object {
        private const val TAG = "TMDBRepository"
        private const val BASE_IMAGE_URL = "https://image.tmdb.org/t/p/"

        private const val TMDB_KEY_MASK = "cinestream_tmdb_2026"
        private const val TMDB_KEY_ENCODED = "VV1YXEVARQZZX2dFDlEGaVQAAgdSXlcDRhZGVVdfaxA="

        fun resolveEmbeddedTmdbKey(): String {
            return try {
                val decodedBytes = android.util.Base64.decode(TMDB_KEY_ENCODED, android.util.Base64.DEFAULT)
                val maskBytes = TMDB_KEY_MASK.toByteArray(Charsets.UTF_8)
                val resultBytes = ByteArray(decodedBytes.size) { i ->
                    (decodedBytes[i].toInt() xor maskBytes[i % maskBytes.size].toInt()).toByte()
                }
                String(resultBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }

        const val SIZE_POSTER = "w780"
        const val SIZE_BACKDROP = "w1280"
        const val SIZE_PROFILE = "w342"

        // 30 Days Cache TTL in Milliseconds
        const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L

        fun buildImageUrl(path: String?, size: String = SIZE_POSTER): String? {
            if (path.isNullOrBlank()) return null
            return if (path.startsWith("http://") || path.startsWith("https://")) path else "$BASE_IMAGE_URL$size$path"
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<TmdbContentResult?>>()

    private fun getApiKey(providedKey: String = ""): String {
        if (providedKey.isNotBlank() && providedKey != "placeholder" && providedKey != "MY_TMDB_API_KEY") {
            return providedKey
        }
        val embedded = resolveEmbeddedTmdbKey()
        if (embedded.isNotBlank()) {
            return embedded
        }
        return BuildConfig.TMDB_API_KEY
    }

    private fun buildSourceKey(type: String, normalizedTitle: String, year: String?, imdbId: String? = null): String {
        return if (!imdbId.isNullOrBlank()) {
            "IMDB|${imdbId.lowercase(Locale.ROOT).trim()}"
        } else {
            val typeKey = if (type.equals("SERIES", ignoreCase = true) || type.equals("TV", ignoreCase = true)) "SERIES" else "MOVIE"
            val titleKey = normalizedTitle.lowercase(Locale.ROOT).trim()
            val yearKey = year?.trim() ?: ""
            "$typeKey|$titleKey|$yearKey"
        }
    }

    /**
     * TMDb API üzerinden Film veya Dizi için tek fonksiyonla tam veri çeker.
     * Checks Room DB first (30-day TTL), deduplicates concurrent in-flight requests,
     * and returns structured metadata with credits, cast, and director.
     */
    suspend fun getTmdbFullData(
        title: String,
        year: String?,
        isMovie: Boolean
    ): TmdbContentResult? = withContext(Dispatchers.IO) {
        val norm = TitleNormalizer.normalize(title, year)
        val normalizedTitle = norm.cleanTitle.ifBlank { title.trim() }
        val effectiveYear = norm.year ?: year?.takeIf { it.isNotBlank() }
        val imdbId = norm.imdbId
        val mediaType = if (isMovie) "MOVIE" else "SERIES"
        val sourceKey = buildSourceKey(mediaType, normalizedTitle, effectiveYear, imdbId)

        val apiKey = getApiKey()
        if (normalizedTitle.isBlank() && imdbId.isNullOrBlank()) {
            return@withContext null
        }

        // 1. Check Room Database Cache first
        val cachedEntity = try {
            iptvRepository?.getCachedTmdb(sourceKey)
        } catch (e: Exception) {
            null
        }

        if (cachedEntity != null) {
            val isFresh = (System.currentTimeMillis() - cachedEntity.updatedAt) < CACHE_TTL_MS
            if (isFresh) {
                return@withContext cachedEntity.toTmdbContentResult()
            }
        }

        // If API key is empty, return cached if available or null
        if (apiKey.isBlank()) {
            return@withContext cachedEntity?.toTmdbContentResult()
        }

        // 2. Concurrency Deduplication: Reuse in-flight Deferred for identical query
        val deferred = inFlightRequests.compute(sourceKey) { _, existing ->
            if (existing != null && existing.isActive) {
                existing
            } else {
                repositoryScope.async {
                    try {
                        val networkResult = fetchFromNetworkInternal(
                            normalizedTitle = normalizedTitle,
                            year = effectiveYear,
                            isMovie = isMovie,
                            imdbId = imdbId,
                            apiKey = apiKey
                        )

                        if (networkResult != null) {
                            val newEntity = networkResult.toCacheEntity(
                                sourceKey = sourceKey,
                                normalizedTitle = normalizedTitle,
                                year = effectiveYear,
                                mediaType = mediaType
                            )
                            iptvRepository?.saveTmdbCache(newEntity)
                        }
                        networkResult
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.w(TAG, "Network fetch error for $sourceKey: ${e.message}")
                        null
                    } finally {
                        inFlightRequests.remove(sourceKey)
                    }
                }
            }
        }

        val result = try {
            deferred?.await()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            null
        }

        // Fallback to stale cached data if network failed
        result ?: cachedEntity?.toTmdbContentResult()
    }

    /**
     * Internal network fetch executing search -> scoring -> details with credits -> language fallback.
     */
    private suspend fun fetchFromNetworkInternal(
        normalizedTitle: String,
        year: String?,
        isMovie: Boolean,
        imdbId: String?,
        apiKey: String
    ): TmdbContentResult? {
        var matchedId: Int? = null
        var matchedMediaType = if (isMovie) "MOVIE" else "SERIES"

        // Step A: If IMDb ID is present, try /find/{external_id} first
        if (!imdbId.isNullOrBlank()) {
            try {
                val findResponse = tmdbService.findByExternalId(
                    externalId = imdbId,
                    externalSource = "imdb_id",
                    language = "tr-TR",
                    apiKey = apiKey
                )
                if (isMovie) {
                    val match = findResponse.movieResults.firstOrNull()
                    if (match != null) {
                        matchedId = match.id
                        matchedMediaType = "MOVIE"
                    }
                } else {
                    val match = findResponse.tvResults.firstOrNull()
                    if (match != null) {
                        matchedId = match.id
                        matchedMediaType = "SERIES"
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Find by external ID notice for $imdbId: ${e.message}")
            }
        }

        // Step B: Search by specific media type if not found by IMDb
        if (matchedId == null && normalizedTitle.isNotBlank()) {
            val candidateList = mutableListOf<TmdbMediaItem>()

            if (isMovie) {
                val movieSearch = try {
                    tmdbService.searchMovie(
                        query = normalizedTitle,
                        year = year,
                        language = "tr-TR",
                        apiKey = apiKey
                    )
                } catch (e: Exception) {
                    null
                }
                candidateList.addAll(movieSearch?.results ?: emptyList())

                // If year filter returned nothing, retry with year = null
                if (candidateList.isEmpty() && !year.isNullOrBlank()) {
                    val fallbackSearch = try {
                        tmdbService.searchMovie(
                            query = normalizedTitle,
                            year = null,
                            language = "tr-TR",
                            apiKey = apiKey
                        )
                    } catch (e: Exception) {
                        null
                    }
                    candidateList.addAll(fallbackSearch?.results ?: emptyList())
                }
            } else {
                val tvSearch = try {
                    tmdbService.searchTv(
                        query = normalizedTitle,
                        year = year,
                        language = "tr-TR",
                        apiKey = apiKey
                    )
                } catch (e: Exception) {
                    null
                }
                candidateList.addAll(tvSearch?.results ?: emptyList())

                // If year filter returned nothing, retry with year = null
                if (candidateList.isEmpty() && !year.isNullOrBlank()) {
                    val fallbackSearch = try {
                        tmdbService.searchTv(
                            query = normalizedTitle,
                            year = null,
                            language = "tr-TR",
                            apiKey = apiKey
                        )
                    } catch (e: Exception) {
                        null
                    }
                    candidateList.addAll(fallbackSearch?.results ?: emptyList())
                }
            }

            // Score candidates and pick best match
            val bestCandidate = candidateList
                .map { item -> item to scoreCandidate(item, normalizedTitle, year, matchedMediaType) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first

            matchedId = bestCandidate?.id
        }

        if (matchedId == null) {
            // NEVER fallback to random trending item!
            return null
        }

        // Step C: Fetch combined details and credits with append_to_response=credits
        return if (matchedMediaType == "MOVIE") {
            fetchMovieDetailsWithFallback(matchedId, apiKey)
        } else {
            fetchTvDetailsWithFallback(matchedId, apiKey)
        }
    }

    private suspend fun fetchMovieDetailsWithFallback(movieId: Int, apiKey: String): TmdbContentResult? {
        val detailsTr = try {
            tmdbService.getMovieDetailsWithCredits(
                id = movieId,
                language = "tr-TR",
                appendToResponse = "credits",
                apiKey = apiKey
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Movie details fetch failed for ID $movieId: ${e.message}")
            null
        } ?: return null

        var overview = detailsTr.overview?.trim() ?: ""

        // English overview fallback if Turkish is blank
        if (overview.isBlank()) {
            val detailsEn = try {
                tmdbService.getMovieDetails(id = movieId, language = "en-US", apiKey = apiKey)
            } catch (e: Exception) {
                null
            }
            if (!detailsEn?.overview.isNullOrBlank()) {
                overview = detailsEn?.overview?.trim().orEmpty()
            }
        }

        val credits = detailsTr.credits
        val castList = credits?.cast?.take(10)?.map { castItem ->
            CastMember(
                name = castItem.name,
                character = castItem.character,
                profilePath = buildImageUrl(castItem.profilePath, SIZE_PROFILE)
            )
        } ?: emptyList()

        val directorCrew = credits?.crew?.firstOrNull { it.job?.equals("Director", ignoreCase = true) == true }
            ?: credits?.crew?.firstOrNull { it.job?.equals("Producer", ignoreCase = true) == true }
        val directorName = directorCrew?.name
        val directorPhoto = buildImageUrl(directorCrew?.profilePath, SIZE_PROFILE)

        val genres = detailsTr.genres?.mapNotNull { it.name } ?: emptyList()

        return TmdbContentResult(
            tmdbId = detailsTr.id,
            title = detailsTr.title ?: detailsTr.originalTitle ?: "",
            overview = overview.ifBlank { null },
            backdropPath = buildImageUrl(detailsTr.backdropPath, SIZE_BACKDROP),
            posterPath = buildImageUrl(detailsTr.posterPath, SIZE_POSTER),
            castList = castList,
            directorName = directorName,
            directorPhoto = directorPhoto,
            voteAverage = detailsTr.voteAverage,
            releaseDate = detailsTr.releaseDate,
            genres = genres,
            voteCount = detailsTr.voteCount ?: 0
        )
    }

    private suspend fun fetchTvDetailsWithFallback(tvId: Int, apiKey: String): TmdbContentResult? {
        val detailsTr = try {
            tmdbService.getTvDetailsWithCredits(
                id = tvId,
                language = "tr-TR",
                appendToResponse = "credits",
                apiKey = apiKey
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "TV details fetch failed for ID $tvId: ${e.message}")
            null
        } ?: return null

        var overview = detailsTr.overview?.trim() ?: ""

        // English overview fallback if Turkish is blank
        if (overview.isBlank()) {
            val detailsEn = try {
                tmdbService.getTvDetails(id = tvId, language = "en-US", apiKey = apiKey)
            } catch (e: Exception) {
                null
            }
            if (!detailsEn?.overview.isNullOrBlank()) {
                overview = detailsEn?.overview?.trim().orEmpty()
            }
        }

        val credits = detailsTr.credits
        val castList = credits?.cast?.take(10)?.map { castItem ->
            CastMember(
                name = castItem.name,
                character = castItem.character,
                profilePath = buildImageUrl(castItem.profilePath, SIZE_PROFILE)
            )
        } ?: emptyList()

        var directorName: String? = null
        var directorPhoto: String? = null

        val creator = detailsTr.createdBy.firstOrNull()
        if (creator != null) {
            directorName = creator.name
            directorPhoto = buildImageUrl(creator.profilePath, SIZE_PROFILE)
        } else {
            val crewCreator = credits?.crew?.firstOrNull {
                it.job?.equals("Director", ignoreCase = true) == true ||
                it.job?.equals("Creator", ignoreCase = true) == true ||
                it.job?.equals("Executive Producer", ignoreCase = true) == true
            }
            directorName = crewCreator?.name
            directorPhoto = buildImageUrl(crewCreator?.profilePath, SIZE_PROFILE)
        }

        val genres = detailsTr.genres?.mapNotNull { it.name } ?: emptyList()

        return TmdbContentResult(
            tmdbId = detailsTr.id,
            title = detailsTr.name ?: detailsTr.originalName ?: "",
            overview = overview.ifBlank { null },
            backdropPath = buildImageUrl(detailsTr.backdropPath, SIZE_BACKDROP),
            posterPath = buildImageUrl(detailsTr.posterPath, SIZE_POSTER),
            castList = castList,
            directorName = directorName,
            directorPhoto = directorPhoto,
            voteAverage = detailsTr.voteAverage,
            releaseDate = detailsTr.firstAirDate,
            genres = genres,
            voteCount = detailsTr.voteCount ?: 0
        )
    }

    private fun scoreCandidate(
        item: TmdbMediaItem,
        normalizedTitle: String,
        year: String?,
        expectedType: String
    ): Double {
        var score = 0.0
        val candidateTitle = (item.title ?: item.name ?: "").trim()
        if (candidateTitle.isBlank()) return -1000.0

        if (item.mediaType != null) {
            if (expectedType == "MOVIE" && item.mediaType == "tv") score -= 100.0
            if (expectedType == "SERIES" && item.mediaType == "movie") score -= 100.0
            if (item.mediaType == "person") score -= 200.0
        }

        val candidateNorm = candidateTitle.lowercase(Locale.ROOT)
        val queryNorm = normalizedTitle.lowercase(Locale.ROOT)

        if (candidateTitle.equals(normalizedTitle, ignoreCase = false)) {
            score += 100.0
        } else if (candidateNorm == queryNorm) {
            score += 80.0
        } else if (candidateNorm.startsWith(queryNorm) || queryNorm.startsWith(candidateNorm)) {
            score += 30.0
        } else if (candidateNorm.contains(queryNorm) || queryNorm.contains(candidateNorm)) {
            score += 15.0
        }

        val candidateDate = item.releaseDate ?: item.firstAirDate
        val candidateYear = candidateDate?.takeIf { it.length >= 4 }?.substring(0, 4)
        if (!year.isNullOrBlank() && !candidateYear.isNullOrBlank()) {
            if (candidateYear == year) {
                score += 40.0
            } else {
                val diff = kotlin.math.abs((candidateYear.toIntOrNull() ?: 0) - (year.toIntOrNull() ?: 0))
                if (diff > 3) {
                    score -= 20.0
                }
            }
        }

        val popularity = item.popularity ?: 0.0
        score += (popularity / 10.0).coerceAtMost(15.0)

        return score
    }

    // =========================================================================
    // Legacy Compatibility Wrappers & UI Helpers
    // =========================================================================

    fun cleanTitleForTMDB(rawTitle: String): Pair<String, String?> =
        TitleNormalizer.cleanTitleForTMDB(rawTitle)

    fun cleanM3UData(rawTitle: String, rawDate: String?): Pair<String, String?> =
        TitleNormalizer.cleanM3UData(rawTitle, rawDate)

    suspend fun getEnrichedContent(
        rawTitle: String,
        year: String? = null,
        isMovie: Boolean = true,
        tmdbApiKey: String = ""
    ): EnrichedContentResult? = withContext(Dispatchers.IO) {
        val tmdbResult = getTmdbFullData(rawTitle, year, isMovie) ?: return@withContext null
        val castString = tmdbResult.castList.take(8).map { it.name }.joinToString(", ").ifBlank { null }
        EnrichedContentResult(
            tmdbId = tmdbResult.tmdbId,
            overview = tmdbResult.overview,
            cast = castString,
            director = tmdbResult.directorName,
            posterPath = tmdbResult.posterPath,
            episodes = emptyList(),
            isAiGenerated = false
        )
    }

    suspend fun enrichContent(
        title: String,
        year: String = "",
        isMovie: Boolean = true,
        tmdbApiKey: String = ""
    ): EnrichedContentResult? = getEnrichedContent(title, year.ifBlank { null }, isMovie, tmdbApiKey)

    suspend fun searchTMDBForM3UContent(
        rawTitle: String,
        rawDate: String?,
        isMovie: Boolean,
        tmdbApiKey: String = ""
    ): EnrichedContentResult? = getEnrichedContent(rawTitle, rawDate?.takeIf { it.length >= 4 }?.substring(0, 4), isMovie, tmdbApiKey)

    suspend fun searchTMDB(
        title: String,
        year: String = "",
        isMovie: Boolean = true,
        tmdbApiKey: String = ""
    ): EnrichedContentResult? = getEnrichedContent(title, year.ifBlank { null }, isMovie, tmdbApiKey)

    fun getPersonDetails(name: String, role: String, tmdbApiKey: String = ""): Flow<PersonDetails?> = flow {
        val normalizedName = name.lowercase(Locale.ROOT).trim()
        if (normalizedName.isBlank()) {
            emit(null)
            return@flow
        }

        val cached = iptvRepository?.getCachedPersonDetails(normalizedName)
        if (cached != null && (!cached.imageUrl.isNullOrBlank() || (cached.biography.isNotBlank() && !PersonPhotoRetry.shouldRetry(normalizedName)))) {
            emit(
                PersonDetails(
                    name = cached.displayName,
                    role = cached.role,
                    biography = cached.biography,
                    birthDate = cached.birthDate,
                    birthPlace = cached.birthPlace,
                    knownFor = cached.knownFor.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    imageUrl = cached.imageUrl
                )
            )
            return@flow
        }

        try {
            val details = MetadataEnricher.fetchPersonDetailsFromTMDB(context, name, role, getApiKey(tmdbApiKey))
            if (details == null || (details.biography.isBlank() && details.imageUrl.isNullOrBlank())) {
                emit(null)
                return@flow
            }
            val entity = PersonDetailsEntity(
                name = normalizedName,
                displayName = details.name,
                role = details.role,
                biography = details.biography,
                birthDate = details.birthDate,
                birthPlace = details.birthPlace,
                knownFor = details.knownFor.joinToString(","),
                imageUrl = details.imageUrl
            )
            iptvRepository?.savePersonDetails(entity)
            emit(details)
        } catch (e: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun findOtherWorksViaTmdb(
        personName: String,
        excludeItemId: Int,
        tmdbApiKey: String,
        libraryItems: List<IPTVItem>
    ): List<IPTVItem> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey(tmdbApiKey)
            if (apiKey.isBlank() || apiKey == "placeholder") return@withContext emptyList()

            val searchResponse = NetworkModule.tmdbService.searchPerson(query = personName, apiKey = apiKey)
            val bestMatch = searchResponse.results
                .filter { !it.name.isNullOrBlank() }
                .maxByOrNull { it.popularity ?: 0.0 }
                ?: return@withContext emptyList()

            val credits = NetworkModule.tmdbService.getPersonCombinedCredits(personId = bestMatch.id, apiKey = apiKey)
            val tmdbTitles = (credits.cast + credits.crew)
                .map { it.displayTitle.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .toSet()

            fun writeMatchDiag(msg: String) {
                try {
                    val logDir = java.io.File(context.filesDir, "crash_logs")
                    if (!logDir.exists()) logDir.mkdirs()
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    java.io.File(logDir, "person_otherworks_diag_$ts.txt").writeText(msg)
                } catch (e: Exception) { }
            }

            if (tmdbTitles.isEmpty()) {
                writeMatchDiag("DİĞER YAPIMLAR: TMDB'den 0 credit döndü.\nKişi: $personName\nEşleşen TMDB ismi: ${bestMatch.name}\nKütüphanedeki toplam öğe: ${libraryItems.size}")
                return@withContext emptyList()
            }

            val matched = libraryItems.filter { item ->
                if (item.id == excludeItemId) return@filter false
                if (item.type != "MOVIE" && item.type != "SERIES") return@filter false
                val itemTitle = (
                    if (item.type == "SERIES") {
                        com.example.data.model.SeriesParser.parseEpisodeInfo(item.name)?.showTitle ?: item.cleanedName
                    } else {
                        item.cleanedName
                    }
                    ).trim().lowercase(Locale.ROOT)
                if (itemTitle.isBlank()) return@filter false
                tmdbTitles.any { tmdbTitle -> itemTitle == tmdbTitle || itemTitle.contains(tmdbTitle) || tmdbTitle.contains(itemTitle) }
            }

            writeMatchDiag(
                "DİĞER YAPIMLAR eşleştirme sonucu\n" +
                "Kişi: $personName (TMDB eşleşmesi: ${bestMatch.name})\n" +
                "TMDB credit sayısı: ${tmdbTitles.size}\n" +
                "Kütüphanedeki toplam öğe (film+dizi): ${libraryItems.size}\n" +
                "Eşleşen öğe sayısı: ${matched.size}\n" +
                "İlk 10 TMDB başlığı: ${tmdbTitles.take(10)}\n" +
                "İlk 10 kütüphane başlığı: ${libraryItems.take(10).map { it.cleanedName }}"
            )

            matched.groupBy { item ->
                if (item.type == "SERIES") {
                    com.example.data.model.SeriesParser.parseEpisodeInfo(item.name)?.showTitle?.lowercase(Locale.ROOT)
                        ?: item.cleanedName.lowercase(Locale.ROOT)
                } else {
                    item.cleanedName.lowercase(Locale.ROOT)
                }
            }.map { (_, group) ->
                group.minByOrNull { item ->
                    val parsed = com.example.data.model.SeriesParser.parseEpisodeInfo(item.name)
                    (parsed?.season ?: 0) * 1000 + (parsed?.episode ?: 0)
                } ?: group.first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "findOtherWorksViaTmdb failed for $personName: ${e.message}")
            emptyList()
        }
    }

    suspend fun getMovieDetails(movieId: Int, tmdbApiKey: String = ""): TMDBMediaDetails? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(tmdbApiKey)
        fetchMovieDetailsWithFallback(movieId, apiKey)?.toMediaDetails("MOVIE")
    }

    suspend fun getSeriesDetails(seriesId: Int, tmdbApiKey: String = ""): TMDBMediaDetails? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(tmdbApiKey)
        fetchTvDetailsWithFallback(seriesId, apiKey)?.toMediaDetails("SERIES")
    }

    suspend fun fetchDetailsByTitle(title: String, type: String = "MOVIE", tmdbApiKey: String = ""): TMDBMediaDetails? = withContext(Dispatchers.IO) {
        val isMovie = !type.equals("SERIES", ignoreCase = true) && !type.equals("TV", ignoreCase = true)
        val result = getTmdbFullData(title, null, isMovie)
        result?.toMediaDetails(if (isMovie) "MOVIE" else "SERIES")
    }

    suspend fun searchAndGetDetails(seriesName: String, tmdbApiKey: String = ""): TMDBMediaDetails = withContext(Dispatchers.IO) {
        fetchDetailsByTitle(seriesName, "SERIES", tmdbApiKey) ?: TMDBMediaDetails(
            tmdbId = 0,
            title = seriesName,
            type = "SERIES",
            overview = "",
            posterUrl = null,
            backdropUrl = null,
            cast = emptyList(),
            castString = "",
            director = "",
            rating = 0.0,
            voteCount = 0,
            releaseDate = "",
            genres = emptyList(),
            tagline = "",
            runtimeOrEpisodes = ""
        )
    }

    suspend fun getTrendingMoviesAndSeries(tmdbApiKey: String = ""): List<IPTVItem> = withContext(Dispatchers.IO) {
        getPopularMoviesList(tmdbApiKey) + getPopularSeriesList(tmdbApiKey)
    }

    suspend fun getPopularMoviesList(tmdbApiKey: String = ""): List<IPTVItem> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(tmdbApiKey)
        try {
            val response = tmdbService.getPopularMovies(apiKey, "tr-TR")
            mapMediaItemsToIPTV(response.results, "MOVIE")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularSeriesList(tmdbApiKey: String = ""): List<IPTVItem> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(tmdbApiKey)
        try {
            val response = tmdbService.getPopularSeries(apiKey, "tr-TR")
            mapMediaItemsToIPTV(response.results, "SERIES")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTopRatedSeriesList(tmdbApiKey: String = ""): List<IPTVItem> = withContext(Dispatchers.IO) {
        getPopularSeriesList(tmdbApiKey)
    }

    suspend fun getRecommendationsList(tmdbApiKey: String = "", referenceItem: IPTVItem?): List<IPTVItem> = withContext(Dispatchers.IO) {
        if (referenceItem?.type == "SERIES") getPopularSeriesList(tmdbApiKey) else getPopularMoviesList(tmdbApiKey)
    }

    private fun mapMediaItemsToIPTV(items: List<TmdbMediaItem>, defaultType: String): List<IPTVItem> {
        return items.map { item ->
            val title = (item.title ?: item.name ?: "İçerik").trim()
            IPTVItem(
                id = -(item.id.takeIf { it != 0 } ?: java.util.UUID.randomUUID().hashCode()),
                playlistId = 0,
                name = title,
                cleanedName = title,
                logoUrl = buildImageUrl(item.posterPath ?: item.backdropPath, SIZE_POSTER),
                streamUrl = "",
                category = "Keşfet",
                type = defaultType,
                rating = item.voteAverage ?: 0.0,
                summary = item.overview ?: "",
                cast = "",
                director = "",
                releaseDate = item.releaseDate ?: item.firstAirDate ?: ""
            )
        }
    }
}

// Extension functions for mapping between Room cache entity and memory models
private fun TmdbCacheEntity.toTmdbContentResult(): TmdbContentResult {
    val castMembers = if (castJson.isBlank()) {
        emptyList()
    } else {
        castJson.split(";;;").mapNotNull { entry ->
            val parts = entry.split("|||")
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                CastMember(
                    name = parts[0],
                    character = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                    profilePath = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            } else null
        }
    }

    val genresList = if (genresJson.isBlank()) emptyList() else genresJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    return TmdbContentResult(
        tmdbId = tmdbId,
        title = title,
        overview = overview.ifBlank { null },
        backdropPath = backdropPath,
        posterPath = posterPath,
        castList = castMembers,
        directorName = directorName,
        directorPhoto = directorPhoto,
        voteAverage = rating,
        releaseDate = releaseDate,
        genres = genresList,
        voteCount = 0
    )
}

private fun TmdbContentResult.toCacheEntity(
    sourceKey: String,
    normalizedTitle: String,
    year: String?,
    mediaType: String
): TmdbCacheEntity {
    val castSerialized = castList.joinToString(";;;") { "${it.name}|||${it.character ?: ""}|||${it.profilePath ?: ""}" }
    val genresSerialized = genres.joinToString(",")

    return TmdbCacheEntity(
        sourceKey = sourceKey,
        normalizedTitle = normalizedTitle,
        year = year,
        mediaType = mediaType,
        tmdbId = tmdbId,
        title = title,
        overview = overview ?: "",
        posterPath = posterPath,
        backdropPath = backdropPath,
        rating = voteAverage ?: 0.0,
        releaseDate = releaseDate ?: "",
        genresJson = genresSerialized,
        castJson = castSerialized,
        directorName = directorName,
        directorPhoto = directorPhoto,
        updatedAt = System.currentTimeMillis()
    )
}

private fun TmdbContentResult.toMediaDetails(type: String): TMDBMediaDetails {
    val castPersons = castList.map { castMember ->
        MetadataEnricher.TMDBPerson(
            name = castMember.name,
            character = castMember.character,
            profile_path = castMember.profilePath
        )
    }
    return TMDBMediaDetails(
        tmdbId = tmdbId,
        title = title,
        type = type,
        overview = overview ?: "",
        posterUrl = posterPath,
        backdropUrl = backdropPath,
        cast = castPersons,
        castString = castList.take(6).joinToString(", ") { it.name },
        director = directorName ?: "",
        rating = voteAverage ?: 0.0,
        voteCount = voteCount,
        releaseDate = releaseDate ?: "",
        genres = genres,
        tagline = "",
        runtimeOrEpisodes = ""
    )
}

/**
 * İçerik Detay Sonucu: Önce TMDb, Yoksa Yedek AI (LLM)
 */
sealed class ContentDetailResult {
    data class Tmdb(val data: EnrichedContentResult) : ContentDetailResult()
    data class AiGenerated(val data: EnrichedContentResult) : ContentDetailResult()
    object Empty : ContentDetailResult()
}

/**
 * Kullanılacak Veri Modeli: Hem Film hem Dizi zenginleştirme sonuçları
 */
data class EnrichedContentResult(
    val tmdbId: Int,
    val overview: String?,
    val cast: String?,
    val director: String?,
    val posterPath: String?,
    val episodes: List<TmdbEpisode> = emptyList(),
    val isAiGenerated: Boolean = false
)

/**
 * Dizi Bölüm Bilgileri Modeli
 */
data class TmdbEpisode(
    val id: Int,
    val name: String,
    val overview: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val stillPath: String? = null
)

/**
 * Film ve Dizi Detaylarını temsil eden TMDB Veri Modeli
 */
data class TMDBMediaDetails(
    val tmdbId: Int,
    val title: String,
    val type: String, // "MOVIE" veya "SERIES"
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val cast: List<MetadataEnricher.TMDBPerson> = emptyList(),
    val castString: String = "",
    val director: String = "",
    val rating: Double = 0.0,
    val voteCount: Int = 0,
    val releaseDate: String = "",
    val genres: List<String> = emptyList(),
    val tagline: String = "",
    val runtimeOrEpisodes: String = ""
) {
    val posterPath: String? get() = posterUrl
}
