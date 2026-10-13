package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DeepPurpleBg
import com.example.ui.theme.MidPurpleBg

/** Üst görselde ne gösterileceği: geniş (yatay) görsel mi, yoksa poster mi. */
data class HeroImage(val url: String, val wide: Boolean)

/**
 * Sıra: sağlayıcının yatay sahne görseli → TMDB'nin yatay görseli → poster. Poster dikey ve küçük olduğundan geniş
 * alana büyütülüp kırpılınca bulanık görünüyordu; poster yalnızca son çare olarak ve kırpılmadan kullanılır.
 */
fun pickHeroImage(providerBackdrop: String?, tmdbBackdrop: String?, poster: String?): HeroImage? {
    providerBackdrop?.trim()?.takeIf { it.isNotEmpty() }?.let { return HeroImage(it, wide = true) }
    tmdbBackdrop?.trim()?.takeIf { it.isNotEmpty() }?.let { return HeroImage(it, wide = true) }
    poster?.trim()?.takeIf { it.isNotEmpty() }?.let { return HeroImage(it, wide = false) }
    return null
}

/**
 * Film/dizi detayının üstündeki geniş görsel. Yatay görsel keskin ve tam alanı kaplar. Yalnızca poster varsa
 * büyütülüp bulanıklaşmasın diye kırpılmadan, kendi oranında ortada gösterilir (arkada uygulamanın koyu zemini).
 */
@Composable
fun HeroCover(backdropUrl: String?, posterUrl: String?, contentDescription: String?, modifier: Modifier = Modifier, tmdbBackdropUrl: String? = null) {
    val image = pickHeroImage(backdropUrl, tmdbBackdropUrl, posterUrl)
    if (image == null || image.wide) {
        SafeAsyncImage(
            model = image?.url,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize().testTag("hero_wide"),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MidPurpleBg, DeepPurpleBg)))
                .testTag("hero_poster"),
            contentAlignment = Alignment.Center
        ) {
            SafeAsyncImage(
                model = image.url,
                contentDescription = contentDescription,
                modifier = Modifier
                    .padding(top = 28.dp, bottom = 44.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}
