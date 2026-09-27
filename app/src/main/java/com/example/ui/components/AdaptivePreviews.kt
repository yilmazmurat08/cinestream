package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IPTVItem
import com.example.ui.screens.MediaCard
import com.example.ui.screens.PosterBottomTextCard
import com.example.ui.theme.DeepPurpleBg
import com.example.ui.theme.rememberAppAdaptiveLayout

@Composable
fun AdaptiveSampleGridPreview() {
    val layout = rememberAppAdaptiveLayout()
    val sampleItems = List(6) { index ->
        IPTVItem(
            id = index,
            playlistId = 1,
            name = "Örnek İçerik ${index + 1} - Çok Uzun Başlık Metni Taşma Testi",
            cleanedName = "Örnek İçerik ${index + 1} - Çok Uzun Başlık Metni Taşma Testi",
            logoUrl = "",
            streamUrl = "",
            category = "Sinema",
            type = "MOVIE",
            rating = 8.5
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepPurpleBg)
            .padding(layout.screenPadding)
    ) {
        Text(
            text = "Duyarlı Grid (${layout.widthSizeClass.toString().replace("WindowWidthSizeClass.", "")})",
            color = Color.White,
            fontSize = layout.titleFontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Sütun Sayısı: ${layout.gridColumns} | Kart Genişliği: ${layout.cardWidth}",
            color = Color.LightGray,
            fontSize = layout.subtitleFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.gridColumns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sampleItems.size, key = { sampleItems[it].id }, contentType = { "media_card" }) { index ->
                MediaCard(
                    item = sampleItems[index],
                    onClick = {},
                    onLongClick = {}
                )
            }
        }
    }
}

@Preview(name = "Compact Phone (360dp)", widthDp = 360, heightDp = 800)
@Composable
fun PreviewPhone360dp() {
    AdaptiveSampleGridPreview()
}

@Preview(name = "Foldable / Small Tablet (600dp)", widthDp = 600, heightDp = 900)
@Composable
fun PreviewFoldable600dp() {
    AdaptiveSampleGridPreview()
}

@Preview(name = "Large Tablet (840dp)", widthDp = 840, heightDp = 1200)
@Composable
fun PreviewTablet840dp() {
    AdaptiveSampleGridPreview()
}
