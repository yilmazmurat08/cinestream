package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// CineStream Brand Colors (Frosted Glass Theme with Purple-Magenta Accents)
val DeepPurpleBg = Color(0xFF0E071A)
val MidPurpleBg = Color(0xFF180D2C)
val CardPurpleBg = Color(0xFF180D2C)
val LiveGold = Color(0xFFF59E0B)
val CineGold = LiveGold
val ElectricBlue = Color(0xFF00D2FF)
val NeonPink = Color(0xFFEC4899)
val PinkToPurpleGradient = Brush.horizontalGradient(listOf(Color(0xFFEC4899), Color(0xFFA855F7)))
val MutedText = Color(0xFF94A3B8)
val AccentNeonPurple = Color(0xFFD946EF)
val MutedPurpleText = Color(0xFF94A3B8)
val PalePurpleText = Color(0xFFA78BFA)
val WhiteText = Color(0xFFF5F5F7)

val CineOrange = AccentNeonPurple // Elegant Purple
val CineRed = AccentNeonPurple    // Elegant Fuchsia/Magenta

// Cinematic Depth Background Colors
val CinematicStart = DeepPurpleBg // Deep night blue/black
val CinematicEnd = MidPurpleBg   // Elegant navy/slate/fume
val CinematicBackgroundGradient = Brush.verticalGradient(
    colors = listOf(CinematicStart, CinematicEnd)
)
val CineBlack = DeepPurpleBg
val CineSurface = MidPurpleBg // white/5 for glassmorphic elements
val CineBorder = AccentNeonPurple.copy(alpha = 0.15f) // white/10

// Netflix/Apple TV typography and readability colors
val BrokenWhite = WhiteText // Ana başlıklar için parlamayan Kırık Beyaz
val SlateGray = Color(0xFFCBD5E1)   // Alt başlıklar ve süreler için belirgin ama soft Slate Grisi (Slate 300)
val MatteGray = Color(0xFFF1F5F9)   // Uzun dizi özetleri için mükemmel okunan, yüksek kontrastlı mat beyaz-gri (Slate 100)
