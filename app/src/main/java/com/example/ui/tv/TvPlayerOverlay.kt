package com.example.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel

/**
 * TV oynatıcı arayüz durumu. Kanal değişince oynatıcı yeniden kurulduğu için bir üst seviyede tutulur: kanal
 * panelinden kanal seçilince panel açık kalır; Yukarı/Aşağı ile geçişte yalnızca bilgi bandı görünür.
 */
class TvPlayerUiState {
    var panelOpen by mutableStateOf(false)
    var categoriesOpen by mutableStateOf(false)
    /** Panelde gösterilen kategori (null = izlenen kanalın kategorisi). */
    var panelCategory by mutableStateOf<String?>(null)
    /** Sıradaki oynatıcı açılışının nedeni: [LAUNCH_ENTER], [LAUNCH_ZAP], [LAUNCH_CONTROLS] veya null. */
    var launch by mutableStateOf<String?>(null)

    companion object {
        /** Canlı TV'ye giriş: kanal paneli ve kontrol çubuğu açık, odak izlenen kanalda. */
        const val LAUNCH_ENTER = "ENTER"
        /** Yukarı/Aşağı, CH+/CH−, numara: kontroller gizli, 3 sn bilgi bandı. */
        const val LAUNCH_ZAP = "ZAP"
        /** Kontrol çubuğundaki önceki/sonraki: kontroller açık kalır. */
        const val LAUNCH_CONTROLS = "CONTROLS"
    }
}

/** Kanal numarası (panelde "numara - ad"): kanalın kendi klasöründeki sırası. */
internal fun channelNumber(ring: List<IPTVItem>, item: IPTVItem): Int = ring.indexOfFirst { it.id == item.id } + 1

/**
 * Kontroller gizliyken kumanda: Canlı'da Yukarı/CH+ sonraki, Aşağı/CH− önceki kanal, Sol kanal paneli, OK
 * kontroller (odak oynat/duraklat), rakamlar kanala atlar. Filmde OK oynat/duraklat + kontroller, Sol/Sağ ±10 sn,
 * Aşağı kontroller. Medya tuşları, kanal tuşları ve ses tuşları her zaman çalışır.
 */
fun Modifier.tvModePlayerKeys(
    isLive: Boolean,
    overlayHidden: () -> Boolean,
    volumeFixed: Boolean,
    onShowControls: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onChannelStep: (Int) -> Unit,
    onOpenPanel: () -> Unit,
    onDigit: (Int) -> Unit,
    onVolume: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onUserActivity: () -> Unit
): Modifier = this.onPreviewKeyEvent { event ->
    val down = event.type == KeyEventType.KeyDown
    val key = event.key
    // Her zaman çalışan tuşlar
    when (key) {
        Key.VolumeUp, Key.VolumeDown -> {
            if (volumeFixed) return@onPreviewKeyEvent false // sabit seste sistem yönetsin
            if (down) onVolume(if (key == Key.VolumeUp) 1 else -1)
            return@onPreviewKeyEvent true
        }
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (down && event.nativeKeyEvent.repeatCount == 0) onPlayPause(); return@onPreviewKeyEvent true }
        Key.ChannelUp -> { if (isLive && down) onChannelStep(1); return@onPreviewKeyEvent isLive }
        Key.ChannelDown -> { if (isLive && down) onChannelStep(-1); return@onPreviewKeyEvent isLive }
        Key.MediaNext -> { if (down) onNext(); return@onPreviewKeyEvent true }
        Key.MediaPrevious -> { if (down) onPrevious(); return@onPreviewKeyEvent true }
        Key.MediaFastForward -> { if (!isLive && down) onSeek(10_000); return@onPreviewKeyEvent !isLive }
        Key.MediaRewind -> { if (!isLive && down) onSeek(-10_000); return@onPreviewKeyEvent !isLive }
        else -> {}
    }
    val digit = when (key) {
        Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9; else -> -1
    }
    if (digit >= 0 && isLive) {
        if (down) onDigit(digit)
        return@onPreviewKeyEvent true
    }
    if (!overlayHidden()) {
        if (down) onUserActivity()
        return@onPreviewKeyEvent false // açık panel/kontroller tuşları kendisi işler
    }
    val isOk = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
    val handled = when {
        isOk -> true
        isLive && key == Key.DirectionUp -> true
        isLive && key == Key.DirectionDown -> true
        isLive && key == Key.DirectionLeft -> true
        isLive && key == Key.DirectionRight -> true
        !isLive && (key == Key.DirectionLeft || key == Key.DirectionRight || key == Key.DirectionDown || key == Key.DirectionUp) -> true
        else -> false
    }
    if (!handled) return@onPreviewKeyEvent false
    // OK işlemi tuş bırakılınca (tuş tekrarında art arda tetiklenmesin); yönler basılınca.
    if (isOk) {
        if (event.type == KeyEventType.KeyUp) {
            if (isLive) onShowControls() else { onPlayPause(); onShowControls() }
        }
        return@onPreviewKeyEvent true
    }
    if (!down) return@onPreviewKeyEvent true
    when {
        isLive && key == Key.DirectionUp -> onChannelStep(1)
        isLive && key == Key.DirectionDown -> onChannelStep(-1)
        isLive && key == Key.DirectionLeft -> onOpenPanel()
        isLive && key == Key.DirectionRight -> onShowControls()
        key == Key.DirectionLeft -> onSeek(-10_000)
        key == Key.DirectionRight -> onSeek(10_000)
        else -> onShowControls()
    }
    true
}

