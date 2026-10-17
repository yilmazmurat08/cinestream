package com.example.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.IPTVItem

/**
 * TV ana sayfasının üst barındaki arama. Kapalıyken PRO'nun solunda tek bir arama simgesi; OK ile yerinde
 * sola doğru ince bir arama çubuğuna açılır (✨ yapay zekâ düğmesi hemen görünür). Yazmaya başlayınca altında
 * küçük bir panel açılır ve bilgi mesajı okunur; klavyede Ara/OK'a basınca sonuçlar aynı panelde listelenir.
 * Ayrı sayfa açılmaz; her şey kumandayla gezilir. Geri: aramayı kapatır, odak arama simgesine döner.
 */
@Composable
fun TvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onAiSearch: (String) -> Unit,
    isAiLoading: Boolean,
    results: List<IPTVItem>,
    onItemClick: (IPTVItem) -> Unit,
    buttonFocus: FocusRequester,
    onFocused: () -> Unit,
    buttonModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Sonuçlar klavyede Ara'ya (ya da ✨'e) basılınca gösterilir; yazı değişince yeniden gizlenir.
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Simgeden çubuğa geçerken odak bir an hiçbir yerde olmaz; çubuk bir kez odak almadan kapanma denetlenmez.
    var barFocusedOnce by remember { mutableStateOf(false) }
    var regionFocused by remember { mutableStateOf(false) }
    // Geri ile kapanınca odak arama simgesine döner; odak başka yere gidince kapanırsa oraya dokunulmaz.
    var returnFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun collapse(clear: Boolean, refocus: Boolean = clear) {
        returnFocus = refocus
        expanded = false
        barFocusedOnce = false
        if (clear) {
            submitted = false
            onQueryChange("")
        }
    }

    BackHandler(enabled = expanded) { collapse(clear = true) }

    LaunchedEffect(expanded) {
        if (expanded) {
            requestFocusWhenReady(fieldFocus)
            keyboard?.show()
        } else if (returnFocus) {
            returnFocus = false
            requestFocusWhenReady(buttonFocus)
        }
    }

    // Odak arama alanının dışına çıkınca (ör. aşağıdaki kartlara) arama kapanır; yazılan korunur. Odak yazı alanından
    // sonuca geçerken bir an "odak yok" görünür; bu yüzden kısa bir süre sonra hâlâ dışarıdaysa kapanır.
    LaunchedEffect(regionFocused, barFocusedOnce) {
        if (!regionFocused && barFocusedOnce && expanded) {
            kotlinx.coroutines.delay(120)
            collapse(clear = false)
        }
    }

    val width by animateDpAsState(if (expanded) 460.dp else 44.dp, tween(220), label = "tvSearchWidth")

    Column(
        modifier = modifier
            .width(width)
            .onFocusChanged { regionFocused = it.hasFocus }
            .testTag("tv_search_bar")
    ) {
        if (!expanded) {
            TvGlassButton(
                onClick = { expanded = true },
                shape = TvTheme.PillShape,
                focusRequester = buttonFocus,
                onFocused = onFocused,
                modifier = buttonModifier.size(44.dp).testTag("tv_top_search")
            ) { f ->
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.tv_search),
                    tint = if (f) TvTheme.FocusGlow else TvTheme.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            SearchField(
                query = query,
                onQueryChange = {
                    if (it != query) submitted = false
                    onQueryChange(it)
                },
                onSubmit = {
                    submitted = true
                    keyboard?.hide()
                    onSubmit(query)
                },
                onAiSearch = {
                    submitted = true
                    keyboard?.hide()
                    onAiSearch(query)
                },
                isAiLoading = isAiLoading,
                fieldFocus = fieldFocus,
                onFieldFocused = {
                    barFocusedOnce = true
                    onFocused()
                }
            )
        }

        AnimatedVisibility(
            visible = expanded && query.isNotBlank(),
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(160))
        ) {
            SearchDropdown(
                showResults = submitted,
                results = results,
                isAiLoading = isAiLoading,
                onItemClick = { item ->
                    collapse(clear = true, refocus = false)
                    onItemClick(item)
                }
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAiSearch: () -> Unit,
    isAiLoading: Boolean,
    fieldFocus: FocusRequester,
    onFieldFocused: () -> Unit
) {
    var fieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(TvTheme.PillShape)
            .background(TvTheme.Glass)
            .border(
                if (fieldFocused) 2.dp else 1.dp,
                if (fieldFocused) TvTheme.FocusGlow else Color.White.copy(alpha = 0.16f),
                TvTheme.PillShape
            )
            .padding(start = 16.dp, end = 4.dp)
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = TvTheme.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.search_popup_placeholder),
                    color = TvTheme.TextMuted,
                    fontSize = 16.sp,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TvTheme.TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(TvTheme.FocusGlow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    // Aşağı: sonuç listesine iner (yazı alanı Aşağı'yı kendisi tutmasın).
                    .onPreviewKeyEvent { event ->
                        event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown &&
                            focusManager.moveFocus(FocusDirection.Down)
                    }
                    .onFocusChanged {
                        fieldFocused = it.isFocused
                        if (it.isFocused) onFieldFocused()
                    }
                    .testTag("tv_search_input")
            )
        }
        if (query.isNotEmpty()) {
            TvSmallIconButton(
                onClick = { onQueryChange("") },
                tag = "tv_search_clear"
            ) { f ->
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = if (f) TvTheme.FocusGlow else TvTheme.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (isAiLoading) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TvTheme.Accent, strokeWidth = 2.dp)
            }
        } else {
            TvSmallIconButton(onClick = onAiSearch, tag = "tv_search_ai") { f ->
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.search_ai_desc),
                    tint = if (f) TvTheme.FocusGlow else TvTheme.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Çubuğun içindeki küçük yuvarlak düğme (temizle, ✨). */
@Composable
private fun TvSmallIconButton(
    onClick: () -> Unit,
    tag: String,
    content: @Composable (focused: Boolean) -> Unit
) {
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.PillShape,
        glassColor = Color.Transparent,
        focusScale = 1.1f,
        modifier = Modifier.size(36.dp).testTag(tag)
    ) { f -> content(f) }
}

