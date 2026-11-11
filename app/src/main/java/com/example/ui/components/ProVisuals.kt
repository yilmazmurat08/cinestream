package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.CineOrange

/**
 * PRO görselleri (satın alma ekranı ve Ayarlar'daki PRO afişi). Hepsi Compose ile çizilir: taç, halka ve parıltı
 * çizimdir; hazır görsel, başka markaya ait logo ya da film afişi kullanılmaz. Uygulamanın adı, logosu ve mor rengi
 * aynı kalır; altın yalnızca PRO vurgusu, yeşil neon ince ayrıntıdır.
 */
val ProGold = Color(0xFFFFC53D)
val ProGoldDeep = Color(0xFFF59E0B)
val ProNeonGreen = Color(0xFF7CFF6B)
internal val ProInk = Color(0xFF1A1205)

/** PRO'nun kullanıcıya verdikleri. Oyunlar ve Spor yalnızca o özellik bu sürümde açıksa listelenir (yanıltıcı olmasın). */
internal data class ProFeature(val icon: ImageVector, val tint: Color, val textRes: Int)

internal fun proFeatures(): List<ProFeature> = listOfNotNull(
    ProFeature(Icons.Filled.AllInclusive, ProGold, R.string.pro_point_unlimited),
    ProFeature(Icons.Filled.PlaylistAddCheck, ProNeonGreen, R.string.pro_point_playlists),
    ProFeature(Icons.Filled.SportsEsports, Color(0xFFFF6BD6), R.string.pro_point_games).takeIf { BuildConfig.FEATURE_GAMES },
    ProFeature(Icons.Filled.SportsSoccer, ProNeonGreen, R.string.pro_point_sports).takeIf { BuildConfig.FEATURE_SPORTS },
    ProFeature(Icons.Filled.Devices, Color(0xFFC084FC), R.string.pro_point_devices),
    ProFeature(Icons.Filled.AccountCircle, Color(0xFF60A5FA), R.string.pro_point_account)
)

private fun DrawScope.drawCrown(center: Offset, width: Float) {
    val h = width * 0.62f
    val left = center.x - width / 2f
    val top = center.y - h / 2f
    fun p(x: Float, y: Float) = Offset(left + width * x, top + h * y)
    val body = Path().apply {
        moveTo(p(0.08f, 0.95f).x, p(0.08f, 0.95f).y)
        lineTo(p(0f, 0.28f).x, p(0f, 0.28f).y)
        lineTo(p(0.27f, 0.58f).x, p(0.27f, 0.58f).y)
        lineTo(p(0.5f, 0.02f).x, p(0.5f, 0.02f).y)
        lineTo(p(0.73f, 0.58f).x, p(0.73f, 0.58f).y)
        lineTo(p(1f, 0.28f).x, p(1f, 0.28f).y)
        lineTo(p(0.92f, 0.95f).x, p(0.92f, 0.95f).y)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFE9A6), ProGold, ProGoldDeep), startY = top, endY = top + h))
    drawPath(body, Color(0xFFB45309), style = Stroke(width = width * 0.025f))
    // Uçlardaki toplar ve taban bandı
    listOf(p(0f, 0.24f), p(0.5f, 0f), p(1f, 0.24f)).forEach { drawCircle(Color(0xFFFFE9A6), radius = width * 0.055f, center = it) }
    drawRoundRect(
        Brush.horizontalGradient(listOf(ProGoldDeep, Color(0xFFFFE9A6), ProGoldDeep), startX = left, endX = left + width),
        topLeft = p(0.08f, 0.82f), size = Size(width * 0.84f, h * 0.16f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.06f)
    )
    listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, x ->
        drawCircle(if (i == 1) CineOrange else ProNeonGreen, radius = width * 0.035f, center = p(x, 0.9f))
    }
}

