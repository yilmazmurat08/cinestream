package com.example.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tv
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.ContinueWatching
import com.example.data.model.SeriesParser
import com.example.data.repository.ShelfContent
import com.example.data.repository.ShelfImageKind
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.delay

/** TV ana sayfasındaki bölümler (kartlar) ve hero'yu etkileyen odak alanları. */
object TvSection {
    const val LIVE = "LIVE"
    const val MOVIE = "MOVIE"
    const val SERIES = "SERIES"
    const val SAVED = "SAVED"
    const val SETTINGS = "SETTINGS"
    const val CONTINUE = "CONTINUE"
    const val ASSISTANT = "ASSISTANT"
    const val TOP_BAR = "TOP_BAR"
    val cards = listOf(LIVE, MOVIE, SERIES, SAVED, SETTINGS)
}

private const val HERO_REFRESH_MS = 6_000L
private const val HERO_FOCUS_DEBOUNCE_MS = 300L

/**
 * TV ana sayfası: üstte logo ve PRO / ✨ / Arama; üst yarıda odaklanan bölüme göre değişen hero (Top Shelf);
 * altında 5 cam kart (Canlı TV, Filmler, Diziler, Kaydedilenler, Profil/Ayarlar) ve İzlemeye Devam Et çubuğu.
 * Alt gezinme çubuğu ve aşağı çek-yenile yoktur; hero bir carousel değildir (odaklanamaz, kaymaz).
 */
@Composable
fun TvHomeScreen(
    viewModel: IPTVViewModel,
    initialFocus: String,
    onFocusChanged: (String) -> Unit,
    onOpenSection: (String) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlayContinue: (ContinueWatching) -> Unit,
    modifier: Modifier = Modifier
) {
    val isProUser by viewModel.isProUser.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val lastWatched = continueWatching.firstOrNull()

    val cardFocus = remember { TvSection.cards.associateWith { FocusRequester() } }
    val askFocus = remember { FocusRequester() }
    val proFocus = remember { FocusRequester() }
    val starFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(initialFocus) }
    var lastCard by remember { mutableStateOf(initialFocus.takeIf { it in TvSection.cards } ?: TvSection.LIVE) }

    LaunchedEffect(Unit) {
        val target = when (initialFocus) {
            in TvSection.cards -> cardFocus.getValue(initialFocus)
            TvSection.CONTINUE -> if (lastWatched != null) continueFocus else cardFocus.getValue(TvSection.LIVE)
            TvSection.ASSISTANT -> askFocus
            else -> cardFocus.getValue(TvSection.LIVE)
        }
        requestFocusWhenReady(target)
    }

    fun onFocus(section: String) {
        focused = section
        if (section in TvSection.cards) lastCard = section
        onFocusChanged(section)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .testTag("tv_home_screen")
    ) {
        val heroHeight = maxHeight * 0.56f
        val cardHeight = maxHeight * 0.2f
        TvHero(
            viewModel = viewModel,
            section = focused,
            continueItem = lastWatched,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        )

        // Üst bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_logo_1782414357652),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "CineStream",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color.White, Color.White, TvTheme.Accent)),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Spacer(Modifier.weight(1f))
            val topDown = Modifier.focusProperties { down = askFocus }
            TvGlassButton(
                onClick = { viewModel.openPaywall() },
                shape = TvTheme.PillShape,
                focusRequester = proFocus,
                onFocused = { onFocus(TvSection.TOP_BAR) },
                modifier = topDown.height(44.dp).testTag("tv_top_pro")
            ) { f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = if (f) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isProUser) "PRO ✓" else "PRO", color = TvTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            TvTopIconButton(Icons.Filled.AutoAwesome, stringResource(R.string.tv_ask_assistant), starFocus, topDown,
                onFocused = { onFocus(TvSection.TOP_BAR) }, onClick = onOpenAssistant, tag = "tv_top_star")
            Spacer(Modifier.width(14.dp))
            TvTopIconButton(Icons.Filled.Search, stringResource(R.string.tv_search), searchFocus, topDown,
                onFocused = { onFocus(TvSection.TOP_BAR) }, onClick = onOpenSearch, tag = "tv_top_search")
        }

        // Asistana sor: yeri sabit (hero'nun sol altı), yazı uzunluğuna göre kaymaz.
        TvGlassButton(
            onClick = onOpenAssistant,
            shape = TvTheme.PillShape,
            focusRequester = askFocus,
            onFocused = { onFocus(TvSection.ASSISTANT) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = heroHeight - 64.dp)
                .height(48.dp)
                .focusProperties {
                    up = proFocus
                    down = cardFocus.getValue(lastCard)
                }
                .testTag("tv_ask_assistant")
        ) { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = if (f) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.tv_ask_assistant), color = TvTheme.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 48.dp, end = 48.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                TvSection.cards.forEach { section ->
                    TvSectionCard(
                        section = section,
                        focusRequester = cardFocus.getValue(section),
                        onFocused = { onFocus(section) },
                        onClick = { onOpenSection(section) },
                        modifier = Modifier
                            .weight(1f)
                            .height(cardHeight)
                            .focusProperties {
                                up = askFocus
                                if (lastWatched != null) down = continueFocus
                            }
                    )
                }
            }
            if (lastWatched != null) {
                TvContinueBar(
                    item = lastWatched,
                    focusRequester = continueFocus,
                    onFocused = { onFocus(TvSection.CONTINUE) },
                    onClick = { onPlayContinue(lastWatched) },
                    modifier = Modifier.focusProperties { up = cardFocus.getValue(lastCard) }
                )
            }
        }
    }
}

