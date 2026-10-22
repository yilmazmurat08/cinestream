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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.example.ui.components.LocalFloatingBarInset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.model.isPlaceholderSummary
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
private const val PUZZLE = "PUZZLE"

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
        // Oyunlar barın üstünde biter (düğmeler ve poster barın altında kalmasın); zemin barın arkasına uzanır.
        val gameModifier = Modifier.fillMaxSize().padding(bottom = if (game == HUB) 0.dp else LocalFloatingBarInset.current)
        Box(gameModifier) {
        when (game) {
            SWIPE -> SwipeGame(viewModel, tvMode, onBack = { game = HUB })
            WHEEL -> WheelGame(viewModel, tvMode, onBack = { game = HUB })
            QUIZ -> QuizGame(viewModel, tvMode, onBack = { game = HUB }, onGoSeriesFolders = onGoSeriesFolders)
            PUZZLE -> PuzzleGame(viewModel, tvMode, onBack = { game = HUB })
            else -> GamesHub(tvMode, onOpen = { game = it })
        }
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
            .padding(horizontal = if (tvMode) 48.dp else 20.dp, vertical = 20.dp)
            // Oyun seçimi barın arkasından kayar; son kart barın altında kalmaz.
            .padding(bottom = LocalFloatingBarInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GameHeader(stringResource(R.string.games_tag), stringResource(R.string.games_title), stringResource(R.string.games_hub_subtitle), null)
        val cards = listOf(
            Triple(SWIPE, Icons.Filled.Style, R.string.game_swipe_title to R.string.game_swipe_desc),
            Triple(WHEEL, Icons.Filled.Casino, R.string.game_wheel_title to R.string.game_wheel_desc),
            Triple(QUIZ, Icons.Filled.Quiz, R.string.game_quiz_title to R.string.game_quiz_desc),
            Triple(PUZZLE, Icons.Filled.Extension, R.string.game_puzzle_title to R.string.game_puzzle_desc)
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
            // Dizi kartında konu da gösterilsin: katalogdaki özet (yer tutucu değilse) taşınır.
            val shows = catalog.map { show ->
                show.toBrowsableItem().copy(summary = if (show.isPlaceholderSummary()) "" else show.summary)
            }
            val source = when (filter) {
                "MOVIE" -> movies
                "SERIES" -> shows
                else -> movies + shows
            }
            GamesLogic.swipeDeck(source, { viewModel.isAdultContent(it) })
        }
        index = 0
    }

    val current = deck?.getOrNull(index)
    // Listede konu yoksa mevcut TMDB eşleştirmesinden (önbellekli) alınır; bulunamazsa konu satırı boş kalır.
    val overviews = remember { mutableStateMapOf<Int, String>() }
    LaunchedEffect(current?.id) {
        val item = current ?: return@LaunchedEffect
        if (item.summary.isBlank() && item.id !in overviews) {
            val details = withTimeoutOrNull(8_000) { viewModel.tvTmdbDetails(item.cleanedName.ifBlank { item.name }, item.type) }
            overviews[item.id] = details?.overview.orEmpty()
        }
    }
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
        modifier = Modifier.fillMaxSize().padding(horizontal = if (tvMode) 48.dp else 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Üst kısım sade: geri, başlık ve yanında küçük filtreler; poster için yer kalsın.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GameIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.game_back), "game_back", onBack)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.game_swipe_title),
                color = GameText,
                fontSize = 22.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to R.string.game_filter_all, "MOVIE" to R.string.game_filter_movie, "SERIES" to R.string.game_filter_series).forEach { (key, label) ->
                FilterChipButton(stringResource(label), "swipe_filter_$key", selected = filter == key) { filter = key }
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
                        summary = current.summary.ifBlank { overviews[current.id].orEmpty() },
                        rating = viewModel.tmdbRatingOf(current).takeIf { it > 0 } ?: current.rating,
                        modifier = Modifier
                            .then(
                                // Telefonda kart tüm genişliği kaplar (büyük poster); TV'de poster oranı korunur.
                                if (tvMode) Modifier.fillMaxHeight().aspectRatio(0.68f, matchHeightConstraintsFirst = true)
                                else Modifier.fillMaxSize()
                            )
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
                GameIconButton(Icons.Filled.Close, stringResource(R.string.game_skip), "swipe_skip", { decide(false) }, size = 56)
                GameIconButton(Icons.Filled.Favorite, stringResource(R.string.game_like), "swipe_like", { decide(true) }, tint = GameAccent, size = 56)
            }
        }
        // Telefonda kaydırma ipucu oyun seçiminde yazıyor; burada yalnızca TV'de kumanda ipucu gösterilir.
        if (tvMode) {
            Text(
                stringResource(R.string.game_swipe_tv_hint),
                color = GameMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FilterChipButton(text: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GameAccent else Color(0x1FFFFFFF))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull(), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwipeCard(item: IPTVItem, summary: String, rating: Double, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(GameCard)) {
        AsyncImage(model = item.logoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xF20E071A))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Tek satır bilgi: tür · yıl · puan.
            val meta = listOfNotNull(
                (item.genre.ifBlank { item.category }).takeIf { it.isNotBlank() },
                GamesLogic.yearOf(item.releaseDate),
                if (rating > 0) "★ " + String.format(java.util.Locale.US, "%.1f", rating) else null
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = GameGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(item.cleanedName.ifBlank { item.name }, color = GameText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary.isNotBlank()) {
                Text(summary, color = Color(0xFFE4DEEC), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("swipe_summary"))
            }
        }
    }
}

