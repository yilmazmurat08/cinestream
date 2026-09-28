package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.ui.draw.shadow
import com.example.ui.components.SafeAsyncImage
import com.example.ui.components.NeonPlayButton
import com.example.R
import com.example.data.model.Episode
import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.model.TvShow
import com.example.data.model.isPlaceholderCast
import com.example.data.model.ContinueWatching
import androidx.compose.runtime.collectAsState
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.ui.components.InfoCapsule
import com.example.ui.components.DescriptionCard
import com.example.ui.components.GeminiSpoilerFreeSummaryCard
import com.example.ui.components.PersonDetailDialog
import com.example.ui.components.SimilarMoviesSection
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.ui.IPTVViewModel

private fun getDominantColorsForSeries(tvShow: TvShow, isDark: Boolean): List<Color> {
    val hash = tvShow.title.hashCode()
    val absHash = kotlin.math.abs(hash)
    
    val baseColor = when (absHash % 6) {
        0 -> Color(0xFFE50914) // Cine Red
        1 -> Color(0xFFE25C00) // Cine Orange
        2 -> Color(0xFF007AFF) // Electric Blue
        3 -> Color(0xFF8E44AD) // Deep Purple
        4 -> Color(0xFF00B16A) // Emerald Green
        else -> Color(0xFFF39C12) // Warm Gold
    }
    
    val secondaryColor = when ((absHash + 1) % 6) {
        0 -> Color(0xFF1E0B36) // Dark Velvet Purple
        1 -> Color(0xFF0F2027) // Dark Ocean
        2 -> Color(0xFF1F1C2C) // Dark Gray-Blue
        3 -> Color(0xFF0D0D0C) // Cine Dark Gray
        4 -> Color(0xFF1A0A21) // Deep Plum
        else -> Color(0xFF120C1F) // Deep Indigo
    }

    return if (isDark) {
        listOf(
            baseColor.copy(alpha = 0.22f),
            secondaryColor.copy(alpha = 0.85f),
            Color(0xFF0F111A)
        )
    } else {
        listOf(
            baseColor.copy(alpha = 0.12f),
            Color.White,
            Color.White
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SeriesDetailScreen(
    item: IPTVItem,
    seriesList: List<IPTVItem>,
    onDismiss: () -> Unit,
    onPlay: (IPTVItem) -> Unit,
    onToggleFavorite: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IPTVViewModel? = null
) {
    val context = LocalContext.current
    val currentTheme = LocalAppTheme.current
    val layout = rememberAppAdaptiveLayout()
    var selectedPersonForBio by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeRecapInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val continueWatchingList by if (viewModel != null) {
        viewModel.continueWatching.collectAsState()
    } else {
        remember { mutableStateOf(emptyList<ContinueWatching>()) }
    }

    val favoritesList by if (viewModel != null) {
        viewModel.favorites.collectAsState()
    } else {
        remember { mutableStateOf(emptyList<IPTVItem>()) }
    }
    val isFavorite = remember(item, favoritesList) {
        favoritesList.any { it.id == item.id } || item.isFavorite
    }

    // Dynamic trailer URL resolution using simulated ViewModel fetcher
    var dynamicTrailerUrl by remember(item) { mutableStateOf(item.trailerUrl) }
    // Netflix tarzı: fragman YouTube'a gitmeden üstteki kapak görselinin yerinde oynar.
    var playingTrailerId by remember(item) { mutableStateOf<String?>(null) }

    if (dynamicTrailerUrl.isNullOrEmpty() && viewModel != null) {
        LaunchedEffect(item) {
            try {
                val videoId = viewModel.fetchTrailerVideoId(item.cleanedName)
                if (!videoId.isNullOrBlank()) {
                    dynamicTrailerUrl = "https://www.youtube.com/watch?v=$videoId"
                }
            } catch (e: Exception) {
                // Keep empty or fallback
            }
        }
    }

    val cachedTvShows by if (viewModel != null) {
        viewModel.groupedTvShows.collectAsState()
    } else {
        remember { mutableStateOf(emptyList<TvShow>()) }
    }
    var tvShows by remember { mutableStateOf<List<TvShow>>(emptyList()) }
    LaunchedEffect(cachedTvShows, seriesList) {
        tvShows = if (cachedTvShows.isNotEmpty()) {
            cachedTvShows
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                SeriesParser.groupItemsIntoShows(seriesList)
            }
        }
    }

    // Pre-fetch cast biographies in the background to make clicking on them instant
    LaunchedEffect(item) {
        val castList = item.cast.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        castList.forEach { actorName ->
            launch(Dispatchers.IO) {
                try {
                    com.example.data.api.MetadataEnricher.fetchPersonDetails(context, actorName, "Oyuncu")
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        if (item.director.isNotEmpty()) {
            launch(Dispatchers.IO) {
                try {
                    com.example.data.api.MetadataEnricher.fetchPersonDetails(context, item.director, "Yönetmen")
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Find the show matching the clicked item
    val currentTvShow = remember(item, tvShows) {
        val parsedInfo = SeriesParser.parseEpisodeInfo(item.cleanedName) ?: SeriesParser.parseEpisodeInfo(item.name)
        val targetTitle = parsedInfo?.showTitle ?: item.cleanedName
        tvShows.find { it.title.equals(targetTitle, ignoreCase = true) }
    }

    val liveFetchedShow by if (viewModel != null) {
        viewModel.liveFetchedShow.collectAsState()
    } else {
        remember { mutableStateOf(null) }
    }

    // Fallback to a single-episode show if not found in groups yet
    val tvShow = remember(item, currentTvShow, liveFetchedShow) {
        liveFetchedShow ?: currentTvShow ?: TvShow(
            id = item.id,
            title = item.cleanedName,
            logoUrl = null,
            category = item.category,
            rating = item.rating,
            summary = item.summary,
            cast = item.cast,
            director = item.director,
            isFavorite = item.isFavorite,
            seasons = listOf(
                com.example.data.model.Season(
                    seasonNumber = 1,
                    episodes = listOf(
                        Episode(
                            id = item.id,
                            name = item.cleanedName,
                            seasonNumber = 1,
                            episodeNumber = 1,
                            item = item
                        )
                    )
                )
            )
        )
    }

    val dynamicPosterUrl = remember(item, tvShow) {
        tvShow.logoUrl
    }
    val dynamicBackdropUrl = remember(dynamicPosterUrl) { dynamicPosterUrl }

    // TMDB Plot Summary state for series
    var tmdbPlotSummary by remember(tvShow.title) { mutableStateOf<String?>(null) }
    LaunchedEffect(tvShow.title) {
        val existingSummary = tvShow.summary.ifEmpty { item.summary }
        val isPlaceholder = existingSummary.isBlank() ||
                existingSummary == "Bu içerik için özet bilgisi bulunmuyor." ||
                existingSummary.contains("özet bilgisi bulunmuyor", ignoreCase = true)
        if (isPlaceholder || existingSummary.length < 30) {
            val summary = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    com.example.data.api.MetadataEnricher.fetchSeriesPlotSummaryFromTMDB(tvShow.title)
                } catch (e: Exception) {
                    null
                }
            }
            if (!summary.isNullOrBlank()) {
                tmdbPlotSummary = summary
            }
        }
    }

    val lastWatchedEpisode = remember(tvShow, continueWatchingList) {
        if (tvShow != null) {
            val episodeIds = tvShow.seasons.flatMap { s -> s.episodes.map { e -> e.item.id } }
            val activeCwItems = continueWatchingList.filter { it.itemId in episodeIds }
            val lastCw = activeCwItems.maxByOrNull { cwItem: com.example.data.model.ContinueWatching -> cwItem.lastPlayedAt }
            if (lastCw != null) {
                tvShow.seasons.flatMap { s -> s.episodes.map { e -> Pair(s.seasonNumber, e) } }
                    .find { it.second.item.id == lastCw.itemId }
            } else {
                null
            }
        } else {
            null
        }
    }

    var selectedSeasonIndex by remember { mutableStateOf(0) }
    val seasons = tvShow.seasons
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()

    val sharedImageModifier = Modifier

    // Infinite transition for waving fluid background
    val infiniteTransition = rememberInfiniteTransition(label = "series_fluid_gradient")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * java.lang.Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
    )

    val dominantColors = remember(tvShow, currentTheme.isDark) {
        getDominantColorsForSeries(tvShow, currentTheme.isDark)
    }

    val modifierBackground = Modifier.drawBehind {
        val width = size.width
        val height = size.height
        
        val x1 = width * (0.5f + 0.20f * kotlin.math.cos(waveOffset.toDouble()).toFloat())
        val y1 = height * (0.25f + 0.12f * kotlin.math.sin(waveOffset.toDouble()).toFloat())
        
        val x2 = width * (0.5f - 0.20f * kotlin.math.sin(waveOffset.toDouble()).toFloat())
        val y2 = height * (0.65f + 0.12f * kotlin.math.cos(waveOffset.toDouble()).toFloat())

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(dominantColors[0], Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(x1, y1),
                radius = width * 0.85f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(dominantColors[1], Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(x2, y2),
                radius = width * 1.10f
            )
        )
        drawRect(
            color = if (currentTheme.isDark) Color(0xFF0F111A).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.45f),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstOver
        )
    }

    // Staggered slide-up entry animations
    var startAnimations by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        startAnimations = true
    }

    val sec1Alpha by animateFloatAsState(
        targetValue = if (startAnimations) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 0),
        label = "sec1_alpha"
    )
    val sec1OffsetY by animateFloatAsState(
        targetValue = if (startAnimations) 0f else 30f,
        animationSpec = tween(durationMillis = 300, delayMillis = 0),
        label = "sec1_offset"
    )

    val sec2Alpha by animateFloatAsState(
        targetValue = if (startAnimations) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 100),
        label = "sec2_alpha"
    )
    val sec2OffsetY by animateFloatAsState(
        targetValue = if (startAnimations) 0f else 30f,
        animationSpec = tween(durationMillis = 300, delayMillis = 100),
        label = "sec2_offset"
    )

    val sec3Alpha by animateFloatAsState(
        targetValue = if (startAnimations) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 200),
        label = "sec3_alpha"
    )
    val sec3OffsetY by animateFloatAsState(
        targetValue = if (startAnimations) 0f else 30f,
        animationSpec = tween(durationMillis = 300, delayMillis = 200),
        label = "sec3_offset"
    )

    val sec4Alpha by animateFloatAsState(
        targetValue = if (startAnimations) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 300),
        label = "sec4_alpha"
    )
    val sec4OffsetY by animateFloatAsState(
        targetValue = if (startAnimations) 0f else 30f,
        animationSpec = tween(durationMillis = 300, delayMillis = 300),
        label = "sec4_offset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
            .testTag("series_detail_overlay")
    ) {
        selectedPersonForBio?.let { (name, role) ->
            PersonDetailDialog(
                personName = name,
                role = role,
                onDismiss = { selectedPersonForBio = null },
                viewModel = viewModel,
                excludeItemId = item.id,
                onItemClick = { work -> viewModel?.selectItem(work) }
            )
        }

        activeRecapInfo?.let { (season, episode) ->
            if (viewModel != null) {
                AIRecapDialog(
                    showTitle = tvShow?.title ?: item.cleanedName,
                    season = season,
                    episode = episode,
                    lastWatchedEpisode = lastWatchedEpisode,
                    onDismiss = { activeRecapInfo = null },
                    onPlayFirstEpisode = {
                        activeRecapInfo = null
                        val firstEpItem = tvShow?.seasons?.firstOrNull()?.episodes?.firstOrNull()?.item ?: item
                        onPlay(firstEpItem)
                    },
                    viewModel = viewModel
                )
            }
        }

        // Apple style card sheet
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (currentTheme.isDark) Color(0xFF0F111A) else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = layout.dialogMaxWidth)
                .fillMaxHeight(0.88f)
                .border(
                    width = 1.dp,
                    color = if (currentTheme.isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(enabled = false) {}
                .testTag("series_detail_card")
        ) {
            val scrollState = rememberScrollState()
            val scrollOffset = scrollState.value.toFloat()
            val parallaxOffset = scrollOffset * 0.5f
            val blurRadius = (scrollOffset / 30f).coerceIn(0f, 20f).dp

            Box(modifier = Modifier.fillMaxSize()) {
                
                // 1. HERO COVER BACKDROP (With Parallax + Gaussian Blur on scroll)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .graphicsLayer {
                            translationY = parallaxOffset
                        }
                        .then(if (blurRadius > 0.5.dp) Modifier.blur(blurRadius) else Modifier)
                        .background(Color(0xFF1E112A))
                ) {
                    val trailerId = playingTrailerId
                    if (trailerId != null) {
                        com.example.ui.components.InAppTrailerPlayer(
                            videoId = trailerId,
                            showControls = false,
                            onEnded = { playingTrailerId = null },
                            onError = {
                                playingTrailerId = null
                                android.widget.Toast.makeText(context, context.getString(R.string.trailer_inapp_failed), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                        )
                    } else {
                    SafeAsyncImage(
                        model = dynamicBackdropUrl ?: dynamicPosterUrl ?: tvShow.logoUrl,
                        contentDescription = "Series Cover Image",
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Scrims for better readability and blend
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Transparent,
                                        if (currentTheme.isDark) Color(0xFF0F111A) else Color.White
                                    )
                                )
                            )
                    )
                    }
                }
                // Fragman başlayınca kapak alanı görünsün diye en üste kaydırılır; video ayrı pencerede çizildiği için
                // sayfa yeniden aşağı kaydırılınca fragman kapanır (içeriğin üstünde kalmaz).
                LaunchedEffect(playingTrailerId) {
                    if (playingTrailerId != null) {
                        scrollState.animateScrollTo(0)
                        snapshotFlow { scrollState.value }.collect { if (it > 48) playingTrailerId = null }
                    }
                }

                // 2. SCROLLABLE BODY OVERLAY
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Transparent Spacer to show Hero Cover underneath
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color.Transparent)
                    )

                    // Details Card Content (sliding up with Animated Fluid Gradient)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .then(modifierBackground)
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        
                        // SECTION 1: COVER THUMBNAIL & METADATA PILLS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = sec1Alpha
                                    translationY = sec1OffsetY.dp.toPx()
                                },
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Left Poster Image with top-left IMDB badge
                            val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
                            Box(
                                modifier = Modifier
                                    .width(layout.cardWidth.coerceAtLeast(120.dp))
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .shadow(12.dp, RoundedCornerShape(16.dp))
                            ) {
                                SafeAsyncImage(
                                    model = dynamicPosterUrl ?: tvShow.logoUrl,
                                    contentDescription = "Poster Thumbnail",
                                    modifier = Modifier.fillMaxSize().then(sharedImageModifier),
                                    contentScale = ContentScale.Crop
                                )

                                if (tvShow.rating > 0.0) {
                                    val formattedRating = String.format(java.util.Locale.US, "%.1f", tvShow.rating)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(Color(0xFF2563EB), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = formattedRating,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Right Column (Title, Date Pill, Genre Pill, Cast Pill)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = tvShow.title,
                                    color = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 25.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Release Date Pill
                                val releaseDateText = item.releaseDate.ifBlank { "2026-04-29" }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF1E1E24), RoundedCornerShape(18.dp))
                                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = stringResource(R.string.detail_date_desc),
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = releaseDateText,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Genre Pill
                                val genreText = item.genre.ifBlank { if (tvShow.category.isNotBlank()) tvShow.category else "Komedi, Dram" }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF1E1E24), RoundedCornerShape(18.dp))
                                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = genreText,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Cast Pill
                                val castText = item.cast.ifBlank { "Meryl Streep, Anne H..." }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF1E1E24), RoundedCornerShape(18.dp))
                                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = castText,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // INFO CAPSULES
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .graphicsLayer {
                                    alpha = sec1Alpha
                                    translationY = sec1OffsetY.dp.toPx()
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoCapsule(
                                text = "${seasons.size} Sezon",
                                icon = Icons.Default.Tv
                            )
                            if (tvShow.rating > 0) {
                                InfoCapsule(
                                    text = "${tvShow.rating} IMDb",
                                    icon = Icons.Default.Star,
                                    iconTint = Color(0xFFFFB300)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 2: ACTION BUTTONS ROW
                        val firstEpisode = seasons.firstOrNull()?.episodes?.firstOrNull()
                        if (firstEpisode != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = sec2Alpha
                                        translationY = sec2OffsetY.dp.toPx()
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NeonPlayButton(
                                    text = stringResource(R.string.detail_play_first_episode),
                                    qualityTag = "FHD",
                                    onClick = { onPlay(firstEpisode.item) },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .testTag("series_play_first_button")
                                )

                                Button(
                                    onClick = {
                                        val videoId = com.example.ui.components.extractYouTubeVideoId(item.trailerUrl ?: dynamicTrailerUrl)
                                        if (playingTrailerId != null) {
                                            playingTrailerId = null
                                        } else if (!videoId.isNullOrEmpty()) {
                                            playingTrailerId = videoId
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.detail_no_trailer_found),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E1E24),
                                        contentColor = if (currentTheme.isDark) Color.White else Color.Black
                                    ),
                                    shape = RoundedCornerShape(26.dp),
                                    border = BorderStroke(
                                        width = 0.8.dp,
                                        color = if (currentTheme.isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(50.dp)
                                        .testTag("series_watch_trailer_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (playingTrailerId != null) Icons.Default.Close else Icons.Default.Movie,
                                            contentDescription = stringResource(R.string.detail_watch_trailer_desc),
                                            tint = if (currentTheme.isDark) Color.White else Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(if (playingTrailerId != null) R.string.trailer_close else R.string.trailer),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onToggleFavorite(item) },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(Color(0xFF1E1E24), CircleShape)
                                        .border(0.8.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                        .testTag("series_watchlist_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = stringResource(R.string.detail_add_favorite),
                                        tint = if (isFavorite) CineOrange else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 3: STORYLINE
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = sec3Alpha
                                    translationY = sec3OffsetY.dp.toPx()
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.detail_summary_and_story),
                                color = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            DescriptionCard(
                                text = tmdbPlotSummary ?: tvShow.summary.ifEmpty { item.summary },
                                title = tvShow.title,
                                itemId = item.id,
                                viewModel = viewModel
                            )

                            if (viewModel != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    onClick = {
                                        val seasonNum = lastWatchedEpisode?.first ?: 1
                                        val episodeNum = (lastWatchedEpisode?.second?.episodeNumber ?: 0) + 1
                                        activeRecapInfo = Pair(seasonNum, episodeNum)
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = CineOrange.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .testTag("ai_recap_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = CineOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.detail_ai_summarize_previous),
                                                color = if (currentTheme.isDark) Color.White else Color.Black,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (lastWatchedEpisode != null) {
                                                    stringResource(R.string.series_recap_last_watched, lastWatchedEpisode.first, lastWatchedEpisode.second.episodeNumber)
                                                } else {
                                                    stringResource(R.string.series_recap_start)
                                                },
                                                color = if (currentTheme.isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = CineOrange,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // SECTION 4: CAST MEMBERS & DIRECTOR
                        com.example.ui.components.CastSection(
                            title = tvShow.title.ifBlank { item.cleanedName },
                            rawCast = tvShow.cast,
                            director = tvShow.director,
                            viewModel = viewModel,
                            item = item,
                            onPersonClick = { personName, role ->
                                selectedPersonForBio = Pair(personName, role)
                            },
                            modifier = Modifier.graphicsLayer {
                                alpha = sec4Alpha
                                translationY = sec4OffsetY.dp.toPx()
                            }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // SECTION 5: SEASONS & EPISODES
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = sec4Alpha
                                    translationY = sec4OffsetY.dp.toPx()
                                }
                        ) {
                            if (seasons.size > 1) {
                                ScrollableTabRow(
                                    selectedTabIndex = selectedSeasonIndex,
                                    containerColor = Color.Transparent,
                                    contentColor = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    edgePadding = 0.dp,
                                    indicator = { tabPositions ->
                                        if (selectedSeasonIndex < tabPositions.size) {
                                            TabRowDefaults.SecondaryIndicator(
                                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                                                color = CineOrange
                                            )
                                        }
                                    },
                                    divider = {}
                                ) {
                                    seasons.forEachIndexed { index, season ->
                                        Tab(
                                            selected = selectedSeasonIndex == index,
                                            onClick = { selectedSeasonIndex = index },
                                            text = {
                                                Text(
                                                    text = "${season.seasonNumber}. Sezon",
                                                    fontSize = 15.sp,
                                                    fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            selectedContentColor = CineOrange,
                                            unselectedContentColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.detail_episodes),
                                    color = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (selectedSeason != null) {
                                selectedSeason.episodes.forEachIndexed { index, episode ->
                                    EpisodeRowItem(
                                        index = index + 1,
                                        episode = episode,
                                        onPlay = onPlay,
                                        continueWatchingList = continueWatchingList
                                    )
                                    if (index < selectedSeason.episodes.lastIndex) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.detail_no_episodes_season),
                                    color = if (currentTheme.isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }

                        // SECTION 6: SIMILAR MOVIES / RECOMMENDATIONS ("Bunları da Sevebilirsin")
                        Spacer(modifier = Modifier.height(32.dp))
                        SimilarMoviesSection(
                            item = item,
                            viewModel = viewModel,
                            onSelectItem = { recItem ->
                                if (viewModel != null) {
                                    viewModel.selectItem(recItem)
                                } else {
                                    onPlay(recItem)
                                }
                            },
                            currentThemeIsDark = currentTheme.isDark
                        )

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }

                // DRAG HANDLE BAR AT ABSOLUTE TOP
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                )

                // FLOATING CLOSE BUTTON AT TOP RIGHT
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.detail_close),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeRowItem(
    index: Int,
    episode: Episode,
    onPlay: (IPTVItem) -> Unit,
    continueWatchingList: List<ContinueWatching> = emptyList()
) {
    val currentTheme = LocalAppTheme.current
    val cw = remember(episode, continueWatchingList) {
        continueWatchingList.find { it.itemId == episode.item.id }
    }
    val progressFraction = remember(cw) {
        if (cw != null && cw.totalSeconds > 0) {
            cw.progressSeconds.toFloat() / cw.totalSeconds.toFloat()
        } else {
            0.0f
        }
    }
    val isWatched = progressFraction >= 0.80f
    val isPartiallyWatched = progressFraction > 0.0f && progressFraction < 0.80f

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(episode.item) }
            .border(
                width = 1.dp,
                color = if (currentTheme.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("episode_item_${episode.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentTheme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            ) {
                SafeAsyncImage(
                    model = episode.logoUrl ?: episode.item.logoUrl,
                    contentDescription = episode.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isWatched) 0.90f else 1.0f),
                    contentScale = ContentScale.Crop
                )

                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.detail_watched),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                if (isPartiallyWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(NeonPink)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.series_episode_n, index),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = episode.name,
                    color = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = stringResource(R.string.series_season_episode, episode.seasonNumber, episode.episodeNumber),
                    color = if (currentTheme.isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                // ÖNEMLİ: IPTVItem veri modelinde süre (duration) alanı bulunmuyor — M3U/Xtream
                // kaynağından bu bilgi hiç gelmiyor. Bu yüzden uydurma bir süre ("45 dk") ASLA
                // gösterilmez. Kalite etiketi de uydurulmaz; sadece bölüm isminin İÇİNDE
                // sağlayıcının kendi koyduğu gerçek bir ibare (4K/1080p/HD) varsa gösterilir.
                val qualityTag = remember(episode.name) {
                    val upperName = episode.name.uppercase()
                    when {
                        upperName.contains("4K") || upperName.contains("2160P") -> "4K"
                        upperName.contains("FHD") || upperName.contains("1080P") -> "FHD"
                        upperName.contains("HD") || upperName.contains("720P") -> "HD"
                        else -> null
                    }
                }

                if (qualityTag != null) {
                    Text(
                        text = qualityTag,
                        color = if (currentTheme.isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CineOrange.copy(alpha = 0.15f))
                    .border(1.dp, CineOrange.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.detail_play_episode_desc),
                    tint = CineOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIRecapDialog(
    showTitle: String,
    season: Int,
    episode: Int,
    lastWatchedEpisode: Pair<Int, com.example.data.model.Episode>?,
    onDismiss: () -> Unit,
    onPlayFirstEpisode: () -> Unit,
    viewModel: IPTVViewModel
) {
    val currentTheme = LocalAppTheme.current
    val context = LocalContext.current

    // Durum A: Hiç bölüm izlenmediyse (lastWatchedEpisode == null or S01E01 or episode <= 1)
    val isNotWatched = lastWatchedEpisode == null || (season == 1 && episode <= 1) || (season <= 1 && episode <= 1)

    var recapText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(!isNotWatched) }

    LaunchedEffect(isNotWatched, showTitle, season, episode) {
        if (!isNotWatched) {
            isLoading = true
            try {
                recapText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.api.GeminiAiService.generatePreviousEpisodesSummary(
                        context = context,
                        showTitle = showTitle,
                        lastWatchedEpisodeNumber = episode
                    )
                }
            } catch (e: Throwable) {
                recapText = context.getString(R.string.series_recap_unavailable)
            } finally {
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (currentTheme.isDark) Color(0xFF131522) else Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (currentTheme.isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                // İçerik küçük/yatay ekranda veya büyük yazı boyutunda sığmazsa kaydırılabilsin.
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header Row with Circular Purple Neon Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFD946EF).copy(alpha = 0.35f), Color(0xFF8B5CF6).copy(alpha = 0.15f))
                            ),
                            shape = CircleShape
                        )
                        .border(1.5.dp, Color(0xFFD946EF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Icon",
                        tint = Color(0xFFD946EF),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = stringResource(R.string.detail_ai_summarize_title),
                        color = if (currentTheme.isDark) Color.White else Color.Black,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isNotWatched) showTitle else stringResource(R.string.series_recap_before, showTitle, season.toString(), episode.toString()),
                        color = if (currentTheme.isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isNotWatched) {
                // Durum A: Hiç izlenmediyse kibar bilgilendirme kartı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                    Color(0xFFD946EF).copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(1.2.dp, Color(0xFFD946EF).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(R.string.detail_start_of_journey_title),
                            color = Color(0xFFD946EF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.detail_start_of_journey_desc),
                            color = if (currentTheme.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onPlayFirstEpisode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD946EF)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_first_episode_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_start_first_episode),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Durum B: Önceki bölümler varsa AI spoiler-free özet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (currentTheme.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                        )
                        .border(
                            1.dp,
                            if (currentTheme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(20.dp)
                ) {
                    if (isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFD946EF),
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.detail_ai_analyzing),
                                color = if (currentTheme.isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = recapText ?: stringResource(R.string.series_recap_unavailable),
                            color = if (currentTheme.isDark) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD946EF)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Kapat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
