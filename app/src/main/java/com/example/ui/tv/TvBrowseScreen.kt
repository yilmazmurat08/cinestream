package com.example.ui.tv

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.TvCategory
import com.example.data.repository.TvPoster
import com.example.data.repository.TvSpecialSection
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 60
private const val GRID_COLUMNS = 6
private const val CATEGORY_SETTLE_MS = 400L

/**
 * TV Filmler / Diziler: solda dar kategori listesi (en üstte Top 10 ve En yüksek puanlılar), sağda 6 sütunlu
 * 2:3 poster ızgarası. Kategoriler arasında gezerken ızgara hemen değil, odak kategoride kısa süre durunca
 * güncellenir. Izgaranın ilk sütunundan Sol, seçili kategoriye döner. Izgara veritabanından sayfa sayfa yüklenir.
 * Kalp/favori odak durağı yoktur; favori detay ekranından eklenir.
 */
@Composable
fun TvBrowseScreen(
    viewModel: IPTVViewModel,
    type: String,
    state: TvAppState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val parentalLock by viewModel.parentalLock.collectAsState()
    val safeSession by viewModel.isSafeSessionActive.collectAsState()
    val covers by viewModel.seriesCoversMap.collectAsState()
    val selectedDetail by viewModel.selectedItem.collectAsState()
    val loadingShow by viewModel.isLoadingLiveShow.collectAsState()

    var categories by remember(type) { mutableStateOf<List<TvCategory>?>(null) }
    var selectedKey by remember(type) {
        mutableStateOf(if (type == "MOVIE") state.movieCategory else state.seriesCategory)
    }
    var pendingKey by remember(type) { mutableStateOf<String?>(null) }
    var lockedCategory by remember { mutableStateOf<TvCategory?>(null) }
    val posters = remember(type) { mutableStateListOf<TvPoster>() }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var focusGridAfterLoad by remember { mutableStateOf(false) }
    val categoryFocus = remember(type) { mutableMapOf<String, FocusRequester>() }
    val posterFocus = remember(type) { mutableMapOf<String, FocusRequester>() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var lastPosterKey by remember(type) { mutableStateOf<String?>(null) }
    fun categoryRequester(key: String) = categoryFocus.getOrPut(key) { FocusRequester() }
    fun posterRequester(key: String) = posterFocus.getOrPut(key) { FocusRequester() }

    fun select(category: TvCategory, focusGrid: Boolean) {
        if (category.isAdult && parentalLock && !safeSession) {
            lockedCategory = category
            return
        }
        if (type == "MOVIE") state.movieCategory = category.key else state.seriesCategory = category.key
        focusGridAfterLoad = focusGrid
        selectedKey = category.key
    }

    LaunchedEffect(type) {
        val list = viewModel.tvCategories(type)
        categories = list
        val initial = list.firstOrNull { it.key == selectedKey }
            ?: list.firstOrNull { it.special == null && !it.isAdult }
            ?: list.firstOrNull()
        if (initial != null) {
            selectedKey = initial.key
            val restore = state.browseFocus
            if (restore == null) requestFocusWhenReady(categoryRequester(initial.key))
        }
    }

    // Odak kategoride kısa süre durunca ızgara güncellenir (hızlı gezinmede her kategori yüklenmez).
    LaunchedEffect(pendingKey) {
        val key = pendingKey ?: return@LaunchedEffect
        delay(CATEGORY_SETTLE_MS)
        val category = categories?.firstOrNull { it.key == key } ?: return@LaunchedEffect
        if (!(category.isAdult && parentalLock && !safeSession)) select(category, focusGrid = false)
    }

    suspend fun loadPage(category: TvCategory, reset: Boolean) {
        if (loading) return
        loading = true
        try {
            if (reset) {
                posters.clear()
                endReached = false
            }
            val page = if (category.special != null) {
                endReached = true
                viewModel.tvSpecial(type, category.special)
            } else {
                viewModel.tvCategoryPage(type, category.key, posters.size, PAGE_SIZE, covers).also {
                    if (it.size < PAGE_SIZE) endReached = true
                }
            }
            val known = posters.mapTo(HashSet()) { it.key }
            posters.addAll(page.filter { known.add(it.key) })
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedKey, categories) {
        val category = categories?.firstOrNull { it.key == selectedKey } ?: return@LaunchedEffect
        loadPage(category, reset = true)
        gridState.scrollToItem(0)
        val restore = state.browseFocus
        when {
            restore != null && posters.any { it.key == restore } -> requestFocusWhenReady(posterRequester(restore))
            focusGridAfterLoad && posters.isNotEmpty() -> requestFocusWhenReady(posterRequester(posters.first().key))
            restore != null -> requestFocusWhenReady(categoryRequester(category.key))
        }
        focusGridAfterLoad = false
        if (restore != null) state.browseFocus = null
    }

    // Sayfalama: sona yaklaşınca sonraki sayfa.
    LaunchedEffect(gridState, selectedKey) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                val category = categories?.firstOrNull { it.key == selectedKey } ?: return@collect
                if (!endReached && !loading && last >= posters.size - GRID_COLUMNS * 3) loadPage(category, reset = false)
            }
    }

    // Detay kapanınca odak açılan postere döner.
    var detailWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selectedDetail == null) {
        if (selectedDetail != null) {
            detailWasOpen = true
        } else if (detailWasOpen) {
            detailWasOpen = false
            lastPosterKey?.let { key -> if (posters.any { it.key == key }) requestFocusWhenReady(posterRequester(key)) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .padding(start = 32.dp, top = 28.dp, end = 32.dp)
            .testTag("tv_browse_$type")
    ) {
        // Kategori listesi
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .testTag("tv_categories")
        ) {
            item {
                Text(
                    stringResource(if (type == "MOVIE") R.string.tv_movies else R.string.tv_series),
                    color = TvTheme.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
                )
            }
            val list = categories
            if (list == null) {
                item { CircularProgressIndicator(color = TvTheme.Accent, modifier = Modifier.padding(16.dp).size(28.dp)) }
            } else {
                items(list, key = { it.key.ifEmpty { "__empty" } }) { category ->
                    val selected = category.key == selectedKey
                    TvGlassButton(
                        onClick = { select(category, focusGrid = true) },
                        shape = TvTheme.RowShape,
                        focusRequester = categoryRequester(category.key),
                        onFocused = { pendingKey = category.key },
                        focusScale = 1.03f,
                        glassColor = if (selected) TvTheme.Accent.copy(alpha = 0.3f) else TvTheme.Glass.copy(alpha = 0.5f),
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            // Sağ: ızgarada son odaklanan postere, yoksa ilk postere (en yakın satıra değil).
                            .focusProperties {
                                val target = (lastPosterKey?.takeIf { k -> posters.any { it.key == k } } ?: posters.firstOrNull()?.key)
                                if (target != null && selected) right = posterRequester(target)
                            }
                            .testTag("tv_category_${category.key}")
                    ) { focused ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                            val icon = when {
                                category.special == TvSpecialSection.TOP10 -> Icons.Filled.TrendingUp
                                category.special == TvSpecialSection.TOP_RATED -> Icons.Filled.Star
                                category.isAdult && parentalLock -> if (safeSession) Icons.Filled.LockOpen else Icons.Filled.Lock
                                else -> null
                            }
                            if (icon != null) {
                                Icon(icon, null, tint = if (focused) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                categoryLabel(category),
                                color = TvTheme.TextPrimary, fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            if (category.special == null) {
                                Text(category.count.toString(), color = TvTheme.TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        // Poster ızgarası
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val category = categories?.firstOrNull { it.key == selectedKey }
            if (posters.isEmpty() && !loading && category != null) {
                Text(
                    stringResource(R.string.home_empty_category),
                    color = TvTheme.TextSecondary, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp, start = 8.dp, end = 8.dp),
                modifier = Modifier.fillMaxSize().testTag("tv_poster_grid")
            ) {
                itemsIndexed(posters, key = { _, p -> p.key }) { index, poster ->
                    val leftTarget = category?.let { categoryRequester(it.key) }
                    TvPosterCard(
                        title = poster.title,
                        posterUrl = poster.posterUrl,
                        focusRequester = posterRequester(poster.key),
                        onFocused = {
                            lastPosterKey = poster.key
                            state.browseFocus = null
                        },
                        onClick = {
                            state.browseFocus = poster.key
                            lastPosterKey = poster.key
                            val show = poster.catalogShow
                            if (show != null) viewModel.openCatalogShow(show) else viewModel.selectItem(poster.item)
                        },
                        tag = "tv_poster_${poster.key}",
                        modifier = Modifier.focusProperties {
                            if (index % GRID_COLUMNS == 0 && leftTarget != null) left = leftTarget
                        }
                    )
                }
            }
            if (loading && posters.isEmpty() || loadingShow) {
                CircularProgressIndicator(color = TvTheme.Accent, modifier = Modifier.align(Alignment.Center).size(40.dp))
            }
        }
    }

    lockedCategory?.let { category ->
        com.example.ui.components.ParentalPinDialog(
            viewModel = viewModel,
            title = stringResource(R.string.folder_pin_title),
            subtitle = stringResource(R.string.folder_pin_subtitle),
            onDismiss = {
                lockedCategory = null
                scope.launch { requestFocusWhenReady(categoryRequester(category.key)) }
            },
            onSuccess = {
                viewModel.unlockSafeSession(15)
                lockedCategory = null
                if (type == "MOVIE") state.movieCategory = category.key else state.seriesCategory = category.key
                focusGridAfterLoad = true
                selectedKey = category.key
            }
        )
    }
}

@Composable
private fun categoryLabel(category: TvCategory): String = when (category.special) {
    TvSpecialSection.TOP10 -> stringResource(R.string.tv_top10)
    TvSpecialSection.TOP_RATED -> stringResource(R.string.tv_top_rated)
    null -> category.name
}