// ---------------------------------------------------------------- 2. Çark

@Composable
private fun WheelGame(viewModel: IPTVViewModel, tvMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val groups by viewModel.movieGroups.collectAsState()
    var categories by remember { mutableStateOf<List<String>?>(null) }
    var eligible by remember { mutableStateOf<List<String>>(emptyList()) }
    var picks by remember { mutableStateOf<List<IPTVItem>>(emptyList()) }
    var landed by remember { mutableStateOf<String?>(null) }
    var spinning by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val spinFocus = remember { FocusRequester() }

    fun showOnWheel(list: List<String>) {
        categories = list
        picks = emptyList()
        landed = null
        scope.launch { rotation.snapTo(0f) }
    }

    LaunchedEffect(groups.size) {
        if (categories.isNullOrEmpty()) {
            val map = groups.associate { it.name to it.items }
            eligible = withContext(Dispatchers.Default) { GamesLogic.wheelEligibleCategories(map) { viewModel.isAdultContent(it) } }
            // Kullanıcının kendi seçtiği kategoriler varsa onlar, yoksa rastgele 8 kategori.
            categories = GamesLogic.wheelSavedCategories(WheelChoiceStore.load(context), eligible)
                ?: eligible.shuffled().take(GamesLogic.WHEEL_SLICES)
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
                            // Hep aynı kategoriler sıkmasın: rastgele yeni kategoriler ya da kullanıcının kendi seçimi.
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                GameButton(stringResource(R.string.game_wheel_shuffle), "wheel_shuffle", modifier = Modifier.weight(1f), primary = false, onClick = {
                                    if (!spinning) {
                                        WheelChoiceStore.save(context, emptyList())
                                        val current = categories.orEmpty().toSet()
                                        val fresh = eligible.filterNot { it in current }.shuffled()
                                        showOnWheel((fresh + eligible.filter { it in current }.shuffled()).take(GamesLogic.WHEEL_SLICES))
                                    }
                                })
                                GameButton(stringResource(R.string.game_wheel_choose), "wheel_choose", modifier = Modifier.weight(1f), primary = false, onClick = { if (!spinning) choosing = true })
                            }
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

    if (choosing) {
        WheelChoiceDialog(
            eligible = eligible,
            initial = categories.orEmpty(),
            onDismiss = { choosing = false },
            onSave = { chosen ->
                choosing = false
                WheelChoiceStore.save(context, chosen)
                showOnWheel(chosen)
            }
        )
    }
}

/** Kullanıcının çark için seçtiği kategoriler (cihazda saklanır; boş liste = rastgele). */
private object WheelChoiceStore {
    private const val PREFS = "games_prefs"
    private const val KEY = "wheel_categories"
    private const val SEP = "\u001F"

    fun load(context: android.content.Context): List<String> = runCatching {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
            ?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()
    }.getOrDefault(emptyList())

    fun save(context: android.content.Context, categories: List<String>) {
        runCatching {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putString(KEY, categories.joinToString(SEP)).apply()
        }
    }
}

/** Çark kategorilerini seçme penceresi: en az 2, en çok 8. Telefonda dokunma, TV'de kumanda ile çalışır. */
@Composable
private fun WheelChoiceDialog(eligible: List<String>, initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial.filter { it in eligible }) }
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GameCard)
                .border(1.dp, GameBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
                .testTag("wheel_choose_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.game_wheel_choose_title), color = GameText, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            Text(stringResource(R.string.game_wheel_choose_desc, selected.size), color = GameMuted, fontSize = 13.sp)
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(eligible.size) { index ->
                    val name = eligible[index]
                    val checked = name in selected
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstRow) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (checked) GameAccent.copy(alpha = 0.18f) else Color.Transparent)
                            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull()) {
                                selected = GamesLogic.toggleWheelChoice(selected, name)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("wheel_choice_$index")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (checked) GameAccent else Color.Transparent)
                                .border(2.dp, if (checked) GameAccent else GameMuted, RoundedCornerShape(6.dp))
                        ) {
                            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(name, color = GameText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                GameButton(stringResource(R.string.game_wheel_cancel), "wheel_choose_cancel", primary = false, onClick = onDismiss)
                val canSave = selected.size >= GamesLogic.WHEEL_MIN_SLICES
                GameButton(
                    stringResource(R.string.game_wheel_save),
                    "wheel_choose_save",
                    primary = canSave,
                    onClick = { if (canSave) onSave(selected) }
                )
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

// ---------------------------------------------------------------- 4. Poster puzzle

/** Posteri bitmap olarak yükler (parçalara bölmek için yazılımsal bitmap gerekir). Olmazsa null. */
private suspend fun loadPosterBitmap(context: android.content.Context, url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null
    return try {
        withTimeoutOrNull(10_000) {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(720, 1080).build()
            val result = context.imageLoader.execute(request)
            ((result as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.asImageBitmap()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun PuzzleGame(viewModel: IPTVViewModel, tvMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val movies by viewModel.movies.collectAsState()
    val catalog by viewModel.xtreamSeriesCatalog.collectAsState()
    var grid by rememberSaveable { mutableIntStateOf(3) }
    var roundKey by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf<IPTVItem?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var order by remember { mutableStateOf(emptyList<Int>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var moves by remember { mutableIntStateOf(0) }
    var showHint by remember { mutableStateOf(false) }
    val solved = GamesLogic.isPuzzleSolved(order) && bitmap != null
    val firstTile = remember { FocusRequester() }

    LaunchedEffect(roundKey, grid, movies.isEmpty(), catalog.isEmpty()) {
        loading = true
        bitmap = null
        item = null
        selected = null
        moves = 0
        showHint = false
        val pool = withContext(Dispatchers.Default) {
            GamesLogic.swipeDeck(movies + catalog.map { it.toBrowsableItem() }, { viewModel.isAdultContent(it) })
        }
        // Posteri yüklenemeyen yapım atlanır, sıradaki denenir.
        for (candidate in pool.take(6)) {
            val image = loadPosterBitmap(context, candidate.logoUrl) ?: continue
            item = candidate
            bitmap = image
            order = GamesLogic.puzzleShuffle(grid * grid)
            break
        }
        loading = false
    }
    // Poster tamamlanınca kısa bir kutlamadan sonra yapımın kartı açılır.
    LaunchedEffect(solved) {
        if (solved) {
            kotlinx.coroutines.delay(1_200)
            item?.let { viewModel.selectItem(it) }
        }
    }
    LaunchedEffect(tvMode, bitmap) { if (tvMode && bitmap != null) runCatching { firstTile.requestFocus() } }
    LaunchedEffect(showHint) {
        if (showHint) {
            kotlinx.coroutines.delay(2_500)
            showHint = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (tvMode) 48.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GameHeader(stringResource(R.string.games_tag), stringResource(R.string.game_puzzle_title), null, onBack)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChipButton(stringResource(R.string.game_puzzle_easy), "puzzle_grid_3", selected = grid == 3) { grid = 3 }
                FilterChipButton(stringResource(R.string.game_puzzle_hard), "puzzle_grid_4", selected = grid == 4) { grid = 4 }
                Spacer(Modifier.weight(1f))
                if (bitmap != null) Text(stringResource(R.string.game_puzzle_moves, moves), color = GameGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            val image = bitmap
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = GameAccent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.game_puzzle_loading), color = GameMuted)
                }
                image == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmptyMessage(stringResource(R.string.game_puzzle_empty))
                    GameButton(stringResource(R.string.game_puzzle_new), "puzzle_new", onClick = { roundKey++ })
                }
                else -> {
                    // Tahta posterin oranında (2:3); ekrana sığacak en büyük boy.
                    val boardWidth = minOf(boxWidth - if (tvMode) 96.dp else 32.dp, (boxHeight - 180.dp).coerceAtLeast(240.dp) * (2f / 3f))
                    val board: @Composable () -> Unit = {
                        Box(Modifier.width(boardWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(GameCard)) {
                            if (showHint || solved) {
                                Canvas(Modifier.fillMaxSize().testTag(if (solved) "puzzle_solved" else "puzzle_hint_image")) {
                                    drawImage(image, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
                                }
                            } else {
                                Column(Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (row in 0 until grid) {
                                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            for (col in 0 until grid) {
                                                val position = row * grid + col
                                                PuzzleTile(
                                                    image = image,
                                                    piece = order.getOrElse(position) { position },
                                                    grid = grid,
                                                    selected = selected == position,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .then(if (position == 0) Modifier.focusRequester(firstTile) else Modifier)
                                                        .testTag("puzzle_tile_$position")
                                                ) {
                                                    val first = selected
                                                    when {
                                                        first == null -> selected = position
                                                        first == position -> selected = null
                                                        else -> {
                                                            order = GamesLogic.puzzleSwap(order, first, position)
                                                            selected = null
                                                            moves++
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val side: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (solved) {
                                Text(
                                    stringResource(R.string.game_puzzle_solved, item?.cleanedName?.ifBlank { item?.name }.orEmpty()),
                                    color = GameAccent,
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic
                                )
                                GameButton(stringResource(R.string.game_puzzle_open), "puzzle_open", onClick = { item?.let { viewModel.selectItem(it) } })
                            } else {
                                Text(
                                    stringResource(if (tvMode) R.string.game_puzzle_tv_hint else R.string.game_puzzle_desc),
                                    color = GameMuted,
                                    fontSize = 13.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!solved) GameButton(stringResource(R.string.game_puzzle_hint), "puzzle_hint", onClick = { showHint = true }, primary = false)
                                GameButton(stringResource(R.string.game_puzzle_new), "puzzle_new", onClick = { roundKey++ }, primary = solved)
                            }
                        }
                    }
                    if (tvMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            board()
                            side(Modifier.weight(1f))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { board() }
                        side(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Posterin bir parçası; seçili parça fuşya, kumanda odağı beyaz çerçeveli. */
@Composable
private fun PuzzleTile(image: ImageBitmap, piece: Int, grid: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pieceW = image.width / grid
    val pieceH = image.height / grid
    Canvas(
        modifier = modifier
            .border(
                width = if (selected || focused) 3.dp else 0.dp,
                color = if (selected) GameAccent else if (focused) Color.White else Color.Transparent
            )
            .clickable(interactionSource = interaction, indication = com.example.ui.tv.tvFocusIndicationOrNull(), onClick = onClick)
    ) {
        drawImage(
            image = image,
            srcOffset = androidx.compose.ui.unit.IntOffset((piece % grid) * pieceW, (piece / grid) * pieceH),
            srcSize = androidx.compose.ui.unit.IntSize(pieceW, pieceH),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )
        if (selected) drawRect(GameAccent.copy(alpha = 0.25f))
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
                            // Hafif bulanık: renkler ve yüzler sezilir, ipucu verir ama adı okunmaz.
                            modifier = Modifier.fillMaxSize().then(if (state.won) Modifier else Modifier.blur(6.dp))
                        )
                        // Android 12 altında bulanıklık çalışmaz (Mi TV Stick): orada yalnızca yarı saydam bir örtü.
                        if (!state.won && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                            Box(Modifier.fillMaxSize().background(Color(0x990E071A)).testTag("quiz_poster_hidden"))
                        }
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
