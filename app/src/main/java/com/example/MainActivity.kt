package com.example
import androidx.compose.ui.res.stringResource

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import com.example.ui.theme.LocalSharedTransitionScope
import com.example.ui.theme.LocalAnimatedVisibilityScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.IntroSplashScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.MultiScreen
import com.example.ui.screens.MultiScreenViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.worker.NotificationWorker
import java.util.concurrent.TimeUnit

import com.example.ui.components.ErrorNotificationBanner
import com.example.util.AppError
import com.example.util.ErrorHandlingManager
import androidx.compose.ui.Alignment

sealed class ActiveScreen {
    object Dashboard : ActiveScreen()
    data class Player(val item: IPTVItem) : ActiveScreen()
    object MultiScreen : ActiveScreen()
    object MovieFinderChat : ActiveScreen()

    companion object {
        /**
         * Sistem uygulamayı arka planda kapatıp geri açtığında (süreç ölümü) kullanıcı açık olduğu
         * ekrana döner. IPTVItem Serializable olduğu için Bundle'a doğrudan yazılabilir.
         */
        val Saver: androidx.compose.runtime.saveable.Saver<ActiveScreen, Any> =
            androidx.compose.runtime.saveable.Saver(
                save = { screen ->
                    when (screen) {
                        is Dashboard -> arrayListOf<java.io.Serializable>("dashboard")
                        is Player -> arrayListOf("player", screen.item)
                        is MultiScreen -> arrayListOf<java.io.Serializable>("multi")
                        is MovieFinderChat -> arrayListOf<java.io.Serializable>("chat")
                    }
                },
                restore = { saved ->
                    val list = saved as? List<*>
                    when (list?.getOrNull(0)) {
                        "player" -> (list.getOrNull(1) as? IPTVItem)?.let { Player(it) } ?: Dashboard
                        "multi" -> MultiScreen
                        "chat" -> MovieFinderChat
                        else -> Dashboard
                    }
                }
            )
    }
}

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.util.LocaleHelper.wrap(newBase))
    }

    private val viewModel: IPTVViewModel by viewModels {
        IPTVViewModel.Factory(application)
    }

    private val multiScreenViewModel: MultiScreenViewModel by viewModels()

    // Uygulama arka plana gidip geri geldiğinde Öne Çıkan içerikler yenilenir (ilk açılışta zaten yeni seçilir).
    private var wentToBackground = false

    override fun onStop() {
        super.onStop()
        if (redirectedToCrashReport) return
        if (!isChangingConfigurations && !isInPictureInPictureMode) wentToBackground = true
        // Gezinirken biriken detay bilgilerini (özet, oyuncular) kullanıcı ekranda değilken tek seferde yaz.
        if (!isChangingConfigurations) viewModel.flushPendingMetadata()
    }

    override fun onStart() {
        super.onStart()
        if (redirectedToCrashReport) return
        if (wentToBackground) {
            wentToBackground = false
            viewModel.onAppReturnedToForeground()
        }
    }

    // Önceki çalıştırma çöktüyse önce hata raporu gösterilir; bu durumda bu ekran hiçbir şey yüklemez.
    private var redirectedToCrashReport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val lang = com.example.util.LocaleHelper.getSavedLanguage(this)
        com.example.util.LocaleHelper.updateResources(this, lang)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && com.example.util.CrashRecoveryManager.pendingCrashReport(this) != null) {
            redirectedToCrashReport = true
            startActivity(android.content.Intent(this, CrashReportActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()

        // Check and request notification permissions on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Cancel existing/reset and reschedule 24-hour retention reminder
        scheduleRetentionReminder()

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val screenOrientation by viewModel.screenOrientation.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()

            val localizedContext = remember(appLanguage) {
                com.example.util.LocaleHelper.updateResources(this@MainActivity, appLanguage)
            }

            LaunchedEffect(screenOrientation) {
                val targetOrientation = when (screenOrientation) {
                    "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    "SENSOR" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    "AUTO" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
                if (requestedOrientation != targetOrientation) {
                    requestedOrientation = targetOrientation
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides localizedContext,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                key(appLanguage) {
                    MyApplicationTheme(appTheme = appTheme) {
                        val isStateReady by viewModel.isStateReady.collectAsState()
                        val userEmail by viewModel.userEmail.collectAsState()
                        val selectedItem by viewModel.selectedItem.collectAsState()
                        val isSetupComplete by viewModel.isSetupComplete.collectAsState()
                        val viewMode by viewModel.viewMode.collectAsState()
                        val seriesList by viewModel.allSeries.collectAsState()
                        var showIntroSplash by rememberSaveable { mutableStateOf(true) }
                        var currentScreen by rememberSaveable(stateSaver = ActiveScreen.Saver) { mutableStateOf<ActiveScreen>(ActiveScreen.Dashboard) }
                        val tvAppState = com.example.ui.tv.rememberTvAppState()
                        val tvPlayerUi = remember { com.example.ui.tv.TvPlayerUiState() }
                        val isTvMode = viewMode == com.example.data.repository.ViewMode.TV
                        SideEffect { com.example.ui.tv.TvUiMode.active = isTvMode }
                        // Oynatıcıdan çıkınca TV kanal paneli bir sonraki girişte kapalı başlar.
                        LaunchedEffect(currentScreen is ActiveScreen.Player) {
                            if (currentScreen !is ActiveScreen.Player) {
                                tvPlayerUi.panelOpen = false
                                tvPlayerUi.categoriesOpen = false
                                tvPlayerUi.panelCategory = null
                            }
                        }
                        var lastNavigationTimeMs by remember { mutableLongStateOf(0L) }
                        var activeError by remember { mutableStateOf<AppError?>(null) }

                        val navigateTo: (ActiveScreen) -> Unit = remember {
                            { targetScreen ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastNavigationTimeMs >= 300L) {
                                    lastNavigationTimeMs = currentTime
                                    currentScreen = targetScreen
                                }
                            }
                        }

                        // Detaydan oynatma (telefon ve TV aynı): kütüphanedeki kaydı bulur, yoksa uyarır.
                        val playFromDetail: (IPTVItem) -> Unit = { item ->
                            viewModel.selectItem(null)
                            // Kendi yayın adresi olan öğe (ör. Xtream dizi bölümü) doğrudan oynatılır. Önceden adla
                            // eşleştirme yapılıyordu; bölüm adı yalnızca dizi adı olduğundan ("4400") hep ilk bölüm
                            // (S1 B1) açılıyordu. Adres yoksa (katalog/öneri öğesi) kütüphanede aranır.
                            val finalItem = if (item.streamUrl.isNotBlank()) item else viewModel.findMatchedItem(item)
                            if (finalItem.streamUrl.isNotEmpty()) {
                                navigateTo(ActiveScreen.Player(finalItem))
                            } else {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    com.example.util.LocaleHelper.getString(this@MainActivity, R.string.toast_not_in_library, finalItem.cleanedName),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        LaunchedEffect(Unit) {
                            ErrorHandlingManager.errorEvents.collect { error ->
                                activeError = error
                            }
                        }

                        // TV: detay paneli kapanınca odak paneli açan karta geri dönsün.
                        val isTvDevice = remember { com.example.ui.tv.TvDevice.isTv(this@MainActivity) }
                        val detailOpen = selectedItem != null
                        var focusBeforeDetail by remember { mutableStateOf<java.lang.ref.WeakReference<androidx.compose.ui.focus.FocusRequester>?>(null) }
                        LaunchedEffect(detailOpen) {
                            if (!isTvDevice) return@LaunchedEffect
                            if (detailOpen) {
                                focusBeforeDetail = com.example.ui.tv.TvFocusMemory.snapshot()
                            } else if (focusBeforeDetail != null) {
                                kotlinx.coroutines.delay(350) // panelin kapanma animasyonu
                                com.example.ui.tv.TvFocusMemory.restore(focusBeforeDetail)
                                focusBeforeDetail = null
                            }
                        }

                        // System Back Button Handling to avoid accidental exits and improve UX
                        if (selectedItem != null) {
                            androidx.activity.compose.BackHandler {
                                viewModel.selectItem(null)
                            }
                        } else if (currentScreen != ActiveScreen.Dashboard) {
                            androidx.activity.compose.BackHandler {
                                navigateTo(ActiveScreen.Dashboard)
                            }
                        }

                        LaunchedEffect(Unit) {
                            viewModel.triggerInAppReviewEvent.collect {
                                com.example.util.InAppReviewManager.requestInAppReview(this@MainActivity)
                            }
                        }

                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("main_scaffold"),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0)
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                
                                if (showIntroSplash) {
                                    IntroSplashScreen(
                                        appName = "CineStream",
                                        tagline = androidx.compose.ui.res.stringResource(R.string.intro_tagline),
                                        onIntroFinished = {
                                            showIntroSplash = false
                                        }
                                    )
                                } else if (!isStateReady || viewMode == com.example.data.repository.ViewMode.LOADING) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(com.example.ui.theme.CineBlack),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = com.example.ui.theme.CineOrange,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                } else if (viewMode == com.example.data.repository.ViewMode.UNSET) {
                                    // İlk açılış: görünüm modu (Telefon / TV) seçimi; sonraki açılışlarda sorulmaz.
                                    com.example.ui.tv.ModeSelectionScreen(onSelect = { mode -> viewModel.setViewMode(mode) })
                                } else if (userEmail == null || !isSetupComplete) {
                                    val savedApiKey by viewModel.geminiApiKey.collectAsState()
                                    LoginScreen(
                                        savedApiKey = savedApiKey,
                                        viewModel = viewModel,
                                        onSaveApiKey = { key -> viewModel.setGeminiApiKey(key) },
                                        onLoginSuccess = { email, name ->
                                            viewModel.loginWithGoogle(email, name)
                                            viewModel.completeSetup()
                                        }
                                    )
                                } else {
                                    // Main Application Flow
                                    AnimatedContent(
                                        targetState = currentScreen,
                                        transitionSpec = {
                                            if (isTvMode && initialState is ActiveScreen.Player && targetState is ActiveScreen.Player) {
                                                // TV'de kanal geçişi anında: iki oynatıcı (iki kod çözücü) aynı anda açık kalmaz.
                                                EnterTransition.None.togetherWith(ExitTransition.None)
                                            } else {
                                                fadeIn(animationSpec = tween(durationMillis = 400)).togetherWith(
                                                    fadeOut(animationSpec = tween(durationMillis = 400))
                                                )
                                            }
                                        },
                                        label = "screen_transition"
                                    ) { screen ->
                                        when (screen) {
                                            is ActiveScreen.Dashboard -> if (viewMode == com.example.data.repository.ViewMode.TV) {
                                                com.example.ui.tv.TvApp(
                                                    viewModel = viewModel,
                                                    state = tvAppState,
                                                    onPlayItem = { item ->
                                                        if (item.type == "LIVE") tvPlayerUi.launch = com.example.ui.tv.TvPlayerUiState.LAUNCH_ENTER
                                                        navigateTo(ActiveScreen.Player(item))
                                                    },
                                                    onPlayContinue = { cw ->
                                                        if (cw.itemType == "LIVE") tvPlayerUi.launch = com.example.ui.tv.TvPlayerUiState.LAUNCH_ENTER
                                                        navigateTo(ActiveScreen.Player(continueWatchingItem(cw)))
                                                    },
                                                    onOpenAssistant = { navigateTo(ActiveScreen.MovieFinderChat) }
                                                )
                                            } else {
                                                HomeScreen(
                                                    // Yatay kullanımda yandaki gezinme çubuğu ve kamera çentiği içeriği örtmesin.
                                                    modifier = Modifier.windowInsetsPadding(
                                                        WindowInsets.displayCutout
                                                            .union(WindowInsets.navigationBars)
                                                            .only(WindowInsetsSides.Horizontal)
                                                    ),
                                                    viewModel = viewModel,
                                                    onNavigateToChat = {
                                                        navigateTo(ActiveScreen.MovieFinderChat)
                                                    },
                                                    onPlayItem = { item ->
                                                        navigateTo(ActiveScreen.Player(item))
                                                    },
                                                    onPlayContinueWatching = { cw ->
                                                        navigateTo(ActiveScreen.Player(continueWatchingItem(cw)))
                                                    },
                                                    onNavigateToMultiScreen = {
                                                        navigateTo(ActiveScreen.MultiScreen)
                                                    },
                                                )
                                            }

                                            is ActiveScreen.Player -> {
                                                val liveChannels by viewModel.liveChannels.collectAsState()
                                                val movies by viewModel.movies.collectAsState()
                                                val series by viewModel.series.collectAsState()
                                                val liveFetchedShow by viewModel.liveFetchedShow.collectAsState()
                                                val continueWatchingList by viewModel.continueWatching.collectAsState()
                                                val activeItem = screen.item

                                                // Büyük listelerde (40.000+ kanal/bölüm) ana iş parçacığını kilitlememek için arka planda hesaplanır.
                                                val siblingWorkSize = when (activeItem.type) {
                                                    "LIVE" -> liveChannels.size
                                                    "MOVIE" -> movies.size
                                                    "SERIES" -> series.size
                                                    else -> 0
                                                }
                                                val siblingList = com.example.ui.components.rememberComputedOffMain(
                                                    activeItem, liveChannels, movies, series, liveFetchedShow,
                                                    workSize = siblingWorkSize,
                                                    fallback = emptyList<IPTVItem>()
                                                ) {
                                                        when (activeItem.type) {
                                                            // İzlenen kanalın klasöründeki kanallar; yetişkin kanallar karışmaz.
                                                            "LIVE" -> viewModel.channelRingFor(activeItem, liveChannels).filter { it.id != activeItem.id }
                                                            "MOVIE" -> {
                                                                val allowAdult = viewModel.isAdultContent(activeItem)
                                                                movies.filter { it.id != activeItem.id && (allowAdult || !viewModel.isAdultContent(it)) }
                                                            }
                                                            "SERIES" -> {
                                                                val liveEpisodeItems = liveFetchedShow?.seasons
                                                                    ?.sortedBy { it.seasonNumber }
                                                                    ?.flatMap { s -> s.episodes.sortedBy { it.episodeNumber } }
                                                                    ?.map { it.item }
                                                                val matchesActiveShow = liveEpisodeItems?.any { it.id == activeItem.id } == true

                                                                if (matchesActiveShow) {
                                                                    liveEpisodeItems.orEmpty()
                                                                } else {
                                                                    val parsedInfo = com.example.data.model.SeriesParser.parseEpisodeInfo(activeItem.cleanedName)
                                                                        ?: com.example.data.model.SeriesParser.parseEpisodeInfo(activeItem.name)
                                                                    val activeShowTitle = parsedInfo?.showTitle?.lowercase()?.trim() ?: ""
                                                                    if (activeShowTitle.isNotEmpty()) {
                                                                        series
                                                                            .mapNotNull { candidate ->
                                                                                val info = com.example.data.model.SeriesParser.parseEpisodeInfo(candidate.cleanedName)
                                                                                    ?: com.example.data.model.SeriesParser.parseEpisodeInfo(candidate.name)
                                                                                if (info?.showTitle?.lowercase()?.trim() == activeShowTitle) candidate to info else null
                                                                            }
                                                                            .sortedWith(compareBy({ it.second.season }, { it.second.episode }))
                                                                            .map { it.first }
                                                                    } else {
                                                                        series
                                                                    }
                                                                }
                                                            }
                                                            else -> emptyList()
                                                        }
                                                }

                                                val initialProgress = remember(activeItem, continueWatchingList) {
                                                    // TV modunda canlı yayında "kaldığın yerden devam" sorulmaz.
                                                    if (isTvMode && activeItem.type == "LIVE") 0L
                                                    else continueWatchingList.find { it.itemId == activeItem.id }?.progressSeconds ?: 0L
                                                }

                                                PlayerScreen(
                                                    item = activeItem,
                                                    siblingItems = siblingList,
                                                    onBack = { navigateTo(ActiveScreen.Dashboard) },
                                                    onPlayItem = { newItem -> navigateTo(ActiveScreen.Player(newItem)) },
                                                    onProgressUpdate = { item, progress, total ->
                                                        viewModel.saveProgress(item, progress, total)
                                                    },
                                                    initialProgressSeconds = initialProgress,
                                                    iptvViewModel = viewModel,
                                                    tvMode = isTvMode,
                                                    tvUiState = tvPlayerUi
                                                )
                                            }

                                            is ActiveScreen.MultiScreen -> {
                                                MultiScreen(
                                                    iptvViewModel = viewModel,
                                                    multiScreenViewModel = multiScreenViewModel,
                                                    onBack = {
                                                        navigateTo(ActiveScreen.Dashboard)
                                                    }
                                                )
                                            }

                                            is ActiveScreen.MovieFinderChat -> if (viewMode == com.example.data.repository.ViewMode.TV) {
                                                com.example.ui.tv.TvAssistantScreen(
                                                    viewModel = viewModel,
                                                    onOpenItem = { item ->
                                                        viewModel.selectItem(item)
                                                        navigateTo(ActiveScreen.Dashboard)
                                                    },
                                                    onOpenSettings = {
                                                        tvAppState.destination = com.example.ui.tv.TvSection.SETTINGS
                                                        navigateTo(ActiveScreen.Dashboard)
                                                    }
                                                )
                                            } else {
                                                com.example.ui.screens.MovieFinderChatScreen(
                                                    viewModel = viewModel,
                                                    onBack = { navigateTo(ActiveScreen.Dashboard) },
                                                    onOpenItem = { item ->
                                                        viewModel.selectItem(item)
                                                        navigateTo(ActiveScreen.Dashboard)
                                                    }
                                                )
                                            }


                                        }
                                    }

                                    // 2. Details Bottom Dialog Overlay (Netflix-Apple hybrid panel)
                                    AnimatedVisibility(
                                        visible = selectedItem != null,
                                        modifier = com.example.ui.tv.tvModalFocus(selectedItem?.id),
                                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                    ) {
                                        key(selectedItem?.id ?: -1) {
                                            val detailItem = selectedItem
                                            if (viewMode == com.example.data.repository.ViewMode.TV && detailItem != null) {
                                                com.example.ui.tv.TvDetailScreen(
                                                    viewModel = viewModel,
                                                    item = detailItem,
                                                    onPlay = { item -> playFromDetail(item) }
                                                )
                                            } else DetailScreen(
                                                item = selectedItem,
                                                seriesList = seriesList,
                                                onDismiss = { viewModel.selectItem(null) },
                                                onPlay = { item -> playFromDetail(item) },
                                                onToggleFavorite = { item ->
                                                    viewModel.toggleFavorite(item)
                                                },
                                                viewModel = viewModel
                                            )
                                        }
                                    }
                                }
                                val showPaywallDialog by viewModel.showPaywallDialog.collectAsState()
                                val paywallReasonMessage by viewModel.paywallReasonMessage.collectAsState()

                                if (showPaywallDialog) {
                                    com.example.ui.components.ProPaywallDialog(
                                        reasonMessage = paywallReasonMessage,
                                        onDismiss = { viewModel.closePaywall() },
                                        onPurchaseSuccess = { viewModel.setProUser(true) }
                                    )
                                }

                                ErrorNotificationBanner(
                                    error = activeError,
                                    onDismiss = { activeError = null },
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleRetentionReminder() {
        try {
            val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(24, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "cinestream_reminder",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule retention reminder", e)
        }
    }
}

/** İzlemeye Devam Et kaydından oynatıcıya verilecek öğe (telefon ve TV ana sayfası aynı şekilde kullanır). */
internal fun continueWatchingItem(cw: com.example.data.model.ContinueWatching): IPTVItem = IPTVItem(
    id = cw.itemId,
    playlistId = 1,
    name = cw.itemName,
    cleanedName = cw.itemName,
    logoUrl = cw.itemLogo,
    streamUrl = cw.streamUrl,
    category = cw.category,
    type = cw.itemType
)
