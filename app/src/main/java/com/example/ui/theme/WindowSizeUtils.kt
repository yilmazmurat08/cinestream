package com.example.ui.theme

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp < 600 -> WindowWidthSizeClass.Compact
            configuration.screenWidthDp < 840 -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        }
    }
}

@Composable
fun rememberWindowHeightSizeClass(): WindowHeightSizeClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenHeightDp) {
        when {
            configuration.screenHeightDp < 480 -> WindowHeightSizeClass.Compact
            configuration.screenHeightDp < 900 -> WindowHeightSizeClass.Medium
            else -> WindowHeightSizeClass.Expanded
        }
    }
}

data class AppAdaptiveLayout(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isLandscape: Boolean,
    val isTablet: Boolean,
    val isCompact: Boolean,
    val gridColumns: Int,
    val screenPadding: Dp,
    val horizontalContentPadding: Dp,
    val gridSpacing: Dp,
    val cardWidth: Dp,
    val cardAspectRatio: Float = 2f / 3f,
    val heroBannerHeight: Dp,
    val heroHeight: Dp,
    val headerHeight: Dp,
    val topBarIconSize: Dp,
    val playerControlSize: Dp,
    val playPauseSize: Dp,
    val playerSecondaryControlSize: Dp,
    val progressBarHeight: Dp,
    val controlSpacing: Dp,
    val playerHorizontalPadding: Dp,
    val playerBottomPadding: Dp,
    val bottomBarHeight: Dp,
    val navBarHeight: Dp,
    val dialogMaxWidth: Dp,
    val titleFontSize: TextUnit,
    val subtitleFontSize: TextUnit,
    val bodyFontSize: TextUnit
)

