package com.example.ui.tv

import com.example.ui.components.fadeEdges
import com.example.util.displayRating

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
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.zIndex
import com.example.data.model.IPTVItem
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
import kotlinx.coroutines.launch

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
    const val GAMES = "GAMES"
    const val SPORTS = "SPORTS"
    // Oyunlar ve Spor açma/kapama anahtarlarıyla görünür (BuildConfig.FEATURE_GAMES / FEATURE_SPORTS).
    val cards = listOfNotNull(
        LIVE, MOVIE, SERIES, SAVED,
        GAMES.takeIf { com.example.BuildConfig.FEATURE_GAMES },
        SPORTS.takeIf { com.example.BuildConfig.FEATURE_SPORTS },
        SETTINGS
    )
}

private const val HERO_REFRESH_MS = 6_000L
private const val HERO_FOCUS_DEBOUNCE_MS = 300L

/**
 * TV ana sayfası: üstte logo ve Arama / PRO / ✨ (arama yerinde açılan çubuktur, [TvSearchBar]); üst yarıda odaklanan bölüme göre değişen hero (Top Shelf);
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
    onSearchResult: (IPTVItem) -> Unit,
    onPlayContinue: (ContinueWatching) -> Unit,
    modifier: Modifier = Modifier,
    // "Nerede kalmıştık" şeridi: (kayıt, baştan mı). OK soru sormadan kaldığı yerden oynatır.
    onResume: (ContinueWatching, Boolean) -> Unit = { cw, _ -> onPlayContinue(cw) }
) {
    val isProUser by viewModel.isProUser.collectAsState()
    // Nerede kalmıştık: yarım kalan son film ve diziler (bkz. ResumeShelf). Eskiden burada yalnızca son izlenen tek
    // kayıt (canlı kanal da olabiliyordu) vardı; şerit Kaydedilenler'deydi ve orada kesik görünüyordu.
    val resumeShelf by viewModel.resumeShelf.collectAsState()
    var focusedResume by remember { mutableStateOf<ContinueWatching?>(null) }
    var menuFor by remember { mutableStateOf<ContinueWatching?>(null) }
    val resumeRemovedText = stringResource(R.string.resume_removed)
    val context = LocalContext.current
    val menuScope = androidx.compose.runtime.rememberCoroutineScope()

    val cardFocus = remember { TvSection.cards.associateWith { FocusRequester() } }
    val askFocus = remember { FocusRequester() }
    val proFocus = remember { FocusRequester() }
    val starFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() } // şeridin ilk kartı
    var focused by remember { mutableStateOf(initialFocus) }
    var lastCard by remember { mutableStateOf(initialFocus.takeIf { it in TvSection.cards } ?: TvSection.LIVE) }

    LaunchedEffect(Unit) {
        val target = when (initialFocus) {
            in TvSection.cards -> cardFocus.getValue(initialFocus)
            TvSection.CONTINUE -> if (resumeShelf.isNotEmpty()) continueFocus else cardFocus.getValue(TvSection.LIVE)
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
        // Şerit varken bölüm kartları biraz alçalır (şerit başlığına yer açılır).
        val cardHeight = maxHeight * (if (resumeShelf.isNotEmpty()) 0.18f else 0.2f)
        TvHero(
            viewModel = viewModel,
            section = focused,
            continueItem = focusedResume?.takeIf { f -> resumeShelf.any { it.id == f.id } } ?: resumeShelf.firstOrNull(),
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        )

        // Üst bar. Arama paneli aşağı açılınca hero ve kartların üstünde kalsın diye en üst katmanda;
        // öğeler üste hizalı ve 44 dp yükseklikte, böylece panel açılınca bar kaymaz.
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Box(modifier = Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo_1782414357652),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "CineStream",
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color.White, Color.White, TvTheme.Accent)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            val topDown = Modifier.focusProperties { down = askFocus }
            val searchQuery by viewModel.searchQuery.collectAsState()
            val searchResults by viewModel.searchResults.collectAsState()
            val isAILoading by viewModel.isAILoading.collectAsState()
            TvSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSubmit = { viewModel.saveSearchQuery(it) },
                onAiSearch = { viewModel.performNLPSearch(it) },
                isAiLoading = isAILoading,
                results = searchResults,
                onItemClick = onSearchResult,
                buttonFocus = searchFocus,
                onFocused = { onFocus(TvSection.TOP_BAR) },
                buttonModifier = topDown
            )
            Spacer(Modifier.width(14.dp))
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
                .padding(start = 48.dp, end = 48.dp, bottom = if (resumeShelf.isNotEmpty()) 24.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (resumeShelf.isNotEmpty()) 16.dp else 20.dp)
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
                                if (resumeShelf.isNotEmpty()) down = continueFocus
                            }
                    )
                }
            }
            if (resumeShelf.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.resume_shelf_title),
                        color = TvTheme.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("tv_home_resume_row")
                    ) {
                        // Anahtar kaydın kendi numarası: aynı itemId iki kayıtta olabilir.
                        items(resumeShelf.size, key = { resumeShelf[it].id }) { index ->
                            val cw = resumeShelf[index]
                            val longFired = remember { BooleanArray(1) }
                            TvResumeCard(
                                item = cw,
                                focusRequester = if (index == 0) continueFocus else null,
                                onFocused = {
                                    focusedResume = cw
                                    onFocus(TvSection.CONTINUE)
                                },
                                onClick = { onResume(cw, false) },
                                modifier = Modifier
                                    .focusProperties { up = cardFocus.getValue(lastCard) }
                                    // OK: kaldığı yerden. Basılı tut: Listeden kaldır / Baştan başlat / Vazgeç.
                                    .tvOkLongPress(longFired, onClick = { onResume(cw, false) }) { menuFor = cw }
                            )
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { cw ->
        val close: () -> Unit = {
            menuFor = null
            menuScope.launch {
                kotlinx.coroutines.delay(200)
                requestFocusWhenReady(if (resumeShelf.isNotEmpty()) continueFocus else cardFocus.getValue(lastCard))
            }
        }
        TvItemMenuDialog(
            title = resumeTitle(cw),
            actions = listOf(
                TvMenuAction(stringResource(R.string.tv_remove_from_list), "tv_resume_menu_remove", primary = true) {
                    viewModel.hideFromResumeShelf(cw)
                    android.widget.Toast.makeText(context, resumeRemovedText, android.widget.Toast.LENGTH_SHORT).show()
                    close()
                },
                TvMenuAction(stringResource(R.string.player_start_over), "tv_resume_menu_start_over") {
                    menuFor = null
                    onResume(cw, true)
                },
                TvMenuAction(stringResource(R.string.game_wheel_cancel), "tv_resume_menu_cancel") { close() }
            ),
            onDismiss = close,
            tag = "tv_resume_menu"
        )
    }
}

/** Kartta görünen ad: dizide dizinin adı (bölüm etiketi ayrı satırda), filmde filmin adı. */
private fun resumeTitle(item: ContinueWatching): String =
    SeriesParser.parseEpisodeInfo(item.itemName)?.showTitle?.takeIf { it.isNotBlank() } ?: item.itemName