@Composable
private fun TvTopIconButton(
    icon: ImageVector,
    description: String,
    focusRequester: FocusRequester,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    tag: String
) {
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.PillShape,
        focusRequester = focusRequester,
        onFocused = onFocused,
        modifier = modifier.size(44.dp).testTag(tag)
    ) { f ->
        Icon(icon, contentDescription = description, tint = if (f) TvTheme.FocusGlow else TvTheme.TextPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun TvSectionCard(
    section: String,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val (icon, label) = when (section) {
        TvSection.LIVE -> Icons.Outlined.LiveTv to R.string.tv_live
        TvSection.MOVIE -> Icons.Outlined.Movie to R.string.tv_movies
        TvSection.SERIES -> Icons.Outlined.Tv to R.string.tv_series
        TvSection.SAVED -> Icons.Outlined.Bookmark to R.string.tv_saved
        else -> Icons.Outlined.Person to R.string.tv_profile_settings
    }
    TvGlassButton(
        onClick = onClick,
        focusRequester = focusRequester,
        onFocused = onFocused,
        modifier = modifier.testTag("tv_card_$section")
    ) { f ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = if (f) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(label), color = TvTheme.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun TvContinueBar(
    item: ContinueWatching,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val episode = remember(item.itemName) { SeriesParser.parseEpisodeInfo(item.itemName) }
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.RowShape,
        focusRequester = focusRequester,
        onFocused = onFocused,
        focusScale = 1.03f,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag("tv_continue_bar")
    ) { f ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            AsyncImage(
                model = item.itemLogo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvTheme.GlassDark)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tv_continue_watching), color = TvTheme.TextMuted, fontSize = 13.sp)
                Text(
                    episode?.showTitle?.takeIf { it.isNotBlank() } ?: item.itemName,
                    color = TvTheme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (episode != null) {
                    Text(stringResource(R.string.tv_season_episode, episode.season, episode.episode), color = TvTheme.TextSecondary, fontSize = 14.sp)
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(TvTheme.PillShape)
                    .background(if (f) TvTheme.FocusGlow else TvTheme.Accent.copy(alpha = 0.85f))
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.tv_resume), tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/**
 * Hero (Top Shelf): odaklanan bölüme göre içerik gösterir, 6 sn'de bir yumuşak geçişle yenilenir. Odaklanamaz,
 * kaymaz; odağı etkilemez. Hızlı gezinmede her kart için yükleme başlamaz (~300 ms bekler); sonraki görsel
 * önceden yüklenir; ekran görünür değilken döngü durur.
 */
@Composable
private fun TvHero(
    viewModel: IPTVViewModel,
    section: String,
    continueItem: ContinueWatching?,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val kind = when (section) {
        TvSection.LIVE, TvSection.MOVIE, TvSection.SERIES, TvSection.SAVED -> section
        TvSection.CONTINUE -> if (continueItem != null) TvSection.CONTINUE else "DEFAULT"
        else -> "DEFAULT"
    }
    var shown by remember { mutableStateOf<ShelfContent?>(null) }
    var shownKind by remember { mutableStateOf("DEFAULT") }

    suspend fun preload(content: ShelfContent?) {
        val url = content?.imageUrl ?: return
        runCatching { context.imageLoader.execute(ImageRequest.Builder(context).data(url).build()) }
    }

    LaunchedEffect(kind, continueItem?.itemId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(HERO_FOCUS_DEBOUNCE_MS)
            val first = viewModel.tvShelfContent(kind, continueItem)
            preload(first)
            if (first != null) {
                shown = first
                shownKind = kind
            }
            while (true) {
                val next = viewModel.tvShelfContent(kind, continueItem)
                preload(next) // bir sonraki görsel geçişten önce hazır
                delay(HERO_REFRESH_MS)
                if (next != null) {
                    shown = next
                    shownKind = kind
                }
            }
        }
    }

    Box(modifier = modifier.testTag("tv_hero")) {
        Crossfade(targetState = shown, animationSpec = tween(700), label = "tvHero") { content ->
            if (content != null) TvHeroLayer(content, showTag = shownKind == "DEFAULT")
        }
    }
}

@Composable
private fun TvHeroLayer(content: ShelfContent, showTag: Boolean) {
    val glow = rememberDominantColor(content.imageUrl.takeIf { content.imageKind != ShelfImageKind.WIDE })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        // Görsel sağa hizalı: yatay görsel sağ tarafı doldurur; poster/logo kendi oranında, arkasında ışık.
        when (content.imageKind) {
            ShelfImageKind.WIDE -> AsyncImage(
                model = content.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(0.72f)
            )
            ShelfImageKind.POSTER, ShelfImageKind.LOGO -> {
                val imageBox: Dp = if (content.imageKind == ShelfImageKind.LOGO) h * 0.5f else h * 0.78f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 96.dp, top = 40.dp)
                        .size(width = if (content.imageKind == ShelfImageKind.LOGO) imageBox * 1.6f else imageBox * 0.7f, height = imageBox)
                ) {
                    Box(
                        Modifier
                            .size(imageBox * 1.3f)
                            .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent)))
                    )
                    AsyncImage(
                        model = content.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit, // gerilmez, kırpılmaz
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(if (content.imageKind == ShelfImageKind.POSTER) 14.dp else 0.dp))
                    )
                }
            }
        }
        // Okunaklılık: soldan sağa şeffaflaşan koyu geçiş + alttan arka plana geçiş.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to TvTheme.Background,
                    0.38f to TvTheme.Background.copy(alpha = 0.92f),
                    0.62f to TvTheme.Background.copy(alpha = 0.2f),
                    1f to Color.Transparent
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.65f to Color.Transparent, 1f to TvTheme.Background)
            )
        )
        val shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)
        // Yazılar üst bar ile "Asistana sor" butonu arasında kalır; uzun konu 3 satırdan önce kısalır.
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .fillMaxHeight()
                .padding(start = 48.dp, top = 88.dp, end = 12.dp, bottom = 80.dp)
        ) {
            if (showTag) {
                Text(
                    stringResource(R.string.tv_next_movie_tag),
                    color = TvTheme.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(shadow = shadow)
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                content.title,
                color = TvTheme.TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 38.sp,
                style = TextStyle(shadow = shadow)
            )
            val meta = listOfNotNull(
                content.rating?.let { "★ " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
                content.year,
                content.genre
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(meta.joinToString(" · "), color = TvTheme.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = shadow))
            }
            content.nowPlaying?.let {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.tv_now_playing, it), color = TvTheme.TextPrimary, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = shadow))
            }
            content.overview?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it, color = TvTheme.TextPrimary.copy(alpha = 0.9f), fontSize = 17.sp, lineHeight = 24.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = shadow), modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}
