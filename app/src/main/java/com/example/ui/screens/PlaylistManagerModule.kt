package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Playlist
import com.example.ui.IPTVViewModel
import com.example.ui.theme.CineOrange

/**
 * Liste Yönetimi: uygulamadaki bütün liste işlemleri burada, tek yerde (telefon ve TV Ayarları).
 * Üç yuva vardır: 1. yuva ücretsiz, 2. ve 3. yuva PRO. Her dolu yuvada aç (ekrana getir), yenile, düzenle, sil;
 * boş yuvada liste ekle. PRO olmayan kullanıcıda 2. ve 3. yuva kilitlidir: boşsa "PRO ile aç", doluysa liste
 * silinmeden bekler (düzenlenebilir, silinebilir ama açılamaz).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistManagerModule(viewModel: IPTVViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val activePlaylist by viewModel.activePlaylist.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val isSwitching by viewModel.isSwitchingPlaylist.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    // Yuva sırası eklenme sırasıdır; liste açılınca yerleri değişmez.
    val ordered = playlists.sortedBy { it.id }
    val activeId = activePlaylist?.id ?: playlists.firstOrNull()?.id
    val slotCount = maxOf(IPTVViewModel.PRO_PLAYLIST_LIMIT, ordered.size)

    Column(modifier = modifier.fillMaxWidth().testTag("playlist_manager")) {
        Text(
            text = stringResource(R.string.playlist_manager_title).uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(if (isProUser) R.string.playlist_manager_subtitle_pro else R.string.playlist_manager_subtitle_free),
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (index in 0 until slotCount) {
                val playlist = ordered.getOrNull(index)
                if (playlist != null) {
                    FilledSlot(
                        number = index + 1,
                        playlist = playlist,
                        isActive = playlist.id == activeId,
                        locked = IPTVViewModel.isSlotLocked(index, isProUser),
                        isSwitching = isSwitching && playlist.id != activeId,
                        busy = isLoading,
                        onOpen = {
                            viewModel.switchPlaylist(playlist.id)
                            Toast.makeText(context, context.getString(R.string.playlist_switched_toast, playlist.name), Toast.LENGTH_SHORT).show()
                        },
                        onRefresh = {
                            viewModel.refreshSinglePlaylist(playlist) {
                                Toast.makeText(context, context.getString(R.string.settings_toast_refreshed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUnlock = { viewModel.openPaywall(context.getString(R.string.paywall_reason_more_playlists)) },
                        onEdit = { editing = playlist },
                        onDelete = { deleting = playlist }
                    )
                } else {
                    // Ücretsiz kullanıcıda 1. yuvadan sonrası PRO ister (dokununca satın alma ekranı açılır).
                    EmptySlot(
                        number = index + 1,
                        locked = !isProUser && index >= IPTVViewModel.FREE_PLAYLIST_LIMIT,
                        onClick = { viewModel.requestAddPlaylist { showAdd = true } }
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.playlist_manager_hint),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, top = 10.dp)
        )
    }

    if (showAdd) {
        AddPlaylistDialog(
            onDismiss = { showAdd = false },
            onAddM3U = { name, url ->
                showAdd = false
                viewModel.loadM3UPlaylistFromUrl(name, url)
                Toast.makeText(context, context.getString(R.string.settings_toast_adding_playlist), Toast.LENGTH_SHORT).show()
            },
            onAddXtream = { name, serverUrl, username, password ->
                showAdd = false
                viewModel.loadXtreamPlaylist(name, serverUrl, username, password)
                Toast.makeText(context, context.getString(R.string.settings_toast_adding_xtream), Toast.LENGTH_SHORT).show()
            }
        )
    }
    editing?.let { playlist ->
        EditPlaylistDialog(
            playlist = playlist,
            onDismiss = { editing = null },
            onSave = { p, newName, newUrl, isXtream, username, password ->
                editing = null
                viewModel.updatePlaylistDetails(p, newName, newUrl, isXtream, username, password)
                Toast.makeText(context, context.getString(R.string.settings_toast_updated), Toast.LENGTH_SHORT).show()
            }
        )
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color(0xFF1B1824),
            title = { Text(stringResource(R.string.playlist_delete_confirm_title), color = Color.White) },
            text = { Text(stringResource(R.string.playlist_delete_confirm_message, playlist.name), color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.deletePlaylist(playlist.id)
                        Toast.makeText(context, context.getString(R.string.settings_toast_deleted), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("playlist_delete_confirm")
                ) { Text(stringResource(R.string.playlist_slot_delete), color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel), color = Color.White) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilledSlot(
    number: Int,
    playlist: Playlist,
    isActive: Boolean,
    locked: Boolean,
    isSwitching: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onUnlock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) CineOrange.copy(alpha = 0.10f) else Color(0xFF1B1824),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) CineOrange.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().testTag("playlist_slot_$number")
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.playlist_slot_label, number),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (locked) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CineOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PRO", color = CineOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else if (isActive) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CineOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.playlist_slot_active), color = CineOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = playlist.name,
                color = if (locked) Color.White.copy(alpha = 0.6f) else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (playlist.isXtream || !playlist.xtreamUsername.isNullOrBlank()) "Xtream Codes" else "M3U",
                color = CineOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (locked) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.playlist_slot_locked_filled), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (locked) {
                    SlotButton(Icons.Default.Lock, stringResource(R.string.playlist_slot_locked), "playlist_pro_$number", primary = true, onClick = onUnlock)
                } else {
                    if (!isActive) {
                        SlotButton(Icons.Default.PlayArrow, stringResource(R.string.playlist_slot_open), "playlist_open_$number", primary = true, onClick = onOpen)
                    }
                    SlotButton(Icons.Default.Refresh, stringResource(R.string.playlist_slot_refresh), "playlist_refresh_$number", enabled = !busy, onClick = onRefresh)
                }
                SlotButton(Icons.Default.Edit, stringResource(R.string.playlist_slot_edit), "playlist_edit_$number", onClick = onEdit)
                SlotButton(Icons.Default.Delete, stringResource(R.string.playlist_slot_delete), "playlist_delete_$number", danger = true, onClick = onDelete)
                if (isSwitching) {
                    CircularProgressIndicator(color = CineOrange, strokeWidth = 2.dp, modifier = Modifier.size(20.dp).align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
private fun EmptySlot(number: Int, locked: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(18.dp),
        color = if (focused) CineOrange.copy(alpha = 0.14f) else Color(0xFF15121C),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) CineOrange else Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().testTag(if (locked) "playlist_locked_$number" else "playlist_add_$number")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Icon(
                imageVector = if (locked) Icons.Default.Lock else Icons.Default.Add,
                contentDescription = null,
                tint = if (locked) CineOrange else Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.playlist_slot_label, number), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                Text(
                    text = stringResource(if (locked) R.string.playlist_slot_locked else R.string.playlist_slot_empty),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (locked) {
                Text(
                    text = "PRO",
                    color = CineOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(CineOrange.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** Yuvadaki işlem düğmesi; TV kumandasıyla odaklanınca belirgin çerçeve alır. */
@Composable
private fun SlotButton(
    icon: ImageVector,
    label: String,
    tag: String,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tint = when {
        danger -> Color(0xFFFF6B6B)
        primary -> Color.White
        else -> Color.White.copy(alpha = 0.9f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(12.dp),
        color = when {
            primary -> CineOrange.copy(alpha = if (focused) 0.55f else 0.35f)
            focused -> Color.White.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.07f)
        },
        modifier = Modifier
            .height(36.dp)
            .then(if (focused) Modifier.border(2.dp, CineOrange, RoundedCornerShape(12.dp)) else Modifier)
            .testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
