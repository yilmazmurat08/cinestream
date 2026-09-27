package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppTheme {
    SYSTEM,
    PURE_BLACK,
    SYSTEM_LIGHT
}

val LocalAppTheme = staticCompositionLocalOf { AppTheme.PURE_BLACK }
val LocalSelectedAppTheme = staticCompositionLocalOf { AppTheme.SYSTEM }

// Extension properties for easy, clean adaptation of frosted glass style across screens
val AppTheme.isDark: Boolean
    get() = this == AppTheme.PURE_BLACK

val AppTheme.frostedBackground: Color
    get() = if (this.isDark) MidPurpleBg.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.72f)

val AppTheme.frostedBorderColor: Color
    get() = if (this.isDark) AccentNeonPurple.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f)

val AppTheme.frostedBlurRadius: Dp
    get() = if (this.isDark) 20.dp else 12.dp

val AppTheme.ambientBlurAlpha: Float
    get() = if (this.isDark) 0.2f else 0.04f

private val PureBlackColorScheme = darkColorScheme(
    primary = AccentNeonPurple,
    onPrimary = WhiteText,
    secondary = PalePurpleText,
    onSecondary = WhiteText,
    background = DeepPurpleBg,
    onBackground = WhiteText,
    surface = MidPurpleBg,
    onSurface = WhiteText,
    surfaceVariant = MidPurpleBg.copy(alpha = 0.8f),
    onSurfaceVariant = PalePurpleText,
    outline = AccentNeonPurple.copy(alpha = 0.2f)
)

private val SystemLightColorScheme = lightColorScheme(
    primary = AccentNeonPurple,
    onPrimary = Color.White,
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    background = Color(0xFFF8F7FF),
    onBackground = Color(0xFF1E1035),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1035),
    surfaceVariant = Color(0xFFF3E8FF),
    onSurfaceVariant = Color(0xFF6B21A8),
    outline = AccentNeonPurple.copy(alpha = 0.25f)
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val effectiveTheme = when (appTheme) {
        AppTheme.SYSTEM -> if (systemInDark) AppTheme.PURE_BLACK else AppTheme.SYSTEM_LIGHT
        AppTheme.PURE_BLACK -> AppTheme.PURE_BLACK
        AppTheme.SYSTEM_LIGHT -> AppTheme.SYSTEM_LIGHT
    }
    val darkTheme = effectiveTheme.isDark
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                // Material 3 dynamic dark color scheme harmonized with signature deep purple tones
                val dynamicDark = dynamicDarkColorScheme(context)
                dynamicDark.copy(
                    primary = AccentNeonPurple,
                    background = DeepPurpleBg,
                    surface = MidPurpleBg,
                    surfaceVariant = MidPurpleBg.copy(alpha = 0.8f),
                    onBackground = WhiteText,
                    onSurface = WhiteText
                )
            } else {
                // Material 3 dynamic light color scheme with purple accent alignment
                val dynamicLight = dynamicLightColorScheme(context)
                dynamicLight.copy(
                    primary = AccentNeonPurple,
                    surfaceVariant = Color(0xFFF3E8FF),
                    outline = AccentNeonPurple.copy(alpha = 0.25f)
                )
            }
        }
        darkTheme -> PureBlackColorScheme
        else -> SystemLightColorScheme
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val clampedDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
    )

    CompositionLocalProvider(
        LocalAppTheme provides effectiveTheme,
        LocalSelectedAppTheme provides appTheme,
        androidx.compose.ui.platform.LocalDensity provides clampedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = { com.example.ui.tv.ProvideTvFocus(content) }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }


