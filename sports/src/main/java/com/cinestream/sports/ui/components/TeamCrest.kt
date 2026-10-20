package com.cinestream.sports.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinestream.sports.model.CrestPattern
import com.cinestream.sports.model.TeamColors
import kotlin.math.min

/**
 * Takım arması yerine sade bir kalkan: takım renkleri ve deseniyle çizilir. Kulüp logosu kullanılmaz
 * (marka hakkı riski yok, internetten görsel indirilmez).
 */
@Composable
internal fun TeamCrest(colors: TeamColors, modifier: Modifier = Modifier, crestSize: Dp = 26.dp) {
    val primary = Color(colors.primary)
    val secondary = Color(colors.secondary)
    Canvas(modifier.size(crestSize)) {
        if (colors.pattern == CrestPattern.ROUND) drawRoundCrest(primary, secondary) else drawShield(colors.pattern, primary, secondary)
    }
}

private fun DrawScope.drawRoundCrest(primary: Color, secondary: Color) {
    val w = size.width
    val h = size.height
    val r = min(w, h) / 2f * 0.92f
    val circle = Path().apply { addOval(Rect(center, r)) }
    clipPath(circle) {
        drawRect(primary)
        drawPath(Path().apply { moveTo(w, 0f); lineTo(w, h); lineTo(0f, h); close() }, secondary)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = h * 0.55f))
    }
    drawCircle(Color.Black.copy(alpha = 0.40f), radius = r, center = center, style = Stroke(w * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.20f), radius = r * 0.52f, center = center, style = Stroke(w * 0.05f))
}

private fun DrawScope.drawShield(pattern: CrestPattern, primary: Color, secondary: Color) {
    val w = size.width
    val h = size.height
    val shield = Path().apply {
        moveTo(w * 0.10f, h * 0.10f)
        cubicTo(w * 0.36f, h * 0.02f, w * 0.64f, h * 0.02f, w * 0.90f, h * 0.10f)
        lineTo(w * 0.90f, h * 0.50f)
        cubicTo(w * 0.90f, h * 0.76f, w * 0.70f, h * 0.90f, w * 0.50f, h * 0.98f)
        cubicTo(w * 0.30f, h * 0.90f, w * 0.10f, h * 0.76f, w * 0.10f, h * 0.50f)
        close()
    }
    clipPath(shield) {
        drawRect(primary)
        when (pattern) {
            CrestPattern.SOLID -> drawRect(secondary, Offset(0f, 0f), Size(w, h * 0.24f))
            CrestPattern.HALVES -> drawRect(secondary, Offset(w / 2f, 0f), Size(w / 2f, h))
            CrestPattern.STRIPES -> {
                val stripe = w / 5f
                drawRect(secondary, Offset(stripe, 0f), Size(stripe, h))
                drawRect(secondary, Offset(stripe * 3, 0f), Size(stripe, h))
            }
            CrestPattern.HOOPS -> {
                val band = h / 5f
                drawRect(secondary, Offset(0f, band), Size(w, band))
                drawRect(secondary, Offset(0f, band * 3), Size(w, band))
            }
            CrestPattern.SASH -> drawPath(
                Path().apply {
                    moveTo(0f, 0f); lineTo(w * 0.32f, 0f); lineTo(w, h * 0.68f)
                    lineTo(w, h); lineTo(w * 0.68f, h); lineTo(0f, h * 0.32f); close()
                },
                secondary
            )
            CrestPattern.QUARTERS -> {
                drawRect(secondary, Offset(w / 2f, 0f), Size(w / 2f, h / 2f))
                drawRect(secondary, Offset(0f, h / 2f), Size(w / 2f, h / 2f))
            }
            CrestPattern.CHEVRON -> drawPath(
                Path().apply {
                    moveTo(0f, h * 0.28f); lineTo(w * 0.5f, h * 0.60f); lineTo(w, h * 0.28f)
                    lineTo(w, h * 0.48f); lineTo(w * 0.5f, h * 0.80f); lineTo(0f, h * 0.48f); close()
                },
                secondary
            )
            CrestPattern.ROUND -> Unit
        }
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent), endY = h * 0.55f))
    }
    drawPath(shield, Color.Black.copy(alpha = 0.45f), style = Stroke(width = w * 0.06f))
}
