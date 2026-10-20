package com.example.ui.features.games

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.IPTVItem
import com.example.data.model.isPlaceholderCast
import com.example.data.model.toBrowsableItem
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

// Onaylı taslak renkleri (docs/tasarim/ozellik-taslaklari).
private val GameBg = Color(0xFF0E071A)
private val GameCard = Color(0xFF1C1030)
private val GameAccent = Color(0xFFD946EF)
private val GameGold = Color(0xFFD4A853)
private val GameText = Color(0xFFF4F1F8)
private val GameMuted = Color(0xFFA79FB5)
private val GameBorder = Color(0x29FFFFFF)
private val WheelColors = listOf(
    Color(0xFF7C3AED), Color(0xFFD946EF), Color(0xFF3B1E55), Color(0xFFDB2777),
    Color(0xFF4C1D95), Color(0xFFA21CAF), Color(0xFF2B2140), Color(0xFFBE185D)
)

private const val HUB = "HUB"
private const val SWIPE = "SWIPE"
private const val WHEEL = "WHEEL"
private const val QUIZ = "QUIZ"

/**
 * Oyunlar (PRO): Kaydırma, Çark ve Dizi bilgi yarışması. Telefonda dokunma, TV'de kumanda ile oynanır.
 * Detay kartı [IPTVViewModel.selectItem] ile açılır (telefon ve TV'de mevcut kart).
 */
@Composable
fun GamesHost(
    viewModel: IPTVViewModel,
    tvMode: Boolean,
    onGoSeriesFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    var game by rememberSaveable { mutableStateOf(HUB) }
    BackHandler(enabled = game != HUB) { game = HUB }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GameBg)
            .testTag("games_host")
    ) {
        when (game) {
            SWIPE -> SwipeGame(viewModel, tvMode, onBack = { game = HUB })
            WHEEL -> WheelGame(viewModel, tvMode, onBack = { game = HUB })
            QUIZ -> QuizGame(viewModel, tvMode, onBack = { game = HUB }, onGoSeriesFolders = onGoSeriesFolders)
            else -> GamesHub(tvMode, onOpen = { game = it })
        }
    }
}

// ---------------------------------------------------------------- Ortak parçalar

@Composable
private fun GameHeader(tag: String, title: String, subtitle: String?, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (onBack != null) {
            GameIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.game_back), "game_back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(tag, color = GameGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(
                title,
                color = GameText,
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) Text(subtitle, color = GameMuted, fontSize = 14.sp)
        }
    }
}

/** Kumandada odak görünür (fuşya çerçeve), telefonda dokunmayla çalışır. */
@Composable
private fun GameButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) GameAccent else Color(0x1FFFFFFF))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull(), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GameIconButton(icon: ImageVector, description: String, tag: String, onClick: () -> Unit, tint: Color = GameText, size: Int = 44) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0x1FFFFFFF))
            .border(2.dp, if (focused) GameAccent else GameBorder, CircleShape)
            .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull(), onClick = onClick)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = GameMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

private fun posterBox(modifier: Modifier) = modifier.clip(RoundedCornerShape(16.dp)).background(GameCard)

// ---------------------------------------------------------------- Oyun seçimi

