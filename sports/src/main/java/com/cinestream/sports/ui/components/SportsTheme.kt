package com.cinestream.sports.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.Locale

/** CineStream'in renkleri: koyu mor zemin, fuşya vurgu, camgöbeği ikincil renk. */
internal object SportsColors {
    val Background = Color(0xFF0E071A)
    val BackgroundTop = Color(0xFF1A0E30)
    val Surface = Color(0xFF180D2C)
    val SurfaceHigh = Color(0xFF241640)
    val Card = Color(0xFF140A25)
    val Stroke = Color(0x1AFFFFFF)
    val StrokeStrong = Color(0x33FFFFFF)
    val Accent = Color(0xFFD946EF)
    val AccentSoft = Color(0x2ED946EF)
    val AccentText = Color(0xFFF0ABFC)
    val Cyan = Color(0xFF00D2FF)
    val Live = Color(0xFFFF4D67)
    val Gold = Color(0xFFF5B83D)
    val Text = Color(0xFFF5F5F7)
    val TextMuted = Color(0xFF9C93B5)
    val TextDim = Color(0xFF6E668A)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val OnAccent = Color(0xFF1A0520)
}

internal val SportsBackground = Brush.verticalGradient(
    0f to SportsColors.BackgroundTop,
    0.35f to SportsColors.Background,
    1f to SportsColors.Background
)

/** Saatlerin gösterileceği saat dilimi (modül dışından verilir). */
internal val LocalSportsZone = staticCompositionLocalOf<ZoneId> { ZoneId.systemDefault() }

internal fun Modifier.glass(shape: Shape, color: Color = SportsColors.Surface, stroke: Color = SportsColors.Stroke): Modifier =
    this.background(color, shape).border(1.dp, stroke, shape)

@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
