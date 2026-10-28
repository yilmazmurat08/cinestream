package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Görselin kenarlarını yumuşakça saydamlaştırır: görsel keskin bir çerçeve yerine arka plana gömülü gibi görünür.
 * Değerler, kenardan içeri doğru solmanın genişliğidir (görsel boyutunun oranı, 0 = solma yok).
 */
fun Modifier.fadeEdges(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (left > 0f) drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, left to Color.Black),
                blendMode = BlendMode.DstIn
            )
            if (right > 0f) drawRect(
                Brush.horizontalGradient((1f - right) to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn
            )
            if (top > 0f) drawRect(
                Brush.verticalGradient(0f to Color.Transparent, top to Color.Black),
                blendMode = BlendMode.DstIn
            )
            if (bottom > 0f) drawRect(
                Brush.verticalGradient((1f - bottom) to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn
            )
        }
