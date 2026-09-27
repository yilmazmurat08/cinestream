package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CineOrange
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonPink
import java.util.Locale

/**
 * Intelligent category-to-system-icon matching engine.
 * Automatically analyzes category title, group name, or content type (LIVE, MOVIE, SERIES, RADIO)
 * and returns the most suitable minimalist Material 3 icon.
 */
object CategoryIconMatcher {

    fun matchIcon(name: String?, type: String? = null): ImageVector {
        if (name.isNullOrBlank()) {
            return when (type?.uppercase(Locale.ROOT)) {
                "LIVE" -> Icons.Default.LiveTv
                "MOVIE" -> Icons.Default.Movie
                "SERIES" -> Icons.Default.Tv
                "RADIO" -> Icons.Default.Radio
                else -> Icons.Default.Category
            }
        }

        val raw = name.lowercase(Locale.ROOT)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')

        // 1. Live TV specific categories
        if (type.equals("LIVE", ignoreCase = true) || raw.contains("canli") || raw.contains("live") || raw.contains("iptv")) {
            return when {
                raw.contains("spor") || raw.contains("sport") || raw.contains("futbol") || raw.contains("basket") || raw.contains("mac") || raw.contains("lig") || raw.contains("bein") -> Icons.Default.SportsSoccer
                raw.contains("haber") || raw.contains("news") || raw.contains("gundem") || raw.contains("son dakika") -> Icons.Default.Newspaper
                raw.contains("belgesel") || raw.contains("docu") || raw.contains("nat geo") || raw.contains("discovery") || raw.contains("tarih") || raw.contains("doga") -> Icons.Default.Science
                raw.contains("cocuk") || raw.contains("kids") || raw.contains("animasyon") || raw.contains("cartoon") || raw.contains("disney") || raw.contains("minika") -> Icons.Default.ChildCare
                raw.contains("muzik") || raw.contains("music") || raw.contains("kral") || raw.contains("power") || raw.contains("nr1") -> Icons.Default.MusicNote
                raw.contains("sinema") || raw.contains("film") || raw.contains("movie") || raw.contains("cinema") -> Icons.Default.Movie
                raw.contains("dizi") || raw.contains("series") || raw.contains("show") -> Icons.Default.Tv
                raw.contains("ulusal") || raw.contains("yerel") || raw.contains("genel") || raw.contains("turk") -> Icons.Default.LiveTv
                raw.contains("dini") || raw.contains("islam") || raw.contains("kuran") -> Icons.Default.MenuBook
                raw.contains("eglence") || raw.contains("show") || raw.contains("moda") || raw.contains("lifestyle") -> Icons.Default.Celebration
                raw.contains("yetiskin") || raw.contains("adult") || raw.contains("18+") || raw.contains("xxx") -> Icons.Default.Lock
                raw.contains("4k") || raw.contains("uhd") || raw.contains("hevc") || raw.contains("fhd") -> Icons.Default.HighQuality
                raw.contains("vip") || raw.contains("premium") || raw.contains("ozel") -> Icons.Default.Star
                raw.contains("almanya") || raw.contains("germany") || raw.contains("fransa") || raw.contains("france") || raw.contains("ingiltere") || raw.contains("uk") || raw.contains("usa") || raw.contains("dunya") -> Icons.Default.Public
                else -> Icons.Default.LiveTv
            }
        }

        // 2. Movie / Cinema specific categories
        if (type.equals("MOVIE", ignoreCase = true) || raw.contains("film") || raw.contains("movie") || raw.contains("sinema")) {
            return when {
                raw.contains("aksiyon") || raw.contains("action") || raw.contains("macera") || raw.contains("adventure") -> Icons.Default.Bolt
                raw.contains("bilim kurgu") || raw.contains("sci-fi") || raw.contains("scifi") || raw.contains("uzay") || raw.contains("space") -> Icons.Default.RocketLaunch
                raw.contains("komedi") || raw.contains("comedy") || raw.contains("eglence") -> Icons.Default.Mood
                raw.contains("dram") || raw.contains("drama") || raw.contains("duygusal") -> Icons.Default.TheaterComedy
                raw.contains("korku") || raw.contains("horror") || raw.contains("gerilim") || raw.contains("thriller") -> Icons.Default.Bedtime
                raw.contains("suc") || raw.contains("crime") || raw.contains("polisiye") || raw.contains("mafya") || raw.contains("gangster") -> Icons.Default.LocalPolice
                raw.contains("gizem") || raw.contains("mystery") -> Icons.Default.Visibility
                raw.contains("romantik") || raw.contains("romance") || raw.contains("ask") || raw.contains("sevgi") -> Icons.Default.Favorite
                raw.contains("animasyon") || raw.contains("animation") || raw.contains("anime") || raw.contains("cizgi") -> Icons.Default.Animation
                raw.contains("aile") || raw.contains("family") || raw.contains("cocuk") || raw.contains("kids") -> Icons.Default.FamilyRestroom
                raw.contains("fantastik") || raw.contains("fantasy") || raw.contains("buyu") -> Icons.Default.AutoFixHigh
                raw.contains("belgesel") || raw.contains("documentary") -> Icons.Default.Science
                raw.contains("tarih") || raw.contains("history") || raw.contains("savas") || raw.contains("war") -> Icons.Default.Shield
                raw.contains("western") || raw.contains("kovboy") -> Icons.Default.Explore
                raw.contains("muzik") || raw.contains("musical") -> Icons.Default.MusicNote
                raw.contains("top 10") || raw.contains("trend") || raw.contains("populer") || raw.contains("vizyon") || raw.contains("imdb") || raw.contains("odullu") -> Icons.Default.Star
                raw.contains("yerli") || raw.contains("turk") -> Icons.Default.Movie
                else -> Icons.Default.Movie
            }
        }

        // 3. Series / TV Shows specific categories
        if (type.equals("SERIES", ignoreCase = true) || raw.contains("dizi") || raw.contains("series") || raw.contains("sezon")) {
            return when {
                raw.contains("aksiyon") || raw.contains("action") || raw.contains("macera") -> Icons.Default.Bolt
                raw.contains("bilim kurgu") || raw.contains("sci-fi") || raw.contains("uzay") -> Icons.Default.RocketLaunch
                raw.contains("komedi") || raw.contains("sitcom") || raw.contains("comedy") -> Icons.Default.Mood
                raw.contains("dram") || raw.contains("drama") -> Icons.Default.TheaterComedy
                raw.contains("suc") || raw.contains("crime") || raw.contains("polisiye") || raw.contains("dedektif") -> Icons.Default.LocalPolice
                raw.contains("gizem") || raw.contains("mystery") -> Icons.Default.Visibility
                raw.contains("korku") || raw.contains("gerilim") -> Icons.Default.Bedtime
                raw.contains("romantik") || raw.contains("ask") -> Icons.Default.Favorite
                raw.contains("animasyon") || raw.contains("anime") -> Icons.Default.Animation
                raw.contains("tarih") || raw.contains("donem") || raw.contains("savas") -> Icons.Default.Shield
                raw.contains("belgesel") -> Icons.Default.Science
                raw.contains("netflix") || raw.contains("prime") || raw.contains("disney") || raw.contains("hbo") || raw.contains("apple") || raw.contains("blu") || raw.contains("gain") || raw.contains("exxen") -> Icons.Default.PlayCircle
                raw.contains("top 10") || raw.contains("populer") || raw.contains("trend") -> Icons.Default.Star
                raw.contains("yerli") || raw.contains("turk") -> Icons.Default.Tv
                else -> Icons.Default.Tv
            }
        }

        // 4. Radio
        if (type.equals("RADIO", ignoreCase = true) || raw.contains("radyo") || raw.contains("radio") || raw.contains("fm")) {
            return Icons.Default.Radio
        }

        // 5. Generic Keyword Matching fallback
        return when {
            raw.contains("spor") || raw.contains("futbol") || raw.contains("basket") || raw.contains("lig") -> Icons.Default.SportsSoccer
            raw.contains("haber") || raw.contains("news") -> Icons.Default.Newspaper
            raw.contains("belgesel") || raw.contains("bilim") -> Icons.Default.Science
            raw.contains("cocuk") || raw.contains("kids") || raw.contains("cizgi") -> Icons.Default.ChildCare
            raw.contains("muzik") || raw.contains("music") || raw.contains("sarki") -> Icons.Default.MusicNote
            raw.contains("aksiyon") || raw.contains("action") -> Icons.Default.Bolt
            raw.contains("komedi") || raw.contains("comedy") -> Icons.Default.Mood
            raw.contains("dram") || raw.contains("drama") -> Icons.Default.TheaterComedy
            raw.contains("korku") || raw.contains("horror") -> Icons.Default.Bedtime
            raw.contains("suc") || raw.contains("crime") || raw.contains("polisiye") -> Icons.Default.LocalPolice
            raw.contains("gizem") || raw.contains("mystery") -> Icons.Default.Visibility
            raw.contains("romantik") || raw.contains("ask") -> Icons.Default.Favorite
            raw.contains("animasyon") || raw.contains("anime") -> Icons.Default.Animation
            raw.contains("bilim kurgu") || raw.contains("sci-fi") -> Icons.Default.RocketLaunch
            raw.contains("tarih") || raw.contains("savas") -> Icons.Default.Shield
            raw.contains("favori") || raw.contains("izleme listem") || raw.contains("begen") -> Icons.Default.Bookmark
            raw.contains("trend") || raw.contains("populer") || raw.contains("top 10") -> Icons.Default.Star
            raw.contains("sinema") || raw.contains("film") -> Icons.Default.Movie
            raw.contains("dizi") -> Icons.Default.Tv
            raw.contains("canli") -> Icons.Default.LiveTv
            raw.contains("radyo") -> Icons.Default.Radio
            raw.contains("klasor") -> Icons.Default.Folder
            else -> Icons.Default.Category
        }
    }

