package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.IPTVViewModel
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.CineBorder
import com.example.ui.theme.CineSurface
import com.example.ui.theme.CinematicBackgroundGradient
import com.example.ui.theme.MutedText
import com.example.ui.theme.PinkToPurpleGradient
import com.example.util.FolderOrder
import com.example.util.FolderPrefs
import java.util.Locale

private val FOLDER_TYPES = listOf("LIVE", "MOVIE", "SERIES")

/** Düzenleme ekranındaki bir klasör satırı. */
data class EditableFolder(val type: String, val name: String, val count: Int)

/**
 * Ayarlar > Liste Yönetimi > Klasörleri düzenle. Canlı TV, film ve dizi klasörlerinden istenenler en üste alınır
 * (sabitleme sırasıyla), istenmeyenler gizlenir. Telefon ve TV'de aynı ekran; TV'de satırdaki iki düğme ayrı odak
 * durağıdır.
 */
@Composable
fun FolderEditScreen(viewModel: IPTVViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by FolderOrder.prefs.collectAsState()
    var lists by remember { mutableStateOf<Map<String, List<EditableFolder>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        lists = FOLDER_TYPES.associateWith { t -> viewModel.lockableCategories(t).map { EditableFolder(t, it.name, it.count) } }
    }
    FolderEditContent(
        lists = lists,
        prefs = prefs,
        onBack = onBack,
        onPin = { f, on -> FolderOrder.setPinned(context, f.type, f.name, on) },
        onHide = { f, on -> FolderOrder.setHidden(context, f.type, f.name, on) },
        onReset = { FolderOrder.reset(context) },
        modifier = modifier
    )
}