@Composable
fun rememberAppAdaptiveLayout(): AppAdaptiveLayout {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val isLandscape = widthDp > heightDp
    val shortestSideDp = minOf(widthDp, heightDp)

    // Cihaz sınıfı (telefon/tablet) rotasyondan bağımsız, en kısa kenara göre belirlenir.
    val isTabletDevice = shortestSideDp >= 600

    // TELEFONSA rotasyondan etkilenmesin (yatayda tablet ölçüsüne atlamasın) - en kısa
    // kenarı kullan. TABLETSE mevcut yönün gerçek genişliğini kullan; yatay tablet
    // sahip olduğu ekstra alanı (Medium->Expanded) olduğu gibi kullanmaya devam eder.
    val effectiveWidthDpForSizing = if (isTabletDevice) widthDp else shortestSideDp

    val widthSizeClass = remember(effectiveWidthDpForSizing) {
        when {
            effectiveWidthDpForSizing < 600 -> WindowWidthSizeClass.Compact
            effectiveWidthDpForSizing < 840 -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        }
    }
    
    val heightSizeClass = remember(heightDp) {
        when {
            heightDp < 480 -> WindowHeightSizeClass.Compact
            heightDp < 900 -> WindowHeightSizeClass.Medium
            else -> WindowHeightSizeClass.Expanded
        }
    }

    return remember(widthSizeClass, heightSizeClass, widthDp, heightDp, isLandscape) {
        val isTablet = shortestSideDp >= 600
        val isCompact = shortestSideDp < 600

        // Hesaplanan dinamik grid kolon sayısı
        val calculatedColumns = when {
            widthDp < 380 -> 2
            widthDp < 600 -> 3
            widthDp < 840 -> 4
            widthDp < 1080 -> 5
            widthDp < 1400 -> 6
            else -> 8
        }

        // Genişliğe ve yüksekliğe göre baz ölçüler
        val base = when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> {
                val cardW = if (widthDp < 380) 100.dp else 115.dp
                val heroH = if (isLandscape) (heightDp * 0.55f).dp.coerceIn(160.dp, 260.dp) else 240.dp
                AppAdaptiveLayout(
                    widthSizeClass = widthSizeClass,
                    heightSizeClass = heightSizeClass,
                    screenWidthDp = widthDp,
                    screenHeightDp = heightDp,
                    isLandscape = isLandscape,
                    isTablet = false,
                    isCompact = true,
                    gridColumns = calculatedColumns,
                    screenPadding = if (widthDp < 380) 12.dp else 16.dp,
                    horizontalContentPadding = if (widthDp < 380) 12.dp else 16.dp,
                    gridSpacing = 14.dp,
                    cardWidth = cardW,
                    cardAspectRatio = 2f / 3f,
                    heroBannerHeight = heroH,
                    heroHeight = heroH,
                    headerHeight = 52.dp,
                    topBarIconSize = 20.dp,
                    playerControlSize = 42.dp,
                    playPauseSize = 52.dp,
                    playerSecondaryControlSize = 40.dp,
                    progressBarHeight = 4.dp,
                    controlSpacing = 16.dp,
                    playerHorizontalPadding = 16.dp,
                    playerBottomPadding = 12.dp,
                    bottomBarHeight = 64.dp,
                    navBarHeight = 64.dp,
                    dialogMaxWidth = (widthDp * 0.92f).dp.coerceAtMost(380.dp),
                    titleFontSize = 18.sp,
                    subtitleFontSize = 14.sp,
                    bodyFontSize = 12.sp
                )
            }
            WindowWidthSizeClass.Medium -> {
                val cardW = 140.dp
                val heroH = if (isLandscape) (heightDp * 0.50f).dp.coerceIn(200.dp, 320.dp) else 300.dp
                AppAdaptiveLayout(
                    widthSizeClass = widthSizeClass,
                    heightSizeClass = heightSizeClass,
                    screenWidthDp = widthDp,
                    screenHeightDp = heightDp,
                    isLandscape = isLandscape,
                    isTablet = true,
                    isCompact = false,
                    gridColumns = calculatedColumns,
                    screenPadding = 20.dp,
                    horizontalContentPadding = 20.dp,
                    gridSpacing = 14.dp,
                    cardWidth = cardW,
                    cardAspectRatio = 2f / 3f,
                    heroBannerHeight = heroH,
                    heroHeight = heroH,
                    headerHeight = 56.dp,
                    topBarIconSize = 22.dp,
                    playerControlSize = 46.dp,
                    playPauseSize = 58.dp,
                    playerSecondaryControlSize = 44.dp,
                    progressBarHeight = 5.dp,
                    controlSpacing = 24.dp,
                    playerHorizontalPadding = 24.dp,
                    playerBottomPadding = 16.dp,
                    bottomBarHeight = 70.dp,
                    navBarHeight = 70.dp,
                    dialogMaxWidth = (widthDp * 0.75f).dp.coerceAtMost(520.dp),
                    titleFontSize = 22.sp,
                    subtitleFontSize = 16.sp,
                    bodyFontSize = 14.sp
                )
            }
            else -> { // Expanded (Geniş Tablet / Desktop)
                val cardW = 165.dp
                val heroH = if (isLandscape) (heightDp * 0.45f).dp.coerceIn(240.dp, 380.dp) else 380.dp
                AppAdaptiveLayout(
                    widthSizeClass = widthSizeClass,
                    heightSizeClass = heightSizeClass,
                    screenWidthDp = widthDp,
                    screenHeightDp = heightDp,
                    isLandscape = isLandscape,
                    isTablet = true,
                    isCompact = false,
                    gridColumns = calculatedColumns,
                    screenPadding = 28.dp,
                    horizontalContentPadding = 28.dp,
                    gridSpacing = 16.dp,
                    cardWidth = cardW,
                    cardAspectRatio = 2f / 3f,
                    heroBannerHeight = heroH,
                    heroHeight = heroH,
                    headerHeight = 60.dp,
                    topBarIconSize = 24.dp,
                    playerControlSize = 50.dp,
                    playPauseSize = 64.dp,
                    playerSecondaryControlSize = 46.dp,
                    progressBarHeight = 6.dp,
                    controlSpacing = 32.dp,
                    playerHorizontalPadding = 36.dp,
                    playerBottomPadding = 20.dp,
                    bottomBarHeight = 76.dp,
                    navBarHeight = 76.dp,
                    dialogMaxWidth = (widthDp * 0.60f).dp.coerceAtMost(650.dp),
                    titleFontSize = 26.sp,
                    subtitleFontSize = 18.sp,
                    bodyFontSize = 16.sp
                )
            }
        }

        // Yatay telefon veya çok kısa dikey yükseklik (height Compact < 480dp)
        if (heightSizeClass == WindowHeightSizeClass.Compact) {
            base.copy(
                heroBannerHeight = (heightDp * 0.45f).dp.coerceIn(140.dp, 220.dp),
                heroHeight = (heightDp * 0.45f).dp.coerceIn(140.dp, 220.dp),
                navBarHeight = 52.dp,
                bottomBarHeight = 52.dp,
                screenPadding = (base.screenPadding * 0.75f).coerceAtLeast(10.dp),
                playerControlSize = 38.dp,
                playPauseSize = 48.dp,
                playerSecondaryControlSize = 36.dp,
                controlSpacing = 16.dp,
                playerBottomPadding = 8.dp
            )
        } else {
            base
        }
    }
}