    fun matchColor(name: String?, type: String? = null): Color {
        val raw = name?.lowercase(Locale.ROOT) ?: ""
        return when {
            type.equals("LIVE", ignoreCase = true) || raw.contains("canli") -> NeonPink
            type.equals("SERIES", ignoreCase = true) || raw.contains("dizi") -> ElectricBlue
            type.equals("MOVIE", ignoreCase = true) || raw.contains("film") || raw.contains("sinema") -> CineOrange
            type.equals("RADIO", ignoreCase = true) || raw.contains("radyo") -> Color(0xFF10B981)
            raw.contains("spor") -> Color(0xFF22C55E)
            raw.contains("haber") -> Color(0xFFEF4444)
            raw.contains("belgesel") -> Color(0xFF14B8A6)
            raw.contains("cocuk") -> Color(0xFFF472B6)
            raw.contains("muzik") -> Color(0xFFA855F7)
            raw.contains("bilim") || raw.contains("uzay") -> Color(0xFF38BDF8)
            raw.contains("korku") -> Color(0xFFDC2626)
            raw.contains("komedi") -> Color(0xFFFBBF24)
            raw.contains("suc") -> Color(0xFF64748B)
            raw.contains("romantik") -> Color(0xFFFB7185)
            else -> CineOrange
        }
    }
}

/**
 * Minimalist adaptive category icon with optional glowing pill / circular container.
 */
@Composable
fun CategoryAdaptiveIcon(
    category: String,
    type: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null,
    containerColor: Color? = null,
    shape: Shape = CircleShape,
    contentDescription: String? = null
) {
    val icon = remember(category, type) {
        CategoryIconMatcher.matchIcon(category, type)
    }
    val defaultColor = remember(category, type) {
        CategoryIconMatcher.matchColor(category, type)
    }
    val finalTint = tint ?: defaultColor

    if (containerColor != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(shape)
                .background(containerColor)
                .padding(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription ?: category,
                tint = finalTint,
                modifier = Modifier.size(size)
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription ?: category,
            tint = finalTint,
            modifier = modifier.size(size)
        )
    }
}