@Composable
private fun SearchDropdown(
    showResults: Boolean,
    results: List<IPTVItem>,
    isAiLoading: Boolean,
    onItemClick: (IPTVItem) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .tvGlass(shape = TvTheme.RowShape, color = TvTheme.GlassDark)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("tv_search_dropdown")
    ) {
        Text(
            text = stringResource(R.string.search_ai_hint),
            color = TvTheme.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
        if (showResults && !isAiLoading) {
            Spacer(Modifier.height(10.dp))
            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_no_results),
                    color = TvTheme.TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 6.dp).testTag("tv_search_empty")
                )
            } else {
                // Panel çok büyümez: en fazla ~4 satır görünür, gerisi kumandayla kaydırılır.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp).testTag("tv_search_results"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(results, key = { "${it.type}_${it.playlistId}_${it.id}" }) { item ->
                        SearchResultRow(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: IPTVItem, onClick: () -> Unit) {
    TvGlassButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        focusScale = 1.02f,
        glassColor = Color.White.copy(alpha = 0.04f),
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().height(64.dp).testTag("tv_search_result")
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            AsyncImage(
                model = item.logoUrl ?: "",
                contentDescription = null,
                modifier = Modifier
                    .size(width = 36.dp, height = 50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.cleanedName.ifEmpty { item.name },
                    color = TvTheme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOf(typeLabel(item.type), item.category).filter { it.isNotBlank() }.joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = TvTheme.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun typeLabel(type: String): String = when (type) {
    "MOVIE" -> stringResource(R.string.watchlist_type_movie)
    "SERIES" -> stringResource(R.string.watchlist_type_series)
    "LIVE", "LIVE_TV" -> stringResource(R.string.watchlist_type_live)
    "RADIO" -> stringResource(R.string.radio)
    else -> ""
}
