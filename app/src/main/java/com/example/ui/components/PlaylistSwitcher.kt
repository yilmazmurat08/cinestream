package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Playlist
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.CineOrange

/** Ana ekranın üstündeki liste seçici: ekrandaki listenin adı; dokununca listeler açılır. */
@Composable
fun PlaylistSwitcherChip(
    activeName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desc = stringResource(R.string.playlist_switcher_desc)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = AccentNeonPurple.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, AccentNeonPurple.copy(alpha = 0.35f)),
        modifier = modifier
            .height(30.dp)
            .widthIn(max = 260.dp)
            .testTag("playlist_switcher_chip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp)
        ) {
            Icon(Icons.Default.PlaylistPlay, contentDescription = desc, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = activeName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Listelerim: ekrandaki liste işaretli; başka listeye dokununca anında geçilir. "Liste ekle" ücretsiz sürümde
 * ikinci liste için PRO rozeti taşır (dokununca satın alma ekranı açılır).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSwitcherSheet(
    playlists: List<Playlist>,
    activeId: Int?,
    addNeedsPro: Boolean,
    onSelect: (Playlist) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1B1824)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("playlist_switcher_sheet")
        ) {
            Text(
                text = stringResource(R.string.playlist_switcher_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            playlists.forEach { playlist ->
                val active = playlist.id == activeId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(playlist) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("playlist_option_${playlist.id}")
                ) {
                    Icon(
                        imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (active) CineOrange else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (playlist.isXtream || !playlist.xtreamUsername.isNullOrBlank()) "Xtream Codes" else "M3U",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                    if (active) {
                        Text(
                            text = stringResource(R.string.playlist_switcher_active),
                            color = CineOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("playlist_switcher_add")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.playlist_switcher_add),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (addNeedsPro) ProChip()
            }
            Text(
                text = stringResource(R.string.playlist_switcher_hint),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ProChip(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.playlist_switcher_pro),
        color = CineOrange,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CineOrange.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
