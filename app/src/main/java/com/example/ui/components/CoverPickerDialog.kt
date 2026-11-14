package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.repository.CoverOverrideStore
import com.example.data.repository.CoverSuggestion
import com.example.data.repository.CoverSuggestions
import kotlinx.coroutines.launch

private val CoverAccent = Color(0xFFD946EF)

/** Kullanıcının bu yapım için seçtiği kapak (yoksa null). Seçim değişince ekran kendiliğinden yenilenir. */
@Composable
fun rememberCoverOverride(isSeries: Boolean, title: String): String? {
    val overrides by CoverOverrideStore.overrides.collectAsState()
    return overrides[CoverOverrideStore.keyFor(isSeries, title)]?.cover
}

/**
 * "Kapak Görselini Değiştir" penceresi (telefon ve TV). TMDB'de aynı adla bulunan yapımların kapakları ve en yakın
 * yapımın öteki afişleri listelenir; telefonda galeriden resim de seçilebilir. Seçim yalnızca görseli değiştirir.
 */
@Composable
fun CoverPickerDialog(
    title: String,
    isSeries: Boolean,
    originalUrl: String?,
    onDismiss: () -> Unit,
    loadSuggestions: suspend (Context) -> List<CoverSuggestion> = { CoverSuggestions.fetch(it, title, isSeries) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember(title, isSeries) { CoverOverrideStore.keyFor(isSeries, title) }
    val overrides by CoverOverrideStore.overrides.collectAsState()
    val current = overrides[key]
    val tvControls = com.example.ui.tv.TvUiMode.active || remember { com.example.ui.tv.TvDevice.isTv(context) }
    var suggestions by remember { mutableStateOf<List<CoverSuggestion>?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(key) {
        suggestions = runCatching { loadSuggestions(context) }.getOrDefault(emptyList())
        if (tvControls) com.example.ui.tv.requestFocusWhenReady(firstFocus)
    }

    fun choose(cover: String) {
        CoverOverrideStore.set(context.applicationContext, key, originalUrl, cover)
        Toast.makeText(context, R.string.cover_changed, Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = CoverOverrideStore.importFromGallery(context.applicationContext, uri)
            if (path != null) choose(path) else Toast.makeText(context, R.string.cover_import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (tvControls) 0.8f else 0.94f)
                .widthIn(max = if (tvControls) 900.dp else 560.dp)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(22.dp))
                .testTag("cover_picker_dialog"),
            color = Color(0xFF15101F)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text(stringResource(R.string.cover_change), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    val list = suggestions
                    when {
                        list == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CoverAccent, modifier = Modifier.size(34.dp))
                        }
                        list.isEmpty() -> Text(
                            stringResource(R.string.cover_no_suggestions), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 24.dp).testTag("cover_picker_empty")
                        )
                        else -> {
                            Text(
                                stringResource(R.string.cover_suggestions_title), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            // Tembel (lazy) ızgara yerine düz satırlar: TV'de odak henüz çizilmemiş hücreye gidip çökmez.
                            val columns = if (tvControls) 6 else 3
                            list.chunked(columns).forEachIndexed { rowIndex, row ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEachIndexed { index, suggestion ->
                                        CoverCell(
                                            suggestion = suggestion,
                                            selected = current?.cover == suggestion.coverUrl,
                                            onClick = { choose(suggestion.coverUrl) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(if (rowIndex == 0 && index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                                .testTag("cover_option_${rowIndex * columns + index}")
                                        )
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    // TV'de galeri yok: yalnızca TMDB önerileri.
                    if (!tvControls) {
                        CoverAction(Icons.Default.PhotoLibrary, stringResource(R.string.cover_from_gallery), Modifier.weight(1f).testTag("cover_gallery")) {
                            runCatching {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }.onFailure { Toast.makeText(context, R.string.cover_import_failed, Toast.LENGTH_SHORT).show() }
                        }
                    }
                    if (current != null) {
                        CoverAction(Icons.Default.Restore, stringResource(R.string.cover_reset), Modifier.weight(1f).testTag("cover_reset")) {
                            CoverOverrideStore.clear(context.applicationContext, key)
                            Toast.makeText(context, R.string.cover_restored, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    CoverAction(null, stringResource(R.string.close), Modifier.weight(if (tvControls || current != null) 0.6f else 0.5f).testTag("cover_close"), onDismiss)
                }
            }
        }
    }
}

@Composable
private fun CoverCell(suggestion: CoverSuggestion, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(
                    if (focused || selected) 3.dp else 1.dp,
                    if (focused) Color.White else if (selected) CoverAccent else Color.White.copy(alpha = 0.12f),
                    shape
                )
        ) {
            AsyncImage(model = suggestion.thumbUrl, contentDescription = suggestion.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            suggestion.label, color = Color.White.copy(alpha = if (focused) 1f else 0.65f), fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun CoverAction(icon: ImageVector?, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(46.dp)
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Detay ekranındaki küçük "kapağı değiştir" düğmesi (posterin köşesinde). */
@Composable
fun CoverChangeBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("cover_change_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Image, contentDescription = stringResource(R.string.cover_change), tint = Color.White, modifier = Modifier.size(19.dp))
        FeatureTip(FeatureTips.COVER, stringResource(R.string.tip_cover), onClick = onClick)
    }
}
