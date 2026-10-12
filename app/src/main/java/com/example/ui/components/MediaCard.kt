package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IPTVItem
import com.example.ui.theme.CineBorder
import com.example.ui.theme.CineGold
import com.example.ui.theme.LocalAppTheme
import com.example.ui.theme.isDark
import com.example.ui.theme.rememberAppAdaptiveLayout

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    item: IPTVItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val layout = rememberAppAdaptiveLayout()
    val defaultModifier = Modifier
        .width(layout.cardWidth)
        .aspectRatio(2f / 3f)
    
    val cardModifier = if (modifier == Modifier) defaultModifier else modifier
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        label = "media_card_scale"
    )
    val currentTheme = LocalAppTheme.current

    // Performans: Formatlama işlemini remember ile sarmalayarak Recomposition yükünü hafifletiyoruz
    val formattedRating = remember(item.rating) {
        String.format("%.1f", item.rating)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
        ),
        modifier = cardModifier
            .scale(scale)
            .border(
                width = 1.dp,
                color = if (currentTheme.isDark) CineBorder else Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .then(com.example.ui.tv.tvRestorableFocus())
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SafeAsyncImage(
                model = item.logoUrl,
                contentDescription = item.cleanedName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dikey karartma katmanı (Scrim)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Alt içerik detayları
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                if (item.type != "LIVE" && item.rating > 0) {
                    Box(
                        modifier = Modifier
                            .background(CineGold, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ $formattedRating",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = item.cleanedName,
                    color = Color.White,
                    fontSize = layout.bodyFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

