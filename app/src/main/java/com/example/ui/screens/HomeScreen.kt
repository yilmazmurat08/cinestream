package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import com.example.ui.components.VerticalEPGComponent
import com.example.ui.components.SafeAsyncImage
import com.example.ui.components.extractYouTubeVideoId
import com.example.player.RadioPlayerManager
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.example.R
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.model.toBrowsableItem
import com.example.ui.IPTVViewModel
import com.example.ui.components.PlaylistAddDialog
import com.example.ui.components.CategoryAdaptiveIcon
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateDpAsState

private data class NextEpisodeInfo(
    val showTitle: String,
    val currentCw: ContinueWatching,
    val nextEpisodeItem: IPTVItem,
    val nextSeason: Int,
    val nextEpisode: Int,
    val nextEpisodeName: String
)

private data class BottomNavItem(
    val title: String,
    val filterType: String,
    val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: IPTVViewModel,
    onNavigateToChat: () -> Unit = {},
    onPlayItem: (IPTVItem) -> Unit,
    onPlayContinueWatching: (ContinueWatching) -> Unit,
    onNavigateToMultiScreen: () -> Unit,
    onNavigateToSeriesCalendar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val liveChannels by viewModel.liveChannels.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val series by viewModel.series.collectAsState()
    val radioChannels by viewModel.radioChannels.collectAsState()
    val topRatedSeriesList by viewModel.topRatedSeriesList.collectAsState()
    val isTMDBLoading by viewModel.isTMDBLoading.collectAsState()
    val liveGroups by viewModel.liveGroups.collectAsState()
    val movieGroups by viewModel.movieGroups.collectAsState()
    val seriesGroups by viewModel.seriesGroups.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val nlpSearchResults by viewModel.nlpSearchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val context = LocalContext.current
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val trailerBoxItems by viewModel.trailerBoxItems.collectAsState()
    val recommendedItems by viewModel.recommendedItems.collectAsState()
    val isRecommendationLoading by viewModel.isRecommendationLoading.collectAsState()

    var nextEpisodes by remember { mutableStateOf<List<NextEpisodeInfo>>(emptyList()) }
    LaunchedEffect(continueWatching, series) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val seriesByShowTitle = HashMap<String, MutableList<Pair<IPTVItem, SeriesParser.ParsedEpisodeInfo>>>()
            for (item in series) {
                val parsed = SeriesParser.parseEpisodeInfo(item.name) ?: continue
                val key = parsed.showTitle.lowercase(java.util.Locale.ROOT)
                seriesByShowTitle.getOrPut(key) { mutableListOf() }.add(Pair(item, parsed))
            }

            continueWatching.filter { it.itemType == "SERIES" }.mapNotNull { cw ->
                val parsedCurrent = SeriesParser.parseEpisodeInfo(cw.itemName) ?: return@mapNotNull null
                val showEpisodes = seriesByShowTitle[parsedCurrent.showTitle.lowercase(java.util.Locale.ROOT)] ?: emptyList()
                var next = showEpisodes.firstOrNull { (_, parsed) ->
                    parsed.season == parsedCurrent.season && parsed.episode == parsedCurrent.episode + 1
                }
                if (next == null) {
                    next = showEpisodes.firstOrNull { (_, parsed) ->
                        parsed.season == parsedCurrent.season + 1 && parsed.episode == 1
                    }
                }
                next?.let { (nextItem, nextParsed) ->
                    NextEpisodeInfo(
                        showTitle = parsedCurrent.showTitle,
                        currentCw = cw,
                        nextEpisodeItem = nextItem,
                        nextSeason = nextParsed.season,
                        nextEpisode = nextParsed.episode,
                        nextEpisodeName = nextParsed.episodeName
                    )
                }
            }.distinctBy { it.showTitle }
        }
        nextEpisodes = result
    }

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var isSearchPopupOpen by remember { mutableStateOf(false) }
    var isVoiceOverlayOpen by remember { mutableStateOf(false) }
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val activeFolderGroup by viewModel.activeFolderGroup.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val parentalLockEnabled by viewModel.parentalLock.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Handle back button on sub-tabs (e.g. LIVE, MOVIE, SERIES) to return to main ALL tab when no folder is open
    androidx.activity.compose.BackHandler(enabled = selectedItem == null && activeFolderGroup == null && selectedTypeFilter != "ALL") {
        viewModel.setSelectedTypeFilter("ALL")
    }

    // Reset selected category to "Tümü" when switching bottom navigation tabs
    LaunchedEffect(selectedTypeFilter) {
        viewModel.selectCategory("Tümü")
    }

    // Dynamically filter category chips based on the selected tab (All, Canlı, Film, Dizi, Radyo)
    val currentCategories = remember(categories, selectedTypeFilter) {
        if (selectedTypeFilter == "SETTINGS") return@remember emptyList<String>()
        categories
    }

    val top10Movies = remember(movies) {
        movies.sortedByDescending { it.rating }.take(10)
    }

    val xtreamSeriesCatalogForTop10 by viewModel.xtreamSeriesCatalog.collectAsState()
    val top10Series = remember(xtreamSeriesCatalogForTop10) {
        xtreamSeriesCatalogForTop10.sortedByDescending { it.rating }.take(10)
            .map { it.toBrowsableItem() }
    }

    val top10Live = remember(liveChannels) {
        liveChannels.take(10)
    }

    val currentTheme = LocalAppTheme.current
    val themeBg = MaterialTheme.colorScheme.background
    val contentColor = MaterialTheme.colorScheme.onBackground
    val mutedContentColor = contentColor.copy(alpha = 0.5f)
    val actionButtonBg = if (currentTheme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    // Dynamic background ambient color state based on focus / active carousel (glowing purple/magenta Aurora)
    val ambientColorStart = remember(currentTheme) {
        CineOrange.copy(alpha = currentTheme.ambientBlurAlpha * 1.5f)
    }
    val ambientColorEnd = remember(currentTheme) {
        CineRed.copy(alpha = currentTheme.ambientBlurAlpha * 0.8f)
    }

    val backgroundBrush = if (currentTheme.isDark) {
        CinematicBackgroundGradient
    } else {
        SolidColor(themeBg)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val homeLazyListState = rememberLazyListState()
    val isScrollable = selectedTypeFilter == "ALL" || searchQuery.isNotEmpty()
    val isHeroVisible = searchQuery.isEmpty() && selectedTypeFilter == "ALL"
    val isScrolled by remember { derivedStateOf { homeLazyListState.firstVisibleItemIndex > 0 || homeLazyListState.firstVisibleItemScrollOffset > 30 } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CineStreamTopBar(
                isProUser = isProUser,
                isScrolled = isScrolled,
                onLogoClick = { viewModel.setSelectedTypeFilter("ALL") },
                onProClick = { viewModel.openPaywall() },
                onSearchClick = { isSearchPopupOpen = true },
                onChatClick = onNavigateToChat,
                onCalendarClick = onNavigateToSeriesCalendar
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF141218).copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MidPurpleBg.copy(alpha = 0.85f))
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        AccentNeonPurple.copy(alpha = 0.25f),
                                        AccentNeonPurple.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        val barWidth = maxWidth
                        val itemWidth = barWidth / 6
                        val indicatorWidth = 26.dp

                        val selectedIndex = when (selectedTypeFilter) {
                            "ALL" -> 0
                            "MOVIE" -> 1
                            "SERIES" -> 2
                            "LIVE" -> 3
                            "WATCHLIST" -> 4
                            "SETTINGS" -> 5
                            else -> 0
                        }

                        val indicatorOffset by animateDpAsState(
                            targetValue = (itemWidth * selectedIndex) + (itemWidth - indicatorWidth) / 2,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
                            label = "indicator_offset"
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(bottom = 2.dp)
                                .offset(x = indicatorOffset)
                                .width(indicatorWidth)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            AccentNeonPurple,
                                            AccentNeonPurple.copy(alpha = 0.5f),
                                            AccentNeonPurple
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val navItems = listOf(
                                BottomNavItem(stringResource(R.string.nav_home), "ALL", Icons.Filled.Home, Icons.Outlined.Home),
                                BottomNavItem(stringResource(R.string.nav_movies), "MOVIE", Icons.Filled.Movie, Icons.Outlined.Movie),
                                BottomNavItem(stringResource(R.string.nav_series), "SERIES", Icons.Filled.Tv, Icons.Outlined.Tv),
                                BottomNavItem(stringResource(R.string.nav_live_tv), "LIVE", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
                                BottomNavItem(stringResource(R.string.nav_watchlist), "WATCHLIST", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
                                BottomNavItem(stringResource(R.string.nav_profile), "SETTINGS", Icons.Filled.Person, Icons.Outlined.Person)
                            )

                            navItems.forEachIndexed { index, navItem ->
                                val isSelected = selectedTypeFilter == navItem.filterType
                                val itemColor = if (isSelected) AccentNeonPurple else MutedPurpleText

                                val scale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                    label = "nav_item_scale"
                                )
                                val translationY by animateDpAsState(
                                    targetValue = if (isSelected) (-2).dp else 0.dp,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                    label = "nav_item_translation"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            // Telefonda görünüm aynı (vurgu yok); TV'de kumanda odağı görünür.
                                            indication = com.example.ui.tv.tvFocusIndicationOrNull()
                                        ) {
                                            viewModel.setSelectedTypeFilter(navItem.filterType)
                                        }
                                        .padding(vertical = 4.dp)
                                        .offset(y = translationY)
                                        .scale(scale)
                                        .testTag("bottom_nav_${navItem.filterType}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = if (isSelected) {
                                            Modifier
                                                .background(color = Color(0x22D946EF), shape = RoundedCornerShape(50.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        } else {
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) navItem.activeIcon else navItem.inactiveIcon,
                                            contentDescription = navItem.title,
                                            tint = itemColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = navItem.title,
                                            color = itemColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundBrush)
                .testTag("home_screen")
        ) {
            // 1. Main Content Container
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refreshPlaylists() },
                state = pullState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pull_to_refresh_box"),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isLoading,
                        modifier = Modifier.align(Alignment.TopCenter),
                        color = CineOrange,
                        containerColor = Color(0xFF1E1E24)
                    )
                }
            ) {
                if (selectedTypeFilter == "ALL" || searchQuery.isNotEmpty()) {
                    LazyColumn(
                        state = homeLazyListState,
                        verticalArrangement = Arrangement.Top,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // 1. Top Hero Featured Movie Card (12 Saatte Bir Değişen M3U Özel Film Kartı)
                        if (isHeroVisible) {
                            item(key = "hero_featured") {
                                val featuredMovie by viewModel.featuredMovie.collectAsState()
                                if (featuredMovie != null) {
                                    com.example.ui.components.FeaturedMovieCard(
                                        featuredMovie = featuredMovie,
                                        onPlayClick = { item ->
                                            val matched = viewModel.findMatchedItem(item)
                                            val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                                            if (targetItem.streamUrl.isNotEmpty()) {
                                                onPlayItem(targetItem)
                                            } else {
                                                viewModel.selectItem(targetItem)
                                            }
                                        },
                                        onInfoClick = { item ->
                                            val matched = viewModel.findMatchedItem(item)
                                            val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                                            viewModel.selectItem(targetItem)
                                        },
                                        onRefreshRandom = {
                                            viewModel.refreshFeaturedMovie()
                                        },
                                        favorites = favorites,
                                        onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // 2. ImdbSection (Yıldız ikonu ve metinler)
                        if (selectedTypeFilter == "ALL" && searchQuery.isEmpty() && (selectedCategory == null || selectedCategory == "Tümü")) {
                            item(key = "legends_section") {
                                val legendItems by viewModel.legendItems.collectAsState()

                                if (legendItems.isNotEmpty()) {
                                    LegendsSection(
                                        items = legendItems,
                                        onItemClick = { item -> viewModel.selectItem(item) },
                                        onLongClick = { }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // 3. MovieHorizontalGrid / LazyRow (Top 10 & Film/Dizi Listeleri)
                        if (selectedTypeFilter == "ALL" && searchQuery.isEmpty() && (selectedCategory == null || selectedCategory == "Tümü")) {
                            // Sinemada Bu Hafta: TMDB'den kullanıcının ülkesinde vizyondaki ilk 10 film.
                            // Kütüphanede olan film dokununca doğrudan oynar, olmayan TMDB bilgileriyle detayda açılır.
                            item(key = "now_playing_cinema") {
                                val nowPlaying by viewModel.nowPlaying.collectAsState()
                                val nowPlayingRegion by viewModel.nowPlayingRegion.collectAsState()
                                LaunchedEffect(movies.size) {
                                    viewModel.loadNowPlaying()
                                }
                                if (nowPlaying.isNotEmpty()) {
                                    com.example.ui.components.NowPlayingSection(
                                        entries = nowPlaying,
                                        region = nowPlayingRegion,
                                        onRegionSelected = { code -> viewModel.setNowPlayingRegion(code) },
                                        onPlay = { item -> onPlayItem(item) },
                                        onOpen = { item -> viewModel.selectItem(item) },
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            if (top10Movies.isNotEmpty()) {
                                item(key = "top10_movies") {
                                    com.example.ui.components.RankedTop10Section(
                                        title = "🔥 Bugün Trend Filmler",
                                        displayTitle = stringResource(R.string.home_top10_movies),
                                        items = top10Movies,
                                        onItemClick = { item ->
                                            val matched = viewModel.findMatchedItem(item)
                                            val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                                            if (targetItem.streamUrl.isNotEmpty()) {
                                                onPlayItem(targetItem)
                                            } else {
                                                viewModel.selectItem(targetItem)
                                            }
                                        },
                                        onItemLongClick = { },
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }


                            if (top10Series.isNotEmpty()) {
                                item(key = "top10_series") {
                                    com.example.ui.components.RankedTop10Section(
                                        title = "📺 Popüler Diziler",
                                        displayTitle = stringResource(R.string.home_top10_series),
                                        items = top10Series,
                                        onItemClick = { item ->
                                            val matched = viewModel.findMatchedItem(item)
                                            val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                                            viewModel.selectItem(targetItem)
                                        },
                                        onItemLongClick = { },
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            if (top10Live.isNotEmpty()) {
                                item(key = "top10_live") {
                                    com.example.ui.components.RankedTop10Section(
                                        title = "🔴 Popüler Kanallar",
                                        displayTitle = stringResource(R.string.home_top10_channels),
                                        items = top10Live,
                                        onItemClick = { item ->
                                            val matched = viewModel.findMatchedItem(item)
                                            val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                                            if (targetItem.streamUrl.isNotEmpty()) {
                                                onPlayItem(targetItem)
                                            } else {
                                                viewModel.selectItem(targetItem)
                                            }
                                        },
                                        onItemLongClick = { },
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        if (searchQuery.isEmpty() && searchHistory.isNotEmpty()) {
                            item(key = "search_history_idle") {
                                com.example.ui.components.SearchHistorySection(
                                    historyList = searchHistory,
                                    onQueryClick = { query ->
                                        viewModel.updateSearchQuery(query)
                                        viewModel.saveSearchQuery(query)
                                    },
                                    onDeleteQuery = { query ->
                                        viewModel.deleteSearchQuery(query)
                                    },
                                    onClearAll = {
                                        viewModel.clearSearchHistory()
                                    },
                                    isDarkTheme = currentTheme.isDark
                                )
                            }
                        }

                        // Search Results or Standard Layout
                        if (searchQuery.isNotEmpty()) {
                            val liveResults = searchResults.filter { it.type == "LIVE" }
                            val movieResults = searchResults.filter { it.type == "MOVIE" }
                            val seriesResults = searchResults.filter { it.type == "SERIES" }
                            val radioResults = searchResults.filter { it.type == "RADIO" }

                            if (searchHistory.isNotEmpty()) {
                                item(key = "search_history_active") {
                                    com.example.ui.components.SearchHistorySection(
                                        historyList = searchHistory,
                                        onQueryClick = { query ->
                                            viewModel.updateSearchQuery(query)
                                            viewModel.saveSearchQuery(query)
                                        },
                                        onDeleteQuery = { query ->
                                            viewModel.deleteSearchQuery(query)
                                        },
                                        onClearAll = {
                                            viewModel.clearSearchHistory()
                                        },
                                        isDarkTheme = currentTheme.isDark
                                    )
                                }
                            }

                            item(key = "search_results_title") {
                                val isShowingAIResults = nlpSearchResults.isNotEmpty()
                                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isShowingAIResults) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = CineOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (isShowingAIResults) stringResource(R.string.home_ai_results, searchQuery) else stringResource(R.string.home_search_results, searchQuery),
                                            color = contentColor,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (isShowingAIResults) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.home_ai_results_hint),
                                            color = contentColor.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            if (searchResults.isEmpty()) {
                                item(key = "search_results_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.home_no_search_results),
                                            color = mutedContentColor,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            } else {
                                // Show Live channels if any
                                if (liveResults.isNotEmpty()) {
                                    item(key = "search_live_results") {
                                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                CategoryAdaptiveIcon(
                                                    category = "Canlı Kanallar",
                                                    type = "LIVE",
                                                    size = 20.dp,
                                                    containerColor = NeonPink.copy(alpha = 0.15f),
                                                    tint = NeonPink,
                                                    modifier = Modifier.padding(end = 10.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.home_live_channels_header),
                                                    color = CineOrange,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                itemsIndexed(liveResults.filterNotNull(), key = { index, item -> "${item.id}_$index" }) { _, item ->
                                                    PosterBottomTextCard(
                                                        item = item,
                                                        onClick = {
                                                            val matched = viewModel.findMatchedItem(item)
                                                            val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                                                            onPlayItem(targetItem)
                                                        },
                                                        onLongClick = { }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Show Movies if any
                                if (movieResults.isNotEmpty()) {
                                    item(key = "search_movie_results") {
                                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                CategoryAdaptiveIcon(
                                                    category = "Filmler",
                                                    type = "MOVIE",
                                                    size = 20.dp,
                                                    containerColor = CineOrange.copy(alpha = 0.15f),
                                                    tint = CineOrange,
                                                    modifier = Modifier.padding(end = 10.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.nav_movies),
                                                    color = CineOrange,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                itemsIndexed(movieResults.filterNotNull(), key = { index, item -> "${item.id}_$index" }) { _, item ->
                                                    PosterBottomTextCard(
                                                        item = item,
                                                        onClick = { viewModel.selectItem(item) },
                                                        onLongClick = { }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Show Series if any
                                if (seriesResults.isNotEmpty()) {
                                    item(key = "search_series_results") {
                                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                CategoryAdaptiveIcon(
                                                    category = "Diziler",
                                                    type = "SERIES",
                                                    size = 20.dp,
                                                    containerColor = ElectricBlue.copy(alpha = 0.15f),
                                                    tint = ElectricBlue,
                                                    modifier = Modifier.padding(end = 10.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.nav_series),
                                                    color = CineOrange,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                itemsIndexed(seriesResults.filterNotNull(), key = { index, item -> "${item.id}_$index" }) { _, item ->
                                                    PosterBottomTextCard(
                                                        item = item,
                                                        onClick = { viewModel.selectItem(item) },
                                                        onLongClick = { }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Show Radios if any
                                if (radioResults.isNotEmpty()) {
                                    item(key = "search_radio_results") {
                                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                CategoryAdaptiveIcon(
                                                    category = "Radyolar",
                                                    type = "RADIO",
                                                    size = 20.dp,
                                                    containerColor = Color(0xFF10B981).copy(alpha = 0.15f),
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.padding(end = 10.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.radio),
                                                    color = CineOrange,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                itemsIndexed(radioResults.filterNotNull(), key = { index, item -> "${item.id}_$index" }) { _, item ->
                                                    RadioGlassCard(
                                                        item = item,
                                                        onClick = { RadioPlayerManager.toggleRadio(context, item) },
                                                        onLongClick = { }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (selectedTypeFilter == "ALL") {
                                // 1. AI Sinema Bülteni Widget
                                if (selectedCategory == null || selectedCategory == "Tümü") {
                                    item(key = "ai_bulletin_card") {
                                        val aiBulletinText by viewModel.aiBulletinText.collectAsState()
                                        if (aiBulletinText.isNotEmpty() && !aiBulletinText.contains("bulunmuyor")) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (currentTheme.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = CineOrange,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = stringResource(R.string.home_ai_bulletin),
                                                            color = CineOrange,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.5.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = aiBulletinText,
                                                        color = contentColor.copy(alpha = 0.85f),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Continue Watching (Kaldığın Yerden Devam Et)
                                if (continueWatching.isNotEmpty() && (selectedCategory == null || selectedCategory == "Tümü")) {
                                    item(key = "continue_watching_section") {
                                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                            Text(
                                                text = stringResource(id = R.string.continue_watching),
                                                color = contentColor,
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                                            )
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                itemsIndexed(continueWatching.filterNotNull(), key = { index, cw -> "${cw.itemId}_$index" }) { _, cw ->
                                                    ContinueWatchingCard(
                                                        cw = cw,
                                                        onClick = { onPlayContinueWatching(cw) },
                                                        onDismiss = { viewModel.deleteProgress(cw.itemId) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2b. İzlemeye Devam Et: Sıradaki Bölüm (Next Episode Tracker) Widget
                                if (nextEpisodes.isNotEmpty() && (selectedCategory == null || selectedCategory == "Tümü")) {
                                    item(key = "next_episodes_section") {
                                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SkipNext,
                                                    contentDescription = null,
                                                    tint = CineOrange,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.home_next_episode),
                                                    color = contentColor,
                                                    fontSize = 19.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                itemsIndexed(nextEpisodes.filterNotNull(), key = { index, info -> "${info.nextEpisodeItem.id}_$index" }) { _, info ->
                                                    NextEpisodeCard(
                                                        showTitle = info.showTitle,
                                                        nextItem = info.nextEpisodeItem,
                                                        nextSeason = info.nextSeason,
                                                        nextEpisode = info.nextEpisode,
                                                        nextEpisodeName = info.nextEpisodeName,
                                                        onPlay = { item -> onPlayItem(item) },
                                                        onDismiss = { viewModel.deleteProgress(info.currentCw.itemId) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2c. Dizi Takvimi tanıtım kartı kaldırıldı (özellik geri çekildi)

                                // 3. Fragman Kutusu (Trailer Box)
                                if ((selectedCategory == null || selectedCategory == "Tümü") && trailerBoxItems.isNotEmpty()) {
                                    item(key = "trailer_box") {
                                        TrailerBoxSection(
                                            items = trailerBoxItems,
                                            onTrailerClick = { trailerUrl ->
                                                val cleanUrl = com.example.ui.components.formatYouTubeWatchUrl(trailerUrl)
                                                if (!cleanUrl.isNullOrEmpty()) {
                                                    com.example.ui.components.openYoutubeTrailerExternally(context, cleanUrl)
                                                }
                                            }
                                        )
                                    }
                                }

                                // 4. Bölüm: 📻 Radyo (M3U Radyo Yayınları)
                                if (radioChannels.isNotEmpty() && (selectedCategory == null || selectedCategory == "Tümü")) {
                                    item(key = "radio_glass_row") {
                                        GlassmorphismRadioRow(
                                            radios = radioChannels,
                                            onSelect = { item -> viewModel.selectItem(item) },
                                            onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                                            isLoading = false
                                        )
                                    }
                                }

                                // 6. Watchlist (İzleme Listem)
                                if (favorites.isNotEmpty() && (selectedCategory == null || selectedCategory == "Tümü")) {
                                    item(key = "watchlist_section") {
                                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    tint = CineOrange,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.watchlist),
                                                    color = contentColor,
                                                    fontSize = 19.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                TextButton(
                                                    onClick = { viewModel.setSelectedTypeFilter("WATCHLIST") },
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.testTag("see_all_watchlist_button")
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.home_see_all_count, favorites.size),
                                                        color = CineOrange,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                itemsIndexed(favorites.filterNotNull(), key = { index, item -> "${item.id}_$index" }) { _, item ->
                                                    MediaCard(
                                                        item = item,
                                                        onClick = { viewModel.selectItem(item) },
                                                        onLongClick = { }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 7. AI Asistanı ve Tür Bazlı Öneriler
                                if (selectedCategory == null || selectedCategory == "Tümü") {
                                    item(key = "mood_recs_section") {
                                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                            Text(
                                                text = stringResource(R.string.home_ai_assistant),
                                                color = contentColor,
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                                            )
                                            
                                            val context = LocalContext.current

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                                                    .clickable { onNavigateToChat() }
                                                    .testTag("mood_randomizer_card"),
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MidPurpleBg
                                                ),
                                                border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.15f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .background(
                                                                Brush.radialGradient(
                                                                    colors = listOf(CineOrange.copy(alpha = 0.25f), Color.Transparent)
                                                                ),
                                                                shape = CircleShape
                                                            )
                                                            .border(1.dp, CineOrange.copy(alpha = 0.4f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = stringResource(R.string.home_ai_assistant_desc),
                                                            tint = CineOrange,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(16.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = stringResource(R.string.home_ai_card_title),
                                                            color = Color.White,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = stringResource(R.string.home_ai_card_body),
                                                            color = MutedText,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Normal,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                val genresList = listOf(
                                                    Pair("Korku", Icons.Default.Bedtime),
                                                    Pair("Komedi", Icons.Default.Mood),
                                                    Pair("Dram", Icons.Default.TheaterComedy),
                                                    Pair("Tarih & Savaş", Icons.Default.Shield),
                                                    Pair("Aksiyon & Macera", Icons.Default.Bolt)
                                                )
                                                itemsIndexed(genresList, key = { index, item -> "${item.first}_$index" }) { _, (genreName, icon) ->
                                                    val interactionSource = remember { MutableInteractionSource() }
                                                    val isHovered by interactionSource.collectIsHoveredAsState()
                                                    val scale by animateFloatAsState(if (isHovered) 1.05f else 1f)
                                                    
                                                    Surface(
                                                        onClick = {
                                                            val randomItem = viewModel.getRandomItemByGenre(genreName)
                                                            if (randomItem != null) {
                                                                viewModel.selectItem(randomItem)
                                                            } else {
                                                                android.widget.Toast.makeText(context, context.getString(R.string.home_no_genre_content), android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        color = MidPurpleBg.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(50),
                                                        border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.25f)),
                                                        modifier = Modifier
                                                            .scale(scale)
                                                            .hoverable(interactionSource)
                                                            .testTag("genre_btn_${genreName.lowercase(java.util.Locale.ROOT).replace(" ", "_").replace("&", "and")}")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = genreName,
                                                                tint = CineOrange,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = when (genreName) {
                                                                    "Korku" -> stringResource(R.string.home_genre_horror)
                                                                    "Komedi" -> stringResource(R.string.home_genre_comedy)
                                                                    "Dram" -> stringResource(R.string.home_genre_drama)
                                                                    "Tarih & Savaş" -> stringResource(R.string.home_genre_history_war)
                                                                    "Aksiyon & Macera" -> stringResource(R.string.home_genre_action_adventure)
                                                                    else -> genreName
                                                                },
                                                                color = Color.White,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Günün Düellosu: Ne İzlesem? Widget'ı
                                if (selectedCategory == null || selectedCategory == "Tümü") {
                                    item(key = "movie_duel_widget") {
                                        val candidates by viewModel.movieDuelCandidates.collectAsState()

                                        var currentDuelPair by remember(candidates) {
                                            mutableStateOf(
                                                if (candidates.size >= 2) {
                                                    val shuffled = candidates.shuffled()
                                                    Pair(shuffled[0], shuffled[1])
                                                } else null
                                            )
                                        }

                                        currentDuelPair?.let { (item1, item2) ->
                                            MovieDuelWidget(
                                                item1 = item1,
                                                item2 = item2,
                                                onPlay = { onPlayItem(it) },
                                                onDetail = { viewModel.selectItem(it) },
                                                onRefreshDuel = {
                                                    if (candidates.size >= 2) {
                                                        val shuffled = candidates.shuffled()
                                                        currentDuelPair = Pair(shuffled[0], shuffled[1])
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // Empty state check for selected category
                                val hasContent = liveChannels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty() || radioChannels.isNotEmpty()
                                if (!hasContent && selectedCategory != null && selectedCategory != "Tümü") {
                                    item(key = "category_empty_state") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp, horizontal = 24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = CineOrange.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = stringResource(R.string.home_empty_category),
                                                    color = mutedContentColor,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTypeFilter == "LIVE") {
                    FolderGridScreen(
                        groups = liveGroups,
                        title = "Canlı Yayın",
                        displayTitle = stringResource(R.string.home_folder_live),
                        viewModel = viewModel,
                        onPlayItem = onPlayItem,
                        continueWatching = continueWatching.filter { it.itemType == "LIVE" },
                        onPlayContinueWatching = onPlayContinueWatching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                } else if (selectedTypeFilter == "EPG") {
                    val realEpgPrograms by viewModel.realEpgPrograms.collectAsState()
                    VerticalEPGComponent(
                        liveChannels = liveChannels,
                        onPlayChannel = onPlayItem,
                        onSelectChannelDetails = { viewModel.selectItem(it) },
                        realEpgPrograms = realEpgPrograms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            onClick = { viewModel.setSelectedTypeFilter("LIVE") },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.home_back_to_folders),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (selectedTypeFilter == "MOVIE") {
                    FolderGridScreen(
                        groups = movieGroups,
                        title = "Sinema Klasörleri",
                        displayTitle = stringResource(R.string.home_folder_movies),
                        viewModel = viewModel,
                        onPlayItem = onPlayItem,
                        continueWatching = continueWatching.filter { it.itemType == "MOVIE" },
                        onPlayContinueWatching = onPlayContinueWatching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                } else if (selectedTypeFilter == "SERIES") {
                    val xtreamCatalog by viewModel.xtreamSeriesCatalog.collectAsState()
                    if (xtreamCatalog.isNotEmpty()) {
                        var selectedSeriesCategory by remember { mutableStateOf<String?>(null) }
                        val grouped = remember(xtreamCatalog) {
                            xtreamCatalog.groupBy { it.platformName.ifBlank { "Diğer" } }
                                .toSortedMap()
                        }
                        var seriesCategorySearchQuery by remember { mutableStateOf("") }
                        val currentCategory = selectedSeriesCategory
                        if (currentCategory == null) {
                            val filteredCategoryEntries = remember(grouped, seriesCategorySearchQuery) {
                                if (seriesCategorySearchQuery.isBlank()) {
                                    grouped.entries.toList()
                                } else {
                                    grouped.entries.filter { it.key.contains(seriesCategorySearchQuery, ignoreCase = true) }
                                }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(600.dp)
                            ) {
                                // Dizi Takvimi tanıtım kartı kaldırıldı (özellik geri çekildi)
                                item(key = "series_category_search_bar") {
                                    OutlinedTextField(
                                        value = seriesCategorySearchQuery,
                                        onValueChange = { seriesCategorySearchQuery = it },
                                        placeholder = { Text(stringResource(R.string.folder_search_placeholder), fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = stringResource(R.string.folder_search_desc),
                                                tint = Color(0xFF94A3B8)
                                            )
                                        },
                                        trailingIcon = {
                                            if (seriesCategorySearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { seriesCategorySearchQuery = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = stringResource(R.string.folder_clear_desc),
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                            focusedBorderColor = CineOrange,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            focusedLabelColor = CineOrange,
                                            focusedTextColor = Color(0xFFF5F5F7),
                                            unfocusedTextColor = Color(0xFFF5F5F7)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                                items(filteredCategoryEntries, key = { it.key }) { (categoryName, shows) ->
                                    val syntheticGroup = remember(categoryName, shows.size) {
                                        com.example.data.model.IPTVGroup(
                                            id = categoryName,
                                            name = categoryName,
                                            type = "SERIES",
                                            items = List(shows.size) {
                                                IPTVItem(
                                                    id = it,
                                                    playlistId = 0,
                                                    name = "",
                                                    cleanedName = "",
                                                    logoUrl = null,
                                                    streamUrl = "",
                                                    category = categoryName,
                                                    type = "SERIES"
                                                )
                                            }
                                        )
                                    }
                                    FolderCard(
                                        group = syntheticGroup,
                                        onClick = { selectedSeriesCategory = categoryName },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clickable { selectedSeriesCategory = null },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(currentCategory, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                val filteredShows = grouped[currentCategory] ?: emptyList()
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(600.dp)
                                ) {
                                    items(filteredShows, key = { it.id }) { show ->
                                        com.example.ui.screens.TvShowFolderCard(
                                            show = show,
                                            onClick = { viewModel.openCatalogShow(show) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        FolderGridScreen(
                            groups = seriesGroups,
                            title = "Dizi Klasörleri",
                            displayTitle = stringResource(R.string.home_folder_series),
                            viewModel = viewModel,
                            onPlayItem = onPlayItem,
                            continueWatching = continueWatching.filter { it.itemType == "SERIES" },
                            onPlayContinueWatching = onPlayContinueWatching,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )
                    }
                } else if (selectedTypeFilter == "RADIO") {
                    val chunkedRadio = remember(radioChannels) { radioChannels.chunked(2) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.home_radio_header),
                                color = contentColor,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                        }

                        if (radioChannels.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp, horizontal = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_empty_radio_category),
                                        color = mutedContentColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(chunkedRadio, key = { index, row -> "${row.joinToString("-") { it.id.toString() }}_$index" }) { _, rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            RadioGlassCard(
                                                item = item,
                                                onClick = { RadioPlayerManager.toggleRadio(context, item) },
                                                onLongClick = { },
                                                modifier = Modifier.fillMaxWidth().height(125.dp)
                                            )
                                        }
                                    }
                                    repeat(2 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTypeFilter == "WATCHLIST") {
                    WatchlistScreen(
                        viewModel = viewModel,
                        onSelectItem = { item -> viewModel.selectItem(item) },
                        onExploreClick = { viewModel.setSelectedTypeFilter("ALL") }
                    )
                } else if (selectedTypeFilter == "SETTINGS") {
                    SettingsScreen(viewModel = viewModel)
                }

                // Bottom padding for edge-to-edge comfort (Requirement 2: Universal Responsive UI)
                Spacer(modifier = Modifier.height(88.dp))
            }

        // Playlist add popup dialog
        if (showPlaylistDialog) {
            PlaylistAddDialog(
                onDismiss = { showPlaylistDialog = false },
                onM3UAdded = { name, content ->
                    viewModel.loadM3UPlaylist(name, content)
                },
                onM3UUrlAdded = { name, url ->
                    viewModel.loadM3UPlaylistFromUrl(name, url)
                }
            )
        }




        // Search Popup Dialog
        val isAILoading by viewModel.isAILoading.collectAsState()
        com.example.ui.components.SearchPopupDialog(
            isOpen = isSearchPopupOpen || searchQuery.isNotEmpty(),
            searchQuery = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onSearchSubmit = { viewModel.saveSearchQuery(it) },
            onAISearchClick = { viewModel.performNLPSearch(searchQuery) },
            isAILoading = isAILoading,
            searchResults = searchResults,
            onItemClick = { item ->
                isSearchPopupOpen = false
                viewModel.updateSearchQuery("")
                val matched = viewModel.findMatchedItem(item)
                val targetItem = if (matched.streamUrl.isNotEmpty()) matched else item
                if (targetItem.type == "LIVE" || targetItem.type == "LIVE_TV") {
                    onPlayItem(targetItem)
                } else {
                    // MOVIE veya SERIES ise doğrudan Detay Kartı Ekranına yönlendir
                    viewModel.selectItem(targetItem)
                }
            },
            onDismiss = {
                isSearchPopupOpen = false
                viewModel.updateSearchQuery("")
            }
        )
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val defaultModifier = Modifier
        .width(layout.cardWidth)
        .aspectRatio(2f / 3f)
    val cardModifier = if (modifier == Modifier) defaultModifier else modifier
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        label = "media_card_scale"
    )
    val currentTheme = LocalAppTheme.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)),
        modifier = cardModifier
            .scale(scale)
            .border(1.dp, if (currentTheme.isDark) CineBorder else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_card_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SafeAsyncImage(
                model = item.logoUrl,
                contentDescription = item.cleanedName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Scrim layer with vertical gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Content details overlay aligned at bottom start
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                if (item.type != "LIVE" && item.rating > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "★ ${item.rating}",
                            color = Color.Yellow,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (item.type == "LIVE") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(CineOrange, CineRed)
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_live_badge),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = item.cleanedName,
                    color = Color.White,
                    fontSize = layout.bodyFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterBottomTextCard(
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val cardModifier = if (modifier == Modifier) Modifier.width(layout.cardWidth) else modifier
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "poster_card_scale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isHovered) 10.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "poster_card_shadow"
    )
    val currentTheme = LocalAppTheme.current

    val sharedImageModifier = Modifier

    Column(
        modifier = cardModifier
            .scale(scale)
            .hoverable(interactionSource)
            .then(com.example.ui.tv.tvRestorableFocus())
            .clickable { onClick() }
            .testTag("search_media_card_${item.id}"),
        horizontalAlignment = Alignment.Start
    ) {
        // Rounded poster card with dynamic aspect ratio
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(shadowElevation, RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = if (currentTheme.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SafeAsyncImage(
                    model = item.logoUrl,
                    contentDescription = item.cleanedName,
                    modifier = Modifier.fillMaxSize().then(sharedImageModifier),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title under the card
        Text(
            text = item.cleanedName,
            color = Color.White,
            fontSize = layout.bodyFontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}


@Composable
fun ContinueWatchingCard(
    cw: ContinueWatching,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val progressFraction = if (cw.totalSeconds > 0) cw.progressSeconds.toFloat() / cw.totalSeconds else 0f
    val currentTheme = LocalAppTheme.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)),
        modifier = Modifier
            .width((layout.cardWidth * 1.8f).coerceAtLeast(200.dp))
            .aspectRatio(16f / 9f)
            .border(1.dp, if (currentTheme.isDark) CineBorder else Color.Black.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .then(com.example.ui.tv.tvRestorableFocus())
            .clickable(onClick = onClick)
            .testTag("continue_watching_card_${cw.itemId}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SafeAsyncImage(
                model = cw.itemLogo,
                contentDescription = cw.itemName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, CineBlack.copy(alpha = 0.9f))
                        )
                    )
            )

            // Dynamic category type badge at top left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when (cw.itemType) {
                            "LIVE" -> Color(0xFFEF4444) // Vibrant Red
                            "RADIO" -> Color(0xFF3B82F6) // Electric Blue
                            "MOVIE" -> CineOrange
                            "SERIES" -> Color(0xFF8B5CF6) // Beautiful Purple
                            else -> Color(0xFF64748B)
                        }
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = when (cw.itemType) {
                        "LIVE" -> stringResource(R.string.home_badge_live)
                        "RADIO" -> stringResource(R.string.home_badge_radio)
                        "MOVIE" -> stringResource(R.string.home_badge_movie)
                        "SERIES" -> stringResource(R.string.home_badge_series)
                        else -> cw.itemType
                    },
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Dismiss (Delete progress) Button top right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_remove_desc),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Info overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = cw.itemName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cw.category,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Orange progress bar at the bottom of the card (only if it has progress, e.g. MOVIE/SERIES)
            if (cw.totalSeconds > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(CineOrange)
                    )
                }
            }
        }
    }
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColors = listOf(
        Color(0xFF1E1E1E),
        Color(0xFF2C2C2C),
        Color(0xFF1E1E1E)
    )

    return this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 300f, translateAnim - 300f),
            end = Offset(translateAnim, translateAnim)
        )
    )
}

@Composable
fun ShimmerLiveItem() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
    }
}

@Composable
fun ShimmerCarouselItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .shimmerEffect()
    )
}

@Composable
fun ShimmerSeriesItem() {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    Box(
        modifier = Modifier
            .width(layout.cardWidth)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(14.dp))
            .shimmerEffect()
    )
}

@Composable
fun ShimmerRadioItem() {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    Box(
        modifier = Modifier
            .width((layout.cardWidth * 1.6f).coerceAtLeast(180.dp))
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp))
            .shimmerEffect()
    )
}

@Composable
fun GlassmorphismRadioRow(
    radios: List<IPTVItem>,
    onSelect: (IPTVItem) -> Unit,
    onToggleFavorite: (IPTVItem) -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = CineOrange,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_radio_music_header),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }

        if (isLoading) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(4, key = { "radio_skeleton_$it" }, contentType = { "skeleton" }) {
                    ShimmerRadioItem()
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(radios, key = { index, item -> "${item.id}_$index" }) { _, item ->
                    RadioGlassCard(
                        item = item,
                        onClick = { RadioPlayerManager.toggleRadio(context, item) },
                        onLongClick = { onToggleFavorite(item) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RadioGlassCard(
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val cardModifier = if (modifier == Modifier) {
        Modifier
            .width((layout.cardWidth * 1.6f).coerceAtLeast(180.dp))
            .aspectRatio(16f / 10f)
    } else modifier
    val context = LocalContext.current
    val activeRadioItem by RadioPlayerManager.activeRadioItem.collectAsState()
    val isPlaying by RadioPlayerManager.isPlaying.collectAsState()
    val isBuffering by RadioPlayerManager.isBuffering.collectAsState()

    val isActive = activeRadioItem?.id == item.id

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered || isActive) 1.03f else 1.0f,
        label = "card_scale"
    )

    // Eflatun / Neon Purple palette
    val neonPurple = Color(0xFFA855F7)
    val neonPurpleDark = Color(0xFF3B0764)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) neonPurpleDark.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.06f)
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) neonPurple else Color.White.copy(alpha = 0.15f)
        ),
        modifier = cardModifier
            .scale(scale)
            // TV: OK'yi basılı tutmak da uzun basış (favoriye ekle) sayılır.
            .then(com.example.ui.tv.tvLongPressKeys(onClick = onClick, onLongClick = onLongClick))
            .then(com.example.ui.tv.tvRestorableFocus())
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .offset(x = 130.dp, y = (-25).dp)
                        .background(neonPurple.copy(alpha = 0.25f), CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, if (isActive) neonPurple.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SafeAsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.cleanedName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.cleanedName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        if (isActive) {
                            // Eflatun / Neon Purple Equalizer Ritim Efekti
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "radio_equalizer")
                                repeat(5) { index ->
                                    val heightFraction by infiniteTransition.animateFloat(
                                        initialValue = 0.2f,
                                        targetValue = 1.0f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(
                                                durationMillis = 260 + (index * 80),
                                                easing = FastOutSlowInEasing
                                            ),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "eq_bar_$index"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(if (isPlaying) (12 * heightFraction).dp else 4.dp)
                                            .background(neonPurple, RoundedCornerShape(1.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBuffering) stringResource(R.string.home_radio_loading) else if (isPlaying) stringResource(R.string.home_radio_on_air) else stringResource(R.string.home_radio_paused),
                                    color = neonPurple,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        } else {
                            Text(
                                text = item.category.ifEmpty { "RADYO" },
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(neonPurple, CircleShape)
                                .clickable { RadioPlayerManager.toggleRadio(context, item) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Kayan Yazı (Marquee Text & UI State)
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_radio_playing_message),
                            color = Color(0xFFF3E8FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    velocity = 35.dp
                                )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(CineOrange, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.home_radio_badge),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Radio",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// YENİ DİNAMİK VE KEŞİF WİDGET COMPONENT'LERİ
// =========================================================================

@Composable
fun LegendsSection(
    items: List<IPTVItem>,
    onItemClick: (IPTVItem) -> Unit,
    onLongClick: (IPTVItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF9800))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_top_rated_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.home_top_rated_subtitle),
                        color = Color(0xFFFFD700).copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                color = Color(0xFFFFD700).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${items.size} Efsane",
                    color = Color(0xFFFFD700),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "${item.id}_$index" }) { _, item ->
                LegendCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onLongClick(item) }
                )
            }
        }
    }
}

@Composable
fun LegendCard(
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    Card(
        onClick = onClick,
        modifier = Modifier
            .then(com.example.ui.tv.tvRestorableFocus())
            .width(layout.cardWidth)
            .aspectRatio(2f / 3f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1938)),
        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SafeAsyncImage(
                model = item.logoUrl,
                contentDescription = item.cleanedName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark Bottom Overlay Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.95f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Top Gold Badge
            Surface(
                color = Color(0xFFFFD700),
                shape = RoundedCornerShape(bottomEnd = 12.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (item.rating > 0) String.format(java.util.Locale.US, "%.1f", item.rating) else "8.5",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Title and Category
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = item.cleanedName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.category.isNotBlank()) {
                    Text(
                        text = item.category,
                        color = Color(0xFFFFD700).copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun NextEpisodeCard(
    showTitle: String,
    nextItem: IPTVItem,
    nextSeason: Int,
    nextEpisode: Int,
    nextEpisodeName: String,
    onPlay: (IPTVItem) -> Unit,
    onDismiss: () -> Unit
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val currentTheme = LocalAppTheme.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme.isDark) Color(0xFF1B1637) else Color.Black.copy(alpha = 0.03f)
        ),
        border = BorderStroke(1.dp, if (currentTheme.isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)),
        modifier = Modifier
            .width((layout.cardWidth * 1.8f).coerceAtLeast(200.dp))
            .aspectRatio(16f / 9f)
            .then(com.example.ui.tv.tvRestorableFocus())
            .clickable { onPlay(nextItem) }
            .testTag("next_episode_card_${nextItem.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SafeAsyncImage(
                model = nextItem.logoUrl,
                contentDescription = showTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0F0B21).copy(alpha = 0.95f))
                        )
                    )
            )

            // Dismiss Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_remove_desc),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Play Icon Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CineOrange.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Oynat",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Info text
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CineOrange)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_next_episode_badge),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = showTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "S${nextSeason}:E${nextEpisode} - $nextEpisodeName",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TrailerBoxSection(
    items: List<IPTVItem>,
    onTrailerClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = CineOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_trailer_box),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "trailerbox_${item.id}_$index" }) { _, item ->
                val ytId = remember(item.trailerUrl) { extractYouTubeVideoId(item.trailerUrl) }
                if (ytId != null) {
                    TrailerBoxCard(
                        title = item.cleanedName,
                        youtubeThumbnailUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                        onClick = { onTrailerClick(item.trailerUrl.orEmpty()) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrailerBoxCard(
    title: String,
    youtubeThumbnailUrl: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1633)),
        modifier = Modifier.width(220.dp).then(com.example.ui.tv.tvRestorableFocus())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            com.example.ui.components.SafeAsyncImage(
                model = youtubeThumbnailUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.player_play_desc),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
fun MovieDuelWidget(
    item1: IPTVItem,
    item2: IPTVItem,
    onPlay: (IPTVItem) -> Unit,
    onDetail: (IPTVItem) -> Unit,
    onRefreshDuel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181330)),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(AccentNeonPurple, CineOrange))),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("movie_duel_widget")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Duel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = CineOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_duel_header),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two Contenders Side by Side with VS Badge in Middle
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Contender 1
                    DuelContenderCard(
                        item = item1,
                        onPlay = { onPlay(item1) },
                        onDetail = { onDetail(item1) },
                        modifier = Modifier.weight(1f)
                    )

                    // Contender 2
                    DuelContenderCard(
                        item = item2,
                        onPlay = { onPlay(item2) },
                        onDetail = { onDetail(item2) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // VS Emblem Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(CineOrange, Color(0xFF181330)))
                        )
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(onClick = onRefreshDuel)
                        .testTag("movie_duel_vs_refresh_button")
                ) {
                    Text(
                        text = "VS",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun DuelContenderCard(
    item: IPTVItem,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onDetail,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.then(com.example.ui.tv.tvRestorableFocus())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                SafeAsyncImage(
                    model = item.logoUrl,
                    contentDescription = item.cleanedName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Rating Tag
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "⭐ ${if (item.rating > 0) String.format(java.util.Locale.US, "%.1f", item.rating) else "8.2"}",
                        color = Color(0xFFFFD700),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.cleanedName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.home_watch_desc),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.home_watch_button), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Compact, modern fixed Top Header directly anchored below the Android status bar.
 * Responsive across all standard screen widths (360dp - 430dp+).
 */
@Composable
fun CineStreamTopBar(
    isProUser: Boolean,
    isScrolled: Boolean,
    onLogoClick: () -> Unit,
    onProClick: () -> Unit,
    onSearchClick: () -> Unit,
    onChatClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val isNarrowScreen = layout.screenWidthDp < 380

    Surface(
        color = if (isScrolled) MidPurpleBg.copy(alpha = 0.98f) else DeepPurpleBg.copy(alpha = 0.94f),
        border = BorderStroke(0.5.dp, if (isScrolled) AccentNeonPurple.copy(alpha = 0.2f) else AccentNeonPurple.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layout.headerHeight)
                    .padding(horizontal = layout.horizontalContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Branding: Logo + Application Name (Always visible)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onLogoClick)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo_1782414357652),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(if (isNarrowScreen) 30.dp else 34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CineStream",
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                listOf(Color.White, Color.White, CineOrange)
                            ),
                            fontSize = if (isNarrowScreen) 16.sp else 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Right Actions: PRO Badge + AI Chat Icon + Compact Search Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // PRO Badge Button
                    Surface(
                        onClick = onProClick,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isProUser) Color(0xFF00E676).copy(alpha = 0.18f) else CineOrange.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, if (isProUser) Color(0xFF00E676) else CineOrange),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("pro_badge_header_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "PRO",
                                tint = if (isProUser) Color(0xFF00E676) else CineOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "PRO",
                                color = if (isProUser) Color(0xFF00E676) else CineOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // AI Chat Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(CineOrange.copy(alpha = 0.18f), CircleShape)
                            .border(1.dp, CineOrange.copy(alpha = 0.4f), CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onChatClick)
                            .testTag("ai_chat_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.home_ai_movie_assistant_desc),
                            tint = CineOrange,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Series Calendar Button kaldırıldı (özellik geri çekildi)

                    // Compact Circular Search Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onSearchClick)
                            .testTag("compact_search_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Arama",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