/** Öne alınanlar sabitleme sırasıyla üstte, kalanlar ad sırasıyla. */
internal fun orderForEditing(list: List<EditableFolder>, prefs: FolderPrefs): List<EditableFolder> {
    val order = prefs.pinned.withIndex().associate { (i, k) -> k to i }
    val (top, rest) = list.partition { com.example.util.CategoryLocks.key(it.type, it.name) in order }
    return top.sortedBy { order[com.example.util.CategoryLocks.key(it.type, it.name)] } +
        rest.sortedBy { it.name.lowercase(Locale.ROOT) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderEditContent(
    lists: Map<String, List<EditableFolder>>,
    prefs: FolderPrefs,
    onBack: () -> Unit,
    onPin: (EditableFolder, Boolean) -> Unit,
    onHide: (EditableFolder, Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by rememberSaveable { mutableStateOf("LIVE") }
    var query by rememberSaveable { mutableStateOf("") }

    fun label(type: String) = when (type) {
        "LIVE" -> R.string.category_locks_tab_live
        "MOVIE" -> R.string.category_locks_tab_movies
        else -> R.string.category_locks_tab_series
    }

    // Sıra ekran açılırken (ve sekme değişince) bir kez kurulur: dokunulan satır kullanıcının elinin altından kaçmaz.
    val base = remember(lists, selectedType) { orderForEditing(lists[selectedType].orEmpty(), prefs) }
    val shown = remember(base, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) base else base.filter { it.name.lowercase(Locale.ROOT).contains(q) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinematicBackgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("folder_edit_screen")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("folder_edit_back")) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.folder_go_back_desc), tint = Color.White)
            }
            Text(stringResource(R.string.folder_edit_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.folder_edit_intro), color = MutedText, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            item(key = "chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    FOLDER_TYPES.forEach { type ->
                        EditChip(
                            text = stringResource(label(type)),
                            selected = selectedType == type,
                            tag = "folder_edit_tab_$type",
                            onClick = { selectedType = type }
                        )
                    }
                }
            }
            item(key = "search") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CineSurface)
                        .border(1.dp, CineBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MutedText)
                    Box(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.category_locks_search), color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(AccentNeonPurple),
                            modifier = Modifier.fillMaxWidth().testTag("folder_edit_search")
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.category_locks_empty), color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            } else {
                items(shown, key = { "${it.type}|${it.name}" }) { folder ->
                    FolderEditRow(
                        folder = folder,
                        pinned = FolderOrder.isPinned(prefs, folder.type, folder.name),
                        hidden = FolderOrder.isHidden(prefs, folder.type, folder.name),
                        onPin = { onPin(folder, it) },
                        onHide = { onHide(folder, it) }
                    )
                }
            }
            if (!prefs.isEmpty) {
                item(key = "reset") {
                    TextButton(onClick = onReset, modifier = Modifier.padding(top = 12.dp).testTag("folder_edit_reset")) {
                        Text(stringResource(R.string.folder_edit_reset), color = AccentNeonPurple, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun Modifier.editFocusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, AccentNeonPurple, shape) else Modifier)
}

@Composable
private fun EditChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (selected) Modifier.background(PinkToPurpleGradient)
                else Modifier.background(CineSurface).border(1.dp, CineBorder, RoundedCornerShape(26.dp))
            )
            .editFocusRing(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp)
            .testTag(tag)
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun FolderEditRow(folder: EditableFolder, pinned: Boolean, hidden: Boolean, onPin: (Boolean) -> Unit, onHide: (Boolean) -> Unit) {
    val tag = "${folder.type}_${folder.name}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CineSurface)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .testTag("folder_edit_row_$tag")
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                folder.name.ifBlank { "—" },
                color = Color.White.copy(alpha = if (hidden) 0.45f else 1f), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val items = stringResource(R.string.category_locks_items, folder.count)
            val state = when {
                pinned -> " · " + stringResource(R.string.folder_edit_state_pinned)
                hidden -> " · " + stringResource(R.string.folder_edit_state_hidden)
                else -> ""
            }
            Text(items + state, color = if (pinned) AccentNeonPurple else MutedText, fontSize = 13.sp)
        }
        ToggleAction(
            icon = Icons.Default.PushPin,
            label = stringResource(if (pinned) R.string.folder_edit_unpin else R.string.folder_edit_pin),
            active = pinned,
            tag = "folder_pin_$tag",
            onClick = { onPin(!pinned) }
        )
        Spacer(Modifier.size(8.dp))
        ToggleAction(
            icon = if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            label = stringResource(if (hidden) R.string.folder_edit_show else R.string.folder_edit_hide),
            active = hidden,
            tag = "folder_hide_$tag",
            onClick = { onHide(!hidden) }
        )
    }
    Spacer(Modifier.height(1.dp))
}

@Composable
private fun ToggleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(if (active) AccentNeonPurple.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .editFocusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) AccentNeonPurple else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
    }
}

/** Liste Yönetimi'nin altında "Klasörleri düzenle" satırı; dokununca tam ekran düzenleme açılır (telefon ve TV). */
@Composable
fun FolderEditEntry(viewModel: IPTVViewModel, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val prefs by FolderOrder.prefs.collectAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(CineSurface)
            .border(1.dp, CineBorder, shape)
            .editFocusRing(shape)
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("folder_edit_entry")
    ) {
        Icon(Icons.Default.ViewList, contentDescription = null, tint = AccentNeonPurple)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.folder_edit_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                if (prefs.isEmpty) stringResource(R.string.folder_edit_entry_sub)
                else stringResource(R.string.folder_edit_entry_counts, prefs.pinned.size, prefs.hidden.size),
                color = MutedText, fontSize = 13.sp
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MutedText)
    }
    if (open) FolderEditDialog(viewModel) { open = false }
}

/** "Klasörleri düzenle" tam ekran penceresi; Geri kapatır. */
@Composable
fun FolderEditDialog(viewModel: IPTVViewModel, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        FolderEditScreen(viewModel = viewModel, onBack = onClose, modifier = Modifier.fillMaxSize())
    }
}
