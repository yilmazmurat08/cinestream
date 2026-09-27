package com.example.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.CategoryAdaptiveIcon
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.IPTVItem
import com.example.player.ExoPlayerConfigurator
import com.example.ui.IPTVViewModel

import com.example.ui.theme.rememberAppAdaptiveLayout

@Composable
fun MultiScreen(
    iptvViewModel: IPTVViewModel,
    multiScreenViewModel: MultiScreenViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val currentLayout by multiScreenViewModel.currentLayout.collectAsState()
    val slotItems by multiScreenViewModel.slotItems.collectAsState()
    val focusedSlotIndex by multiScreenViewModel.focusedSlotIndex.collectAsState()
    val activeSelectorSlot by multiScreenViewModel.activeSelectorSlot.collectAsState()

    val multiScreenContext = LocalContext.current
    DisposableEffect(Unit) {
        com.example.player.PlaybackForegroundService.start(multiScreenContext, "Çoklu Ekran Yayını")
        onDispose {
            com.example.player.PlaybackForegroundService.stop(multiScreenContext)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("multi_screen_scaffold"),
        containerColor = Color(0xFF0D0E12)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentLayout) {
                MultiScreenLayout.SELECT_TEMPLATE -> {
                    TemplateSelectionScreen(
                        onSelectLayout = { multiScreenViewModel.selectLayout(it) },
                        onBack = onBack
                    )
                }
                else -> {
                    ActiveMultiViewScreen(
                        currentLayout = currentLayout,
                        slotItems = slotItems,
                        focusedSlotIndex = focusedSlotIndex,
                        iptvViewModel = iptvViewModel,
                        multiScreenViewModel = multiScreenViewModel,
                        onBackToTemplate = { multiScreenViewModel.resetToTemplateSelection() }
                    )
                }
            }

            // Channel Selector Dialog Dialog Overlay
            activeSelectorSlot?.let { slotIndex ->
                ChannelSelectorDialog(
                    iptvViewModel = iptvViewModel,
                    onDismiss = { multiScreenViewModel.closeChannelSelector() },
                    onChannelSelected = { item ->
                        multiScreenViewModel.setChannelForSlot(slotIndex, item)
                    }
                )
            }
        }
    }
}

