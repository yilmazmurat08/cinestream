package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.example.ui.components.CategoryAdaptiveIcon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.IPTVGroup
import com.example.data.model.IPTVItem
import com.example.data.model.SeriesParser
import com.example.data.model.TvShow
import com.example.data.model.ContinueWatching
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.example.ui.IPTVViewModel
import com.example.ui.theme.*
import com.example.data.epg.EPGProvider
import com.example.data.model.ChannelEPG
import com.example.data.model.EPGProgram
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import com.example.ui.theme.rememberAppAdaptiveLayout

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FolderGridScreen(
    groups: List<IPTVGroup>,
    title: String,
    viewModel: IPTVViewModel,
    onPlayItem: (IPTVItem) -> Unit,
    continueWatching: List<ContinueWatching> = emptyList(),
    onPlayContinueWatching: (ContinueWatching) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val activeGroup by viewModel.activeFolderGroup.collectAsState()
    val isParentalLockEnabled by viewModel.parentalLock.collectAsState()
    val isSafeSessionActive by viewModel.isSafeSessionActive.collectAsState()
    val safeSessionRemainingSeconds by viewModel.safeSessionRemainingSeconds.collectAsState()
    var pendingAdultGroup by remember { mutableStateOf<IPTVGroup?>(null) }

    // Intercept back button if in sub-level folder
    BackHandler(enabled = activeGroup != null) {
        viewModel.setActiveFolderGroup(null)
    }

    AnimatedContent(
        targetState = activeGroup,
        transitionSpec = {
            if (targetState != null) {
                // Apple TV style zoom-in transition when entering folder detail
                (fadeIn(animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)) +
                 scaleIn(
                     initialScale = 0.88f,
                     animationSpec = spring(
                         dampingRatio = Spring.DampingRatioLowBouncy,
                         stiffness = Spring.StiffnessMediumLow
                     )
                 ) +
                 slideInVertically(
                     initialOffsetY = { it / 10 },
                     animationSpec = tween(380, easing = FastOutSlowInEasing)
                 )
                ).togetherWith(
                 fadeOut(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) +
                 scaleOut(
                     targetScale = 1.08f,
                     animationSpec = tween(280, easing = FastOutSlowInEasing)
                 )
                )
            } else {
                // Apple TV style zoom-out return transition when going back
                (fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)) +
                 scaleIn(
                     initialScale = 1.08f,
                     animationSpec = tween(350, easing = FastOutSlowInEasing)
                 )
                ).togetherWith(
                 fadeOut(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) +
                 scaleOut(
                     targetScale = 0.88f,
                     animationSpec = spring(
                         dampingRatio = Spring.DampingRatioNoBouncy,
                         stiffness = Spring.StiffnessMedium
                     )
                 ) +
                 slideOutVertically(
                     targetOffsetY = { it / 10 },
                     animationSpec = tween(280, easing = FastOutSlowInEasing)
                 )
                )
            }
        },
        label = "apple_tv_folder_navigation"
    ) { group ->
        if (group == null) {
            FolderListLayout(
                groups = groups,
                title = title,
                onFolderClick = { clickedGroup ->
                    val isAdult = viewModel.isAdultContent(clickedGroup)
                    if (isAdult && isParentalLockEnabled && !isSafeSessionActive) {
                        pendingAdultGroup = clickedGroup
                    } else {
                        viewModel.setActiveFolderGroup(clickedGroup)
                    }
                },
                onPlayItem = onPlayItem,
                continueWatching = continueWatching,
                onPlayContinueWatching = onPlayContinueWatching,
                viewModel = viewModel,
                modifier = modifier
            )
        } else {
            FolderDetailLayout(
                group = group,
                viewModel = viewModel,
                onPlayItem = onPlayItem,
                onBackClick = { viewModel.setActiveFolderGroup(null) },
                modifier = modifier
            )
        }
    }

    if (pendingAdultGroup != null) {
        com.example.ui.components.ParentalPinDialog(
            viewModel = viewModel,
            title = "Ebeveyn Kilidi",
            subtitle = "Bu kategoriyi açmak için PIN kodunuzu girin. Doğrulama sonrası 15 dakika boyunca Güvenli Oturum aktif kalır.",
            onDismiss = { pendingAdultGroup = null },
            onSuccess = {
                viewModel.unlockSafeSession(15)
                viewModel.setActiveFolderGroup(pendingAdultGroup)
                pendingAdultGroup = null
            }
        )
    }
}