/**
 * Nerede kalmıştık kartı: yatay küçük resim, ad, "Sezon 1 · Bölüm 3 · 38 dk kaldı" ve ilerleme çubuğu.
 */
@Composable
private fun TvResumeCard(
    item: ContinueWatching,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val episode = remember(item.itemName) { SeriesParser.parseEpisodeInfo(item.itemName) }
    val progress = com.example.util.ResumeShelf.progress(item)
    val left = com.example.util.ResumeShelf.minutesLeft(item)
    val detail = listOfNotNull(
        episode?.let { stringResource(R.string.tv_season_episode_short, it.season, it.episode) },
        left?.let { stringResource(R.string.resume_minutes_left, it) }
    ).joinToString(" · ")
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.RowShape,
        focusRequester = focusRequester,
        onFocused = onFocused,
        focusScale = 1.04f,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .width(300.dp)
            .height(76.dp)
            .testTag("tv_home_resume_${item.itemId}")
    ) { f ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            Box {
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                        .clip(TvTheme.PillShape)
                        .background(if (f) TvTheme.FocusGlow else Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.tv_resume), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    resumeTitle(item),
                    color = TvTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (detail.isNotEmpty()) {
                    Text(detail, color = TvTheme.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(TvTheme.PillShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(TvTheme.Accent)
                        )
                    }
                }
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
        TvSection.GAMES -> Icons.Outlined.SportsEsports to R.string.tv_games
        TvSection.SPORTS -> Icons.Outlined.SportsSoccer to R.string.tv_sports
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
            // Oyunlar ve Spor açıkken 7 kart olur: yazı küçülür ve gerekirse iki satıra iner (kesilmez).
            val crowded = TvSection.cards.size > 5
            Text(
                stringResource(label),
                color = TvTheme.TextPrimary,
                fontSize = if (crowded) 14.sp else 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (crowded) 2 else 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = if (crowded) 17.sp else androidx.compose.ui.unit.TextUnit.Unspecified
            )
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
    val appLogoPainter = androidx.compose.ui.res.painterResource(R.drawable.img_app_logo_1782414357652)
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
                // Afiş yüklenemezse mor boşluk yerine uygulamanın logosu görünür.
                error = appLogoPainter,
                fallback = appLogoPainter,
                // Kenarlar yumuşakça zemine karışır; görselin sol kenarında keskin çizgi kalmaz.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .fadeEdges(left = 0.35f, top = 0.12f, right = 0.04f, bottom = 0.3f)
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
                        error = appLogoPainter,
                        fallback = appLogoPainter,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(if (content.imageKind == ShelfImageKind.POSTER) 14.dp else 0.dp))
                            .then(
                                if (content.imageKind == ShelfImageKind.POSTER) Modifier.fadeEdges(left = 0.18f, top = 0.1f, right = 0.12f, bottom = 0.18f)
                                else Modifier
                            )
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
            // Puan yalnızca TMDB'den (sağlayıcının puanı gösterilmez).
            val shelfRating = content.item?.displayRating()?.takeIf { it > 0.0 }
            val meta = listOfNotNull(
                shelfRating?.let { "★ " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
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
