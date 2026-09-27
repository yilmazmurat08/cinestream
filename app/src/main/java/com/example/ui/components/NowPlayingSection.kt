package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.NowPlayingEntry
import com.example.data.model.tmdb.NowPlayingRegionInfo
import com.example.data.repository.NowPlayingRepository
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.SlateGray

/**
 * Ana sayfa "Sinemada Bu Hafta" satırı. Liste kullanıcının ülkesinin sinemalarından gelir; başlığın
 * yanındaki ülke rozetine dokununca ülke değiştirilebilir (ya da otomatiğe dönülebilir).
 * Kütüphanede olan filmlerde "İzle" rozeti vardır ve dokununca doğrudan oynar; olmayanlarda
 * "Vizyonda" rozeti vardır ve dokununca TMDB bilgileriyle detay açılır.
 */
@Composable
fun NowPlayingSection(
    entries: List<NowPlayingEntry>,
    region: NowPlayingRegionInfo?,
    onRegionSelected: (String?) -> Unit,
    onPlay: (IPTVItem) -> Unit,
    onOpen: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val inLibraryCount = entries.count { it.libraryItem != null }
    val english = region?.uiLanguage == "en"
    var showPicker by remember { mutableStateOf(false) }

    val subtitle = when {
        region != null && region.isGlobalFallback ->
            if (english) "No cinema list for ${region.name}, showing worldwide releases."
            else "${region.name} için sinema listesi bulunamadı, dünya genelinde vizyondakiler gösteriliyor."
        inLibraryCount > 0 ->
            if (english) "Now showing in cinemas. $inLibraryCount in your library, tap to play."
            else "Vizyondaki filmler. $inLibraryCount tanesi kütüphanende, dokununca oynar."
        else -> if (english) "Now showing in cinemas." else "Vizyondaki filmler."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("now_playing_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.screenPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (english) "🎬 In Cinemas This Week" else "🎬 Sinemada Bu Hafta",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (region != null) {
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, AccentNeonPurple.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .then(com.example.ui.tv.tvRestorableFocus())
                        .clickable(onClickLabel = if (english) "Change country" else "Ülkeyi değiştir") { showPicker = true }
                        .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
                        .testTag("now_playing_region_chip"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📍 ${region.name}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            text = subtitle,
            color = SlateGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = layout.screenPadding, end = layout.screenPadding, bottom = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(start = layout.screenPadding, end = layout.screenPadding + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(entries, key = { it.tmdbItem.id }) { entry ->
                NowPlayingCard(
                    entry = entry,
                    english = english,
                    width = layout.cardWidth,
                    onClick = {
                        val libraryItem = entry.libraryItem
                        if (libraryItem != null) onPlay(libraryItem) else onOpen(entry.tmdbItem)
                    }
                )
            }
        }
    }

    if (showPicker && region != null) {
        RegionPickerDialog(
            region = region,
            english = english,
            onDismiss = { showPicker = false },
            onSelect = { code ->
                showPicker = false
                onRegionSelected(code)
            }
        )
    }
}

@Composable
private fun RegionPickerDialog(
    region: NowPlayingRegionInfo,
    english: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    val options = remember(region.uiLanguage) {
        NowPlayingRepository.PICKER_REGIONS.map { code -> code to NowPlayingRepository.countryName(code, region.uiLanguage) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (english) "Close" else "Kapat", color = AccentNeonPurple)
            }
        },
        title = {
            Text(
                text = if (english) "Cinema country" else "Sinema ülkesi",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item(key = "auto") {
                    RegionOptionRow(
                        label = (if (english) "Automatic" else "Otomatik") + " (${region.autoName})",
                        selected = region.isAuto,
                        onClick = { onSelect(null) }
                    )
                }
                items(options, key = { it.first }) { (code, name) ->
                    RegionOptionRow(
                        label = name,
                        selected = !region.isAuto && region.code == code,
                        onClick = { onSelect(code) }
                    )
                }
            }
        },
        containerColor = Color(0xFF160E1E)
    )
}

@Composable
private fun RegionOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AccentNeonPurple,
                unselectedColor = Color.White.copy(alpha = 0.5f)
            )
        )
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun NowPlayingCard(
    entry: NowPlayingEntry,
    english: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val inLibrary = entry.libraryItem != null
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClickLabel = if (inLibrary) (if (english) "Play" else "Oynat") else (if (english) "Details" else "Ayrıntılar"),
                onClick = onClick
            )
            .testTag("now_playing_card_${entry.tmdbItem.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1A34))
                .border(
                    width = 1.dp,
                    color = if (inLibrary) AccentNeonPurple.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            SafeAsyncImage(
                model = entry.posterUrl ?: "",
                contentDescription = entry.tmdbItem.cleanedName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (inLibrary) AccentNeonPurple else Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inLibrary) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = if (inLibrary) (if (english) "Play" else "İzle") else (if (english) "In cinemas" else "Vizyonda"),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry.tmdbItem.cleanedName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        if (entry.tmdbItem.rating > 0.0) {
            Text(
                text = "★ " + String.format(java.util.Locale.US, "%.1f", entry.tmdbItem.rating),
                color = SlateGray,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}