@Composable
fun TemplateSelectionScreen(
    onSelectLayout: (MultiScreenLayout) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back Button & Title Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Çoklu Ekran İzleme",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aynı anda birden fazla yayını canlı takip edin",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "YAYIN ŞABLONU SEÇİN",
            color = Color(0xFFE50914),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Layout Option 1: Side by Side (2 columns)
        TemplateCard(
            title = "İkili Ekran (Yan Yana)",
            description = "Ekranı dikeyde ikiye böler, maç ve haberleri yan yana takip etmek için idealdir.",
            icon = Icons.Default.VerticalSplit,
            onClick = { onSelectLayout(MultiScreenLayout.DUAL_SIDE_BY_SIDE) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Layout Option 2: Top / Bottom (2 rows)
        TemplateCard(
            title = "İkili Ekran (Alt Alta)",
            description = "Ekranı yatayda ikiye böler. Sinema ve spor kanalları için geniş açılı izleme sunar.",
            icon = Icons.Default.HorizontalSplit,
            onClick = { onSelectLayout(MultiScreenLayout.DUAL_TOP_BOTTOM) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Layout Option 3: 4-Way Grid (Quad grid)
        TemplateCard(
            title = "Dörtlü Ekran (Izgara)",
            description = "Ekranı 4 eşit hücreye böler. Tüm IPTV yayın akışını tek bir yerden kontrol edin.",
            icon = Icons.Default.GridView,
            onClick = { onSelectLayout(MultiScreenLayout.QUAD_GRID) }
        )
    }
}

@Composable
fun TemplateCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15161E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFFE50914).copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ActiveMultiViewScreen(
    currentLayout: MultiScreenLayout,
    slotItems: List<IPTVItem?>,
    focusedSlotIndex: Int,
    iptvViewModel: IPTVViewModel,
    multiScreenViewModel: MultiScreenViewModel,
    onBackToTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Upper Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackToTemplate,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = "Şablon Değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Çoklu Canlı İzleme",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ses odaklı hücrenin ses düzeyi açıktır.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            // Quick reset button
            TextButton(
                onClick = { multiScreenViewModel.resetToTemplateSelection() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE50914))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sıfırla", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cellular Warning indicator
        val context = LocalContext.current
        val isMobile = remember { ExoPlayerConfigurator.isConnectedToCellular(context) }
        if (isMobile) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE50914).copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCell,
                        contentDescription = null,
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Mobil veri bağlantısı aktif. Aynı anda birden çok yayın yüksek miktarda veri tüketebilir.",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Multi View Body based on Selected Layout
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentLayout) {
                MultiScreenLayout.DUAL_SIDE_BY_SIDE -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MultiScreenCell(
                                slotIndex = 0,
                                item = slotItems[0],
                                isFocused = focusedSlotIndex == 0,
                                iptvViewModel = iptvViewModel,
                                multiScreenViewModel = multiScreenViewModel
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MultiScreenCell(
                                slotIndex = 1,
                                item = slotItems[1],
                                isFocused = focusedSlotIndex == 1,
                                iptvViewModel = iptvViewModel,
                                multiScreenViewModel = multiScreenViewModel
                            )
                        }
                    }
                }
                MultiScreenLayout.DUAL_TOP_BOTTOM -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MultiScreenCell(
                                slotIndex = 0,
                                item = slotItems[0],
                                isFocused = focusedSlotIndex == 0,
                                iptvViewModel = iptvViewModel,
                                multiScreenViewModel = multiScreenViewModel
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MultiScreenCell(
                                slotIndex = 1,
                                item = slotItems[1],
                                isFocused = focusedSlotIndex == 1,
                                iptvViewModel = iptvViewModel,
                                multiScreenViewModel = multiScreenViewModel
                            )
                        }
                    }
                }
                MultiScreenLayout.QUAD_GRID -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                MultiScreenCell(
                                    slotIndex = 0,
                                    item = slotItems[0],
                                    isFocused = focusedSlotIndex == 0,
                                    iptvViewModel = iptvViewModel,
                                    multiScreenViewModel = multiScreenViewModel
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                MultiScreenCell(
                                    slotIndex = 1,
                                    item = slotItems[1],
                                    isFocused = focusedSlotIndex == 1,
                                    iptvViewModel = iptvViewModel,
                                    multiScreenViewModel = multiScreenViewModel
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                MultiScreenCell(
                                    slotIndex = 2,
                                    item = slotItems[2],
                                    isFocused = focusedSlotIndex == 2,
                                    iptvViewModel = iptvViewModel,
                                    multiScreenViewModel = multiScreenViewModel
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                MultiScreenCell(
                                    slotIndex = 3,
                                    item = slotItems[3],
                                    isFocused = focusedSlotIndex == 3,
                                    iptvViewModel = iptvViewModel,
                                    multiScreenViewModel = multiScreenViewModel
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun MultiScreenCell(
    slotIndex: Int,
    item: IPTVItem?,
    isFocused: Boolean,
    iptvViewModel: IPTVViewModel,
    multiScreenViewModel: MultiScreenViewModel
) {
    // Glowing border transition for audio-focused player
    val borderGlowColor by animateColorAsState(
        targetValue = if (isFocused && item != null) Color(0xFFE50914) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(durationMillis = 300)
    )

    val borderThickness by animateDpAsState(
        targetValue = if (isFocused && item != null) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 300)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF15161E))
            .border(borderThickness, borderGlowColor, RoundedCornerShape(16.dp))
            .clickable {
                if (item != null) {
                    multiScreenViewModel.setAudioFocus(slotIndex)
                }
            }
    ) {
        if (item == null) {
            // Empty State: Add Button
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { multiScreenViewModel.openChannelSelector(slotIndex) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Kanal Ekle",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kanal Ekle",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Video Player view
            MultiScreenPlayerView(
                streamUrl = item.streamUrl,
                isMuted = !isFocused,
                viewModel = iptvViewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Audio Focus indicator overlay (Top Left)
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Surface(
                    color = if (isFocused) Color(0xFFE50914) else Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFocused) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (isFocused) "Ses Açık" else "Sessiz",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFocused) "SES AKTİF" else "SESSİZ",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Cell Management Overlay (Always visible top-right controls)
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Change channel button
                IconButton(
                    onClick = { multiScreenViewModel.openChannelSelector(slotIndex) },
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Kanal Değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Close / Remove channel button
                IconButton(
                    onClick = { multiScreenViewModel.removeChannelFromSlot(slotIndex) },
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Channel label on bottom overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!item.logoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.category,
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MultiScreenPlayerView(
    streamUrl: String,
    isMuted: Boolean,
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hardwareAcceleration by viewModel.hardwareAcceleration.collectAsState()
    val bufferSize by viewModel.bufferSize.collectAsState()
    val reduceCellularQuality by viewModel.reduceCellularQuality.collectAsState()

    // Initialize/release player carefully based on configuration
    val player = remember(streamUrl, hardwareAcceleration, bufferSize, reduceCellularQuality) {
        ExoPlayerConfigurator.buildConfiguredPlayer(
            context = context,
            hardwareAcceleration = hardwareAcceleration,
            bufferSize = bufferSize,
            reduceCellularQuality = reduceCellularQuality
        ).apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            playWhenReady = true
            volume = if (isMuted) 0.0f else 1.0f

            val mediaItem = ExoPlayerConfigurator.buildMediaItemForUrl(streamUrl)
            setMediaItem(mediaItem)
            prepare()
        }
    }

    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0.0f else 1.0f
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    player.playWhenReady = false
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                keepScreenOn = true
            }
        },
        update = { playerView ->
            playerView.player = player
        },
        modifier = modifier
    )
}

@Composable
fun ChannelSelectorDialog(
    iptvViewModel: IPTVViewModel,
    onDismiss: () -> Unit,
    onChannelSelected: (IPTVItem) -> Unit
) {
    val liveChannels by iptvViewModel.liveChannels.collectAsState()
    val movies by iptvViewModel.movies.collectAsState()
    val series by iptvViewModel.series.collectAsState()

    // Compile everything into a unified list
    val allChannels = remember(liveChannels, movies, series) {
        liveChannels + movies + series
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("Tümü") }

    // Dynamic categories extracted from our combined list
    val categories = remember(allChannels) {
        val raw = allChannels.map { it.category }.distinct().filter { it.isNotBlank() }
        listOf("Tümü") + raw.sorted()
    }

    // Perform interactive filtering
    val filteredChannels = remember(allChannels, searchQuery, selectedCategoryTab) {
        allChannels.filter { channel ->
            val matchesQuery = channel.name.contains(searchQuery, ignoreCase = true) ||
                    channel.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryTab == "Tümü" || channel.category.equals(selectedCategoryTab, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
            color = Color(0xFF15161E),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header of Dialogue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Kanal Ekle/Değiştir",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Modern visual Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0E12))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Kanal veya kategori ara...",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFFE50914)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Temizle",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Category Selection list
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it }) { category ->
                        val isSelected = selectedCategoryTab == category
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFFE50914) else Color(0xFF0D0E12))
                                .border(1.dp, if (isSelected) Color(0xFFE50914) else Color(0xFF1E293B), RoundedCornerShape(20.dp))
                                .clickable { selectedCategoryTab = category }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            CategoryAdaptiveIcon(
                                category = category,
                                type = "LIVE",
                                size = 12.dp,
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = category,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Channels List
                if (filteredChannels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sonuç bulunamadı",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredChannels, key = { it.id }) { channel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0D0E12))
                                    .clickable { onChannelSelected(channel) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel Thumbnail Logo
                                AsyncImage(
                                    model = channel.logoUrl,
                                    contentDescription = channel.name,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = channel.category,
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE50914).copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "SEÇ",
                                        color = Color(0xFFE50914),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
