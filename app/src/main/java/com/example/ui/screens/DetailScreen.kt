package com.example.ui.screens

import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
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
import coil.compose.AsyncImage
import androidx.compose.ui.draw.shadow
import com.example.ui.components.SafeAsyncImage
import com.example.ui.components.NeonPlayButton
import com.example.R
import com.example.data.model.IPTVItem
import com.example.data.model.isPlaceholderCast
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.ui.components.InfoCapsule
import com.example.ui.components.DescriptionCard
import com.example.ui.components.GeminiSpoilerFreeSummaryCard
import com.example.ui.components.PersonDetailDialog
import com.example.ui.components.SimilarMoviesSection
import com.example.ui.IPTVViewModel

private fun getDominantColorsForItem(item: IPTVItem, isDark: Boolean): List<Color> {
    val hash = item.cleanedName.hashCode()
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
fun DetailScreen(
    item: IPTVItem?,
    seriesList: List<IPTVItem> = emptyList(),
    onDismiss: () -> Unit,
    onPlay: (IPTVItem) -> Unit,
    onToggleFavorite: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IPTVViewModel? = null
) {
    if (item == null) return

    if (item.type == "SERIES") {
        SeriesDetailScreen(
            item = item,
            seriesList = seriesList,
            onDismiss = onDismiss,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            modifier = modifier,
            viewModel = viewModel
        )
        return
    }

    val context = LocalContext.current
    val currentTheme = LocalAppTheme.current
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    var selectedPersonForBio by remember { mutableStateOf<Pair<String, String>?>(null) }

    val dynamicPosterUrl = item.logoUrl
    // Üst görsel: sağlayıcının yatay sahne görseli varsa o, yoksa poster. Poster (küçük kapak) listedekiyle aynı kalır.
    val detailBackdrop by if (viewModel != null) {
        viewModel.detailBackdrop.collectAsState()
    } else {
        remember { mutableStateOf<Pair<Int, String>?>(null) }
    }
    val dynamicBackdropUrl = detailBackdrop?.takeIf { it.first == item.id }?.second ?: item.logoUrl

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

    val sharedImageModifier = Modifier

    // Infinite transition for waving fluid background
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_gradient")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * java.lang.Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_offset"
    )

    val dominantColors = remember(item, currentTheme.isDark) {
        getDominantColorsForItem(item, currentTheme.isDark)
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

    val sec5Alpha by animateFloatAsState(
        targetValue = if (startAnimations) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 400),
        label = "sec5_alpha"
    )
    val sec5OffsetY by animateFloatAsState(
        targetValue = if (startAnimations) 0f else 30f,
        animationSpec = tween(durationMillis = 300, delayMillis = 400),
        label = "sec5_offset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
            .testTag("detail_screen_overlay")
    ) {
        selectedPersonForBio?.let { (name, role) ->
            PersonDetailDialog(
                personName = name,
                role = role,
                onDismiss = { selectedPersonForBio = null },
                viewModel = viewModel,
                excludeItemId = item?.id ?: -1,
                onItemClick = { work -> viewModel?.selectItem(work) }
            )
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
                .testTag("detail_card")
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
                            // Fragman kapak alanının tamamını kaplar (Netflix tarzı); taşan kenarlar kırpılır.
                            fillArea = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                    SafeAsyncImage(
                        model = dynamicBackdropUrl ?: dynamicPosterUrl ?: item.logoUrl,
                        contentDescription = "Cover Image",
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
                // Fragman başlayınca kapak alanı görünsün diye en üste kaydırılır.
                LaunchedEffect(playingTrailerId) { if (playingTrailerId != null) scrollState.animateScrollTo(0) }

                // 2. SCROLLABLE BODY OVERLAY
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        // Kaydırılan içeriğin sonu gezinme çubuğunun altında kalmasın (kart arka planı yine kenara kadar uzanır).
                        .navigationBarsPadding()
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
                                    model = dynamicPosterUrl ?: item.logoUrl,
                                    contentDescription = "Poster Thumbnail",
                                    modifier = Modifier.fillMaxSize().then(sharedImageModifier),
                                    contentScale = ContentScale.Crop
                                )

                                if (item.rating > 0.0) {
                                    val formattedRating = String.format(java.util.Locale.US, "%.1f", item.rating)
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
                                    text = item.cleanedName,
                                    color = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 25.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Çıkış tarihi, tür ve oyuncu: bilgi yoksa gösterilmez (eskiden uydurma değer yazılıyordu).
                                val releaseDateText = item.releaseDate
                                if (releaseDateText.isNotBlank()) Row(
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
                                val genreText = item.genre.ifBlank { item.category }
                                if (genreText.isNotBlank()) Box(
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
                                val castText = item.cast
                                if (castText.isNotBlank()) Box(
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

                        // SECTION 2: ACTION BUTTONS (ŞİMDİ İZLE, FAVORİ KALP, SEÇENEKLER)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = sec2Alpha
                                    translationY = sec2OffsetY.dp.toPx()
                                },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Large "ŞİMDİ İZLE" Button
                            NeonPlayButton(
                                text = stringResource(R.string.detail_watch_now),
                                qualityTag = "4K / FHD",
                                onClick = { onPlay(item) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("watch_now_button")
                            )

                            // Favorite Heart Button
                            IconButton(
                                onClick = { onToggleFavorite(item) },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color(0xFF1E1E24), CircleShape)
                                    .border(0.8.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("watchlist_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(R.string.detail_add_favorite),
                                    tint = if (item.isFavorite) CineOrange else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Fragman Button
                            Button(
                                onClick = {
                                    val rawUrl = item.trailerUrl ?: dynamicTrailerUrl
                                    val videoId = com.example.ui.components.extractYouTubeVideoId(rawUrl)
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
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(26.dp),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("action_trailer_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (playingTrailerId != null) Icons.Default.Close else Icons.Default.Movie,
                                        contentDescription = stringResource(R.string.trailer),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(if (playingTrailerId != null) R.string.trailer_close else R.string.trailer),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 18.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        )

                        // SECTION 3: STORYLINE / SUMMARY
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = sec3Alpha
                                    translationY = sec3OffsetY.dp.toPx()
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.detail_summary),
                                color = if (currentTheme.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            DescriptionCard(
                                text = item.summary,
                                title = item.cleanedName,
                                itemId = item.id,
                                viewModel = viewModel
                            )
                        }

                        // SECTION 5: CAST MEMBERS & DIRECTOR
                        com.example.ui.components.CastSection(
                            title = item.cleanedName,
                            rawCast = item.cast,
                            director = item.director,
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

                        // SECTION 6: SIMILAR MOVIES / RECOMMENDATIONS ("Bunları da Sevebilirsin")
                        Spacer(modifier = Modifier.height(24.dp))
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
