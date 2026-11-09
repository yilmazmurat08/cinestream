package com.example.ui.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel

/**
 * TV Kaydedilenler (telefondaki İzleme Listem): üstte Tümü / Filmler / Diziler / Canlı sekmeleri, altında 6 sütunlu
 * poster ızgarası. OK: film/dizi detayı açılır, kanal oynatılır. OK'e basılı tutmak kaydı listeden kaldırır.
 */
@Composable
fun TvSavedScreen(
    viewModel: IPTVViewModel,
    onPlayItem: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favorites by viewModel.favoritesWithSeries.collectAsState()
    val resumeShelf by viewModel.resumeShelf.collectAsState()
    var tab by rememberSaveable { mutableStateOf("ALL") }
    val firstFocus = remember { FocusRequester() }
    val removedText = stringResource(R.string.tv_removed_from_saved)
    val resumeRemovedText = stringResource(R.string.resume_removed)
    // OK'e basılı tutunca açılan "Listeden kaldır" penceresi (kart adı + kaldırma işi).
    var removeTarget by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var refocusTick by remember { mutableStateOf(0) }
    val shelfFirstFocus = remember { FocusRequester() }
    val gridFirstFocus = remember { FocusRequester() }
    val shown = favorites.filter { tab == "ALL" || it.type == tab || (tab == "LIVE" && it.type == "LIVE_TV") }

    LaunchedEffect(Unit) { requestFocusWhenReady(firstFocus) }

    Column(
        modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .padding(start = 48.dp, end = 48.dp, top = 32.dp)
            .testTag("tv_saved_screen")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tv_saved), color = TvTheme.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(28.dp))
            listOf(
                "ALL" to R.string.watchlist_tab_all, "MOVIE" to R.string.tv_movies,
                "SERIES" to R.string.tv_series, "LIVE" to R.string.watchlist_tab_live
            ).forEachIndexed { i, (key, label) ->
                TvGlassButton(
                    onClick = { tab = key },
                    onFocused = { tab = key },
                    shape = TvTheme.PillShape,
                    focusRequester = if (i == 0) firstFocus else null,
                    focusScale = 1.05f,
                    glassColor = if (tab == key) TvTheme.Accent.copy(alpha = 0.4f) else TvTheme.Glass,
                    modifier = Modifier.height(42.dp).padding(end = 10.dp).testTag("tv_saved_tab_$key")
                ) {
                    Text(stringResource(label), color = TvTheme.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 18.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.tv_saved_hint), color = TvTheme.TextMuted, fontSize = 13.sp)
        // "Nerede kalmıştık": yarım kalan son 5 film/dizi; OK kaldığı yerden oynatır.
        if (resumeShelf.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.resume_shelf_title), color = TvTheme.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 4.dp),
                modifier = Modifier.testTag("tv_resume_shelf")
            ) {
                // Anahtar kaydın kendi numarası: aynı itemId iki kayıtta olabilir (liste yenilenince eşleştirme), çakışma çökertir.
                lazyItems(resumeShelf, key = { it.id }) { cw ->
                    val longFired = remember { BooleanArray(1) }
                    val play = { onPlayItem(com.example.continueWatchingItem(cw)) }
                    TvPosterCard(
                        title = cw.itemName,
                        posterUrl = cw.itemLogo,
                        onClick = play, // dokunma
                        subtitle = com.example.util.ResumeShelf.minutesLeft(cw)?.let { stringResource(R.string.resume_minutes_left, it) },
                        tag = "tv_resume_${cw.itemId}",
                        // OK: kaldığı yerden oynat. OK'e basılı tut: şeritten kaldır (izleme kaydı ve kaldığı yer silinmez).
                        focusRequester = if (cw.id == resumeShelf.firstOrNull()?.id) shelfFirstFocus else null,
                        modifier = Modifier.width(104.dp).tvOkLongPress(longFired, onClick = play) {
                            removeTarget = cw.itemName to {
                                viewModel.hideFromResumeShelf(cw)
                                Toast.makeText(context, resumeRemovedText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.watchlist_empty_title), color = TvTheme.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { "${it.type}_${it.playlistId}_${it.id}" }) { item ->
                    val longFired = remember { BooleanArray(1) }
                    val open = {
                        if (item.type == "LIVE" || item.type == "LIVE_TV" || item.type == "RADIO") onPlayItem(item) else viewModel.selectItem(item)
                    }
                    TvPosterCard(
                        title = item.cleanedName.ifBlank { item.name },
                        posterUrl = item.logoUrl,
                        onClick = open, // dokunma
                        tag = "tv_saved_${item.id}",
                        focusRequester = if (item === shown.firstOrNull()) gridFirstFocus else null,
                        modifier = Modifier.tvOkLongPress(longFired, onClick = open) {
                            removeTarget = (item.cleanedName.ifBlank { item.name }) to {
                                viewModel.toggleFavorite(item)
                                Toast.makeText(context, removedText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    removeTarget?.let { (title, remove) ->
        TvRemoveDialog(
            title = title,
            onRemove = {
                remove()
                removeTarget = null
                refocusTick++
            },
            onDismiss = {
                removeTarget = null
                refocusTick++
            }
        )
    }
    // Pencere kapanınca odak listede kalsın (kaldırılan kart yoksa şeridin ya da listenin ilk kartına).
    LaunchedEffect(refocusTick) {
        if (refocusTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        val target = when {
            resumeShelf.isNotEmpty() -> shelfFirstFocus
            shown.isNotEmpty() -> gridFirstFocus
            else -> firstFocus
        }
        requestFocusWhenReady(target)
    }
}

/**
 * Kumanda OK tuşu: kısa basış [onClick], basılı tutma (tuş tekrarı) [onLongPress]. Uzun basıştan sonra tuş bırakılınca
 * [onClick] çalışmaz. [longFired] kartın kendi durumudur (remember ile).
 */
private fun Modifier.tvOkLongPress(longFired: BooleanArray, onClick: () -> Unit, onLongPress: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!ok) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                val native = event.nativeKeyEvent
                if (native.repeatCount == 0 && !native.isLongPress) {
                    longFired[0] = false
                } else if (!longFired[0]) {
                    longFired[0] = true
                    onLongPress()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (!longFired[0]) onClick()
                longFired[0] = false
                true
            }
            else -> false
        }
    }

/**
 * OK'e basılı tutunca açılan küçük pencere: kartın adı, "Listeden kaldır" ve "Vazgeç". Basılı tutulan OK tuşu
 * bırakılana kadar (tuş tekrarları ve bırakma) pencerede yok sayılır; yanlışlıkla kaldırma olmaz.
 */
@Composable
private fun TvRemoveDialog(title: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val removeFocus = remember { FocusRequester() }
    val armed = remember { BooleanArray(1) }
    LaunchedEffect(Unit) { requestFocusWhenReady(removeFocus) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .tvGlass(color = TvTheme.Background.copy(alpha = 0.96f))
                .padding(horizontal = 32.dp, vertical = 26.dp)
                .onPreviewKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (!ok || armed[0]) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        armed[0] = true
                        false
                    } else {
                        true
                    }
                }
                .testTag("tv_remove_dialog")
        ) {
            Text(
                title,
                color = TvTheme.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvGlassButton(
                    onClick = onRemove,
                    shape = TvTheme.PillShape,
                    focusRequester = removeFocus,
                    glassColor = TvTheme.Accent.copy(alpha = 0.85f),
                    modifier = Modifier.width(210.dp).height(52.dp).testTag("tv_remove_confirm")
                ) {
                    Text(stringResource(R.string.tv_remove_from_list), color = TvTheme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                TvGlassButton(
                    onClick = onDismiss,
                    shape = TvTheme.PillShape,
                    modifier = Modifier.width(160.dp).height(52.dp).testTag("tv_remove_cancel")
                ) {
                    Text(stringResource(R.string.game_wheel_cancel), color = TvTheme.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
