package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Neon Purple Color Palette
val NeonPurpleDark = Color(0xFF7E22CE)
val NeonPurpleDeep = Color(0xFF6B21A8)
val NeonPurpleLight = Color(0xFFF3E8FF)
val NeonPurpleBorder1 = Color(0xFFC084FC)
val NeonPurpleBorder2 = Color(0xFFA855F7)
val NeonPurpleBorder3 = Color(0xFFE9D5FF)

@Composable
fun NeonPlayButton(
    text: String = "ŞİMDİ İZLE",
    qualityTag: String? = "FHD",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_play_rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neon_rotation_angle"
    )

    val sweepGradient = Brush.sweepGradient(
        colors = listOf(
            NeonPurpleBorder1,
            NeonPurpleBorder2,
            Color.Transparent,
            NeonPurpleBorder3,
            NeonPurpleBorder1
        )
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(NeonPurpleDark, NeonPurpleDeep)
                )
            )
            .border(
                width = 2.dp,
                brush = sweepGradient,
                shape = RoundedCornerShape(26.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = text,
                tint = NeonPurpleLight,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = NeonPurpleLight,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!qualityTag.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurpleBorder1.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = qualityTag,
                        color = NeonPurpleBorder3,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
