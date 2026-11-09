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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    var tab by rememberSaveable { mutableStateOf("ALL") }
    val firstFocus = remember { FocusRequester() }
    val removedText = stringResource(R.string.tv_removed_from_saved)
    // OK'e basılı tutunca açılan "Listeden kaldır" penceresi (kart adı + kaldırma işi).
    var removeTarget by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var refocusTick by remember { mutableStateOf(0) }
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
        // "Nerede kalmıştık" ana ekranda (kartların altında); burada yalnızca kullanıcının kaydettikleri.
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("tv_saved_grid")
        ) {
            if (shown.isEmpty()) {
                item(key = "saved_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.watchlist_empty_title), color = TvTheme.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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

    removeTarget?.let { (title, remove) ->
        val close: () -> Unit = {
            removeTarget = null
            refocusTick++
        }
        TvItemMenuDialog(
            title = title,
            actions = listOf(
                TvMenuAction(stringResource(R.string.tv_remove_from_list), "tv_remove_confirm", primary = true) {
                    remove()
                    close()
                },
                TvMenuAction(stringResource(R.string.game_wheel_cancel), "tv_remove_cancel") { close() }
            ),
            onDismiss = close,
            tag = "tv_remove_dialog"
        )
    }
    // Pencere kapanınca odak listede kalsın (kaldırılan kart yoksa şeridin ya da listenin ilk kartına).
    LaunchedEffect(refocusTick) {
        if (refocusTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        val target = if (shown.isNotEmpty()) gridFirstFocus else firstFocus
        requestFocusWhenReady(target)
    }
}
