package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.IPTVViewModel
import com.example.ui.IPTVViewModel.LockableCategory
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.CineBorder
import com.example.ui.theme.CineSurface
import com.example.ui.theme.CinematicBackgroundGradient
import com.example.ui.theme.MutedText
import com.example.ui.theme.PinkToPurpleGradient
import java.util.Locale

private val LOCK_TYPES = listOf("LIVE", "MOVIE", "SERIES")

/**
 * Ayarlar > Güvenlik > Kilitli kategoriler. Canlı TV, film ve dizi klasörlerinden hangilerinin PIN ile açılacağını
 * kullanıcı seçer. Elle ayar yoksa yetişkin kategorileri otomatik kilitlidir; elle yapılan ayarlar "elle ayarlandı"
 * olarak işaretlenir.
 */
@Composable
fun ParentalCategoriesScreen(
    viewModel: IPTVViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locks by viewModel.parentalCategoryLocks.collectAsState()
    var lists by remember { mutableStateOf<Map<String, List<LockableCategory>>>(emptyMap()) }

    // Kilit ayarı değişince sayılar yeniden hesaplanır (yalnızca gruplu sayım sorguları).
    LaunchedEffect(locks) {
        lists = LOCK_TYPES.associateWith { viewModel.lockableCategories(it) }
    }

    ParentalCategoriesContent(
        lists = lists,
        anyManual = locks.isNotEmpty(),
        onBack = onBack,
        onToggle = { category, locked -> viewModel.setCategoryLocked(category.type, category.name, locked) },
        onResetAll = { viewModel.resetAllCategoryLocks() },
        modifier = modifier
    )
}

/** Ekranın durumsuz hali: liste ve geri çağrılar dışarıdan verilir (testlenebilir). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParentalCategoriesContent(
    lists: Map<String, List<LockableCategory>>,
    anyManual: Boolean,
    onBack: () -> Unit,
    onToggle: (LockableCategory, Boolean) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by rememberSaveable { mutableStateOf("LIVE") }
    var onlyLocked by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    fun label(type: String) = when (type) {
        "LIVE" -> R.string.category_locks_tab_live
        "MOVIE" -> R.string.category_locks_tab_movies
        else -> R.string.category_locks_tab_series
    }

    val shown = remember(lists, selectedType, onlyLocked, query) {
        val q = query.trim().lowercase(Locale.ROOT)
        lists[selectedType].orEmpty().filter { c ->
            (!onlyLocked || c.locked) && (q.isEmpty() || c.name.lowercase(Locale.ROOT).contains(q))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinematicBackgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("parental_categories_screen")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("category_locks_back")) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.folder_go_back_desc), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.settings_category_locks_title),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.category_locks_intro),
                    color = MutedText, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            item(key = "chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    LOCK_TYPES.forEach { type ->
                        val count = lists[type].orEmpty().count { it.locked }
                        LockChip(
                            text = stringResource(R.string.category_locks_tab_count, stringResource(label(type)), count),
                            selected = selectedType == type,
                            tag = "category_locks_tab_$type",
                            onClick = { selectedType = type }
                        )
                    }
                    LockChip(
                        text = stringResource(R.string.category_locks_only_locked),
                        selected = onlyLocked,
                        tag = "category_locks_only_locked",
                        onClick = { onlyLocked = !onlyLocked }
                    )
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
                            modifier = Modifier.fillMaxWidth().testTag("category_locks_search")
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.category_locks_empty),
                        color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                    )
                }
            } else {
                items(shown, key = { "${it.type}|${it.name}" }) { category ->
                    CategoryLockRow(category = category, onToggle = { locked -> onToggle(category, locked) })
                }
            }
            if (anyManual) {
                item(key = "reset") {
                    TextButton(
                        onClick = onResetAll,
                        modifier = Modifier.padding(top = 12.dp).testTag("category_locks_reset")
                    ) {
                        Text(stringResource(R.string.category_locks_reset), color = AccentNeonPurple, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Kumandayla gezinirken (TV) odaktaki öğeyi mor çerçeveyle gösterir. */
private fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, AccentNeonPurple, shape) else Modifier)
}

@Composable
private fun LockChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .then(
                if (selected) Modifier.background(PinkToPurpleGradient)
                else Modifier.background(CineSurface).border(1.dp, CineBorder, RoundedCornerShape(26.dp))
            )
            .focusRing(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp)
            .testTag(tag)
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CategoryLockRow(category: LockableCategory, onToggle: (Boolean) -> Unit) {
    val tag = "${category.type}_${category.name}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CineSurface)
            .focusRing(RoundedCornerShape(4.dp))
            .clickable { onToggle(!category.locked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("category_lock_row_$tag")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (category.locked) AccentNeonPurple.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = if (category.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (category.locked) AccentNeonPurple else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                text = category.name.ifBlank { "—" },
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val items = stringResource(R.string.category_locks_items, category.count)
            Text(
                text = if (category.manual) "$items · ${stringResource(R.string.category_locks_manual)}" else items,
                color = MutedText, fontSize = 13.sp
            )
        }
        Switch(
            checked = category.locked,
            onCheckedChange = onToggle,
            // Satırın tamamı odak durağıdır (kumanda); anahtarın ayrı bir durağı yoktur.
            modifier = Modifier.focusProperties { canFocus = false }.testTag("category_lock_switch_$tag"),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentNeonPurple,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
    Spacer(Modifier.height(1.dp))
}


/**
 * "Kilitli kategoriler" ekranını açan işlevi döndürür (telefon ve TV ayarlarında ortak). Açmadan önce PIN sorulur;
 * güvenli oturum açıksa sorulmaz. Ekran tam ekran bir pencere olarak açılır, Geri tuşu kapatır.
 */
@Composable
fun rememberCategoryLocksOpener(viewModel: IPTVViewModel): () -> Unit {
    var showScreen by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    if (showPin) {
        com.example.ui.components.ParentalPinDialog(
            viewModel = viewModel,
            title = stringResource(R.string.pin_title),
            subtitle = stringResource(R.string.category_locks_pin_subtitle),
            onDismiss = { showPin = false },
            onSuccess = {
                showPin = false
                showScreen = true
            }
        )
    }
    if (showScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            ParentalCategoriesScreen(viewModel = viewModel, onBack = { showScreen = false }, modifier = Modifier.fillMaxSize())
        }
    }
    return { if (viewModel.isSafeSessionValid()) showScreen = true else showPin = true }
}
