package com.example.ui.components
import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.IPTVItem
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.CineOrange

@Composable
fun SearchPopupDialog(
    isOpen: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onAISearchClick: () -> Unit,
    onVoiceSearchClick: (() -> Unit)? = null,
    isAILoading: Boolean,
    searchResults: List<IPTVItem>,
    onItemClick: (IPTVItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Alttan açılan panel: üstte tutma çubuğu, tam genişlikte sade arama alanı, nötr gri tonlar.
        // Panelin üstündeki karartılmış alana dokununca kapanır; TV'de Geri tuşuyla.
        // Panel alt bar gibi yarı saydam; arkadaki ekran bulanıklaşır (Android 12+).
        val blurred = rememberDialogBackdropBlur()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            SearchSheet(
                searchQuery, onQueryChange, onSearchSubmit, onAISearchClick, isAILoading, searchResults, onItemClick, onDismiss,
                color = frostedPanelColor(blurred),
                border = androidx.compose.foundation.BorderStroke(1.dp, FrostedPanelBorder)
            )
        }
    }
}

/** Arama panelinin kendisi (pencere olmadan; ekran görüntüsü testleri de bunu çizer). */
@Composable
internal fun SearchSheet(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onAISearchClick: () -> Unit,
    isAILoading: Boolean,
    searchResults: List<IPTVItem>,
    onItemClick: (IPTVItem) -> Unit,
    onDismiss: () -> Unit,
    color: Color = SearchSheetColor,
    border: androidx.compose.foundation.BorderStroke? = null
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = color,
        border = border,
        modifier = Modifier
            // Yatayda (TV, tablet) panel ortada ve sınırlı genişlikte; telefonda tam genişlik.
            .then(if (layout.isLandscape) Modifier.widthIn(max = 640.dp) else Modifier)
            .fillMaxWidth()
            .fillMaxHeight(if (layout.isLandscape) 0.92f else 0.80f)
            // Panelin içine dokunmak onu kapatmaz.
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("search_popup_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            // Tutma çubuğu (dokununca kapanır).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 18.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.28f))
                    .clickable(onClick = onDismiss)
                    .testTag("search_sheet_handle")
            )

            // Arama alanı: panelin tam genişliği.
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(SearchCursorColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(searchQuery) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("popup_search_input"),
                decorationBox = { inner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                            .padding(start = 18.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_desc),
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    stringResource(R.string.search_popup_placeholder),
                                    color = Color.White.copy(alpha = 0.42f),
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (isAILoading) {
                            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White.copy(alpha = 0.7f),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = onAISearchClick, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.search_ai_desc),
                                    tint = AccentNeonPurple.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )

            Text(
                text = stringResource(R.string.search_ai_hint),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Results list or empty state
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(R.string.search_type_prompt) else stringResource(R.string.search_no_results),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Katalog dizisinin numarası bir kanalın numarasıyla aynı olabilir: anahtar türü de içerir.
                    items(searchResults, key = { "${it.type}_${it.playlistId}_${it.id}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable { onItemClick(item) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.logoUrl ?: "",
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size( width = 50.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.cleanedName.ifEmpty { item.name },
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = listOf(typeLabel(item.type), item.category).filter { it.isNotBlank() }.joinToString(" • "),
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { onItemClick(item) }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.action_play),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Arama penceresinin renkleri: nötr koyu gri panel, açık turkuaz imleç. */
private val SearchSheetColor = Color(0xFF2E2A2D)
private val SearchCursorColor = Color(0xFF6FE3D8)

@Composable
private fun typeLabel(type: String): String = when (type) {
    "MOVIE" -> stringResource(R.string.watchlist_type_movie)
    "SERIES" -> stringResource(R.string.watchlist_type_series)
    "LIVE", "LIVE_TV" -> stringResource(R.string.watchlist_type_live)
    "RADIO" -> stringResource(R.string.radio)
    else -> ""
}
