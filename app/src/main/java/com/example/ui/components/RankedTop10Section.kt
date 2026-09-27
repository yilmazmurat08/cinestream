package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IPTVItem
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.CineOrange

@Composable
fun RankedTop10Section(
    title: String,
    items: List<IPTVItem>,
    onItemClick: (IPTVItem) -> Unit,
    onItemLongClick: ((IPTVItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val top10Items = items.filterNotNull().take(10)
    if (top10Items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("top_10_section_${title.take(10).lowercase().replace(" ", "_")}")
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.screenPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryAdaptiveIcon(
                category = title,
                type = if (title.contains("Canlı", ignoreCase = true)) "LIVE" else if (title.contains("Dizi", ignoreCase = true)) "SERIES" else if (title.contains("Film", ignoreCase = true)) "MOVIE" else null,
                size = 18.dp,
                containerColor = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontal Top 10 List
        LazyRow(
            contentPadding = PaddingValues(start = layout.screenPadding, end = layout.screenPadding + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(top10Items, key = { _, item -> item.id }) { index, item ->
                RankedCard(
                    index = index,
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick?.invoke(item) }
                )
            }
        }
    }
}

@Composable
fun RankedCard(
    index: Int,
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    val cardWidth = layout.cardWidth
    val overallWidth = cardWidth + 40.dp

    Box(
        modifier = modifier
            .width(overallWidth)
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .testTag("ranked_card_${index + 1}")
    ) {
        // Sol Katman: Devasa Conturlu Numara (1, 2, 3... 10)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = (-20).dp)
        ) {
            // Subtle background fill for contrast
            Text(
                text = "${index + 1}",
                fontSize = 110.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.align(Alignment.Center)
            )

            // Neon stroke outline text
            Text(
                text = "${index + 1}",
                fontSize = 110.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(
                    color = AccentNeonPurple.copy(alpha = 0.7f),
                    drawStyle = Stroke(
                        width = 8f,
                        join = StrokeJoin.Round
                    )
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Sağ Katman: Poster Kartı & Başlık/Tür Bilgisi (Numaranın sağ üzerine 35.dp offset ile biner)
        Column(
            modifier = Modifier
                .offset(x = 35.dp)
                .width(cardWidth)
        ) {
            // Poster Image Box
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1A34)),
                border = BorderStroke(1.dp, AccentNeonPurple.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SafeAsyncImage(
                        model = item.logoUrl ?: "",
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Optional Rating Badge
                    if (item.rating > 0.0) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(bottomStart = 8.dp),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format("%.1f", item.rating),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Play Button Circle on Bottom Right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(CineOrange, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Film/Dizi/Kanal Adı
            Text(
                text = item.cleanedName.ifEmpty { item.name },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Tür / Kategori Bilgisi
            Text(
                text = item.category.ifEmpty { item.type },
                color = AccentNeonPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