/**
 * TV oynatıcı katmanı (Canlı ve film/dizi aynı tasarım): solda cam kanal paneli (Sol ile kategori listesi),
 * altta cam kontrol çubuğu, sağ altta kanal bilgi kartı, sağda ses çubuğu, sağ üstte girilen kanal numarası.
 * Blur yerine yarı saydam koyu cam katmanlar kullanılır (video yüzeyi değiştirilmez).
 */
@Composable
fun TvPlayerOverlay(
    viewModel: IPTVViewModel,
    item: IPTVItem,
    ui: TvPlayerUiState,
    ring: List<IPTVItem>,
    allLive: List<IPTVItem>,
    vodList: List<IPTVItem>,
    controlsVisible: Boolean,
    isPlaying: Boolean,
    positionSec: Long,
    durationSec: Long,
    infoBandVisible: Boolean,
    volumeLevel: Float?,
    digits: String,
    aspectBadge: String?,
    playPauseFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStartOver: () -> Unit,
    onPlayFromPanel: (IPTVItem) -> Unit,
    onStep: (Int) -> Unit,
    onNextEpisode: (() -> Unit)?,
    onPreviousEpisode: (() -> Unit)?,
    onOpenTracks: () -> Unit,
    onCycleAspect: () -> Unit,
    onUserActivity: () -> Unit
) {
    val isLive = item.type == "LIVE"
    val parentalLock by viewModel.parentalLock.collectAsState()
    val safeSession by viewModel.isSafeSessionActive.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val epg = remember(item.id, positionSec / 30) { if (isLive) viewModel.tvEpgNowNext(item) else null to null }
    var pinCategory by remember { mutableStateOf<String?>(null) }

    // Geri: önce kategori listesi, sonra kanal paneli kapanır (sonra kontroller, sonra çıkış).
    BackHandler(enabled = ui.categoriesOpen) { ui.categoriesOpen = false }
    BackHandler(enabled = ui.panelOpen && !ui.categoriesOpen) {
        ui.panelOpen = false
        ui.panelCategory = null
    }

    Box(Modifier.fillMaxSize().testTag("tv_player_overlay")) {
        Row(Modifier.fillMaxSize()) {
            // Kategori listesi (kanal panelinde Sol)
            AnimatedVisibility(ui.panelOpen && ui.categoriesOpen, enter = fadeIn() + slideInHorizontally { -it }, exit = fadeOut() + slideOutHorizontally { -it }) {
                val categories = remember(allLive) { allLive.map { it.category }.distinct().sortedBy { it.lowercase() } }
                TvPlayerCategories(
                    categories = categories,
                    current = ui.panelCategory ?: item.category,
                    isLocked = { cat -> parentalLock && !safeSession && viewModel.isAdultContent(cat) },
                    onSelect = { cat ->
                        if (parentalLock && !safeSession && viewModel.isAdultContent(cat)) {
                            pinCategory = cat
                        } else {
                            ui.panelCategory = cat
                            ui.categoriesOpen = false
                        }
                    },
                    onUserActivity = onUserActivity
                )
            }
            // Kanal paneli (Canlı) / bölümler-öneriler paneli (film/dizi)
            AnimatedVisibility(ui.panelOpen, enter = fadeIn() + slideInHorizontally { -it }, exit = fadeOut() + slideOutHorizontally { -it }) {
                val category = ui.panelCategory ?: item.category
                val list = when {
                    !isLive -> vodList.take(300)
                    ui.panelCategory == null || ui.panelCategory == item.category -> ring
                    else -> remember(allLive, category) {
                        allLive.filter { it.category.equals(category, ignoreCase = true) }
                    }
                }
                TvChannelPanel(
                    viewModel = viewModel,
                    title = if (isLive) category.ifBlank { stringResource(R.string.tv_live) } else stringResource(R.string.player_lists),
                    items = list,
                    playingId = item.id,
                    isLive = isLive,
                    categoriesOpen = ui.categoriesOpen,
                    onOpenCategories = { if (isLive) ui.categoriesOpen = true },
                    onSelect = { selected ->
                        if (selected.id == item.id) {
                            ui.panelOpen = false
                            ui.panelCategory = null
                        } else {
                            onPlayFromPanel(selected)
                        }
                    },
                    onUserActivity = onUserActivity
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Kanal bilgi kartı (sağ alt): kontroller açıkken ya da kanal geçişinde 3 sn
                if (isLive && (controlsVisible || infoBandVisible)) {
                    TvChannelInfoCard(
                        item = item,
                        number = channelNumber(ring, item),
                        now = epg.first,
                        next = epg.second,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 36.dp, bottom = if (controlsVisible) 190.dp else 36.dp)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    TvControlBar(
                        item = item,
                        isLive = isLive,
                        number = channelNumber(ring, item),
                        isPlaying = isPlaying,
                        positionSec = positionSec,
                        durationSec = durationSec,
                        now = epg.first,
                        isSaved = favorites.any { it.id == item.id },
                        playPauseFocus = playPauseFocus,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onStartOver = onStartOver,
                        onPrevious = { if (isLive) onStep(-1) else onPreviousEpisode?.invoke() },
                        onNext = { if (isLive) onStep(1) else onNextEpisode?.invoke() },
                        hasPrevious = isLive || onPreviousEpisode != null,
                        hasNext = isLive || onNextEpisode != null,
                        onOpenPanel = { ui.panelOpen = true },
                        compact = ui.panelOpen,
                        onOpenTracks = onOpenTracks,
                        onCycleAspect = onCycleAspect,
                        onToggleSaved = { viewModel.toggleFavorite(favorites.firstOrNull { it.id == item.id } ?: item) },
                        onUserActivity = onUserActivity
                    )
                }
                // Film/dizide kontroller gizliyken Sol/Sağ ile sarınca kısa süre konum görünür
                if (!isLive && infoBandVisible && !controlsVisible && durationSec > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 36.dp, vertical = 28.dp)
                            .fillMaxWidth()
                            .tvGlass(TvTheme.PillShape, TvTheme.GlassDark)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .testTag("tv_seek_band")
                    ) {
                        Text(formatClock(positionSec), color = TvTheme.TextPrimary, fontSize = 14.sp)
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(4.dp).clip(TvTheme.PillShape).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxWidth((positionSec.toFloat() / durationSec).coerceIn(0f, 1f)).fillMaxHeight().background(TvTheme.Accent))
                        }
                        Text(formatClock(durationSec), color = TvTheme.TextPrimary, fontSize = 14.sp)
                    }
                }
                aspectBadge?.let {
                    Text(
                        it, color = TvTheme.TextPrimary, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
                            .tvGlass(TvTheme.PillShape, TvTheme.GlassDark).padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
                // Girilen kanal numarası
                if (digits.isNotEmpty()) {
                    Text(
                        digits,
                        color = TvTheme.TextPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(36.dp)
                            .tvGlass(TvTheme.RowShape, TvTheme.GlassDark)
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                            .testTag("tv_player_digits")
                    )
                }
                // Ses çubuğu (STREAM_MUSIC)
                if (volumeLevel != null) {
                    TvVolumeBar(volumeLevel, Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
                }
            }
        }
    }

    pinCategory?.let { cat ->
        com.example.ui.components.ParentalPinDialog(
            viewModel = viewModel,
            title = stringResource(R.string.folder_pin_title),
            subtitle = stringResource(R.string.folder_pin_subtitle),
            onDismiss = { pinCategory = null },
            onSuccess = {
                viewModel.unlockSafeSession(15)
                ui.panelCategory = cat
                ui.categoriesOpen = false
                pinCategory = null
            }
        )
    }
}