@Composable
fun SafeSessionIndicator(
    remainingSeconds: Int,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = remember(remainingSeconds) {
        String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1B4B).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("safe_session_indicator")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.folder_safe_session_desc),
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.folder_safe_session_active),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                    }
                    Text(
                        text = "Kalan Süre: $formattedTime (PIN sormadan erişim)",
                        color = Color(0xFFB0AEC7),
                        fontSize = 11.sp
                    )
                }
            }

            FilledTonalButton(
                onClick = onLockClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                    contentColor = Color(0xFFFCA5A5)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("lock_safe_session_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.folder_lock),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun FolderListLayout(
    groups: List<IPTVGroup>,
    title: String,
    onFolderClick: (IPTVGroup) -> Unit,
    onPlayItem: (IPTVItem) -> Unit = {},
    continueWatching: List<ContinueWatching> = emptyList(),
    onPlayContinueWatching: (ContinueWatching) -> Unit = {},
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val isParentalLockEnabled by viewModel.parentalLock.collectAsState()
    val isSafeSessionActive by viewModel.isSafeSessionActive.collectAsState()
    val safeSessionRemainingSeconds by viewModel.safeSessionRemainingSeconds.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) {
            groups
        } else {
            groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = adaptiveLayout.screenPadding)
            .testTag("folder_grid_view")
    ) {
        item(key = "folder_title_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            ) {
                CategoryAdaptiveIcon(
                    category = title,
                    type = if (title.contains("Canlı", ignoreCase = true)) "LIVE" else if (title.contains("Sinema", ignoreCase = true) || title.contains("Film", ignoreCase = true)) "MOVIE" else if (title.contains("Dizi", ignoreCase = true)) "SERIES" else null,
                    size = 20.dp,
                    containerColor = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    text = title,
                    color = Color(0xFFF5F5F7),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (title.contains("Canlı", ignoreCase = true)) {
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        onClick = { viewModel.setSelectedTypeFilter("EPG") },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFF6D00).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6D00).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarViewDay,
                                contentDescription = null,
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "EPG",
                                color = Color(0xFFFF6D00),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (isParentalLockEnabled && isSafeSessionActive) {
            item(key = "safe_session_banner") {
                SafeSessionIndicator(
                    remainingSeconds = safeSessionRemainingSeconds,
                    onLockClick = { viewModel.lockSafeSession() }
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue_watching_section") {
                Column {
                    Text(
                        text = stringResource(R.string.folder_continue_watching),
                        color = Color(0xFFF5F5F7),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(continueWatching, key = { index, cw -> "${cw.itemId}_$index" }) { _, cw ->
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

        // Elegant Search Bar for Folders
        item(key = "folder_search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.folder_search_placeholder), fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.folder_search_desc),
                        tint = Color(0xFF94A3B8)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        // EPG Info Area positioned directly below search bar for Live TV
        if (groups.any { it.type == "LIVE" } || title.contains("Canlı", ignoreCase = true)) {
            val liveChannels = groups.flatMap { it.items }
            if (liveChannels.isNotEmpty()) {
                item(key = "live_epg_section") {
                    CompactEPGSection(
                        liveChannels = liveChannels,
                        onPlayChannel = { item -> onPlayItem(item) }
                    )
                }
            }
        }

        if (filteredGroups.isEmpty()) {
            item(key = "empty_folders_msg") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.folder_not_found),
                        color = Color(0xFF94A3B8), // Slate Gray
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            itemsIndexed(filteredGroups, key = { index, group -> "${group.id}_$index" }) { _, group ->
                val isAdult = viewModel.isAdultContent(group)
                FolderCard(
                    group = group,
                    isAdult = isAdult,
                    isLocked = isAdult && isParentalLockEnabled && !isSafeSessionActive,
                    isSafeSessionUnlocked = isAdult && isParentalLockEnabled && isSafeSessionActive,
                    onClick = { onFolderClick(group) }
                )
            }
        }
    }
}

@Composable
fun FolderCard(
    group: IPTVGroup,
    isAdult: Boolean = false,
    isLocked: Boolean = false,
    isSafeSessionUnlocked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameLower = group.name.lowercase(Locale.ROOT)
    val isLive = group.type == "LIVE"
    val isMovie = group.type == "MOVIE"
    val isSeries = group.type == "SERIES"
    
    val (cardBg, accentColor, tagline) = remember(nameLower, isLive, isMovie, isSeries, isAdult, isLocked) {
        val defaultTagline = when {
            isAdult -> "Yetişkin İçerik Kategorisi"
            isLive -> "Canlı Televizyon Kanalları"
            isMovie -> "Popüler Sinema Filmleri"
            isSeries -> "Popüler Dizi Sezonları"
            else -> "Seçkin İçerik Arşivi"
        }
        
        when {
            isAdult -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF2E0854), Color(0xFF4A0E4E))),
                Color(0xFFE040FB),
                if (isLocked) "PIN Doğrulaması Gereklidir" else if (isSafeSessionUnlocked) "Güvenli Oturum ile Açık" else defaultTagline
            )
            nameLower.contains("netflix") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF0F0F0F), Color(0xFF1F1F1F))),
                Color(0xFFE50914),
                if (isLive) "Netflix Canlı Yayın Kuşağı" else if (isMovie) "Netflix Özel Sinema Kuşağı" else if (isSeries) "Netflix Popüler Dizileri" else "Netflix Özel İçerikleri"
            )
            nameLower.contains("prime") || nameLower.contains("amazon") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))),
                Color(0xFF00A8E1),
                if (isLive) "Prime Canlı Yayınları" else if (isMovie) "Prime Video Özel Filmleri" else if (isSeries) "Prime Orijinal Dizileri" else "Prime Video Ayrıcalıklı İçerikleri"
            )
            nameLower.contains("disney") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF020024), Color(0xFF090979), Color(0xFF1E3A8A))),
                Color(0xFF38BDF8),
                if (isLive) "Disney Canlı Kanalları" else if (isMovie) "Disney+ Sihirli Filmleri" else if (isSeries) "Disney+ Orijinal Dizileri" else "Sihirli Dünyalar & Animasyonlar"
            )
            nameLower.contains("blu") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF030712), Color(0xFF1E3A8A))),
                Color(0xFF3B82F6),
                if (isLive) "BluTV Canlı Yayınları" else if (isMovie) "BluTV Özel Sinema Arşivi" else if (isSeries) "BluTV Özel Dizileri" else "Türkiye'nin İnternet Televizyonu"
            )
            nameLower.contains("exxen") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))),
                Color(0xFFFACC15),
                if (isLive) "Exxen Spor & Canlı Yayınlar" else if (isMovie) "Exxen Eğlenceli Filmleri" else if (isSeries) "Exxen Komedi & Drama Dizileri" else "Eğlenceli Şovlar & Yapımlar"
            )
            nameLower.contains("gain") || nameLower.contains("gaın") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF1C1917), Color(0xFF292524))),
                Color(0xFF22C55E),
                if (isLive) "Gain Canlı Yayın Kuşağı" else if (isMovie) "Gain Kaliteli Sinema Yapımları" else if (isSeries) "Gain Özgün Hikayeleri & Dizileri" else "Kısa ve Kaliteli Özgün Hikayeler"
            )
            nameLower.contains("apple") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF000000), Color(0xFF27272A))),
                Color.White,
                if (isLive) "Apple TV Canlı Yayınları" else if (isMovie) "Apple Orijinal Sinema Filmleri" else if (isSeries) "Apple Orijinal Dizileri" else "Ödüllü Yapımlar & Apple Orijinalleri"
            )
            nameLower.contains("bein") || nameLower.contains("connect") || nameLower.contains("tod") || nameLower.contains("spor") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF1E1B4B), Color(0xFF311042))),
                Color(0xFFD946EF),
                if (isLive) "Canlı Maçlar & Spor Kanalları" else if (isMovie) "TOD Premium Sinema Filmleri" else if (isSeries) "TOD Popüler Dizileri" else "Premium Spor & Eğlence Platformu"
            )
            nameLower.contains("belgesel") || nameLower.contains("docu") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF064E3B), Color(0xFF022C22))),
                Color(0xFF34D399),
                "Doğa, Bilim & Tarih Belgeselleri"
            )
            nameLower.contains("haber") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF450A0A), Color(0xFF1E0101))),
                Color(0xFFEF4444),
                "Güncel Haberler & Canlı Gelişmeler"
            )
            nameLower.contains("çocuk") || nameLower.contains("kids") || nameLower.contains("animasyon") -> Triple(
                Brush.horizontalGradient(colors = listOf(Color(0xFF1E1B4B), Color(0xFF1E3A8A))),
                Color(0xFFF472B6),
                "Eğlenceli Çocuk & Animasyon Kanalları"
            )
            else -> {
                val fallbackColor = when {
                    isLive -> NeonPink
                    isMovie -> NeonPink
                    isSeries -> ElectricBlue
                    else -> CineOrange
                }
                Triple(
                    Brush.horizontalGradient(colors = listOf(Color(0xFF180D2C), Color(0xFF27134A))),
                    fallbackColor,
                    defaultTagline
                )
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val targetScale = when {
        isPressed -> 0.95f
        isHovered -> 1.03f
        else -> 1.0f
    }

    val cardScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "apple_tv_folder_scale"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isHovered || isPressed) 14.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "apple_tv_folder_shadow"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(16.dp),
                spotColor = accentColor,
                ambientColor = accentColor
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                // Telefonda görünüm aynı (vurgu yok); TV'de kumanda odağı görünür.
                indication = com.example.ui.tv.tvFocusIndicationOrNull(),
                onClick = onClick
            )
            .border(
                width = if (isPressed || isHovered) 1.5.dp else 1.dp,
                brush = Brush.horizontalGradient(
                    colors = if (isPressed || isHovered) {
                        listOf(accentColor, accentColor.copy(alpha = 0.5f))
                    } else {
                        listOf(accentColor.copy(alpha = 0.3f), Color.Transparent)
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("folder_card_${group.name}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg)
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(44.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CategoryAdaptiveIcon(
                            category = group.name,
                            type = group.type,
                            size = 18.dp,
                            tint = accentColor,
                            containerColor = accentColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = group.name.uppercase(Locale.ROOT),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tagline,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAdaptiveIcon(
                            category = group.name,
                            type = group.type,
                            size = 14.dp,
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${group.items.size} ${if (isLive) "Kanal" else "Yayın"}",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (isLocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = stringResource(R.string.folder_locked_desc),
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = stringResource(R.string.folder_pin_badge),
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else if (isSafeSessionUnlocked) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF22C55E).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFF22C55E).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = stringResource(R.string.folder_unlocked_desc),
                                        tint = Color(0xFF86EFAC),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = stringResource(R.string.folder_open_badge),
                                        color = Color(0xFF86EFAC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.folder_detail_desc),
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun FolderDetailLayout(
    group: IPTVGroup,
    viewModel: IPTVViewModel,
    onPlayItem: (IPTVItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val isParentalLockEnabled by viewModel.parentalLock.collectAsState()
    val isSafeSessionActive by viewModel.isSafeSessionActive.collectAsState()
    val safeSessionRemainingSeconds by viewModel.safeSessionRemainingSeconds.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val favorites by viewModel.favorites.collectAsState()

    if (group.type == "LIVE") {
        val filteredItems = remember(group.items, searchQuery) {
            if (searchQuery.isBlank()) {
                group.items
            } else {
                group.items.filter { it.cleanedName.contains(searchQuery, ignoreCase = true) }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = adaptiveLayout.screenPadding)
                .testTag("premium_live_list")
        ) {
            // Header with Back Button - Scrolls with list
            item(key = "folder_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.folder_go_back_desc),
                            tint = Color(0xFFF5F5F7)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryAdaptiveIcon(
                                category = group.name,
                                type = "LIVE",
                                size = 18.dp,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = group.name.uppercase(java.util.Locale.ROOT),
                                color = Color(0xFFF5F5F7),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.folder_content_title),
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isParentalLockEnabled && isSafeSessionActive) {
                item(key = "live_detail_safe_session") {
                    SafeSessionIndicator(
                        remainingSeconds = safeSessionRemainingSeconds,
                        onLockClick = {
                            viewModel.lockSafeSession()
                            onBackClick()
                        }
                    )
                }
            }

            // Search Bar inside Folder - Scrolls with list
            item(key = "folder_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.folder_search_inside_placeholder), fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.folder_search_desc),
                            tint = Color(0xFF94A3B8)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
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
                        .padding(bottom = 4.dp)
                )
            }

            // EPG Info Area - Scrolls with list
            item(key = "folder_epg") {
                CompactEPGSection(
                    liveChannels = group.items,
                    onPlayChannel = { item -> onPlayItem(item) }
                )
            }

            // Category Title Header - Scrolls with list
            item(key = "folder_category_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryAdaptiveIcon(
                        category = group.name,
                        type = "LIVE",
                        size = 18.dp,
                        tint = NeonPink,
                        containerColor = NeonPink.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = group.name.uppercase(Locale.ROOT),
                        color = NeonPink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = NeonPink.copy(alpha = 0.3f)
                    )
                }
            }

            // Channel List
            if (filteredItems.isEmpty()) {
                item(key = "folder_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.folder_no_search_results),
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(filteredItems, key = { index, item -> "${item.id}_$index" }) { index, item ->
                    val isFav = favorites.any { it.id == item.id } || item.isFavorite
                    LiveChannelCard(
                        rank = index + 1,
                        item = item,
                        groupName = group.name,
                        isFavorite = isFav,
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        onClick = { onPlayItem(item) }
                    )
                }
            }
        }
    } else if (group.type == "SERIES") {
        val seriesCovers by viewModel.seriesCoversMap.collectAsState()
        val tvShows = remember(group.items, searchQuery, seriesCovers) {
            val filteredItems = if (searchQuery.isBlank()) {
                group.items
            } else {
                group.items.filter { it.cleanedName.contains(searchQuery, ignoreCase = true) }
            }
            SeriesParser.groupItemsIntoShows(filteredItems, seriesCovers)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveLayout.gridColumns),
            verticalArrangement = Arrangement.spacedBy(adaptiveLayout.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(adaptiveLayout.gridSpacing),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = adaptiveLayout.screenPadding)
                .testTag("series_folder_item_grid")
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "series_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.folder_go_back_desc),
                            tint = Color(0xFFF5F5F7)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryAdaptiveIcon(
                                category = group.name,
                                type = "SERIES",
                                size = 18.dp,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = group.name.uppercase(java.util.Locale.ROOT),
                                color = Color(0xFFF5F5F7),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.folder_content_title),
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isParentalLockEnabled && isSafeSessionActive) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "series_detail_safe_session") {
                    SafeSessionIndicator(
                        remainingSeconds = safeSessionRemainingSeconds,
                        onLockClick = {
                            viewModel.lockSafeSession()
                            onBackClick()
                        }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "series_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.folder_search_inside_placeholder), fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.folder_search_desc),
                            tint = Color(0xFF94A3B8)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
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
                        .padding(bottom = 8.dp)
                )
            }

            if (tvShows.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "series_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.folder_no_series_results),
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(tvShows, key = { index, show -> "${show.id}_$index" }) { _, show ->
                    TvShowFolderCard(
                        show = show,
                        onClick = {
                            val firstEp = show.seasons.firstOrNull()?.episodes?.firstOrNull()
                            if (firstEp != null) {
                                viewModel.selectItem(firstEp.item)
                            }
                        }
                    )
                }
            }
        }
    } else {
        // MOVIE items
        val filteredItems = remember(group.items, searchQuery) {
            if (searchQuery.isBlank()) {
                group.items
            } else {
                group.items.filter { it.cleanedName.contains(searchQuery, ignoreCase = true) }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveLayout.gridColumns),
            verticalArrangement = Arrangement.spacedBy(adaptiveLayout.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(adaptiveLayout.gridSpacing),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = adaptiveLayout.screenPadding)
                .testTag("folder_item_grid")
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "movie_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.folder_go_back_desc),
                            tint = Color(0xFFF5F5F7)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryAdaptiveIcon(
                                category = group.name,
                                type = "MOVIE",
                                size = 18.dp,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = group.name.uppercase(java.util.Locale.ROOT),
                                color = Color(0xFFF5F5F7),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = stringResource(R.string.folder_content_title),
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isParentalLockEnabled && isSafeSessionActive) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "movie_detail_safe_session") {
                    SafeSessionIndicator(
                        remainingSeconds = safeSessionRemainingSeconds,
                        onLockClick = {
                            viewModel.lockSafeSession()
                            onBackClick()
                        }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "movie_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.folder_search_inside_placeholder), fontSize = 14.sp, color = Color(0xFF94A3B8)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.folder_search_desc),
                            tint = Color(0xFF94A3B8)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
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
                        .padding(bottom = 8.dp)
                )
            }

            if (filteredItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "movie_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.folder_no_search_results),
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                itemsIndexed(filteredItems, key = { index, item -> "${item.id}_$index" }) { _, item ->
                    val isFav = favorites.any { it.id == item.id } || item.isFavorite
                    FolderItemCard(
                        item = item,
                        isFavorite = isFav,
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        onClick = {
                            if (item.type == "LIVE" || group.type == "LIVE") {
                                onPlayItem(item)
                            } else {
                                viewModel.selectItem(item)
                            }
                        },
                        onLongClick = { }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemCard(
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = item.isFavorite,
    modifier: Modifier = Modifier
) {
    val currentTheme = LocalAppTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "folder_item_card_scale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isHovered) 12.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "folder_item_card_shadow"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(shadowElevation, RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = 1.dp,
                color = if (currentTheme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("folder_item_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            ) {
                if (!item.logoUrl.isNullOrEmpty()) {
                    com.example.ui.components.SafeAsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.cleanedName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.type == "LIVE") Icons.Default.LiveTv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (onToggleFavorite != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.folder_favorite_desc),
                            tint = if (isFavorite) CineOrange else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (item.type == "LIVE") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CineOrange)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_live_badge),
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.cleanedName,
                    color = Color(0xFFF5F5F7), // Broken White
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.type == "LIVE") "Canlı Yayın" else "Film",
                    color = SlateGray, // High-contrast light Slate Gray
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
fun TvShowFolderCard(
    show: TvShow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTheme = LocalAppTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = when {
        isPressed -> 0.94f
        isHovered -> 1.05f
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tv_show_card_scale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isHovered || isPressed) 12.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 150f),
        label = "tv_show_card_shadow"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(shadowElevation, RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (currentTheme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("series_folder_show_${show.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            ) {
                if (!show.logoUrl.isNullOrEmpty()) {
                    com.example.ui.components.SafeAsyncImage(
                        model = show.logoUrl,
                        contentDescription = show.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = CineOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = show.title,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                val epCount = show.seasons.sumOf { it.episodes.size }
                if (epCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$epCount Bölüm",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = show.title,
                    color = Color(0xFFF5F5F7), // Broken White
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (show.seasons.isNotEmpty()) "${show.seasons.size} Sezon" else show.category.ifBlank { "Dizi" },
                    color = SlateGray, // High-contrast light Slate Gray
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
fun LiveChannelCard(
    rank: Int,
    item: IPTVItem,
    groupName: String,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = item.isFavorite,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardPurpleBg),
        border = BorderStroke(1.dp, Color(0x33F59E0B)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("live_channel_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number in NeonPink
            Text(
                text = String.format("%02d", rank),
                color = NeonPink,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(end = 10.dp)
            )

            // Circular Channel Logo with thin magenta border
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, NeonPink.copy(alpha = 0.5f), CircleShape)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logoUrl.isNullOrEmpty()) {
                    com.example.ui.components.SafeAsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.cleanedName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = NeonPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel name and subtext with multi-line support
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.cleanedName,
                    color = WhiteText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@$groupName • Canlı",
                    color = SlateGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Small fire indicator and viewers
            val viewers = remember(item.id) { (item.id.hashCode() % 350 + 150).coerceAtLeast(120) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = NeonPink,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$viewers",
                    color = NeonPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Favorite Heart Icon Button
            if (onToggleFavorite != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("live_fav_button_${item.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.folder_favorite_desc),
                        tint = if (isFavorite) CineOrange else WhiteText.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompactEPGSection(
    liveChannels: List<IPTVItem>,
    onPlayChannel: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: com.example.ui.IPTVViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    if (liveChannels.isEmpty()) return

    // UX FIX: Bu bölüm eskiden 120dp+ yükseklikte, kanal alanını gereksiz yere
    // kaplayan ve zaman zaman boş/kırık kartlar gösteren bir yatay carousel'di.
    // Artık tek satırlık, dönen (rotating) bir "Şu An Ekranda" bandı: ~52dp yer
    // kaplıyor, kanal klasör listesine çok daha fazla alan bırakıyor. Tıklanınca
    // uygulamada zaten var olan tam ekran EPG rehberini (VerticalEPGComponent)
    // bir diyalog içinde açıyor, böylece hiçbir işlevsellik kaybı olmuyor.
    val epgList = remember(liveChannels) {
        EPGProvider.generateEPGForChannels(liveChannels)
    }

    val nowPlayingEntries = remember(epgList) {
        epgList.mapNotNull { channelEpg ->
            val prog = channelEpg.currentProgram ?: return@mapNotNull null
            channelEpg.channel to prog.title
        }
    }

    if (nowPlayingEntries.isEmpty()) return

    var tickerIndex by remember(nowPlayingEntries) { mutableStateOf(0) }
    LaunchedEffect(nowPlayingEntries) {
        while (isActive) {
            delay(4000L)
            tickerIndex = (tickerIndex + 1) % nowPlayingEntries.size
        }
    }

    var currentTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (isActive) {
            currentTimeStr = sdf.format(Date())
            delay(1000L)
        }
    }

    var showFullEpg by remember { mutableStateOf(false) }
    val (activeChannel, activeProgramTitle) = nowPlayingEntries[tickerIndex % nowPlayingEntries.size]

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardPurpleBg.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { showFullEpg = true }
            .testTag("compact_epg_section")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(CineOrange)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (!activeChannel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = activeChannel.logoUrl,
                        contentDescription = activeChannel.cleanedName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = activeChannel.cleanedName.take(1).uppercase(),
                        color = CineOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeChannel.cleanedName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Şimdi: $activeProgramTitle",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (currentTimeStr.isNotEmpty()) {
                Text(
                    text = currentTimeStr,
                    color = CineOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.folder_view_full_epg),
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showFullEpg) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullEpg = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0518))
            ) {
                val realEpgPrograms by viewModel.realEpgPrograms.collectAsState()
                com.example.ui.components.VerticalEPGComponent(
                    liveChannels = liveChannels,
                    onPlayChannel = { channel ->
                        showFullEpg = false
                        onPlayChannel(channel)
                    },
                    onSelectChannelDetails = { },
                    realEpgPrograms = realEpgPrograms,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { showFullEpg = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