@Composable
private fun GamesHub(tvMode: Boolean, onOpen: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(tvMode) { if (tvMode) runCatching { first.requestFocus() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (tvMode) 48.dp else 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GameHeader(stringResource(R.string.games_tag), stringResource(R.string.games_title), stringResource(R.string.games_hub_subtitle), null)
        val cards = listOf(
            Triple(SWIPE, Icons.Filled.Style, R.string.game_swipe_title to R.string.game_swipe_desc),
            Triple(WHEEL, Icons.Filled.Casino, R.string.game_wheel_title to R.string.game_wheel_desc),
            Triple(QUIZ, Icons.Filled.Quiz, R.string.game_quiz_title to R.string.game_quiz_desc)
        )
        val content: @Composable (Modifier) -> Unit = { itemModifier ->
            cards.forEachIndexed { i, (kind, icon, texts) ->
                HubCard(
                    icon = icon,
                    title = stringResource(texts.first),
                    desc = stringResource(texts.second),
                    tag = "game_card_$kind",
                    focusRequester = if (i == 0) first else null,
                    onClick = { onOpen(kind) },
                    modifier = itemModifier
                )
            }
        }
        if (tvMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                content(Modifier.weight(1f).height(200.dp))
            }
        } else {
            content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HubCard(
    icon: ImageVector,
    title: String,
    desc: String,
    tag: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B1E55), GameCard)))
            .border(2.dp, if (focused) GameAccent else GameBorder, RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull(), onClick = onClick)
            .padding(20.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = GameAccent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, color = GameText, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
            Text(
                "PRO",
                color = Color(0xFF1A0F24),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(GameGold).padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Text(desc, color = GameMuted, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------- 1. Kaydırma

@Composable
private fun SwipeGame(viewModel: IPTVViewModel, tvMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val movies by viewModel.movies.collectAsState()
    val catalog by viewModel.xtreamSeriesCatalog.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var shuffleKey by remember { mutableIntStateOf(0) }
    var deck by remember { mutableStateOf<List<IPTVItem>?>(null) }
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(filter, shuffleKey, movies.size, catalog.size) {
        deck = withContext(Dispatchers.Default) {
            val source = when (filter) {
                "MOVIE" -> movies
                "SERIES" -> catalog.map { it.toBrowsableItem() }
                else -> movies + catalog.map { it.toBrowsableItem() }
            }
            GamesLogic.swipeDeck(source, { viewModel.isAdultContent(it) })
        }
        index = 0
    }

    val current = deck?.getOrNull(index)
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    val cardFocus = remember { FocusRequester() }

    fun decide(like: Boolean) {
        val item = current ?: return
        scope.launch {
            offsetX.animateTo(if (like) threshold * 4 else -threshold * 4, tween(220))
            if (like) {
                if (!viewModel.isFavorite(item, favorites, favoriteSeriesIds)) viewModel.toggleFavorite(item)
                Toast.makeText(context, R.string.game_swipe_added, Toast.LENGTH_SHORT).show()
            }
            index += 1
            offsetX.snapTo(0f)
        }
    }

    LaunchedEffect(tvMode, current?.id) { if (tvMode && current != null) runCatching { cardFocus.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (tvMode) 48.dp else 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GameHeader(stringResource(R.string.games_tag), stringResource(R.string.game_swipe_title), null, onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to R.string.game_filter_all, "MOVIE" to R.string.game_filter_movie, "SERIES" to R.string.game_filter_series).forEach { (key, label) ->
                GameButton(stringResource(label), "swipe_filter_$key", onClick = { filter = key }, primary = filter == key)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                deck == null -> CircularProgressIndicator(color = GameAccent)
                deck!!.isEmpty() -> EmptyMessage(stringResource(R.string.game_swipe_empty))
                current == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.game_swipe_done), color = GameText, fontSize = 20.sp)
                    GameButton(stringResource(R.string.game_shuffle), "swipe_shuffle", onClick = { shuffleKey++ })
                }
                else -> {
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    SwipeCard(
                        item = current,
                        rating = viewModel.tmdbRatingOf(current).takeIf { it > 0 } ?: current.rating,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.68f, matchHeightConstraintsFirst = true)
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .graphicsLayer { rotationZ = offsetX.value / 40f }
                            .border(2.dp, if (focused) GameAccent else Color.Transparent, RoundedCornerShape(18.dp))
                            .focusRequester(cardFocus)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { decide(false); true }
                                    Key.DirectionRight -> { decide(true); true }
                                    else -> false
                                }
                            }
                            .focusable(interactionSource = interaction)
                            .clickable(interactionSource = interaction, indication = null) { viewModel.selectItem(current) }
                            .pointerInput(current.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            offsetX.value > threshold -> decide(true)
                                            offsetX.value < -threshold -> decide(false)
                                            else -> scope.launch { offsetX.animateTo(0f, tween(150)) }
                                        }
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        scope.launch { offsetX.snapTo(offsetX.value + amount) }
                                    }
                                )
                            }
                            .testTag("swipe_card")
                    )
                }
            }
        }
        if (current != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                GameIconButton(Icons.Filled.Close, stringResource(R.string.game_skip), "swipe_skip", { decide(false) }, size = 60)
                GameIconButton(Icons.Filled.Favorite, stringResource(R.string.game_like), "swipe_like", { decide(true) }, tint = GameAccent, size = 60)
            }
        }
        Text(
            stringResource(if (tvMode) R.string.game_swipe_tv_hint else R.string.game_swipe_desc),
            color = GameMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwipeCard(item: IPTVItem, rating: Double, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(GameCard)) {
        AsyncImage(model = item.logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF20E071A))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val meta = listOfNotNull(
                stringResource(if (item.type == "SERIES") R.string.game_filter_series else R.string.game_filter_movie).uppercase(),
                (item.genre.ifBlank { item.category }).takeIf { it.isNotBlank() },
                GamesLogic.yearOf(item.releaseDate)
            ).joinToString(" · ")
            Text(meta, color = GameGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.cleanedName.ifBlank { item.name }, color = GameText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (rating > 0) {
                Text(stringResource(R.string.game_rating, String.format(java.util.Locale.US, "%.1f", rating)), color = GameText, fontSize = 13.sp)
            }
            if (item.summary.isNotBlank()) {
                Text(item.summary, color = GameMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- 2. Çark

@Composable
private fun WheelGame(viewModel: IPTVViewModel, tvMode: Boolean, onBack: () -> Unit) {
    val groups by viewModel.movieGroups.collectAsState()
    var categories by remember { mutableStateOf<List<String>?>(null) }
    var picks by remember { mutableStateOf<List<IPTVItem>>(emptyList()) }
    var landed by remember { mutableStateOf<String?>(null) }
    var spinning by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val spinFocus = remember { FocusRequester() }

    LaunchedEffect(groups.size) {
        if (categories.isNullOrEmpty()) {
            categories = withContext(Dispatchers.Default) {
                GamesLogic.wheelCategories(groups.associate { it.name to it.items }, { viewModel.isAdultContent(it) })
            }
        }
    }
    LaunchedEffect(tvMode, categories) { if (tvMode && !categories.isNullOrEmpty()) runCatching { spinFocus.requestFocus() } }

    fun spin() {
        val cats = categories ?: return
        if (spinning || cats.isEmpty()) return
        spinning = true
        picks = emptyList()
        landed = null
        scope.launch {
            val target = rotation.value + 360f * 5 + (0 until 360).random()
            rotation.animateTo(target, tween(3200, easing = FastOutSlowInEasing))
            val name = cats[GamesLogic.sliceAtTop(rotation.value, cats.size)]
            landed = name
            val items = groups.firstOrNull { it.name == name }?.items.orEmpty()
            picks = withContext(Dispatchers.Default) { GamesLogic.wheelPicks(items, { viewModel.isAdultContent(it) }) }
            spinning = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (tvMode) 48.dp else 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GameHeader(stringResource(R.string.games_tag), stringResource(R.string.game_wheel_title), stringResource(R.string.game_wheel_desc), onBack)
            val cats = categories
            when {
                cats == null -> CircularProgressIndicator(color = GameAccent)
                cats.isEmpty() -> EmptyMessage(stringResource(R.string.game_wheel_empty))
                else -> {
                    val wheelSize = if (wide) (boxHeight * 0.62f) else (boxWidth - 40.dp).coerceAtMost(340.dp)
                    val wheel: @Composable () -> Unit = {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(wheelSize)) {
                            Wheel(cats, rotation.value, Modifier.fillMaxSize())
                            GameButton(
                                stringResource(R.string.game_wheel_spin),
                                "wheel_spin",
                                onClick = ::spin,
                                modifier = Modifier.size(wheelSize * 0.3f),
                                focusRequester = spinFocus
                            )
                        }
                    }
                    val results: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            landed?.let {
                                Text(stringResource(R.string.game_wheel_result, it), color = GameGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            if (picks.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    picks.forEachIndexed { i, item -> PosterTile(item, "wheel_pick_$i", Modifier.weight(1f)) { viewModel.selectItem(item) } }
                                }
                            }
                            Text(stringResource(R.string.game_wheel_hint), color = GameMuted, fontSize = 13.sp)
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                            wheel()
                            results(Modifier.weight(1f))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { wheel() }
                        results(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun Wheel(categories: List<String>, angle: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.testTag("wheel")) {
        val n = categories.size
        val sweep = 360f / n
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        rotate(angle, center) {
            categories.forEachIndexed { i, name ->
                // Dilim 0 saat 12'den başlar (−90°) ve saat yönünde ilerler.
                drawArc(
                    color = WheelColors[i % WheelColors.size],
                    startAngle = -90f + i * sweep,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
                val mid = -90f + i * sweep + sweep / 2f
                rotate(mid + 90f, center) {
                    val label = measurer.measure(
                        name.take(14),
                        style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - radius * 0.86f))
                }
            }
        }
        // Üstteki ok: çarkın durduğu dilimi gösterir.
        val pointer = Path().apply {
            moveTo(center.x - 14f, 0f)
            lineTo(center.x + 14f, 0f)
            lineTo(center.x, 30f)
            close()
        }
        drawPath(pointer, GameGold)
    }
}

@Composable
private fun PosterTile(item: IPTVItem, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsyncImage(
            model = item.logoUrl,
            contentDescription = item.cleanedName,
            contentScale = ContentScale.Crop,
            modifier = posterBox(Modifier.fillMaxWidth().aspectRatio(0.68f))
                .border(2.dp, if (focused) GameAccent else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull(), onClick = onClick)
                .testTag(tag)
        )
        Text(item.cleanedName.ifBlank { item.name }, color = GameText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- 3. Dizi bilgi yarışması

private class QuizRound(val show: IPTVItem, val questions: List<GamesLogic.Question>)

@Composable
private fun QuizGame(viewModel: IPTVViewModel, tvMode: Boolean, onBack: () -> Unit, onGoSeriesFolders: () -> Unit) {
    val catalog by viewModel.xtreamSeriesCatalog.collectAsState()
    var roundKey by remember { mutableIntStateOf(0) }
    var round by remember { mutableStateOf<QuizRound?>(null) }
    var loading by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf(GamesLogic.QuizState()) }
    val firstAnswer = remember { FocusRequester() }

    LaunchedEffect(roundKey, catalog.isEmpty()) {
        loading = true
        round = null
        state = GamesLogic.QuizState()
        round = buildRound(viewModel, catalog)
        loading = false
    }
    LaunchedEffect(tvMode, round, state.finished) { if (tvMode && round != null) runCatching { firstAnswer.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (tvMode) 48.dp else 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GameHeader(stringResource(R.string.games_tag), stringResource(R.string.game_quiz_title), null, onBack)
        val r = round
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = GameAccent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.game_quiz_loading), color = GameMuted)
            }
            r == null -> EmptyMessage(stringResource(R.string.game_quiz_empty))
            else -> {
                val question = r.questions[state.index % r.questions.size]
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(posterBox(Modifier.width(if (tvMode) 200.dp else 120.dp).aspectRatio(0.68f))) {
                        AsyncImage(
                            model = r.show.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().then(if (state.won) Modifier else Modifier.blur(16.dp))
                        )
                        // Android 12 altında bulanıklık çalışmaz: poster koyu bir örtüyle de gizlenir.
                        if (!state.won) Box(Modifier.fillMaxSize().background(Color(0xB30E071A)).testTag("quiz_poster_hidden"))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            state.won -> {
                                Text(stringResource(R.string.game_quiz_won), color = GameAccent, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                                Text(stringResource(R.string.game_quiz_secret, r.show.cleanedName.ifBlank { r.show.name }), color = GameText, fontSize = 16.sp, modifier = Modifier.testTag("quiz_secret"))
                                GameButton(stringResource(R.string.game_quiz_open), "quiz_open", onClick = { viewModel.selectItem(r.show) }, focusRequester = firstAnswer)
                                GameButton(stringResource(R.string.game_quiz_new), "quiz_new", onClick = { roundKey++ }, primary = false)
                            }
                            state.lost -> {
                                Text(stringResource(R.string.game_quiz_lost_title), color = GameText, fontSize = 24.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                                Text(stringResource(R.string.game_quiz_lost_body), color = GameMuted, fontSize = 14.sp)
                                GameButton(stringResource(R.string.game_quiz_go_folders), "quiz_folders", onClick = onGoSeriesFolders, focusRequester = firstAnswer)
                                GameButton(stringResource(R.string.game_quiz_new), "quiz_new", onClick = { roundKey++ }, primary = false)
                            }
                            else -> {
                                Text(
                                    stringResource(R.string.game_quiz_progress, state.index + 1, state.livesLeft),
                                    color = GameGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("quiz_progress")
                                )
                                if (state.lastWrong) Text(stringResource(R.string.game_quiz_wrong), color = GameMuted, fontSize = 13.sp)
                                Text(stringResource(questionText(question.fact)), color = GameText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("quiz_question"))
                                GameButton(question.optionA, "quiz_answer_a", onClick = { state = GamesLogic.answer(state, question, true) }, modifier = Modifier.fillMaxWidth(), primary = false, focusRequester = firstAnswer)
                                GameButton(question.optionB, "quiz_answer_b", onClick = { state = GamesLogic.answer(state, question, false) }, modifier = Modifier.fillMaxWidth(), primary = false)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.game_quiz_note), color = GameMuted, fontSize = 12.sp)
            }
        }
    }
}

private fun questionText(fact: GamesLogic.Fact): Int = when (fact) {
    GamesLogic.Fact.YEAR -> R.string.game_quiz_q_year
    GamesLogic.Fact.GENRE -> R.string.game_quiz_q_genre
    GamesLogic.Fact.LEAD_ACTOR -> R.string.game_quiz_q_actor
    GamesLogic.Fact.SEASONS -> R.string.game_quiz_q_seasons
}

/**
 * Bir tur hazırlar: listeden posterli bir dizi seçilir; bilgileri mevcut TMDB eşleştirmesinden (önbellekli) ya da
 * listenin kendi bilgisinden alınır. Yanlış şıklar için birkaç başka dizinin bilgisi de çekilir.
 */
private suspend fun buildRound(viewModel: IPTVViewModel, catalog: List<com.example.data.model.TvShow>): QuizRound? {
    val pool = withContext(Dispatchers.Default) {
        catalog.filter { !it.logoUrl.isNullOrBlank() && !viewModel.isAdultContent(it.category, it.title) }.shuffled()
    }
    if (pool.size < 2) return null
    suspend fun facts(show: com.example.data.model.TvShow): GamesLogic.ShowFacts {
        val details = withTimeoutOrNull(8_000) { viewModel.tvTmdbDetails(show.title, "SERIES") }
        val localLead = show.cast.takeIf { !show.isPlaceholderCast() }?.split(",")?.firstOrNull()?.trim()
        return GamesLogic.ShowFacts(
            title = show.title,
            year = GamesLogic.yearOf(details?.releaseDate),
            genre = details?.genres?.take(2)?.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            leadActor = details?.cast?.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: localLead,
            seasons = show.seasons.size.takeIf { it > 0 }
        )
    }
    var attempt = 0
    while (attempt < 3 && attempt < pool.size) {
        val target = pool[attempt]
        val others = pool.filter { it.id != target.id }.take(5)
        val all = coroutineScope { (listOf(target) + others).map { async { facts(it) } }.awaitAll() }
        val questions = GamesLogic.questions(all.first(), all.drop(1))
        if (questions.isNotEmpty()) return QuizRound(target.toBrowsableItem(), questions)
        attempt++
    }
    return null
}
