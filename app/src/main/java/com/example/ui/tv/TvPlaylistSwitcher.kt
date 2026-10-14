package com.example.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.Playlist
import com.example.ui.components.ProChip

/**
 * TV'de "Listelerim": kumandayla listeler arasında gezilir, OK ile geçilir. Odak açılışta ekrandaki listededir.
 */
@Composable
fun TvPlaylistSwitcherDialog(
    playlists: List<Playlist>,
    activeId: Int?,
    addNeedsPro: Boolean,
    onSelect: (Playlist) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusFor = remember(playlists) { playlists.associate { it.id to FocusRequester() } }
    val addFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        requestFocusWhenReady(activeId?.let { focusFor[it] } ?: focusFor.values.firstOrNull() ?: addFocus)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .tvGlass(TvTheme.CardShape, TvTheme.GlassDark)
                .padding(28.dp)
                .testTag("tv_playlist_switcher")
        ) {
            Text(
                text = stringResource(R.string.playlist_switcher_title),
                color = TvTheme.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            playlists.forEach { playlist ->
                val active = playlist.id == activeId
                TvGlassButton(
                    onClick = { onSelect(playlist) },
                    shape = TvTheme.RowShape,
                    focusRequester = focusFor[playlist.id],
                    focusScale = 1.03f,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("tv_playlist_option_${playlist.id}")
                ) { focused ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                        Icon(
                            imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (active || focused) TvTheme.Accent else TvTheme.TextMuted,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                color = TvTheme.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (playlist.isXtream || !playlist.xtreamUsername.isNullOrBlank()) "Xtream Codes" else "M3U",
                                color = TvTheme.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        if (active) {
                            Text(
                                text = stringResource(R.string.playlist_switcher_active),
                                color = TvTheme.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            TvGlassButton(
                onClick = onAdd,
                shape = TvTheme.RowShape,
                focusRequester = addFocus,
                focusScale = 1.03f,
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("tv_playlist_add")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = TvTheme.TextPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.playlist_switcher_add),
                        color = TvTheme.TextPrimary,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (addNeedsPro) ProChip()
                }
            }
            Text(
                text = stringResource(R.string.playlist_switcher_hint),
                color = TvTheme.TextMuted,
                fontSize = 13.sp
            )
        }
    }
}