@Composable
private fun TvPlayerCategories(
    categories: List<String>,
    current: String,
    isLocked: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onUserActivity: () -> Unit
) {
    val listState = rememberLazyListState()
    val requesters = remember(categories) { categories.associateWith { FocusRequester() } }
    LaunchedEffect(categories) {
        val index = categories.indexOfFirst { it.equals(current, ignoreCase = true) }.coerceAtLeast(0)
        listState.scrollToItem(index)
        categories.getOrNull(index)?.let { requesters[it]?.let { r -> requestFocusWhenReady(r) } }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(TvTheme.GlassDark)
            .testTag("tv_player_categories")
    ) {
        itemsIndexed(categories, key = { _, c -> c }) { _, category ->
            val selected = category.equals(current, ignoreCase = true)
            TvGlassButton(
                onClick = { onSelect(category) },
                onFocused = onUserActivity,
                shape = TvTheme.RowShape,
                focusRequester = requesters[category],
                focusScale = 1.03f,
                glassColor = if (selected) TvTheme.Accent.copy(alpha = 0.3f) else Color.Transparent,
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    if (isLocked(category)) {
                        Icon(Icons.Filled.Lock, null, tint = TvTheme.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(category.ifBlank { "—" }, color = TvTheme.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvChannelPanel(
    viewModel: IPTVViewModel,
    title: String,
    items: List<IPTVItem>,
    playingId: Int,
    isLive: Boolean,
    categoriesOpen: Boolean,
    onOpenCategories: () -> Unit,
    onSelect: (IPTVItem) -> Unit,
    onUserActivity: () -> Unit
) {
    val listState = rememberLazyListState()
    val requesters = remember(items) { HashMap<Int, FocusRequester>() }
    val continueWatching by viewModel.continueWatching.collectAsState()
    fun requester(id: Int) = requesters.getOrPut(id) { FocusRequester() }
    // Panel açılınca (ve kategori değişince) odak izlenen kanalda, yoksa listenin başında.
    LaunchedEffect(items, categoriesOpen) {
        if (categoriesOpen || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.id == playingId }.let { if (it < 0) 0 else it }
        listState.scrollToItem((index - 3).coerceAtLeast(0))
        requestFocusWhenReady(requester(items[index].id))
    }
    Column(
        Modifier
            .width(380.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(TvTheme.GlassDark, TvTheme.GlassDark.copy(alpha = 0.7f))))
            .padding(top = 24.dp)
            .testTag("tv_channel_panel")
    ) {
        Text(title, color = TvTheme.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
        if (isLive) {
            Text(stringResource(R.string.tv_panel_left_hint), color = TvTheme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items, key = { _, c -> c.id }) { index, channel ->
                val playing = channel.id == playingId
                val now = remember(channel.id) { if (isLive) viewModel.tvEpgNowNext(channel).first?.title else null }
                TvGlassButton(
                    onClick = { onSelect(channel) },
                    onFocused = onUserActivity,
                    shape = TvTheme.RowShape,
                    focusRequester = requester(channel.id),
                    focusScale = 1.03f,
                    glassColor = if (playing) TvTheme.Accent.copy(alpha = 0.3f) else Color.Transparent,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .focusProperties { if (isLive) left = FocusRequester.Cancel }
                        .onPreviewKeyEvent { e ->
                            if (isLive && e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) {
                                onOpenCategories()
                                true
                            } else false
                        }
                        .testTag("tv_channel_${channel.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Box(Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center) {
                            if (!channel.logoUrl.isNullOrBlank()) {
                                AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            // Dizi bölümlerinde ad yerine "S1 B3 · bölüm adı" (Xtream'de ad yalnızca dizi adıdır)
                            val episode = remember(channel.id) {
                                if (!isLive && channel.type == "SERIES") com.example.data.model.SeriesParser.episodeInfoOf(channel) else null
                            }
                            val label = when {
                                isLive -> "${index + 1} - ${channel.cleanedName.ifBlank { channel.name }}"
                                episode != null -> stringResource(R.string.tv_episode_label, episode.season, episode.episode) +
                                    episode.episodeName.takeIf { it.isNotBlank() && !it.equals(episode.showTitle, true) }?.let { " · $it" }.orEmpty()
                                else -> channel.cleanedName.ifBlank { channel.name }
                            }
                            Text(
                                label,
                                color = TvTheme.TextPrimary, fontSize = 16.sp,
                                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (now != null) Text(now, color = TvTheme.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!isLive) {
                                val cw = continueWatching.firstOrNull { it.itemId == channel.id }
                                val progress = cw?.let { if (it.totalSeconds > 0) it.progressSeconds.toFloat() / it.totalSeconds else null }
                                TvWatchedLine(progress)
                            }
                        }
                        if (playing) Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = TvTheme.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private fun formatClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
private fun TvControlBar(
    item: IPTVItem,
    isLive: Boolean,
    number: Int,
    isPlaying: Boolean,
    positionSec: Long,
    durationSec: Long,
    now: EPGProgram?,
    isSaved: Boolean,
    playPauseFocus: FocusRequester,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStartOver: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onOpenPanel: () -> Unit,
    onOpenTracks: () -> Unit,
    onCycleAspect: () -> Unit,
    onToggleSaved: () -> Unit,
    onUserActivity: () -> Unit,
    /** Kanal paneli açıkken çubuk dar: yalnızca ana düğmeler (diğerleri panel kapanınca görünür). */
    compact: Boolean = false
) {
    val episode = remember(item.id) {
        if (item.type == "SERIES") com.example.data.model.SeriesParser.episodeInfoOf(item) else null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 24.dp)
            .tvGlass(TvTheme.CardShape, TvTheme.GlassDark)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("tv_control_bar")
    ) {
        // Başlık satırı
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    isLive && number > 0 -> "$number - ${item.cleanedName.ifBlank { item.name }}"
                    episode != null -> episode.showTitle
                    else -> item.cleanedName.ifBlank { item.name }
                },
                color = TvTheme.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (episode != null) {
                Text(stringResource(R.string.tv_season_episode, episode.season, episode.episode), color = TvTheme.TextSecondary, fontSize = 15.sp)
            }
        }
        // İlerleme: filmde konum, canlıda EPG (yoksa gizli)
        val progress: Float? = when {
            !isLive && durationSec > 0 -> (positionSec.toFloat() / durationSec).coerceIn(0f, 1f)
            isLive && now != null -> ((System.currentTimeMillis() - now.startEpochMillis).toFloat() / (now.endEpochMillis - now.startEpochMillis).coerceAtLeast(1)).coerceIn(0f, 1f)
            else -> null
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isLive) now!!.startTimeFormatted else formatClock(positionSec), color = TvTheme.TextSecondary, fontSize = 13.sp)
                Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(4.dp).clip(TvTheme.PillShape).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(TvTheme.Accent))
                }
                Text(if (isLive) now!!.endTimeFormatted else formatClock(durationSec), color = TvTheme.TextSecondary, fontSize = 13.sp)
            }
            if (isLive) Text(now!!.title, color = TvTheme.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvBarButton(Icons.Filled.SkipPrevious, stringResource(if (isLive) R.string.player_previous_channel_desc else R.string.player_previous_episode_desc), "tv_bar_prev", enabled = hasPrevious, onClick = onPrevious, onUserActivity = onUserActivity)
            TvBarButton(Icons.Filled.FastRewind, stringResource(R.string.player_seek_back_10), "tv_bar_rew", enabled = !isLive, onClick = { onSeek(-10_000) }, onUserActivity = onUserActivity)
            TvBarButton(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (isPlaying) R.string.player_pause else R.string.player_play_desc), "tv_bar_play", focusRequester = playPauseFocus, big = true, onClick = onPlayPause, onUserActivity = onUserActivity)
            TvBarButton(Icons.Filled.FastForward, stringResource(R.string.player_seek_forward_10), "tv_bar_ff", enabled = !isLive, onClick = { onSeek(10_000) }, onUserActivity = onUserActivity)
            TvBarButton(Icons.Filled.SkipNext, stringResource(if (isLive) R.string.player_next_channel_desc else R.string.player_next_episode_desc), "tv_bar_next", enabled = hasNext, onClick = onNext, onUserActivity = onUserActivity)
            if (!compact) {
            Spacer(Modifier.weight(1f))
            if (!isLive) TvBarButton(Icons.Filled.Replay, stringResource(R.string.player_start_over), "tv_bar_start_over", onClick = onStartOver, onUserActivity = onUserActivity)
            TvBarButton(Icons.Filled.FormatListBulleted, stringResource(if (isLive) R.string.player_channel_list else R.string.player_lists), "tv_bar_list", onClick = onOpenPanel, onUserActivity = onUserActivity)
            TvBarButton(Icons.Filled.Subtitles, stringResource(R.string.player_audio_subtitle_desc), "tv_bar_tracks", onClick = onOpenTracks, onUserActivity = onUserActivity)
            TvBarButton(Icons.Filled.AspectRatio, stringResource(R.string.player_aspect_ratio_desc), "tv_bar_aspect", onClick = onCycleAspect, onUserActivity = onUserActivity)
            if (isLive) TvBarButton(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, stringResource(if (isSaved) R.string.tv_remove_favorite else R.string.tv_add_favorite), "tv_bar_save", onClick = onToggleSaved, onUserActivity = onUserActivity)
            }
        }
    }
}

@Composable
private fun TvBarButton(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean = true,
    big: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onUserActivity: () -> Unit
) {
    val size = if (big) 60.dp else 48.dp
    TvGlassButton(
        onClick = { if (enabled) onClick() },
        onFocused = onUserActivity,
        shape = TvTheme.PillShape,
        focusRequester = focusRequester,
        glassColor = if (big) TvTheme.Accent.copy(alpha = 0.85f) else TvTheme.Glass,
        // Canlı yayında ileri/geri sarma soluk ve odaklanamaz.
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .focusProperties { canFocus = enabled }
            .testTag(tag)
    ) { focused ->
        Icon(icon, description, tint = if (big) Color.White else if (focused) TvTheme.FocusGlow else TvTheme.TextPrimary,
            modifier = Modifier.size(if (big) 30.dp else 24.dp))
    }
}

@Composable
private fun TvChannelInfoCard(item: IPTVItem, number: Int, now: EPGProgram?, next: EPGProgram?, modifier: Modifier) {
    Column(
        modifier
            .widthIn(max = 420.dp)
            .tvGlass(TvTheme.CardShape, TvTheme.GlassDark)
            .padding(18.dp)
            .testTag("tv_channel_info")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!item.logoUrl.isNullOrBlank()) {
                AsyncImage(model = item.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 64.dp, height = 44.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(stringResource(R.string.tv_channel_info), color = TvTheme.TextMuted, fontSize = 12.sp)
                Text(if (number > 0) "$number - ${item.cleanedName.ifBlank { item.name }}" else item.cleanedName.ifBlank { item.name },
                    color = TvTheme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.category.isNotBlank()) Text(item.category, color = TvTheme.TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (now != null) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tv_now_playing, now.title), color = TvTheme.TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${now.startTimeFormatted} – ${now.endTimeFormatted}", color = TvTheme.TextMuted, fontSize = 13.sp)
        }
        if (next != null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tv_up_next, next.startTimeFormatted, next.title), color = TvTheme.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvVolumeBar(level: Float, modifier: Modifier) {
    Column(
        modifier
            .tvGlass(TvTheme.PillShape, TvTheme.GlassDark)
            .padding(vertical = 16.dp, horizontal = 12.dp)
            .testTag("tv_volume_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(8.dp).height(160.dp).clip(TvTheme.PillShape).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(level.coerceIn(0f, 1f)).background(TvTheme.Accent))
        }
        Spacer(Modifier.height(10.dp))
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = TvTheme.TextPrimary, modifier = Modifier.size(20.dp))
        Text("${(level * 100).toInt()}", color = TvTheme.TextPrimary, fontSize = 12.sp)
    }
}

/** Bölüm/film izlenme durumu: bitmişse "✓ İzlendi", yarımsa ince ilerleme çubuğu, hiç izlenmemişse boş. */
@Composable
internal fun TvWatchedLine(progress: Float?) {
    when {
        progress == null -> {}
        progress >= WATCHED_THRESHOLD -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = TvTheme.Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.detail_watched).replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }, color = TvTheme.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        else -> Box(Modifier.padding(top = 4.dp).width(120.dp).height(3.dp).clip(TvTheme.PillShape).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(TvTheme.Accent))
        }
    }
}

/** Bu orandan sonra bölüm/film "izlendi" sayılır (jenerik kısmı izlenmese de). */
internal const val WATCHED_THRESHOLD = 0.9f