/** Taçlı uygulama simgesi: arkada mor-altın parıltı, çevresinde altın halka. [size] simge karesinin kenarı. */
@Composable
fun ProCrownEmblem(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(width = size * 1.9f, height = size * 1.75f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height * 0.6f)
            val tile = size.toPx()
            drawCircle(
                Brush.radialGradient(listOf(CineOrange.copy(alpha = 0.55f), ProGold.copy(alpha = 0.16f), Color.Transparent), center = c, radius = tile * 1.05f),
                radius = tile * 1.05f, center = c
            )
            // Halkanın arkada kalan yarısı (simgenin arkasından geçer)
            rotate(-16f, c) {
                drawArc(
                    Brush.sweepGradient(listOf(ProGold.copy(alpha = 0.15f), ProGold, Color(0xFFFFF3C4), ProGold.copy(alpha = 0.15f)), center = c),
                    startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(c.x - tile * 0.86f, c.y - tile * 0.26f), size = Size(tile * 1.72f, tile * 0.52f),
                    style = Stroke(width = tile * 0.035f, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier
                .padding(top = size * 0.42f)
                .size(size)
                .clip(RoundedCornerShape(size * 0.26f))
                .background(Color(0xFF0E071A))
                .border(1.5.dp, Brush.linearGradient(listOf(ProGold, CineOrange, ProNeonGreen.copy(alpha = 0.7f))), RoundedCornerShape(size * 0.26f))
        ) {
            Image(
                painter = painterResource(R.drawable.img_app_logo_1782414357652),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height * 0.6f)
            val tile = size.toPx()
            // Halkanın önde kalan yarısı
            rotate(-16f, c) {
                drawArc(
                    Brush.sweepGradient(listOf(ProGold, Color(0xFFFFF3C4), ProGold, ProGoldDeep), center = c),
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(c.x - tile * 0.86f, c.y - tile * 0.26f), size = Size(tile * 1.72f, tile * 0.52f),
                    style = Stroke(width = tile * 0.045f, cap = StrokeCap.Round)
                )
            }
            drawCrown(Offset(c.x, c.y - tile * 0.66f), tile * 0.74f)
            // Küçük kıvılcımlar
            listOf(Offset(-0.78f, -0.5f) to ProGold, Offset(0.8f, -0.36f) to ProNeonGreen, Offset(0.66f, 0.56f) to ProGold, Offset(-0.7f, 0.5f) to ProNeonGreen)
                .forEach { (o, color) -> drawCircle(color.copy(alpha = 0.85f), radius = tile * 0.018f, center = Offset(c.x + tile * o.x, c.y + tile * o.y)) }
        }
    }
}

/** Satın alma ekranındaki özellik listesi: renkli simge rozeti + metin; satırlar arasında ince çizgi. */
@Composable
internal fun ProFeatureList(modifier: Modifier = Modifier) {
    val features = proFeatures()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), ProNeonGreen.copy(alpha = 0.22f))), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        features.forEachIndexed { index, f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(f.tint.copy(alpha = 0.14f))
                        .border(1.dp, f.tint.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(f.icon, contentDescription = null, tint = f.tint, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(f.textRes), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 17.sp)
            }
            if (index != features.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
        }
    }
}

/**
 * Ayarlar'daki PRO afişi: solda taçlı simge, sağda başlık, üç kısa madde ve altın düğme. PRO kullanıcıda düğme yerine
 * "PRO aktif" görünür. Her zaman koyu zeminlidir (açık temada da).
 */
@Composable
fun ProHeroBanner(isProUser: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF241046), Color(0xFF12091F), Color(0xFF0C1A12))))
            .border(1.5.dp, Brush.linearGradient(listOf(ProGold, CineOrange, ProNeonGreen.copy(alpha = 0.8f))), shape)
            .then(com.example.ui.tv.tvRestorableFocus())
            .clickable(onClick = onClick)
            .testTag("settings_pro_row")
    ) {
        // Köşeden gelen yeşil neon ışık
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.radialGradient(listOf(ProNeonGreen.copy(alpha = 0.16f), Color.Transparent), center = Offset(0f, Float.POSITIVE_INFINITY), radius = 520f))
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
            ProCrownEmblem(size = 58.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CineStream ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("PRO", color = ProGold, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    stringResource(if (isProUser) R.string.pro_card_title_active else R.string.pro_banner_tagline),
                    color = ProGold.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (!isProUser) {
                    Spacer(Modifier.height(2.dp))
                    listOfNotNull(
                        R.string.pro_banner_b1,
                        R.string.pro_banner_b2,
                        R.string.pro_banner_b3.takeIf { BuildConfig.FEATURE_GAMES }
                    ).forEach { res ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = ProNeonGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(res), color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (isProUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pro_badge_active), color = ProNeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFE08A), ProGold, ProGoldDeep)))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.pro_banner_cta), color = ProInk, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ProInk, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
