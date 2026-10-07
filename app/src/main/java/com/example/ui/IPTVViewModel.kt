package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.IPTVGroup
import com.example.data.model.toBrowsableItem
import com.example.data.model.Playlist
import com.example.data.model.EPGProgram
import com.example.data.epg.RealEpgProvider
import com.example.data.repository.IPTVRepository
import com.example.data.repository.TMDBRepository
import com.example.data.model.PersonDetailsEntity
import com.example.data.model.SearchHistory
import com.example.data.model.AiRecommendationHistory
import com.example.data.api.PersonDetails
import com.example.ui.theme.AppTheme
import com.example.util.ErrorHandlingManager
import com.example.util.AppError
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class SpotlightCollection(val title: String, val subtitle: String, val queryKey: String)

class IPTVViewModel(
    application: Application,
    private val repository: IPTVRepository
) : AndroidViewModel(application) {

    companion object {
        /** Ücretsiz kullanıcının günlük izleme hakkı (saniye). */
        const val FREE_DAILY_WATCH_SECONDS = 3600L
    }

    // Coroutine Exception Handler for robust crash resistance
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainViewModel", "Hata yakalandı, uygulama çökmesi engellendi", throwable)
        _isLoading.value = false
    }
    private val coroutineExceptionHandler = exceptionHandler

    // --- Ortak (paylaşılan) tür listeleri ---
    // Her tür veritabanından tek kez okunur ve tüm ekranlar/hesaplamalar aynı listeyi paylaşır. Eskiden aynı
    // tablo 13 ayrı sorguyla okunuyordu; tek bir satır değişince (ör. detay açılınca) on binlerce satır 13 kez
    // yeniden okunuyor, kaydırma takılıyordu. En üstte tanımlı: init bloklarındaki işler de kullanıyor.
    private fun sharedItemsOf(type: String): Flow<List<IPTVItem>> = repository.getItemsByType(type)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    // Detay açılınca indirilen bilgiler (özet, oyuncular...) hemen ekranda gösterilir ama veritabanına
    // uygulama arka plana geçince tek seferde yazılır: gezinirken tüm listeler yeniden okunmaz.
    private val pendingMetadata = java.util.concurrent.ConcurrentHashMap<Int, com.example.data.db.ItemMetadataUpdate>()

    private fun IPTVItem.withPendingMetadata(): IPTVItem = pendingMetadata[id]?.let { m ->
        copy(summary = m.summary, cast = m.cast, director = m.director, rating = m.rating,
            logoUrl = m.logoUrl, trailerUrl = m.trailerUrl, releaseDate = m.releaseDate, genre = m.genre)
    } ?: this

    /** Bekleyen detay bilgilerini tek işlemde veritabanına yazar (MainActivity.onStop ve onCleared). */
    fun flushPendingMetadata() {
        if (pendingMetadata.isEmpty()) return
        val batch = pendingMetadata.values.toList()
        batch.forEach { pendingMetadata.remove(it.itemId, it) }
        // viewModelScope kapanmış olabilir (onCleared); uygulama kapsamında yazılır.
        com.example.IPTVApplication.applicationScope.launch {
            try {
                repository.updateItemsMetadata(batch)
            } catch (e: Exception) {
                Log.w("IPTVViewModel", "Pending metadata flush failed", e)
            }
        }
    }

    private fun queueMetadata(item: IPTVItem) {
        if (item.id == 0) return
        pendingMetadata[item.id] = com.example.data.db.ItemMetadataUpdate(
            itemId = item.id, summary = item.summary, cast = item.cast, director = item.director,
            rating = item.rating, logoUrl = item.logoUrl, trailerUrl = item.trailerUrl,
            releaseDate = item.releaseDate, genre = item.genre
        )
    }

    override fun onCleared() {
        flushPendingMetadata()
        super.onCleared()
    }

    private val liveItemsShared = sharedItemsOf("LIVE")
    private val movieItemsShared = sharedItemsOf("MOVIE")
    private val seriesItemsShared = sharedItemsOf("SERIES")
    private val radioItemsShared = sharedItemsOf("RADIO")

    private var syncFetchJob: Job? = null

    // TMDB Repository Integration
    private val tmdbRepository = TMDBRepository(application, repository)

    // Featured Movie Repository (12 Hours Random M3U Movie)

    // Settings Repository Integration
    private val settingsRepository = com.example.data.repository.SettingsRepository(application)

    // Exposed Settings flows as StateFlows
    val hardwareAcceleration: StateFlow<Boolean> = settingsRepository.hardwareAccelerationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val bufferSize: StateFlow<String> = settingsRepository.bufferSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Normal")

    val reduceCellularQuality: StateFlow<Boolean> = settingsRepository.reduceCellularQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoRefreshList: StateFlow<Boolean> = settingsRepository.autoRefreshListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val subtitleSize: StateFlow<Int> = settingsRepository.subtitleSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)

    val subtitleColor: StateFlow<String> = settingsRepository.subtitleColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Beyaz")

    val parentalLock: StateFlow<Boolean> = settingsRepository.parentalLockFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val parentalPin: StateFlow<String> = settingsRepository.parentalPinFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0000")

    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val tmdbApiKey: StateFlow<String> = settingsRepository.tmdbApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val appLanguage: StateFlow<String> = settingsRepository.appLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.util.LocaleHelper.getSavedLanguage(application))

    // --- TV ana sayfası (Top Shelf) ---
    private val heroShelf by lazy {
        com.example.data.repository.HeroShelfRepository(
            dao = com.example.data.db.AppDatabase.getDatabase(getApplication()).iptvDao(),
            tmdb = tmdbRepository,
            tmdbApiKey = { tmdbApiKey.value }
        )
    }

    /**
     * TV ana sayfası hero içeriği. [section]: "LIVE", "MOVIE", "SERIES", "SAVED", "CONTINUE" veya varsayılan
     * (rastgele film). Veri yoksa null döner.
     */
    suspend fun tvShelfContent(section: String, continueItem: ContinueWatching? = null): com.example.data.repository.ShelfContent? =
        withContext(Dispatchers.IO) {
            try {
                when (section) {
                    "LIVE" -> heroShelf.randomChannel { currentProgramTitle(it) }
                    "MOVIE" -> heroShelf.randomMovie()
                    "SERIES" -> heroShelf.randomSeries()
                    "SAVED" -> repository.favoriteItemsFlow.first()
                        .filterNot { isAdultContent(it) }
                        .filter { !it.logoUrl.isNullOrBlank() }
                        .randomOrNull()
                        ?.let { shelfFor(it) }
                    "CONTINUE" -> continueItem?.let { cw ->
                        shelfFor(
                            repository.getItemByIdDirect(cw.itemId) ?: IPTVItem(
                                id = cw.itemId, playlistId = 1, name = cw.itemName, cleanedName = cw.itemName,
                                logoUrl = cw.itemLogo, streamUrl = cw.streamUrl, category = cw.category, type = cw.itemType
                            )
                        )
                    }
                    else -> heroShelf.randomMovie()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("IPTVViewModel", "TV shelf content failed", e)
                null
            }
        }

    // --- TV modu: Filmler/Diziler (veritabanından sayfa sayfa; telefonla aynı kurallar) ---
    private val tvCatalog by lazy { com.example.data.repository.TvCatalogRepository(AppDatabase.getDatabase(getApplication()).iptvDao()) }

    suspend fun tvCategories(type: String): List<com.example.data.repository.TvCategory> =
        tvCatalog.categories(type) { if (type == "SERIES") seriesCategoryRank(it) else movieCategoryRank(it) }

    suspend fun tvSpecial(type: String, section: com.example.data.repository.TvSpecialSection): List<com.example.data.repository.TvPoster> =
        tvCatalog.special(type, section)

    /** Kategorinin [offset]'ten başlayan sayfası. Dizilerde M3U bölümleri tek seferde gruplanır (sayfa yok). */
    suspend fun tvCategoryPage(type: String, categoryKey: String, offset: Int, limit: Int, covers: Map<String, String>): List<com.example.data.repository.TvPoster> =
        when {
            type == "MOVIE" -> tvCatalog.moviePage(categoryKey, offset, limit)
            tvCatalog.hasSeriesCatalog() -> tvCatalog.catalogPage(categoryKey, offset, limit)
            offset == 0 -> tvCatalog.seriesInCategory(categoryKey, covers)
            else -> emptyList()
        }

    /** Dizi bölümünün dizisi (Xtream'den canlı çekilmişse o, yoksa yalnızca bu dizinin bölümleri sorgulanır). */
    suspend fun tvShowForItem(item: IPTVItem, covers: Map<String, String>): com.example.data.model.TvShow? {
        _liveFetchedShow.value?.let { show ->
            if (show.seasons.any { s -> s.episodes.any { it.item.id == item.id } }) return show
        }
        return tvCatalog.showForEpisode(item, covers)
    }

    /**
     * TV detay "benzer yapımlar": dizide aynı klasördeki diğer diziler; filmde telefondaki benzer içerik
     * sorgusundan yalnızca filmler (dizi bölümleri karışmaz).
     */
    suspend fun tvSimilar(item: IPTVItem, show: com.example.data.model.TvShow?, covers: Map<String, String>): List<com.example.data.repository.TvPoster> =
        try {
            if (item.type == "SERIES") {
                tvCatalog.otherShowsInFolder(show, item, covers)
            } else {
                getSimilarItemsFlow(item).first()
                    .filter { it.type == "MOVIE" && it.id != item.id }
                    .map { com.example.data.repository.TvPoster("MOVIE_${it.id}", it.cleanedName.ifBlank { it.name }, it.logoUrl, it.rating, it) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("IPTVViewModel", "tvSimilar failed: ${e.message}")
            emptyList()
        }

    /** TMDB detayı (önbellekli; telefondaki ile aynı çağrı): arka plan, oyuncu karakterleri, yönetmen. */
    suspend fun tvTmdbDetails(title: String, type: String): com.example.data.repository.TMDBMediaDetails? =
        try {
            tmdbRepository.fetchDetailsByTitle(title, type = type, tmdbApiKey = tmdbApiKey.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("IPTVViewModel", "TV TMDB details failed: ${e.message}")
            null
        }

    /**
     * Kişi sayfası: telefondaki "searchPersonWorks" ile aynı adımlar ve aynı eşleştirme kuralları; kütüphane
     * adayları tüm liste yerine veritabanında sorguyla süzülür.
     */
    fun tvPersonWorks(personName: String, role: String, excludeItemId: Int): Flow<com.example.data.model.tmdb.PersonWorksUiState> = flow {
        val query = com.example.data.repository.PersonWorksMatcher.primaryName(personName)
        val current = selectedItem.value
        var state = com.example.data.model.tmdb.PersonWorksUiState(query = query, phase = com.example.data.model.tmdb.PersonWorksPhase.SEARCHING_LIBRARY)
        emit(state)
        val castOnly = tvCatalog.personWorks(null, query, excludeItemId, current)
        state = state.copy(phase = com.example.data.model.tmdb.PersonWorksPhase.FETCHING_TMDB, inLibrary = castOnly.inLibrary)
        emit(state)
        state = when (val outcome = personCreditsRepository.lookupPersonCredits(query, role, tmdbApiKey.value)) {
            is com.example.data.model.tmdb.PersonCreditsOutcome.Found -> {
                emit(state.copy(phase = com.example.data.model.tmdb.PersonWorksPhase.MATCHING, tmdbCreditCount = outcome.lookup.credits.size, profileUrl = outcome.lookup.profileUrl))
                val result = tvCatalog.personWorks(outcome.lookup, query, excludeItemId, current)
                state.copy(
                    phase = com.example.data.model.tmdb.PersonWorksPhase.DONE,
                    tmdbCreditCount = outcome.lookup.credits.size,
                    profileUrl = outcome.lookup.profileUrl,
                    inLibrary = result.inLibrary,
                    notInLibrary = result.notInLibrary
                )
            }
            is com.example.data.model.tmdb.PersonCreditsOutcome.NotFound -> state.copy(phase = com.example.data.model.tmdb.PersonWorksPhase.DONE)
            is com.example.data.model.tmdb.PersonCreditsOutcome.Failed -> state.copy(phase = com.example.data.model.tmdb.PersonWorksPhase.DONE, tmdbProblem = outcome.reason)
        }
        emit(state)
    }.catch { e ->
        Log.w("IPTVViewModel", "tvPersonWorks error: ${e.message}")
        emit(com.example.data.model.tmdb.PersonWorksUiState(query = personName, phase = com.example.data.model.tmdb.PersonWorksPhase.DONE, tmdbProblem = e.message))
    }.flowOn(Dispatchers.IO)

    // --- TV modu: Canlı TV ---

    /** Canlı TV'ye girişte açılacak kanal: son izlenen canlı kanal, yoksa ilk (yetişkin olmayan) kanal. */
    suspend fun tvStartChannel(): IPTVItem? = withContext(Dispatchers.IO) {
        val last = AppDatabase.getDatabase(getApplication()).iptvDao().getAllContinueWatchingOnce()
            .filter { it.itemType == "LIVE" }
            .maxByOrNull { it.lastPlayedAt }
            ?.let { repository.getItemByIdDirect(it.itemId) }
        last ?: tvCatalog.firstLiveChannel { isAdultContent(it) }
    }

    /** Kanalın EPG'deki şu anki ve sonraki programı (EPG yoksa ikisi de null). */
    fun tvEpgNowNext(channel: IPTVItem): Pair<com.example.data.model.EPGProgram?, com.example.data.model.EPGProgram?> {
        val key = channel.tvgId?.lowercase(java.util.Locale.ROOT)?.trim().orEmpty()
        if (key.isEmpty()) return null to null
        val programs = _realEpgPrograms.value[key] ?: return null to null
        val now = System.currentTimeMillis()
        val index = programs.indexOfFirst { now >= it.startEpochMillis && now < it.endEpochMillis }
        if (index < 0) return null to programs.firstOrNull { it.startEpochMillis > now }
        return programs[index] to programs.getOrNull(index + 1)
    }

    /** Tek bir öğenin hero içeriği: kanal logosu + EPG, dizi bölümünde dizi adı, filmde kendi bilgisi. */
    private suspend fun shelfFor(item: IPTVItem): com.example.data.repository.ShelfContent = when (item.type) {
        "LIVE" -> com.example.data.repository.ShelfContent(
            key = "live-${item.id}", title = item.cleanedName.ifBlank { item.name }, rating = null,
            year = null, genre = item.category.takeIf { it.isNotBlank() }, overview = null,
            imageUrl = item.logoUrl, imageKind = com.example.data.repository.ShelfImageKind.LOGO,
            nowPlaying = currentProgramTitle(item), item = item
        )
        "SERIES" -> {
            val show = (com.example.data.model.SeriesParser.parseEpisodeInfo(item.cleanedName.ifBlank { item.name })
                ?: com.example.data.model.SeriesParser.parseEpisodeInfo(item.name))?.showTitle?.takeIf { it.isNotBlank() }
            heroShelf.fromItem(item, titleOverride = show, type = "SERIES")
        }
        else -> heroShelf.fromItem(item)
    }

    /** Kanalın EPG'deki şu anki programı (EPG yoksa null). */
    fun currentProgramTitle(channel: IPTVItem): String? {
        val key = channel.tvgId?.lowercase(java.util.Locale.ROOT)?.trim().orEmpty()
        if (key.isEmpty()) return null
        val now = System.currentTimeMillis()
        return _realEpgPrograms.value[key]?.firstOrNull { now >= it.startEpochMillis && now < it.endEpochMillis }?.title
            ?.takeIf { it.isNotBlank() }
    }

    /** Görünüm modu (Telefon / TV); bkz. [com.example.data.repository.ViewMode]. */
    val viewMode: StateFlow<String> = settingsRepository.viewModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.repository.ViewMode.LOADING)

    fun setViewMode(mode: String) {
        viewModelScope.launch(coroutineExceptionHandler) { settingsRepository.setViewMode(mode) }
    }

    val syncInterval: StateFlow<String> = settingsRepository.syncIntervalFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "24_HOURS")

    // PRO & Trial State Flow
    val isProUser: StateFlow<Boolean> = settingsRepository.isProUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val watchUsage: StateFlow<com.example.data.repository.WatchUsage> = settingsRepository.watchUsageFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.repository.WatchUsage(null, 0L))

    /** Bugün izlenen ücretsiz süre (saniye). Gün değişince (uygulama açıkken de) kendiliğinden 0'a döner. */
    val totalWatchSeconds: StateFlow<Long> = kotlinx.coroutines.flow.combine(
        watchUsage,
        kotlinx.coroutines.flow.flow {
            while (true) {
                emit(com.example.data.repository.WatchUsage.today())
                kotlinx.coroutines.delay(60_000)
            }
        }.distinctUntilChanged()
    ) { usage, today -> usage.secondsOn(today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Ücretsiz kullanıcının bugünkü izleme hakkı doldu mu (anlık, gün değişimini hemen dikkate alır)? */
    /** Yeni içerik başlatılırken: DataStore'daki güncel kullanım okunarak (soğuk açılışta da doğru) kontrol eder. */
    suspend fun isFreeWatchLimitReachedNow(): Boolean {
        if (!com.example.BuildConfig.FREE_WATCH_LIMIT || isProUser.value) return false
        val usage = kotlinx.coroutines.withTimeoutOrNull(2_000) { settingsRepository.watchUsageFlow.first() } ?: watchUsage.value
        return usage.secondsOn(com.example.data.repository.WatchUsage.today()) >= FREE_DAILY_WATCH_SECONDS
    }

    fun isFreeWatchLimitReached(): Boolean =
        com.example.BuildConfig.FREE_WATCH_LIMIT && !isProUser.value &&
            watchUsage.value.secondsOn(com.example.data.repository.WatchUsage.today()) >= FREE_DAILY_WATCH_SECONDS

    val firstLaunchTime: StateFlow<Long> = settingsRepository.firstLaunchTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val completedPlaybackSessions: StateFlow<Int> = settingsRepository.completedPlaybackSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hasTriggeredInAppReview: StateFlow<Boolean> = settingsRepository.hasTriggeredInAppReviewFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _triggerInAppReviewEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerInAppReviewEvent: SharedFlow<Unit> = _triggerInAppReviewEvent.asSharedFlow()

    fun recordCompletedPlaybackSession() {
        viewModelScope.launch(coroutineExceptionHandler) {
            val count = settingsRepository.incrementCompletedPlaybackSessions()
            Log.d("IPTVViewModel", "Completed playback session #$count")
            if (count >= 5 && !hasTriggeredInAppReview.value) {
                settingsRepository.setHasTriggeredInAppReview(true)
                _triggerInAppReviewEvent.emit(Unit)
            }
        }
    }

    // Paywall Dialog Visibility & Reason
    // Açık yasal metin (Hizmet Şartları / Gizlilik Politikası); null ise kapalı.
    private val _legalDoc = MutableStateFlow<com.example.data.legal.LegalDoc?>(null)
    val legalDoc: StateFlow<com.example.data.legal.LegalDoc?> = _legalDoc.asStateFlow()
    fun openLegal(doc: com.example.data.legal.LegalDoc) { _legalDoc.value = doc }
    fun closeLegal() { _legalDoc.value = null }

    private val _showPaywallDialog = MutableStateFlow(false)
    val showPaywallDialog: StateFlow<Boolean> = _showPaywallDialog.asStateFlow()

    private val _paywallReasonMessage = MutableStateFlow<String?>(null)
    val paywallReasonMessage: StateFlow<String?> = _paywallReasonMessage.asStateFlow()

    /**
     * Returns true if user is PRO OR within the 24-hour trial period from first launch.
     */
    fun isAiTrialActive(): Boolean {
        if (isProUser.value) return true
        val launch = firstLaunchTime.value
        if (launch <= 0L) return true
        val elapsed = System.currentTimeMillis() - launch
        return elapsed < (24 * 60 * 60 * 1000L)
    }

    fun openPaywall(reason: String? = null) {
        _paywallReasonMessage.value = reason
        _showPaywallDialog.value = true
    }

    fun closePaywall() {
        _showPaywallDialog.value = false
        _paywallReasonMessage.value = null
    }

    fun setProUser(isPro: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setProUser(isPro)
            if (isPro) {
                closePaywall()
            }
        }
    }

    private var lastRecordedWatchSeconds = 0L

    fun addWatchSeconds(seconds: Long) {
        if (seconds <= 0L || isProUser.value) return

        viewModelScope.launch(coroutineExceptionHandler) {
            val updated = settingsRepository.addWatchSeconds(seconds)
            lastRecordedWatchSeconds = updated

            // Süre dolsa da oynayan içerik kesilmez ve araya paywall çıkmaz; sınır yalnızca yeni içerik
            // başlatılırken uygulanır (bkz. PlayerScreen, isFreeWatchLimitReachedNow).
        }
    }

    // Settings Actions
    fun setAppLanguage(language: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setAppLanguage(language)
            com.example.util.LocaleHelper.setLocale(getApplication(), language)
        }
    }

    fun setSyncInterval(interval: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setSyncInterval(interval)
        }
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setHardwareAcceleration(enabled)
        }
    }

    fun setBufferSize(size: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setBufferSize(size)
        }
    }

    fun setReduceCellularQuality(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setReduceCellularQuality(enabled)
        }
    }

    fun setAutoRefreshList(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setAutoRefreshList(enabled)
        }
    }

    fun setSubtitleSize(size: Int) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setSubtitleSize(size)
        }
    }

    fun setSubtitleColor(color: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setSubtitleColor(color)
        }
    }

    fun setParentalLock(enabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setParentalLock(enabled)
        }
    }

    fun setParentalPin(pin: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setParentalPin(pin)
        }
    }

    fun verifyParentalPin(enteredPin: String): Boolean {
        val currentPin = parentalPin.value
        val isLocked = parentalLock.value
        return !isLocked || enteredPin == currentPin
    }

    private val _isSafeSessionActive = MutableStateFlow(false)
    val isSafeSessionActive: StateFlow<Boolean> = _isSafeSessionActive.asStateFlow()

    private val _safeSessionRemainingSeconds = MutableStateFlow(0)
    val safeSessionRemainingSeconds: StateFlow<Int> = _safeSessionRemainingSeconds.asStateFlow()

    private var safeSessionTimerJob: kotlinx.coroutines.Job? = null

    /**
     * Başarılı bir PIN doğrulaması sonrasında oturum süresince (varsayılan 15 dakika)
     * tekrar PIN sormamasını sağlayan 'Güvenli Oturum' (Safe Session) zamanlayıcısı.
     */
    fun unlockSafeSession(durationMinutes: Int = 15) {
        safeSessionTimerJob?.cancel()
        _isSafeSessionActive.value = true
        val totalSeconds = durationMinutes * 60
        _safeSessionRemainingSeconds.value = totalSeconds

        safeSessionTimerJob = viewModelScope.launch(Dispatchers.Main + coroutineExceptionHandler) {
            var remaining = totalSeconds
            while (remaining > 0 && coroutineContext.isActive) {
                kotlinx.coroutines.delay(1000L)
                remaining--
                _safeSessionRemainingSeconds.value = remaining
            }
            lockSafeSession()
        }
    }

    /**
     * Güvenli Oturumu manuel veya otomatik olarak sonlandırıp kilitler.
     */
    fun lockSafeSession() {
        safeSessionTimerJob?.cancel()
        safeSessionTimerJob = null
        _isSafeSessionActive.value = false
        _safeSessionRemainingSeconds.value = 0
    }

    fun isSafeSessionValid(): Boolean {
        return _isSafeSessionActive.value && (_safeSessionRemainingSeconds.value > 0)
    }

    fun formatSafeSessionRemainingTime(): String {
        val totalSec = _safeSessionRemainingSeconds.value
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs)
    }

    private val _isValidatingGeminiKey = MutableStateFlow(false)
    val isValidatingGeminiKey: StateFlow<Boolean> = _isValidatingGeminiKey.asStateFlow()

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setGeminiApiKey(key)
        }
    }

    /** Kullanıcının Gemini anahtarını cihazdaki tüm ayar depolarından siler. */
    fun deleteGeminiApiKey() {
        viewModelScope.launch(coroutineExceptionHandler) {
            settingsRepository.setGeminiApiKey("")
            userPreferencesRepository.setGeminiApiKey("")
        }
    }

    fun validateAndSaveGeminiApiKey(
        key: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) {
            onResult(false, "Geçersiz Gemini API Anahtarı! Lütfen kontrol edin.")
            return
        }

        viewModelScope.launch(coroutineExceptionHandler) {
            _isValidatingGeminiKey.value = true
            val isValid = performGeminiApiKeyValidation(trimmedKey)
            _isValidatingGeminiKey.value = false

            if (isValid) {
                settingsRepository.setGeminiApiKey(trimmedKey)
                onResult(true, "Gemini API Anahtarı başarıyla doğrulandı ve kaydedildi.")
            } else {
                onResult(false, "Geçersiz Gemini API Anahtarı! Lütfen kontrol edin.")
            }
        }
    }

    private suspend fun performGeminiApiKeyValidation(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = com.example.data.api.NetworkModule.okHttpClient
            val jsonBody = org.json.JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("text", "ping")
                            })
                        })
                    })
                })
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val models = com.example.util.GeminiApi.MODELS
            var valid = false

            for (model in models) {
                val request = com.example.util.GeminiApi.request(model, apiKey, requestBody)

                try {
                    val response = client.newCall(request).execute()
                    val code = response.code
                    val isSuccess = response.isSuccessful
                    response.close()

                    if (isSuccess) {
                        valid = true
                        break
                    }
                    if (code == 400 || code == 401 || code == 403) {
                        valid = false
                        break
                    }
                } catch (e: Exception) {
                    // Try next model if timeout
                }
            }
            valid
        } catch (e: Exception) {
            false
        }
    }

    fun clearAppCache(onComplete: () -> Unit = {}) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            settingsRepository.clearAppCache()
            _isLoading.value = false
            onComplete()
        }
    }

    // Authentication State
    data class UserProfile(
        val displayName: String? = null,
        val email: String? = null,
        val photoUrl: String? = null
    )

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow<String?>(null)
    val userPhotoUrl: StateFlow<String?> = _userPhotoUrl.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isStateReady = MutableStateFlow(false)
    val isStateReady: StateFlow<Boolean> = _isStateReady.asStateFlow()

    private val userPreferencesRepository = com.example.data.repository.UserPreferencesRepository(application)

    // Onboarding Setup State - Driven purely by persistent user preferences
    val isSetupComplete: StateFlow<Boolean> = userPreferencesRepository.isSetupCompletedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Global Theme State
    private val _appTheme = MutableStateFlow(AppTheme.SYSTEM)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    // Loading State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected IPTV Item for Details overlay/card
    private val _selectedItem = MutableStateFlow<IPTVItem?>(null)
    val selectedItem: StateFlow<IPTVItem?> = _selectedItem.asStateFlow()

    // Selected Category for filtering
    private val _selectedCategory = MutableStateFlow<String?>("Tümü")
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Screen tab and active folder navigation state (persisted across player transitions)
    private val _selectedTypeFilter = MutableStateFlow("ALL")
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    private val _activeFolderGroup = MutableStateFlow<IPTVGroup?>(null)
    val activeFolderGroup: StateFlow<IPTVGroup?> = _activeFolderGroup.asStateFlow()

    // Database streams
    val playlists: StateFlow<List<Playlist>> = repository.allPlaylistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _realEpgPrograms = MutableStateFlow<Map<String, List<EPGProgram>>>(emptyMap())
    val realEpgPrograms: StateFlow<Map<String, List<EPGProgram>>> = _realEpgPrograms.asStateFlow()

    val manualEpgUrl: StateFlow<String> = settingsRepository.manualEpgUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        // PRO durumu Google Play'deki geçerli satın alımlardan okunur (abonelik iptal/süre dolumu dahil).
        com.example.util.SubscriptionManager.init(application) { isPro ->
            if (isPro != isProUser.value) setProUser(isPro)
        }
        // Eski sürümlerin 12 saatlik "öne çıkan film" kaydını sil (artık her girişte yeniden seçiliyor).
        com.example.data.repository.FeaturedMovieRepository.clearLegacyCache(application)
        // TMDB önbellekleri birikmesin: süresi (30 gün) dolan film bilgileri silinir, oyuncu/yönetmen
        // önbelleği 200 kayıtla sınırlanır. Sadece önbellek silinir; listeler/favoriler/geçmiş etkilenmez.
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.trimOversizedItemFields()
            repository.trimTmdbCaches(
                System.currentTimeMillis() - com.example.data.repository.TMDBRepository.CACHE_TTL_MS
            )
        }
        viewModelScope.launch(coroutineExceptionHandler) {
            combine(playlists, settingsRepository.manualEpgUrlFlow) { list, manualUrl ->
                Pair(manualUrl.trim(), list.firstOrNull { !it.epgUrl.isNullOrBlank() }?.epgUrl)
            }.distinctUntilChanged().collect { (manualUrl, autoUrl) ->
                val lastFetch = settingsRepository.lastEpgFetchTimeFlow.first()
                val sixHoursMillis = 6 * 60 * 60 * 1000L
                // Rehber yalnızca bellekte tutuluyor: uygulama yeniden açıldığında (veya liste yenilenip
                // EPG adresi yeni geldiğinde) bellek boşsa süreye bakmadan indir.
                if (_realEpgPrograms.value.isNotEmpty() && System.currentTimeMillis() - lastFetch < sixHoursMillis) {
                    return@collect
                }
                if (manualUrl.isNotBlank()) {
                    try {
                        _realEpgPrograms.value = RealEpgProvider.fetchProgramsFromUrl(manualUrl)
                        settingsRepository.setLastEpgFetchTime(System.currentTimeMillis())
                    } catch (e: Exception) {
                        android.util.Log.w("IPTVViewModel", "Manuel EPG yüklenemedi, otomatiğe dönülüyor: ${e.message}")
                        if (!autoUrl.isNullOrBlank()) {
                            try {
                                _realEpgPrograms.value = RealEpgProvider.getProgramsByChannelId(autoUrl)
                                if (_realEpgPrograms.value.isNotEmpty()) settingsRepository.setLastEpgFetchTime(System.currentTimeMillis())
                            } catch (e2: Exception) {
                                android.util.Log.w("IPTVViewModel", "Otomatik EPG indirilemedi: ${e2.message}")
                            }
                        }
                    }
                } else if (!autoUrl.isNullOrBlank()) {
                    try {
                        _realEpgPrograms.value = RealEpgProvider.getProgramsByChannelId(autoUrl)
                        if (_realEpgPrograms.value.isNotEmpty()) settingsRepository.setLastEpgFetchTime(System.currentTimeMillis())
                    } catch (e: Exception) {
                        android.util.Log.w("IPTVViewModel", "Otomatik EPG indirilemedi: ${e.message}")
                    }
                }
            }
        }
    }

    fun setManualEpgUrl(url: String, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val trimmed = url.trim()
            settingsRepository.setManualEpgUrl(trimmed)
            if (trimmed.isNotBlank()) {
                try {
                    val programs = RealEpgProvider.fetchProgramsFromUrl(trimmed)
                    _realEpgPrograms.value = programs
                    onResult?.invoke(true)
                } catch (e: Exception) {
                    android.util.Log.w("IPTVViewModel", "Manuel EPG indirilemedi veya geçersiz: ${e.message}")
                    val autoUrl = playlists.value.firstOrNull { !it.epgUrl.isNullOrBlank() }?.epgUrl
                    if (!autoUrl.isNullOrBlank()) {
                        try {
                            _realEpgPrograms.value = RealEpgProvider.getProgramsByChannelId(autoUrl)
                        } catch (e2: Exception) {
                            android.util.Log.w("IPTVViewModel", "Otomatik EPG indirilemedi: ${e2.message}")
                        }
                    }
                    onResult?.invoke(false)
                }
            } else {
                val autoUrl = playlists.value.firstOrNull { !it.epgUrl.isNullOrBlank() }?.epgUrl
                if (!autoUrl.isNullOrBlank()) {
                    try {
                        _realEpgPrograms.value = RealEpgProvider.getProgramsByChannelId(autoUrl)
                    } catch (e: Exception) {
                        android.util.Log.w("IPTVViewModel", "Otomatik EPG indirilemedi: ${e.message}")
                    }
                }
                onResult?.invoke(true)
            }
        }
    }

    val continueWatching: StateFlow<List<ContinueWatching>> = repository.continueWatchingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<IPTVItem>> = repository.favoriteItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistory>> = repository.getRecentSearchQueriesFlow(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Items combined
    private val _allItems = repository.allItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<IPTVItem>> = _allItems

    // Recommended Items flow (Senin İçin Seçilenler)
    private val _recommendedItems = MutableStateFlow<List<IPTVItem>>(emptyList())
    val recommendedItems: StateFlow<List<IPTVItem>> = _recommendedItems.asStateFlow()

    private val _isRecommendationLoading = MutableStateFlow(false)
    val isRecommendationLoading: StateFlow<Boolean> = _isRecommendationLoading.asStateFlow()

    // TMDB Carousel and Hero Banner States
    private val _trendingList = MutableStateFlow<List<IPTVItem>>(emptyList())
    val trendingList: StateFlow<List<IPTVItem>> = _trendingList.asStateFlow()

    private val _popularMoviesList = MutableStateFlow<List<IPTVItem>>(emptyList())
    val popularMoviesList: StateFlow<List<IPTVItem>> = _popularMoviesList.asStateFlow()

    private val _popularSeriesList = MutableStateFlow<List<IPTVItem>>(emptyList())
    val popularSeriesList: StateFlow<List<IPTVItem>> = _popularSeriesList.asStateFlow()

    private val _topRatedSeriesList = MutableStateFlow<List<IPTVItem>>(emptyList())
    val topRatedSeriesList: StateFlow<List<IPTVItem>> = _topRatedSeriesList.asStateFlow()

    private val _isTMDBLoading = MutableStateFlow(false)
    val isTMDBLoading: StateFlow<Boolean> = _isTMDBLoading.asStateFlow()

    private val _heroBannerItem = MutableStateFlow<IPTVItem?>(null)
    val heroBannerItem: StateFlow<IPTVItem?> = _heroBannerItem.asStateFlow()

    // 12 Saatte bir güncellenen öne çıkan M3U filmi
    private val _featuredMovie = MutableStateFlow<com.example.data.repository.FeaturedMovie?>(null)
    val featuredMovie: StateFlow<com.example.data.repository.FeaturedMovie?> = _featuredMovie.asStateFlow()

    // --- Öne Çıkan kaydırmalı alanı (5 sn'de bir otomatik / parmakla kaydırma) ---
    private val _featuredCarousel = MutableStateFlow<List<com.example.data.repository.FeaturedMovie>>(emptyList())
    private var featuredCarouselJob: kotlinx.coroutines.Job? = null

    /** Öne çıkan film ilk sırada, ardından kütüphaneden seçilen filmler. Yetişkin içerik asla yer almaz. */
    val heroSlides: StateFlow<List<com.example.data.repository.FeaturedMovie>> =
        combine(_featuredMovie, _featuredCarousel) { featured, carousel ->
            buildHeroSlides(featured, carousel) { isAdultFeatured(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    // Manuel / Carousel filmleri (M3U listesinden rastgele 10 film - donma ve performans korumalı)
    private val _moviesState = MutableStateFlow<List<IPTVItem>>(emptyList())
    val moviesState: StateFlow<List<IPTVItem>> = _moviesState.asStateFlow()

    private val _aiBulletinText = MutableStateFlow("🍿 Bugün ne izlemek istersiniz? İlgi alanlarınıza göre harika içerikler keşfetmek için takipte kalın!")
    val aiBulletinText: StateFlow<String> = _aiBulletinText.asStateFlow()

    // Gemini NLP / AI Search States
    private val _nlpSearchResults = MutableStateFlow<List<IPTVItem>>(emptyList())
    val nlpSearchResults: StateFlow<List<IPTVItem>> = _nlpSearchResults.asStateFlow()

    private val _isAILoading = MutableStateFlow(false)
    val isAILoading: StateFlow<Boolean> = _isAILoading.asStateFlow()

    private val _isMoodLoading = MutableStateFlow(false)
    val isMoodLoading: StateFlow<Boolean> = _isMoodLoading.asStateFlow()

    // Available categories dynamically extracted from database query without loading full item objects
    val categories: StateFlow<List<String>> = repository.getDistinctCategoriesFlow()
        .map { rawCats ->
            val finalCats = mutableListOf<String>()
            finalCats.add("Tümü")
            finalCats.addAll(rawCats)
            finalCats
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Tümü"))

    // Filtered by Type and selectedCategory using type-specific Room flows
    val liveChannels: StateFlow<List<IPTVItem>> = combine(
        liveItemsShared.debounce(400),
        _selectedCategory
    ) { items, category ->
        if (category == null || category == "Tümü") {
            items
        } else {
            items.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Tüm canlı kanallar (seçili klasöre göre süzülmez). TV oynatıcısının kategori listesi için. */
    val allLiveChannels: StateFlow<List<IPTVItem>> = liveItemsShared.debounce(400)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movies: StateFlow<List<IPTVItem>> = combine(
        movieItemsShared.debounce(400),
        _selectedCategory
    ) { items, category ->
        if (category == null || category == "Tümü") {
            items
        } else {
            items.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<IPTVItem>> = combine(
        seriesItemsShared.debounce(400),
        _selectedCategory
    ) { items, category ->
        if (category == null || category == "Tümü") {
            items
        } else {
            items.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val legendItems: StateFlow<List<IPTVItem>> = combine(movies, series) { m, s -> m + s }
        .debounce(400)
        .map { pool ->
            val filtered = pool.filterNot { isAdultContent(it) }
            val highRated = filtered.filter { it.rating >= 8.0 }
            if (highRated.isNotEmpty()) highRated.sortedByDescending { it.rating }
            else filtered.filter { it.rating >= 7.5 }.sortedByDescending { it.rating }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieDuelCandidates: StateFlow<List<IPTVItem>> = combine(movies, series) { m, s -> m + s }
        .debounce(400)
        .map { pool ->
            val filtered = pool.filterNot { isAdultContent(it) }
            val highRated = filtered.filter { it.rating >= 7.5 }
            if (highRated.size >= 2) highRated else filtered
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSeries: StateFlow<List<IPTVItem>> = seriesItemsShared
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val xtreamSeriesCatalog: StateFlow<List<com.example.data.model.TvShow>> =
        repository.xtreamSeriesCatalogFlow
            .map { list ->
                list.map { entity ->
                    com.example.data.model.TvShow(
                        id = entity.seriesId,
                        title = entity.name,
                        logoUrl = entity.coverUrl.ifEmpty { entity.backdropUrl }.ifEmpty { null },
                        category = entity.genre.ifEmpty { "Dizi" },
                        rating = entity.rating,
                        summary = entity.plot,
                        cast = entity.cast,
                        director = entity.director,
                        isFavorite = false,
                        seasons = emptyList(),
                        platformName = entity.categoryId.ifBlank { "Diğer" }
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesCoversMap: StateFlow<Map<String, String>> = combine(
        repository.getAllSeriesCoversFlow(),
        repository.xtreamSeriesCatalogFlow
    ) { coversList, catalogList ->
        val map = HashMap<String, String>(coversList.size + catalogList.size)
        // Catalog öncelikli (daha zengin ve güncel)
        catalogList.forEach { entity ->
            if (entity.coverUrl.isNotBlank()) {
                map[entity.canonicalKey] = entity.coverUrl
            }
        }
        // Tekil kaydedilmiş kapakları eksik olanlara ekle
        coversList.forEach { (key, url) ->
            if (url.isNotBlank() && !map.containsKey(key)) {
                map[key] = url
            }
        }
        map
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val groupedTvShows: StateFlow<List<com.example.data.model.TvShow>> = combine(
        allSeries.debounce(400),
        seriesCoversMap.debounce(400)
    ) { seriesItems, covers ->
        com.example.data.model.SeriesParser.groupItemsIntoShows(seriesItems, covers)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val radioChannels: StateFlow<List<IPTVItem>> = combine(
        radioItemsShared,
        _selectedCategory
    ) { items, category ->
        if (category == null || category == "Tümü") {
            items
        } else {
            items.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun naturalCompare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]; val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var i2 = i; while (i2 < a.length && a[i2].isDigit()) i2++
                var j2 = j; while (j2 < b.length && b[j2].isDigit()) j2++
                val numA = a.substring(i, i2).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, j2).trimStart('0').ifEmpty { "0" }
                val cmp = if (numA.length != numB.length) numA.length - numB.length else numA.compareTo(numB)
                if (cmp != 0) return cmp
                i = i2; j = j2
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++; j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
    private val naturalOrderItemComparator = Comparator<IPTVItem> { a, b ->
        naturalCompare(a.cleanedName.ifBlank { a.name }, b.cleanedName.ifBlank { b.name })
    }
    private val SERIES_WEEKDAY_ORDER = listOf("pazartesi", "salı", "çarşamba", "perşembe", "cuma", "cumartesi", "pazar")
    private val DIGITAL_PLATFORM_KEYWORDS = listOf("netflix", "disney", "exxen", "blutv", "blu tv", "gain", "hbo", "apple", "amazon", "prime", "bein connect")
    internal fun seriesCategoryRank(name: String): Pair<Int, String> {
        val lower = name.lowercase(java.util.Locale.ROOT)
        val weekdayIdx = SERIES_WEEKDAY_ORDER.indexOfFirst { lower.contains(it) }
        return when {
            weekdayIdx >= 0 -> 0 to weekdayIdx.toString().padStart(2, '0')
            DIGITAL_PLATFORM_KEYWORDS.any { lower.contains(it) } -> 2 to lower
            else -> 1 to lower
        }
    }
    internal fun movieCategoryRank(name: String): Pair<Int, String> {
        val lower = name.lowercase(java.util.Locale.ROOT)
        return when {
            lower.contains("yeni") -> 0 to lower
            DIGITAL_PLATFORM_KEYWORDS.any { lower.contains(it) } -> 2 to lower
            else -> 1 to lower
        }
    }

    val liveGroups: StateFlow<List<com.example.data.model.IPTVGroup>> = liveItemsShared
        .map { items ->
            items.groupBy { it.category }
                .map { (cat, list) ->
                    com.example.data.model.IPTVGroup(id = cat, name = cat, type = "LIVE", items = list.sortedWith(naturalOrderItemComparator))
                }
                .sortedBy { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movieGroups: StateFlow<List<com.example.data.model.IPTVGroup>> = movieItemsShared
        .map { items ->
            items.groupBy { it.category }
                .map { (cat, list) ->
                    com.example.data.model.IPTVGroup(id = cat, name = cat, type = "MOVIE", items = list.sortedWith(naturalOrderItemComparator))
                }
                .sortedWith(compareBy({ movieCategoryRank(it.name).first }, { movieCategoryRank(it.name).second }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesGroups: StateFlow<List<com.example.data.model.IPTVGroup>> = seriesItemsShared
        .map { items ->
            items.groupBy { it.category }
                .map { (cat, list) ->
                    com.example.data.model.IPTVGroup(id = cat, name = cat, type = "SERIES", items = list.sortedWith(naturalOrderItemComparator))
                }
                .sortedWith(compareBy({ seriesCategoryRank(it.name).first }, { seriesCategoryRank(it.name).second }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val directorActorSpotlights: StateFlow<List<SpotlightCollection>> = combine(
        movieItemsShared,
        seriesItemsShared
    ) { movies, series ->
        val candidates = movies + series
        val directorGroups = mutableMapOf<String, MutableList<IPTVItem>>()
        val actorGroups = mutableMapOf<String, MutableList<IPTVItem>>()

        candidates.forEach { item ->
            val d = item.director.trim()
            if (d.isNotBlank()) {
                directorGroups.getOrPut(d) { mutableListOf() }.add(item)
            }
            item.cast.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { actor ->
                actorGroups.getOrPut(actor) { mutableListOf() }.add(item)
            }
        }

        val directorCards = directorGroups.filterValues { it.size >= 2 }
            .entries.sortedByDescending { it.value.size }
            .take(4)
            .map { (name, list) ->
                SpotlightCollection(
                    title = "🎬 $name Sineması",
                    subtitle = list.take(3).joinToString(" • ") { it.cleanedName },
                    queryKey = name
                )
            }

        val actorCards = actorGroups.filterValues { it.size >= 2 }
            .entries.sortedByDescending { it.value.size }
            .take(4)
            .map { (name, list) ->
                SpotlightCollection(
                    title = "🎭 $name Koleksiyonu",
                    subtitle = list.take(3).joinToString(" • ") { it.cleanedName },
                    queryKey = name
                )
            }

        (directorCards + actorCards)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trailerBoxItems: StateFlow<List<IPTVItem>> = combine(
        movieItemsShared,
        seriesItemsShared
    ) { movies, series ->
        (movies + series)
            .filter { !it.trailerUrl.isNullOrBlank() }
            .shuffled()
            .take(15)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class ChatMessage(
        val role: String,
        val text: String,
        val matchedItem: IPTVItem? = null,
        /** Asistanın tahmin ettiği yapım adı (kütüphanede bulunamasa da; TV "kütüphanende yok" der). */
        val detectedTitle: String = "",
        /** Kütüphanede bulunan olası yapımlar (TV'de yan yana kart); ilki [matchedItem]. */
        val matchedItems: List<IPTVItem> = emptyList()
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    val aiRecommendationHistory: StateFlow<List<AiRecommendationHistory>> =
        repository.getAiRecommendationHistoryFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _chatMessages.value = _chatMessages.value + ChatMessage(role = "user", text = userText)
            _isChatLoading.value = true
            try {
                val historyForPrompt = _chatMessages.value.dropLast(1).takeLast(10).map { it.role to it.text }
                val aiResult = try {
                    com.example.data.api.GeminiAiService.chatAboutMoviesAndSeries(getApplication(), historyForPrompt, userText)
                } catch (e: Throwable) {
                    Log.w("IPTVViewModel", "chat AI call failed safely: ${e.message}")
                    com.example.data.api.GeminiAiService.ChatAiResult("Üzgünüm, şu anda yanıt veremiyorum. Lütfen tekrar deneyin.", "")
                }

                // Kütüphane eşleştirmesi: asistanın olası yapımları (Türkçe + orijinal ad) Türkçe harf, etiket ve
                // yıl farklarına dayanıklı biçimde aranır; bulunamazsa eski basit arama denenir.
                var matchedItems: List<IPTVItem> = emptyList()
                try {
                    matchedItems = tvCatalog.findTitles(aiResult.candidates)
                    if (matchedItems.isEmpty() && aiResult.detectedTitle.isNotBlank()) {
                        matchedItems = repository.searchCollectionItems(aiResult.detectedTitle, limit = 3)
                            .filter { it.type == "MOVIE" || it.type == "SERIES" }
                            .take(1)
                    }
                } catch (e: Throwable) {
                    Log.w("IPTVViewModel", "chat library search failed safely: ${e.message}")
                }
                val matchedItem: IPTVItem? = matchedItems.firstOrNull()

                _chatMessages.value = _chatMessages.value + ChatMessage(
                    role = "ai",
                    text = aiResult.reply,
                    matchedItem = matchedItem,
                    detectedTitle = aiResult.detectedTitle,
                    matchedItems = matchedItems
                )

                try {
                    repository.saveAiRecommendationHistory(
                        AiRecommendationHistory(
                            query = userText,
                            reply = aiResult.reply,
                            detectedTitle = aiResult.detectedTitle,
                            matchedItemId = matchedItem?.id,
                            matchedItemName = matchedItem?.cleanedName,
                            matchedItemLogoUrl = matchedItem?.logoUrl,
                            matchedItemType = matchedItem?.type,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Throwable) {
                    Log.w("IPTVViewModel", "saving AI history failed safely: ${e.message}")
                }
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    fun deleteAiHistoryItem(id: Int) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.deleteAiRecommendationHistory(id)
        }
    }

    fun clearAllAiHistory() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.clearAllAiRecommendationHistory()
        }
    }

    // Search Results combined with Grouped Series logic and NLP Search
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<IPTVItem>> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isEmpty()) flowOf(emptyList()) else repository.searchItems(query)
        },
        _nlpSearchResults
    ) { results, nlpResults ->
        val filtered = if (nlpResults.isNotEmpty() && _searchQuery.value.isNotEmpty()) {
            nlpResults
        } else {
            results
        }
        
        val seriesItems = filtered.filter { it.type == "SERIES" }
        val otherItems = filtered.filter { it.type != "SERIES" }
        
        val groupedSeries = seriesItems.groupBy { item ->
            val cleanName = cleanSeriesNameForGrouping(item.cleanedName.ifEmpty { item.name })
            cleanName.lowercase().trim()
        }.map { (key, itemsInGroup) ->
            val firstItem = itemsInGroup.first()
            val cleanName = cleanSeriesNameForGrouping(firstItem.cleanedName.ifEmpty { firstItem.name })
            val logo: String? = null
            
            firstItem.copy(
                name = cleanName,
                cleanedName = cleanName,
                logoUrl = logo
            )
        }
        
        otherItems + groupedSeries
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Collect & initialize user profile from persistent DataStore
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                // Disk okuması takılırsa açılıştaki yükleme göstergesi sonsuza kadar dönmesin.
                val savedEmail = kotlinx.coroutines.withTimeoutOrNull(8_000) { userPreferencesRepository.userEmailFlow.firstOrNull() }
                val savedName = kotlinx.coroutines.withTimeoutOrNull(2_000) { userPreferencesRepository.userNameFlow.firstOrNull() }
                val savedAvatar = kotlinx.coroutines.withTimeoutOrNull(2_000) { userPreferencesRepository.profileAvatarFlow.firstOrNull() } ?: "avatar_1"
                
                if (!savedEmail.isNullOrBlank()) {
                    _userEmail.value = savedEmail
                    _userName.value = savedName ?: "Kullanıcı"
                    _userPhotoUrl.value = savedAvatar
                    _userProfile.value = UserProfile(
                        displayName = savedName ?: "Kullanıcı",
                        email = savedEmail,
                        photoUrl = savedAvatar
                    )
                } else {
                    _userEmail.value = null
                    _userName.value = null
                    _userPhotoUrl.value = null
                    _userProfile.value = null
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error restoring user preferences", e)
            } finally {
                _isStateReady.value = true
            }

            userPreferencesRepository.userNameFlow.collect { name ->
                if (!name.isNullOrBlank()) {
                    _userName.value = name
                    _userProfile.value = _userProfile.value?.copy(displayName = name)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            userPreferencesRepository.profileAvatarFlow.collect { avatar ->
                if (!avatar.isNullOrBlank()) {
                    _userPhotoUrl.value = avatar
                    _userProfile.value = _userProfile.value?.copy(photoUrl = avatar)
                }
            }
        }

        // Strict Local-First Architecture: Instant startup (0-second launch) using local Room DB with full crash protection
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                val localChannels = repository.getLocalChannels()
                if (localChannels.isNotEmpty()) {
                    // DB has data -> bypass network request completely and load UI state instantly
                    _isLoading.value = false
                    val autoRefresh = settingsRepository.autoRefreshListFlow.firstOrNull() ?: true
                    if (autoRefresh) {
                        launch(Dispatchers.IO) {
                            try {
                                repository.fetchFromNetwork()
                            } catch (e: Exception) {
                                Log.w("IPTVViewModel", "Background silent fetchFromNetwork failed: ${e.message}")
                            }
                        }
                    }
                } else {
                    // DB is empty -> initial fetch from network
                    repository.getHomeData()
                    _isLoading.value = false
                }
                loadTMDBDiscoverContent()
                prefetchSeriesCovers()
                syncXtreamSeriesCatalog()
                runVodStreamsDiagnostic()
            } catch (oom: OutOfMemoryError) {
                Log.e("IPTVViewModel", "OutOfMemory during startup. Resetting volatile caches.", oom)
                com.example.util.StorageOptimizer.emergencyResetCorruptedData(getApplication())
                com.example.util.ErrorHandlingManager.emitThrowable(oom)
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error loading initial data on startup. Recovering safely.", e)
                com.example.util.ErrorHandlingManager.emitThrowable(e)
            } finally {
                _isLoading.value = false
            }
        }

        // Listen for sync interval preference changes to schedule or update WorkManager
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            syncInterval.collectLatest { interval ->
                com.example.worker.SyncWorker.scheduleSync(getApplication(), interval)
            }
        }

        // Observe continueWatching to refresh recommendations
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            continueWatching.collectLatest { cw ->
                updateRecommendations(cw, _allItems.value)
            }
        }

        // Automatically trigger TMDB carousel content loading when key changes
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            tmdbApiKey.collectLatest { key ->
                if (key.isNotBlank()) {
                    loadTMDBDiscoverContent()
                }
            }
        }

        // Observe movies to load/update 12-hour Featured Movie and Carousel Movies
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            movieItemsShared.collectLatest { items ->
                if (items.isNotEmpty()) {
                    loadFeaturedMovie(items, forceUpdate = false)
                    // Favori ekleme vb. her veritabanı güncellemesinde kartlar karışmasın: sadece ilk yüklemede
                    // veya gösterilen filmlerden biri kütüphaneden kalktığında yeniden seç.
                    val shownIds = _featuredCarousel.value.mapNotNull { it.iptvItem?.id }
                    val itemIds = items.mapTo(HashSet()) { it.id }
                    if (shownIds.isEmpty() || shownIds.any { it !in itemIds }) {
                        loadFeaturedCarousel(items)
                    }

                    // Carousel filmleri: Sadece filmleri al, karıştır, en fazla 10 tane al (performans korumalı)
                    val carouselMovies = items
                        .filter { it.type == "MOVIE" || it.type.isBlank() }
                        .shuffled()
                        .take(10)
                    _moviesState.value = carouselMovies
                }
            }
        }
    }

    /**
     * Öne çıkan filmi seçer (uygulamaya her girişte yeni bir film; kaydedilmez).
     */
    fun loadFeaturedMovie(m3uItems: List<IPTVItem> = _allItems.value, forceUpdate: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                // 1. Öne çıkan film telefona kaydedilmez; uygulamaya her girişte yeniden seçilir. Bu oturumda
                // zaten seçildiyse (ör. favori ekleme gibi kütüphane güncellemelerinde) değiştirilmez.
                val current = _featuredMovie.value
                if (current != null && !forceUpdate) return@launch

                // 2. TAMAMEN TMDB'YE BAĞLI: eskiden burada kullanıcının kendi
                // playlist'inden RASTGELE bir film seçilip üzerine TMDB bilgisi
                // ekleniyordu. Artık TMDB'nin gerçek trend/popüler listesi
                // (getTrendingMoviesAndSeries) kullanılıyor; sadece kullanıcının
                // kendi kütüphanesinde GERÇEKTEN karşılığı olan (oynatılabilir)
                // film seçiliyor ki "Hemen İzle" her zaman çalışsın.
                val tmdbTrending = try {
                    tmdbRepository.getTrendingMoviesAndSeries(tmdbApiKey.value)
                } catch (e: Exception) {
                    Log.w("IPTVViewModel", "TMDB trending fetch failed for featured movie", e)
                    emptyList()
                }

                val playableTrendingMovies = tmdbTrending.filter {
                    it.type == "MOVIE" && it.id > 0 && it.streamUrl.isNotBlank() && !isAdultContent(it)
                }

                val selectedMovie = if (playableTrendingMovies.isNotEmpty()) {
                    playableTrendingMovies.random()
                } else {
                    val movieList = m3uItems.filter { item ->
                        val isExplicitMovie = item.type == "MOVIE" || item.type.isBlank()
                        val notSeries = !item.name.contains("S0", ignoreCase = true) &&
                                !item.name.contains("E0", ignoreCase = true) &&
                                !item.name.contains("Bölüm", ignoreCase = true) &&
                                !item.name.contains("Season", ignoreCase = true) &&
                                item.type != "SERIES" && item.type != "LIVE" && item.type != "RADIO"
                        isExplicitMovie && notSeries && item.streamUrl.isNotBlank() && !isAdultContent(item)
                    }
                    val targetList = if (movieList.isNotEmpty()) movieList else m3uItems.filter { it.type != "LIVE" && it.type != "RADIO" && !isAdultContent(it) }
                    targetList.shuffled().firstOrNull()
                }

                if (selectedMovie != null) {
                    // SADECE kullanıcının kaynak linkinden gelen görsel veriyi kullan (TMDB/Gemini'den görsel çekme)
                    val poster = selectedMovie.logoUrl
                    val backdrop = selectedMovie.logoUrl
                    var overview = selectedMovie.summary
                    var rating = if (selectedMovie.rating > 0) String.format("%.1f", selectedMovie.rating) else null

                    try {
                        val tmdbDetails = tmdbRepository.fetchDetailsByTitle(selectedMovie.cleanedName.ifEmpty { selectedMovie.name }, type = "MOVIE", tmdbApiKey = tmdbApiKey.value)
                        if (tmdbDetails != null) {
                            if (tmdbDetails.overview.isNotBlank() && tmdbDetails.overview != "Açıklama bulunamadı.") overview = tmdbDetails.overview
                            if (tmdbDetails.rating > 0.0) rating = String.format("%.1f", tmdbDetails.rating)
                        }
                    } catch (e: Exception) {
                        Log.w("IPTVViewModel", "TMDB search fallback for featured movie", e)
                    }

                    val featured = com.example.data.repository.FeaturedMovie(
                        title = selectedMovie.name,
                        posterUrl = poster,
                        backdropUrl = backdrop ?: poster,
                        overview = overview,
                        imdbRating = rating,
                        releaseDate = selectedMovie.releaseDate,
                        category = selectedMovie.category,
                        streamUrl = selectedMovie.streamUrl,
                        iptvItem = selectedMovie
                    )

                    // 5. UI'a bas
                    _featuredMovie.value = featured
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error loading featured movie", e)
            }
        }
    }

    /** Kullanıcı uygulamaya geri döndüğünde (arka plandan) Öne Çıkan içerikleri yenilenir. */
    fun onAppReturnedToForeground() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            // Paylaşılan film listesi zaten bellekte: veritabanını yeniden okumaya gerek yok.
            val items = movieItemsShared.first()
            if (items.isEmpty()) return@launch
            loadFeaturedMovie(items, forceUpdate = true)
            loadFeaturedCarousel(items)
        }
    }

    private fun isAdultFeatured(movie: com.example.data.repository.FeaturedMovie): Boolean =
        movie.iptvItem?.let { isAdultContent(it) } == true ||
            isAdultContent(movie.category ?: "", movie.title)

    /**
     * Kaydırmalı alan için kütüphaneden afişi olan, oynatılabilir, yetişkin olmayan filmlerden rastgele
     * [FEATURED_CAROUSEL_SIZE] tane seçer. Özet/puan eksikse öne çıkan filmdeki gibi TMDB'den tamamlanır.
     */
    fun loadFeaturedCarousel(m3uItems: List<IPTVItem> = movies.value) {
        featuredCarouselJob?.cancel()
        featuredCarouselJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val candidates = m3uItems.filter { item ->
                item.type == "MOVIE" && item.streamUrl.isNotBlank() && !item.logoUrl.isNullOrBlank() &&
                    !isAdultContent(item)
            }
            if (candidates.isEmpty()) {
                _featuredCarousel.value = emptyList()
                return@launch
            }
            // Özeti veya puanı olan filmler önce (daha dolu bir kart); aralarında rastgele.
            val (rich, plain) = candidates.shuffled().partition { it.summary.isNotBlank() || it.rating > 0 }
            val picked = (rich + plain).take(FEATURED_CAROUSEL_SIZE)
            var slides = picked.map { it.toFeaturedMovie() }
            _featuredCarousel.value = slides

            picked.forEachIndexed { index, item ->
                if (item.summary.isNotBlank() && item.rating > 0) return@forEachIndexed
                try {
                    val details = tmdbRepository.fetchDetailsByTitle(
                        item.cleanedName.ifEmpty { item.name }, type = "MOVIE", tmdbApiKey = tmdbApiKey.value
                    ) ?: return@forEachIndexed
                    val current = slides[index]
                    val overview = if (details.overview.isNotBlank() && details.overview != "Açıklama bulunamadı.") details.overview else current.overview
                    val rating = if (details.rating > 0.0) String.format("%.1f", details.rating) else current.imdbRating
                    slides = slides.toMutableList().also { it[index] = current.copy(overview = overview, imdbRating = rating) }
                    _featuredCarousel.value = slides
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("IPTVViewModel", "TMDB details for featured carousel failed", e)
                }
            }
        }
    }

    private fun IPTVItem.toFeaturedMovie() = com.example.data.repository.FeaturedMovie(
        title = cleanedName.ifBlank { name },
        posterUrl = logoUrl,
        backdropUrl = logoUrl,
        overview = summary.ifBlank { null },
        imdbRating = if (rating > 0) String.format("%.1f", rating) else null,
        releaseDate = releaseDate,
        category = category,
        streamUrl = streamUrl,
        iptvItem = this
    )

    private fun prefetchSeriesCovers() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                kotlinx.coroutines.delay(2000)
                val seriesItems = repository.getItemsByTypeDirect("SERIES")
                if (seriesItems.isEmpty()) return@launch

                val existingCovers = try { repository.getAllSeriesCovers() } catch (e: Exception) { emptyMap() }

                val shows = com.example.data.model.SeriesParser.groupItemsIntoShows(seriesItems, existingCovers)

                if (com.example.util.DiagnosticLog.enabled) try {
                    val samples = com.example.data.model.SeriesParser.FallbackDiagnostics.getSamples()
                    val msg = StringBuilder()
                    msg.append("Toplam ham dizi/bölüm satırı (iptv_items, type=SERIES): ${seriesItems.size}\n")
                    msg.append("Gruplama sonrası TESPİT EDİLEN dizi sayısı: ${shows.size}\n")
                    msg.append("Sondaki-düz-sayı yedek yoluna düşen örnek ham isimler (ilk ${samples.size}):\n")
                    samples.forEachIndexed { i, s -> msg.append("  ${i + 1}. $s\n") }
                    val ambiguousMovies = com.example.data.repository.IPTVRepository.ambiguousMovieClassificationSamples
                    msg.append("\nMOVIE sayılan ama belirsiz (dizi olabilecek) örnek isimler (ilk ${ambiguousMovies.size}):\n")
                    ambiguousMovies.forEachIndexed { i, s -> msg.append("  ${i + 1}. $s\n") }
                    com.example.util.DiagnosticLog.write(getApplication<Application>(), "series_grouping_diag", msg.toString())
                } catch (e: Exception) { }
                var pending = shows.filter { show ->
                    val key = show.title.lowercase(java.util.Locale.ROOT).trim()
                    !existingCovers.containsKey(key) && show.logoUrl.isNullOrBlank()
                }
                if (pending.isEmpty()) return@launch

                Log.d("IPTVViewModel", "prefetchSeriesCovers: ${pending.size} dizi için kapak aranacak")

                val firstRepresentative = pending.firstNotNullOfOrNull { show ->
                    show.seasons.firstOrNull()?.episodes?.firstOrNull()?.item
                }
                if (firstRepresentative != null) {
                    val bulkCovers = try {
                        com.example.data.api.MetadataEnricher.fetchAllSeriesCoversFromXtreamBulk(getApplication(), firstRepresentative)
                    } catch (e: Exception) {
                        Log.w("IPTVViewModel", "Toplu Xtream dizi listesi hatası: ${e.message}")
                        emptyMap()
                    }
                    if (bulkCovers.isNotEmpty()) {
                        for (show in pending) {
                            val cover = bulkCovers[show.title.lowercase(java.util.Locale.ROOT).trim()]
                            if (!cover.isNullOrBlank()) {
                                try {
                                    repository.saveSeriesCover(show.title.lowercase(java.util.Locale.ROOT).trim(), cover)
                                } catch (e: Exception) {
                                    Log.w("IPTVViewModel", "Toplu kapak kaydetme hatası (${show.title}): ${e.message}")
                                }
                            }
                        }
                        val matchedKeys = bulkCovers.keys
                        pending = pending.filter { show ->
                            !matchedKeys.contains(show.title.lowercase(java.util.Locale.ROOT).trim())
                        }
                        Log.d("IPTVViewModel", "Toplu çağrı: ${bulkCovers.size} kapak bulundu, ${pending.size} dizi hâlâ eksik")
                    }
                }
                if (pending.isEmpty()) return@launch

                for (show in pending) {
                    val representativeItem = show.seasons.firstOrNull()?.episodes?.firstOrNull()?.item
                    if (representativeItem == null) continue
                    val key = show.title.lowercase(java.util.Locale.ROOT).trim()
                    var resolvedCover: String? = null
                    try {
                        val meta = com.example.data.api.MetadataEnricher.fetchXtreamMetadata(getApplication(), representativeItem)
                        if (meta != null && !meta.logoUrl.isNullOrBlank()) {
                            resolvedCover = meta.logoUrl
                        }
                    } catch (e: Exception) {
                        Log.w("IPTVViewModel", "prefetchSeriesCovers: ${show.title} Xtream API hatası: ${e.message}")
                    }
                    if (!resolvedCover.isNullOrBlank()) {
                        try {
                            repository.saveSeriesCover(key, resolvedCover)
                        } catch (e: Exception) {
                            Log.w("IPTVViewModel", "prefetchSeriesCovers: ${show.title} kaydetme hatası: ${e.message}")
                        }
                    }
                    kotlinx.coroutines.delay(400)
                }
                Log.d("IPTVViewModel", "prefetchSeriesCovers: tamamlandı")
            } catch (e: Exception) {
                Log.w("IPTVViewModel", "prefetchSeriesCovers genel hata: ${e.message}")
            }
        }
    }

    /**
     * Xtream get_series kataloğunu arka planda çeker ve Room'a yazar.
     * Bu sayede FolderGridScreen ve SeriesDetailScreen kapakları ve meta verileri
     * sıfır bekleme süresiyle (Room önbelleği üzerinden) gösterir.
     */
    fun syncXtreamSeriesCatalog() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("cinestream_sync_prefs", android.content.Context.MODE_PRIVATE)
                val lastSync = prefs.getLong("last_xtream_series_catalog_sync", 0L)
                val sixHoursMillis = 2L * 60L * 1000L // GEÇİCİ: aktif test için 2 dakika
                if (System.currentTimeMillis() - lastSync < sixHoursMillis) {
                    Log.d("IPTVViewModel", "syncXtreamSeriesCatalog: son senkronizasyon yakın zamanda oldu, atlanıyor")
                    return@launch
                }

                kotlinx.coroutines.delay(1500)
                val representative = repository.getItemsByTypeDirect("MOVIE").firstOrNull { it.streamUrl.isNotBlank() }
                    ?: repository.getItemsByTypeDirect("LIVE").firstOrNull { it.streamUrl.isNotBlank() }
                    ?: repository.getItemsByTypeDirect("SERIES").firstOrNull { it.streamUrl.isNotBlank() }
                    ?: return@launch

                val catalog = com.example.data.api.MetadataEnricher.fetchFullXtreamSeriesCatalog(
                    getApplication(),
                    representative
                )
                if (catalog.isNotEmpty()) {
                    repository.saveXtreamSeriesCatalog(catalog)
                    prefs.edit().putLong("last_xtream_series_catalog_sync", System.currentTimeMillis()).apply()
                    Log.d("IPTVViewModel", "syncXtreamSeriesCatalog: ${catalog.size} dizi Room'a kaydedildi")
                    reconcileSeriesTypeWithCatalog(catalog)
                }
            } catch (e: Exception) {
                Log.w("IPTVViewModel", "syncXtreamSeriesCatalog hatası: ${e.message}")
            }
        }
    }

    private fun runVodStreamsDiagnostic() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("cinestream_sync_prefs", android.content.Context.MODE_PRIVATE)
                val lastRun = prefs.getLong("last_vod_streams_diag", 0L)
                val cooldownMillis = 2L * 60L * 1000L // GEÇİCİ: aktif test için 2 dakika
                if (System.currentTimeMillis() - lastRun < cooldownMillis) return@launch

                kotlinx.coroutines.delay(3000)
                val movieItems = repository.getItemsByTypeDirect("MOVIE")
                val representative = movieItems.firstOrNull { it.streamUrl.isNotBlank() } ?: return@launch
                val newMovies = com.example.data.api.MetadataEnricher.fetchXtreamVodStreamsAsItems(getApplication(), representative)
                if (newMovies.isNotEmpty()) {
                    repository.replaceAllMovies(newMovies)
                    Log.d("IPTVViewModel", "runVodStreamsDiagnostic: ${newMovies.size} film Xtream'den değiştirildi")
                }
                prefs.edit().putLong("last_vod_streams_diag", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w("IPTVViewModel", "runVodStreamsDiagnostic hatası: ${e.message}")
            }
        }
    }

    private suspend fun reconcileSeriesTypeWithCatalog(catalog: List<com.example.data.model.XtreamSeriesCatalogEntity>) {
        try {
            val catalogNames = catalog.map { it.name.lowercase(java.util.Locale.ROOT).trim() }.toSet()
            fun matchesCatalog(title: String): Boolean {
                val normalized = title.lowercase(java.util.Locale.ROOT).trim()
                return catalogNames.any { canonical ->
                    canonical.length >= 3 && (
                        normalized == canonical ||
                        Regex("(?:^|\\s)${Regex.escape(canonical)}(?:\\s|$)").containsMatchIn(normalized)
                    )
                }
            }

            val seriesItems = repository.getItemsByTypeDirect("SERIES")
            if (seriesItems.isEmpty()) return
            val shows = com.example.data.model.SeriesParser.groupItemsIntoShows(seriesItems)

            val idsToDemote = mutableListOf<Int>()
            for (show in shows) {
                val totalEpisodes = show.seasons.sumOf { it.episodes.size }
                if (totalEpisodes in 1..4 && !matchesCatalog(show.title)) {
                    show.seasons.forEach { season ->
                        season.episodes.forEach { ep -> idsToDemote.add(ep.item.id) }
                    }
                }
            }

            if (idsToDemote.isNotEmpty()) {
                repository.updateItemsType(idsToDemote, "MOVIE")
                Log.d("IPTVViewModel", "reconcileSeriesTypeWithCatalog: ${idsToDemote.size} öğe MOVIE'ye çevrildi (katalogda karşılığı yok, az bölümlü)")
            }
        } catch (e: Exception) {
            Log.w("IPTVViewModel", "reconcileSeriesTypeWithCatalog hatası: ${e.message}")
        }
    }

    fun loadTMDBDiscoverContent() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _isTMDBLoading.value = true
            val key = tmdbApiKey.value
            val history = continueWatching.value
            val favs = favorites.value

            try {
                Log.d("IPTVViewModel", "Loading TMDB Trending V.O.D & Discover Content...")
                // 1. Fetch TMDB Trending Movies & Series for "TMDB Trend V.O.D" Carousel
                val tmdbTrending = tmdbRepository.getTrendingMoviesAndSeries(key)
                val geminiPicks = com.example.data.api.MetadataEnricher.getGeminiDailyPicks(getApplication())
                val finalTrending = if (tmdbTrending.isNotEmpty()) tmdbTrending else geminiPicks
                _trendingList.value = finalTrending.filterNot { isAdultContent(it) }
                _heroBannerItem.value = finalTrending.firstOrNull { !isAdultContent(it) }

                // 2. Fetch popular movies & series from TMDB or fallback
                val popularMovies = tmdbRepository.getPopularMoviesList(key)
                _popularMoviesList.value = (if (popularMovies.isNotEmpty()) popularMovies else geminiPicks.filter { it.type == "MOVIE" }).filterNot { isAdultContent(it) }

                val popularSeries = tmdbRepository.getPopularSeriesList(key)
                _popularSeriesList.value = (if (popularSeries.isNotEmpty()) popularSeries else geminiPicks.filter { it.type == "SERIES" }).filterNot { isAdultContent(it) }

                val topRatedSeries = tmdbRepository.getTopRatedSeriesList(key)
                _topRatedSeriesList.value = if (topRatedSeries.isNotEmpty()) topRatedSeries else geminiPicks.filter { it.type == "SERIES" }

                // 4. Update AI Bulletin text
                _aiBulletinText.value = com.example.data.api.MetadataEnricher.getAIBulletin(getApplication(), history)

                // 5. Update Recommendations List (Sizin İçin Seçilenler)
                val referenceItem = history.firstOrNull()?.let { cw ->
                    _allItems.value.firstOrNull { it.id == cw.itemId }
                } ?: favs.firstOrNull()

                val tmdbRecs = tmdbRepository.getRecommendationsList(key, referenceItem)
                val rawList = if (tmdbRecs.isNotEmpty()) tmdbRecs else geminiPicks.shuffled()
                _recommendedItems.value = rawList.filter { it.type == "MOVIE" || it.type == "SERIES" }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error loading discover content", e)
            } finally {
                _isTMDBLoading.value = false
            }
        }
    }

    fun performNLPSearch(query: String) {
        if (query.isBlank()) {
            _nlpSearchResults.value = emptyList()
            return
        }
        saveSearchQuery(query)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + coroutineExceptionHandler) {
            _isAILoading.value = true
            try {
                val matchedIds = com.example.data.api.MetadataEnricher.nlpSearchWithGemini(
                    getApplication(),
                    query,
                    _allItems.value
                )
                if (matchedIds.isNotEmpty()) {
                    val results = _allItems.value.filter { it.id in matchedIds }
                    _nlpSearchResults.value = results
                } else {
                    _nlpSearchResults.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in NLP Search", e)
                _nlpSearchResults.value = emptyList()
            } finally {
                _isAILoading.value = false
            }
        }
    }

    fun performMoodBasedRecommendation(
        moodQuery: String,
        onRecommendationFound: (IPTVItem) -> Unit,
        onError: (String) -> Unit
    ) {
        if (moodQuery.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + coroutineExceptionHandler) {
            _isMoodLoading.value = true
            try {
                val pool = (_allItems.value + _trendingList.value + _popularMoviesList.value + _popularSeriesList.value)
                    .distinctBy { it.id }

                val matchedId = com.example.data.api.MetadataEnricher.getMoodBasedRecommendation(
                    getApplication(),
                    moodQuery,
                    pool
                )
                if (matchedId != null) {
                    val item = pool.find { it.id == matchedId }
                    if (item != null) {
                        onRecommendationFound(item)
                    } else {
                        val fallbackItem = pool.filter { it.type == "MOVIE" || it.type == "SERIES" }.randomOrNull() ?: pool.firstOrNull()
                        if (fallbackItem != null) {
                            onRecommendationFound(fallbackItem)
                        } else {
                            onError("Önerilen içerik bulunamadı. Lütfen tekrar deneyin!")
                        }
                    }
                } else {
                    val fallbackItem = pool.filter { it.type == "MOVIE" || it.type == "SERIES" }.randomOrNull() ?: pool.firstOrNull()
                    if (fallbackItem != null) {
                        onRecommendationFound(fallbackItem)
                    } else {
                        onError("Ruh halinize tam uyan bir içerik bulamadık. Lütfen farklı kelimeler kullanın!")
                    }
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error in Mood-Based Recommendation", e)
                onError("Yapay zeka analiz yaparken bir hata oluştu.")
            } finally {
                _isMoodLoading.value = false
            }
        }
    }

    fun getSpoilerFreePreviousEpisodesSummary(showTitle: String, season: Int, episode: Int): Flow<String> = flow {
        emit("Yapay zeka bülteni hazırlanıyor...")

        val watchedEpisodes = repository.getWatchedEpisodesForShow(showTitle, season, episode)

        val summary = if (watchedEpisodes.isNotEmpty()) {
            com.example.data.api.MetadataEnricher.getGroundedPreviousEpisodesRecap(
                context = getApplication(),
                showTitle = showTitle,
                watchedEpisodes = watchedEpisodes,
                tmdbApiKey = tmdbApiKey.value
            )
        } else {
            com.example.data.api.MetadataEnricher.getSpoilerFreePreviousEpisodesSummary(
                context = getApplication(),
                showTitle = showTitle,
                currentSeason = season,
                currentEpisode = episode
            )
        }

        emit(summary)
    }.flowOn(Dispatchers.IO)

    fun getParentalControlWarning(title: String, summary: String, cast: String): Flow<String> = flow {
        emit("Ebeveyn kontrol uyarısı analiz ediliyor...")
        val warning = com.example.data.api.MetadataEnricher.getParentalControlWarning(
            getApplication(),
            title,
            summary,
            cast
        )
        emit(warning)
    }.flowOn(Dispatchers.IO)

    private suspend fun updateRecommendations(watchHistory: List<ContinueWatching>, availableItems: List<IPTVItem>) {
        if (availableItems.isEmpty()) {
            _recommendedItems.value = emptyList()
            return
        }

        _isRecommendationLoading.value = true
        try {
            // 1. Try to fetch from Gemini API
            val recommendedIds = com.example.data.api.MetadataEnricher.getRecommendationsWithGemini(
                getApplication(),
                watchHistory,
                availableItems
            )

            if (recommendedIds.isNotEmpty()) {
                val recommendedList = availableItems.filter { it.id in recommendedIds && (it.type == "MOVIE" || it.type == "SERIES") }
                if (recommendedList.isNotEmpty()) {
                    _recommendedItems.value = recommendedList
                    _isRecommendationLoading.value = false
                    return
                }
            }

            // 2. Fallback rule-based recommendation
            // If watch history has items, find their categories and recommend items from the same categories
            if (watchHistory.isNotEmpty()) {
                val watchedCategories = watchHistory.map { it.category }.distinct()
                val recommendedList = availableItems.filter { item ->
                    (item.type == "MOVIE" || item.type == "SERIES") && 
                    item.category in watchedCategories &&
                    watchHistory.none { it.itemId == item.id } // exclude already watched
                }.shuffled().take(8)

                if (recommendedList.isNotEmpty()) {
                    _recommendedItems.value = recommendedList
                    _isRecommendationLoading.value = false
                    return
                }
            }

            // 3. Global fallback (prepopulated trending items)
            val popularItems = availableItems.filter { item ->
                (item.type == "MOVIE" || item.type == "SERIES")
            }.shuffled().take(8)
            
            _recommendedItems.value = popularItems
        } catch (e: Exception) {
            Log.e("IPTVViewModel", "Error updating recommendations", e)
            // Safe fallback to avoid empty screens
            _recommendedItems.value = availableItems.filter { item ->
                (item.type == "MOVIE" || item.type == "SERIES")
            }.take(8)
        } finally {
            _isRecommendationLoading.value = false
        }
    }

    // Authentication Actions
    fun loginWithGoogle(email: String, name: String, photoUrl: String? = null) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            val effectiveEmail = email.trim()
            val effectiveName = name.trim().ifBlank { "Kullanıcı" }
            val avatar = photoUrl ?: "avatar_1" // paket içi avatar; dışarıdan (gerçek kişi) fotoğraf çekilmez
            
            withContext(Dispatchers.IO) {
                userPreferencesRepository.setUserEmail(effectiveEmail)
                userPreferencesRepository.setUserName(effectiveName)
                userPreferencesRepository.setProfileAvatar(avatar)
                settingsRepository.setUserName(effectiveName)
                settingsRepository.setProfileAvatar(avatar)
            }
            
            _userEmail.value = effectiveEmail
            _userName.value = effectiveName
            _userPhotoUrl.value = avatar
            _userProfile.value = UserProfile(
                displayName = effectiveName,
                email = effectiveEmail,
                photoUrl = avatar
            )
            _isLoading.value = false
        }
    }

    fun setUserName(name: String) {
        val trimmed = name.trim()
        _userName.value = trimmed.ifBlank { "Kullanıcı" }
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            userPreferencesRepository.setUserName(trimmed)
            settingsRepository.setUserName(trimmed)
        }
    }

    fun setUserPhotoUrl(photoUrl: String) {
        _userPhotoUrl.value = photoUrl
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            userPreferencesRepository.setProfileAvatar(photoUrl)
            settingsRepository.setProfileAvatar(photoUrl)
        }
    }

    fun updateUserProfile(displayName: String, email: String, photoUrl: String? = null) {
        _userName.value = displayName
        _userEmail.value = email
        if (!photoUrl.isNullOrBlank()) {
            _userPhotoUrl.value = photoUrl
        }
        _userProfile.value = UserProfile(
            displayName = displayName,
            email = email,
            photoUrl = photoUrl ?: _userPhotoUrl.value
        )
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            userPreferencesRepository.setUserEmail(email)
            userPreferencesRepository.setUserName(displayName)
            if (!photoUrl.isNullOrBlank()) {
                userPreferencesRepository.setProfileAvatar(photoUrl)
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            userPreferencesRepository.clearUserSession()
            com.example.data.repository.UserPreferences(getApplication()).setSetupCompleted(false)
        }
        _userEmail.value = null
        _userName.value = null
        _userPhotoUrl.value = null
        _userProfile.value = null
    }

    fun completeSetup() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            userPreferencesRepository.setSetupCompleted(true)
            com.example.data.repository.UserPreferences(getApplication()).setSetupCompleted(true)
        }
    }

    private val _playlistSetupError = MutableStateFlow<String?>(null)
    val playlistSetupError: StateFlow<String?> = _playlistSetupError.asStateFlow()

    fun clearPlaylistSetupError() {
        _playlistSetupError.value = null
    }

    private val _importProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val importProgress: StateFlow<Map<String, Int>> = _importProgress.asStateFlow()

    private val _importFinishedSuccessfully = MutableStateFlow(false)
    val importFinishedSuccessfully: StateFlow<Boolean> = _importFinishedSuccessfully.asStateFlow()

    private fun extractXtreamCredsFromUrl(url: String): Triple<String, String, String>? {
        return try {
            val uri = android.net.Uri.parse(url.trim())
            val path = uri.path ?: return null
            if (!path.contains("get.php", ignoreCase = true)) return null
            val user = uri.getQueryParameter("username")
            val pass = uri.getQueryParameter("password")
            if (user.isNullOrBlank() || pass.isNullOrBlank()) return null
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            Triple("$scheme://$host$port", user, pass)
        } catch (e: Exception) {
            null
        }
    }

    fun setupPlaylists(
        m3uUrl: String,
        xcodeHost: String,
        xcodeUser: String,
        xcodePass: String,
        onFinished: () -> Unit
    ) {
        viewModelScope.launch(coroutineExceptionHandler) {
            try {
                _isLoading.value = true
                _playlistSetupError.value = null
                _importProgress.value = emptyMap()
                _importFinishedSuccessfully.value = false
                val errors = mutableListOf<String>()
                var anySucceeded = false

                repository.clearPlaylists()

                if (m3uUrl.isNotBlank()) {
                    val xtreamFromM3u = extractXtreamCredsFromUrl(m3uUrl)
                    if (xtreamFromM3u != null) {
                        try {
                            val count = repository.importXtreamDirectly(
                                context = getApplication(),
                                playlistName = "Kişisel M3U8 (Xtream)",
                                hostRaw = xtreamFromM3u.first,
                                user = xtreamFromM3u.second,
                                pass = xtreamFromM3u.third
                            ) { progress -> _importProgress.value = progress }
                            if (count <= 0) {
                                errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_xtream_empty))
                            } else {
                                anySucceeded = true
                            }
                        } catch (e: Exception) {
                            Log.w("IPTVViewModel", "setupPlaylists Xtream(M3U) import failed: ${e.message}")
                            errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_content, e.message ?: com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.unknown_error_word)))
                        }
                    } else {
                        try {
                            val count = repository.importRemotePlaylist("Kişisel M3U8", m3uUrl) { progress ->
                                _importProgress.value = progress
                            }
                            if (count <= 0) {
                                errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_m3u_empty))
                            } else {
                                anySucceeded = true
                            }
                        } catch (e: Exception) {
                            Log.w("IPTVViewModel", "setupPlaylists M3U import failed: ${e.message}")
                            errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_m3u, e.message ?: com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.unknown_error_word)))
                        }
                    }
                }

                if (xcodeHost.isNotBlank() && xcodeUser.isNotBlank() && xcodePass.isNotBlank()) {
                    try {
                        val count = repository.importXtreamDirectly(
                            context = getApplication(),
                            playlistName = "Xcode Listesi",
                            hostRaw = xcodeHost.trim(),
                            user = xcodeUser.trim(),
                            pass = xcodePass.trim()
                        ) { progress -> _importProgress.value = progress }
                        if (count <= 0) {
                            errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_xtream_empty))
                        } else {
                            anySucceeded = true
                        }
                    } catch (e: Exception) {
                        Log.w("IPTVViewModel", "setupPlaylists Xtream import failed: ${e.message}")
                        errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_xtream, e.message ?: com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.unknown_error_word)))
                    }
                }

                if (!anySucceeded && errors.isEmpty()) {
                    errors.add(com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_nothing))
                }

                if (errors.isNotEmpty()) {
                    _playlistSetupError.value = errors.joinToString("\n")
                }
                if (anySucceeded) {
                    _importFinishedSuccessfully.value = true
                    kotlinx.coroutines.delay(1400)
                }
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "setupPlaylists beklenmeyen hata", e)
                _playlistSetupError.value = com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.setup_error_unexpected, e.message ?: com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.unknown_error_word))
            } finally {
                _isLoading.value = false
                onFinished()
            }
        }
    }

    // Global Theme Switcher Actions
    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
    }

    // Search Action
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _nlpSearchResults.value = emptyList()
    }

    fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.saveSearchQuery(trimmed)
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.deleteSearchQuery(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            repository.clearSearchHistory()
        }
    }

    // Category Filter Action
    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSelectedTypeFilter(type: String) {
        if (_selectedTypeFilter.value != type) {
            _selectedTypeFilter.value = type
            _activeFolderGroup.value = null
        }
    }

    fun setActiveFolderGroup(group: IPTVGroup?) {
        _activeFolderGroup.value = group
    }

    fun findMatchedItem(item: IPTVItem): IPTVItem {
        val all = _allItems.value
        if (item.id != 0 && item.id > 0) {
            val byId = all.firstOrNull { it.id == item.id }
            if (byId != null) return byId
        }
        val title = item.cleanedName.ifBlank { item.name }.trim()
        if (title.isBlank()) return item

        // 1. Exact match by cleanedName or raw name
        val exactMatch = all.firstOrNull { local ->
            local.type == item.type && (
                local.cleanedName.equals(title, ignoreCase = true) ||
                local.name.equals(title, ignoreCase = true)
            )
        }
        if (exactMatch != null) return exactMatch

        // 2. Contains match for smart M3U search
        val containsMatch = all.firstOrNull { local ->
            local.type == item.type && (
                local.cleanedName.contains(title, ignoreCase = true) ||
                title.contains(local.cleanedName, ignoreCase = true) ||
                local.name.contains(title, ignoreCase = true)
            )
        }
        if (containsMatch != null) return containsMatch

        return item
    }

    private val _liveFetchedShow = MutableStateFlow<com.example.data.model.TvShow?>(null)
    val liveFetchedShow: StateFlow<com.example.data.model.TvShow?> = _liveFetchedShow.asStateFlow()
    private val _isLoadingLiveShow = MutableStateFlow(false)
    val isLoadingLiveShow: StateFlow<Boolean> = _isLoadingLiveShow.asStateFlow()

    fun openCatalogShow(catalogShow: com.example.data.model.TvShow) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                _isLoadingLiveShow.value = true
                _liveFetchedShow.value = null
                // Kimlik bilgisi için akışı olan tek bir kayıt yeter (tüm film listesi belleğe alınmaz).
                val credSource = tvCatalog.firstItemWithStream()
                if (credSource == null) {
                    Log.w("IPTVViewModel", "openCatalogShow: kimlik bilgisi kaynağı bulunamadı")
                    return@launch
                }
                val fetchedShow = com.example.data.api.MetadataEnricher.fetchSeriesInfoLive(
                    context = getApplication(),
                    seriesId = catalogShow.id,
                    showTitle = catalogShow.title,
                    showCover = catalogShow.logoUrl,
                    showRating = catalogShow.rating,
                    showSummary = catalogShow.summary,
                    showCast = catalogShow.cast,
                    showDirector = catalogShow.director,
                    anyItemForCredentials = credSource,
                    showCategory = catalogShow.platformName.takeIf { it.isNotBlank() && it != "Diğer" } ?: catalogShow.category
                )
                if (fetchedShow != null) {
                    _liveFetchedShow.value = fetchedShow
                    val firstEp = fetchedShow.seasons.firstOrNull()?.episodes?.firstOrNull()
                    if (firstEp != null) {
                        selectItem(firstEp.item)
                    }
                } else {
                    Log.w("IPTVViewModel", "openCatalogShow: '${catalogShow.title}' canlı çekilemedi")
                }
            } catch (e: Exception) {
                Log.w("IPTVViewModel", "openCatalogShow hatası: ${e.message}")
            } finally {
                _isLoadingLiveShow.value = false
            }
        }
    }

    // Detail Action
    fun selectItem(item: IPTVItem?) {
        if (item != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + coroutineExceptionHandler) {
                val dbItem = if (item.id != 0) {
                    repository.getItemByIdDirect(item.id) ?: _allItems.value.firstOrNull { it.id == item.id } ?: item
                } else {
                    findMatchedItem(item)
                }
                _selectedItem.value = dbItem.withPendingMetadata()
                if (dbItem.type == "MOVIE" || dbItem.type == "SERIES") {
                    enrichItemMetadata(dbItem)
                }
            }
        } else {
            _selectedItem.value = null
        }
    }

    private fun enrichItemMetadata(item: IPTVItem) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val meta = com.example.data.api.MetadataEnricher.enrichWithXtreamOrFallback(getApplication(), item)
                val updatedItem = item.copy(
                    summary = meta.summary.ifBlank { item.summary },
                    cast = meta.cast.ifBlank { item.cast },
                    director = meta.director.ifBlank { item.director },
                    rating = if (meta.rating > 0.0) meta.rating else item.rating,
                    logoUrl = meta.logoUrl?.ifBlank { null } ?: item.logoUrl,
                    trailerUrl = meta.trailerUrl?.ifBlank { null } ?: item.trailerUrl,
                    releaseDate = meta.releaseDate.ifBlank { item.releaseDate },
                    genre = meta.genre.ifBlank { item.genre }
                )

                if (updatedItem.summary != item.summary || updatedItem.cast != item.cast || updatedItem.director != item.director || updatedItem.rating != item.rating) {
                    queueMetadata(updatedItem)
                }

                // If the user hasn't switched selection, update the current UI state on Main thread
                if (_selectedItem.value?.id == item.id) {
                    _selectedItem.value = updatedItem
                }
            } catch (e: Throwable) {
                Log.w("IPTVViewModel", "enrichItemMetadata failed safely for ${item.name}: ${e.message}")
            }
        }
    }

    // ÖNEMLİ: preFetchTopMetadata (80 popüler öğeyi arka planda toplu
    // zenginleştiren fonksiyon) kaldırıldı — hiçbir yerden çağrılmıyordu, ölü kod.

    /**
     * Exposes cached details for a person from Room DB.
     */
    fun getCachedPersonDetails(name: String): Flow<PersonDetailsEntity?> = flow {
        emit(repository.getCachedPersonDetails(name))
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    // ------------------------------------------------------------------
    // Oyuncu penceresindeki arama (PersonDetailDialog kullanır)
    // ------------------------------------------------------------------
    /** Kadro satırındaki avatarlar için TMDB fotoğrafı (tek arama, önbellekli). */
    suspend fun lookupPersonPhoto(name: String, role: String): String? =
        personCreditsRepository.lookupPhoto(name, role)

    private val personCreditsRepository by lazy {
        com.example.data.repository.PersonCreditsRepository(getApplication())
    }

    /**
     * Profesyonel oynatıcılardaki "oyuncuya göre ara" akışı:
     *  1. Sağlayıcının oyuncu bilgisinde ara (Xtream get_series her dizi için cast verir) → anında sonuç.
     *  2. TMDB'den kişinin film + dizi listesini al (combined_credits).
     *  3. Bu listeyi kütüphanedeki adlarla eşleştir (Türkçe + orijinal ad, yıl kontrolü).
     * Her adım ayrı durum olarak yayınlanır; TMDB çalışmasa bile 1. adımın sonuçları gösterilir.
     */
    fun searchPersonWorks(
        personName: String,
        role: String,
        excludeItemId: Int
    ): Flow<com.example.data.model.tmdb.PersonWorksUiState> = flow {
        val query = com.example.data.repository.PersonWorksMatcher.primaryName(personName)
        var state = com.example.data.model.tmdb.PersonWorksUiState(
            query = query,
            phase = com.example.data.model.tmdb.PersonWorksPhase.SEARCHING_LIBRARY
        )
        emit(state)

        val pool = personWorksLibraryPool()
        val current = selectedItem.value
        val castMatches = com.example.data.repository.PersonWorksMatcher.searchByCast(query, pool, excludeItemId)
            .filterNot { com.example.data.repository.PersonWorksMatcher.sameContent(it.item, current) }
        state = state.copy(
            phase = com.example.data.model.tmdb.PersonWorksPhase.FETCHING_TMDB,
            inLibrary = castMatches
        )
        emit(state)

        val outcome = personCreditsRepository.lookupPersonCredits(query, role, tmdbApiKey.value)
        state = when (outcome) {
            is com.example.data.model.tmdb.PersonCreditsOutcome.Found -> {
                val lookup = outcome.lookup
                emit(
                    state.copy(
                        phase = com.example.data.model.tmdb.PersonWorksPhase.MATCHING,
                        tmdbCreditCount = lookup.credits.size,
                        profileUrl = lookup.profileUrl
                    )
                )
                val matched = personCreditsRepository.matchToLibrary(lookup, pool, excludeItemId)
                state.copy(
                    phase = com.example.data.model.tmdb.PersonWorksPhase.DONE,
                    tmdbCreditCount = lookup.credits.size,
                    profileUrl = lookup.profileUrl,
                    inLibrary = com.example.data.repository.PersonWorksMatcher.merge(matched.inLibrary, castMatches)
                        .filterNot { com.example.data.repository.PersonWorksMatcher.sameContent(it.item, current) },
                    notInLibrary = matched.notInLibrary
                )
            }
            is com.example.data.model.tmdb.PersonCreditsOutcome.NotFound -> state.copy(
                phase = com.example.data.model.tmdb.PersonWorksPhase.DONE,
                tmdbProblem = "TMDB'de bu isimde biri bulunamadı"
            )
            is com.example.data.model.tmdb.PersonCreditsOutcome.Failed -> state.copy(
                phase = com.example.data.model.tmdb.PersonWorksPhase.DONE,
                tmdbProblem = outcome.reason
            )
        }
        emit(state)
    }.catch { e ->
        Log.w("IPTVViewModel", "searchPersonWorks error: ${e.message}")
        emit(
            com.example.data.model.tmdb.PersonWorksUiState(
                query = com.example.data.repository.PersonWorksMatcher.primaryName(personName),
                phase = com.example.data.model.tmdb.PersonWorksPhase.DONE,
                tmdbProblem = e.message ?: e.javaClass.simpleName
            )
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    /**
     * Aramanın taradığı kütüphane: filmler (kategori filtresinden bağımsız), Xtream dizi kataloğu
     * (oyuncu listesiyle birlikte) ve M3U dizi bölümleri. Ekrana bağlı listeler boşsa doğrudan
     * veritabanından okunur; sonuç o an hangi ekranın açık olduğuna bağlı kalmaz.
     */
    private suspend fun personWorksLibraryPool(): List<IPTVItem> {
        val category = _selectedCategory.value
        val cachedMovies = movies.value
        val moviePool = if ((category == null || category == "Tümü") && cachedMovies.isNotEmpty()) {
            cachedMovies
        } else {
            repository.getItemsByTypeDirect("MOVIE")
        }

        val catalogShows = xtreamSeriesCatalog.value
        val seriesPool = if (catalogShows.isNotEmpty()) {
            catalogShows.map { show ->
                show.toBrowsableItem().copy(cast = show.cast, director = show.director)
            }
        } else {
            repository.getAllXtreamSeriesCatalog().map { entity ->
                IPTVItem(
                    id = entity.seriesId,
                    playlistId = 0,
                    name = entity.name,
                    cleanedName = entity.name,
                    logoUrl = entity.coverUrl.ifEmpty { entity.backdropUrl }.ifEmpty { null },
                    streamUrl = "",
                    category = entity.genre.ifEmpty { "Dizi" },
                    type = "SERIES",
                    rating = entity.rating,
                    cast = entity.cast,
                    director = entity.director
                )
            }
        }

        return moviePool + seriesPool + allSeries.value
    }

    // ------------------------------------------------------------------
    // Ana sayfa: Sinemada Bu Hafta (TMDB movie/now_playing, kullanıcının ülkesi)
    // ------------------------------------------------------------------
    private val _nowPlaying = MutableStateFlow<List<com.example.data.model.tmdb.NowPlayingEntry>>(emptyList())
    val nowPlaying: StateFlow<List<com.example.data.model.tmdb.NowPlayingEntry>> = _nowPlaying.asStateFlow()
    private val _nowPlayingRegion = MutableStateFlow<com.example.data.model.tmdb.NowPlayingRegionInfo?>(null)
    val nowPlayingRegion: StateFlow<com.example.data.model.tmdb.NowPlayingRegionInfo?> = _nowPlayingRegion.asStateFlow()
    private var nowPlayingMatchKey = ""
    private var nowPlayingJob: kotlinx.coroutines.Job? = null

    /**
     * Kullanıcının ülkesinde vizyondaki filmleri alır (6 saat önbellekli) ve kütüphaneyle eşleştirir.
     * Ülke: kullanıcı seçtiyse o, seçmediyse cihazdan otomatik (şebeke → SIM → sistem bölgesi).
     * Ana sayfa açıldığında ve kütüphane büyüdükçe çağrılır; hiçbir şey değişmediyse eşleştirmeyi tekrarlamaz.
     */
    fun loadNowPlaying(force: Boolean = false) {
        if (!force && nowPlayingJob?.isActive == true) return
        nowPlayingJob?.cancel()
        nowPlayingJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val result = com.example.data.repository.NowPlayingRepository.fetch(getApplication())
            _nowPlayingRegion.value = result.region
            if (result.films.isEmpty()) return@launch
            val moviePool = personWorksLibraryPool().filter { it.type == "MOVIE" }
            val matchKey = "${result.region.code}_${result.films.map { it.id }}_${moviePool.size}"
            if (matchKey == nowPlayingMatchKey && _nowPlaying.value.isNotEmpty()) return@launch
            _nowPlaying.value = com.example.data.repository.NowPlayingRepository.matchToLibrary(result.films, moviePool)
            nowPlayingMatchKey = matchKey
        }
    }

    /** Sinema ülkesini değiştirir; null = otomatik (cihazın bulunduğu ülke). */
    fun setNowPlayingRegion(code: String?) {
        com.example.data.repository.NowPlayingRepository.setRegionOverride(getApplication(), code)
        loadNowPlaying(force = true)
    }

    fun getPersonDetailsFlow(name: String, role: String): Flow<PersonDetails?> = flow {
        try {
            val tmdbDetails = tmdbRepository.getPersonDetails(name, role, tmdbApiKey.value).firstOrNull()
            if (tmdbDetails != null) {
                emit(tmdbDetails)
                return@flow
            }
            val geminiDetails = com.example.data.api.MetadataEnricher.fetchPersonDetails(getApplication(), name, role)
            emit(geminiDetails ?: com.example.data.api.MetadataEnricher.generateLocalPersonDetails(name, role))
        } catch (e: Throwable) {
            Log.w("IPTVViewModel", "getPersonDetailsFlow error: ${e.message}")
            emit(com.example.data.api.MetadataEnricher.generateLocalPersonDetails(name, role))
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    /**
     * Gemini AI ve yerel veri üzerinden kadro ve yönetmen bilgilerini sorgular ve yerel veri tabanında günceller.
     */
    fun fetchCastAndDirectorInfo(title: String, item: IPTVItem? = null, onResult: ((com.example.data.api.CastAndDirectorInfo) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + coroutineExceptionHandler) {
            val info = try {
                com.example.data.api.GeminiAiService.fetchCastAndDirectorInfo(getApplication(), title)
            } catch (e: Throwable) {
                Log.w("IPTVViewModel", "fetchCastAndDirectorInfo Gemini call failed safely: ${e.message}")
                com.example.data.api.CastAndDirectorInfo()
            }

            if (item != null && (info.cast.isNotEmpty() || info.director.isNotBlank())) {
                try {
                    val current = item.withPendingMetadata()
                    val updatedCast = if (info.cast.isNotEmpty()) info.cast.joinToString(", ") else current.cast
                    val updatedDirector = if (info.director.isNotBlank()) info.director else current.director
                    queueMetadata(current.copy(cast = updatedCast, director = updatedDirector))
                } catch (e: Throwable) {
                    Log.w("IPTVViewModel", "fetchCastAndDirectorInfo DB update failed safely: ${e.message}")
                }
            }
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult?.invoke(info)
                }
            } catch (e: Throwable) {
                Log.w("IPTVViewModel", "fetchCastAndDirectorInfo onResult callback failed safely: ${e.message}")
            }
        }
    }

    /**
     * Retrieves folder/category-based recommendations for a specific IPTV item.
     * Recommendations are pulled from the same folder/category (e.g. Korku, Aksiyon),
     * and shuffled on each call so that fresh suggestions are shown every time the detail screen is opened.
     */
    fun getSimilarItemsFlow(item: IPTVItem): Flow<List<IPTVItem>> = flow {
        val activeCategory = item.category.trim().ifBlank { _selectedCategory.value?.trim() ?: "" }
        val activeGenre = item.genre.trim()
        val results = repository.getSimilarItemsByCategory(item.type, activeCategory, activeGenre, limit = 20)
        val pool = if (results.isNotEmpty()) {
            results.filter { it.id != item.id }
        } else {
            repository.getPopularCatalogPoolByType(item.type, limit = 30).filter { it.id != item.id }
        }.filterNot { isAdultContent(it) }
        val finalResults = if (item.type == "LIVE") {
            val todaySeed = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()).toLong()
            pool.shuffled(java.util.Random(todaySeed)).take(12)
        } else {
            pool.shuffled().take(12)
        }

        emit(finalResults)
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    private val visitedRandomItemIds = mutableSetOf<Int>()

    suspend fun searchCollectionItems(query: String): List<IPTVItem> {
        return repository.searchCollectionItems(query, limit = 20)
    }

    fun getRandomItemByGenre(genre: String): IPTVItem? {
        val pool = (_allItems.value + _trendingList.value + _popularMoviesList.value + _popularSeriesList.value)
            .distinctBy { it.id }
        val allMoviesAndSeries = pool.filter { it.type == "MOVIE" || it.type == "SERIES" }.ifEmpty { pool }
        if (allMoviesAndSeries.isEmpty()) return null

        val normGenre = genre.lowercase(java.util.Locale.ROOT).trim()

        val filteredList = allMoviesAndSeries.filter { item ->
            val cat = item.category.lowercase(java.util.Locale.ROOT)
            val name = item.cleanedName.lowercase(java.util.Locale.ROOT)
            when (normGenre) {
                "korku" -> {
                    cat.contains("korku") || cat.contains("horror") || cat.contains("gerilim") || cat.contains("thriller") || name.contains("korku")
                }
                "komedi" -> {
                    cat.contains("komedi") || cat.contains("comedy") || cat.contains("mizah") || cat.contains("eğlenceli") || name.contains("komedi")
                }
                "dram" -> {
                    cat.contains("dram") || cat.contains("drama") || cat.contains("duygusal") || name.contains("dram")
                }
                "tarih & savaş" -> {
                    cat.contains("tarih") || cat.contains("history") || cat.contains("savaş") || cat.contains("war") || cat.contains("tarihi") || cat.contains("military") || name.contains("savaş")
                }
                "aksiyon & macera" -> {
                    cat.contains("aksiyon") || cat.contains("action") || cat.contains("macera") || cat.contains("adventure") || cat.contains("bilim kurgu") || cat.contains("sci-fi") || cat.contains("fantastic") || cat.contains("fantastik") || name.contains("aksiyon")
                }
                else -> {
                    cat.contains(normGenre) || name.contains(normGenre)
                }
            }
        }

        if (filteredList.isEmpty()) {
            val fallbackPool = allMoviesAndSeries
            val unvisitedFallback = fallbackPool.filter { it.id !in visitedRandomItemIds }
            val selected = if (unvisitedFallback.isNotEmpty()) {
                unvisitedFallback.random()
            } else {
                visitedRandomItemIds.clear()
                fallbackPool.randomOrNull()
            }
            selected?.let { visitedRandomItemIds.add(it.id) }
            return selected
        }

        val unvisited = filteredList.filter { it.id !in visitedRandomItemIds }
        return if (unvisited.isNotEmpty()) {
            val selected = unvisited.random()
            visitedRandomItemIds.add(selected.id)
            selected
        } else {
            val allIdsInFiltered = filteredList.map { it.id }.toSet()
            visitedRandomItemIds.removeAll(allIdsInFiltered)
            val selected = filteredList.random()
            visitedRandomItemIds.add(selected.id)
            selected
        }
    }

    // Favorite Action
    fun toggleFavorite(item: IPTVItem) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val updatedVal = !item.isFavorite
            repository.toggleFavorite(item.id, updatedVal)
            // If the selected item is the one favorited, update it
            if (_selectedItem.value?.id == item.id) {
                _selectedItem.value = _selectedItem.value?.copy(isFavorite = updatedVal)
            }
        }
    }

    // M3U Loading Action
    fun loadM3UPlaylist(name: String, content: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            val playlistId = repository.insertPlaylist(
                Playlist(name = name, url = "m3u_text_input")
            ).toInt()
            repository.parseAndSaveM3U(playlistId, content)
            _isLoading.value = false
        }
    }

    // Remote M3U/M3U8 URL Loading Action
    fun loadM3UPlaylistFromUrl(name: String, urlString: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                val xtreamFromM3u = extractXtreamCredsFromUrl(urlString)
                if (xtreamFromM3u != null) {
                    val count = repository.importXtreamDirectly(
                        context = getApplication(),
                        playlistName = name,
                        hostRaw = xtreamFromM3u.first,
                        user = xtreamFromM3u.second,
                        pass = xtreamFromM3u.third
                    )
                    if (count <= 0) {
                        ErrorHandlingManager.emitError(
                            AppError.Unknown(userFriendlyMessage = com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.playlist_error_xtream_empty, name))
                        )
                    }
                } else {
                    val count = repository.importRemotePlaylist(name, urlString)
                    if (count <= 0) {
                        ErrorHandlingManager.emitError(
                            AppError.Unknown(userFriendlyMessage = com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.playlist_error_m3u_empty, name))
                        )
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ErrorHandlingManager.emitThrowable(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadXtreamPlaylist(name: String, serverUrl: String, username: String, password: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                try {
                    val count = repository.importXtreamDirectly(
                        context = getApplication(),
                        playlistName = name,
                        hostRaw = serverUrl.trim(),
                        user = username.trim(),
                        pass = password.trim()
                    )
                    if (count <= 0) {
                        ErrorHandlingManager.emitError(
                            AppError.Unknown(userFriendlyMessage = com.example.util.LocaleHelper.getString(getApplication(), com.example.R.string.playlist_error_xtream_empty, name))
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    ErrorHandlingManager.emitThrowable(e)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ErrorHandlingManager.emitThrowable(e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                repository.deletePlaylist(playlistId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePlaylistDetails(
        playlist: Playlist,
        newName: String,
        newUrl: String,
        isXtream: Boolean = false,
        username: String? = null,
        password: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val updated = playlist.copy(
                    name = newName,
                    url = newUrl,
                    isXtream = isXtream,
                    xtreamUsername = username,
                    xtreamPassword = password
                )
                repository.insertPlaylist(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshSinglePlaylist(playlist: Playlist, onComplete: () -> Unit = {}) {
        syncFetchJob?.cancel()
        syncFetchJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                if (playlist.url.startsWith("http", ignoreCase = true)) {
                    val importedCount = repository.syncPlaylist(playlist)
                    if (importedCount <= 0) {
                        throw java.io.IOException("Playlist güncellenemedi veya geçerli içerik bulunamadı.")
                    }
                } else {
                    throw java.io.IOException("Geçersiz playlist adresi. Lütfen http:// veya https:// ile başlayan geçerli bir link girin.")
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ErrorHandlingManager.emitThrowable(e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun clearCorruptedCache() {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                repository.clearCorruptedCache(getApplication())
            } catch (e: Exception) {
                Log.e("IPTVViewModel", "Error clearing corrupted cache", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Pull-to-refresh: re-fetch and update all active playlists from source only when manually requested
    fun refreshPlaylists(onComplete: () -> Unit = {}) {
        syncFetchJob?.cancel()
        syncFetchJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _isLoading.value = true
            try {
                repository.fetchFromNetwork()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // İnternet yoksa / liste bozuksa ve önbellekte kanal yoksa kullanıcı sessiz bir boş ekran
                // yerine uygulamanın hata bandını görsün (önbellekteki kanallar varsa sync hata atmaz).
                Log.w("IPTVViewModel", "refreshPlaylists failed: ${e.message}")
                ErrorHandlingManager.emitThrowable(e)
            } finally {
                _isLoading.value = false
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    // Save Progress Action (Continue Watching)
    fun saveProgress(item: IPTVItem, progressSeconds: Long, totalSeconds: Long) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.saveContinueWatching(item, progressSeconds, totalSeconds)
        }
    }

    fun deleteProgress(itemId: Int) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.deleteContinueWatching(itemId)
        }
    }

    suspend fun fetchTrailerVideoId(movieTitle: String): String? {
        return try {
            com.example.data.api.MetadataEnricher.fetchYoutubeTrailerId(getApplication(), movieTitle)
        } catch (e: Throwable) {
            Log.w("IPTVViewModel", "fetchTrailerVideoId safe catch: ${e.message}")
            null
        }
    }

    private fun cleanSeriesNameForGrouping(name: String): String {
        var cleaned = name
        
        // SxxExx patterns
        val patterns = listOf(
            "(?i)\\bS\\d+\\s*E\\d+\\b.*",
            "(?i)\\bE\\d+\\s*S\\d+\\b.*",
            "(?i)\\b(?:Season|Sezon|S)\\s*\\d+\\s*(?:Episode|Bölüm|E)\\s*\\d+\\b.*",
            "(?i)\\b(?:Season|Sezon|S)\\s*\\d+\\b.*",
            "(?i)\\b(?:Episode|Bölüm|E)\\s*\\d+\\b.*",
            "(?i)\\b\\d+\\.\\s*(?:Sezon|Bölüm|Season|Episode)\\b.*",
            "(?i)\\b\\d+\\s*x\\s*\\d+\\b.*"
        )
        
        for (pattern in patterns) {
            cleaned = cleaned.replace(Regex(pattern), "")
        }
        
        cleaned = cleaned
            .replace(Regex("^[-:\\s|/\\(\\[\\{\\)\\]\\}]+"), "")
            .replace(Regex("[-:\\s|/\\(\\[\\{\\)\\]\\}]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            
        return cleaned.ifEmpty { name }
    }

    fun sortItems(items: List<IPTVItem>, sortBy: String): List<IPTVItem> {
        return when (sortBy.uppercase(java.util.Locale.ROOT)) {
            "NAME_AZ", "TITLE_ASC", "A-Z", "A_Z", "ALPHABETICAL" -> items.sortedWith(naturalOrderItemComparator)
            "NAME_ZA", "TITLE_DESC", "Z-A", "Z_A" -> items.sortedWith(naturalOrderItemComparator.reversed())
            "RATING_DESC", "IMDB_HIGH", "RATING_HIGH", "PUAN_AZALAN" -> items.sortedByDescending { it.rating }
            "RATING_ASC", "IMDB_LOW", "RATING_LOW", "PUAN_ARTAN" -> items.sortedBy { it.rating }
            "DATE_DESC", "RECENT", "ADDED_DESC", "YENİ", "YENİDEN_ESKİYE" -> items.sortedByDescending { it.id }
            "YEAR", "YEAR_DESC", "YIL" -> items.sortedByDescending { 
                it.releaseDate.takeIf { d -> d.length >= 4 }?.substring(0, 4)?.toIntOrNull() ?: 0 
            }
            "YEAR_ASC", "YIL_ARTAN" -> items.sortedBy { 
                it.releaseDate.takeIf { d -> d.length >= 4 }?.substring(0, 4)?.toIntOrNull() ?: Int.MAX_VALUE 
            }
            else -> items.sortedWith(naturalOrderItemComparator)
        }
    }

    fun isAdultContent(category: String, name: String = ""): Boolean =
        com.example.util.AdultContentFilter.isAdult(category, name)

    fun isAdultContent(item: IPTVItem): Boolean {
        return isAdultContent(item.category, item.cleanedName.ifBlank { item.name })
    }

    /**
     * Oynatıcıdaki kanal listesi ve kanal ileri/geri için kanal halkası (izlenen kanal dahil, liste sırasıyla):
     * izlenen kanalın klasöründeki (kategorisindeki) kanallar. Yetişkin olmayan bir kanal izlenirken yetişkin
     * kanallar asla girmez. Klasörde başka kanal yoksa tüm (uygun) kanallar kullanılır.
     */
    fun channelRingFor(current: IPTVItem, allChannels: List<IPTVItem>): List<IPTVItem> =
        buildChannelRing(current, allChannels) { isAdultContent(it) }

    fun isAdultContent(group: IPTVGroup): Boolean {
        if (isAdultContent(group.name)) return true
        return group.items.any { isAdultContent(it) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = AppDatabase.getDatabase(application)
            val repository = IPTVRepository(database.iptvDao(), database, application)
            return IPTVViewModel(application, repository) as T
        }
    }
}

/** Öne Çıkan alanında öne çıkan filme ek olarak gösterilen film sayısı. */
private const val FEATURED_CAROUSEL_SIZE = 7

/** Öne Çıkan slaytları: öne çıkan film başta, yetişkin içerik çıkarılmış, aynı başlık bir kez. */
internal fun buildHeroSlides(
    featured: com.example.data.repository.FeaturedMovie?,
    carousel: List<com.example.data.repository.FeaturedMovie>,
    isAdult: (com.example.data.repository.FeaturedMovie) -> Boolean
): List<com.example.data.repository.FeaturedMovie> =
    (listOfNotNull(featured) + carousel)
        .filterNot(isAdult)
        .distinctBy { it.title.trim().lowercase(java.util.Locale.ROOT) }

/** Bkz. [IPTVViewModel.channelRingFor]. */
internal fun buildChannelRing(
    current: IPTVItem,
    allChannels: List<IPTVItem>,
    isAdult: (IPTVItem) -> Boolean
): List<IPTVItem> {
    val allowAdult = isAdult(current)
    fun allowed(c: IPTVItem) = c.id == current.id || allowAdult || !isAdult(c)
    val sameFolder = if (current.category.isNotBlank()) {
        allChannels.filter { it.category.equals(current.category, ignoreCase = true) && allowed(it) }
    } else {
        emptyList()
    }
    val ring = if (sameFolder.any { it.id != current.id }) sameFolder else allChannels.filter { allowed(it) }
    return if (ring.any { it.id == current.id }) ring else listOf(current) + ring
}
