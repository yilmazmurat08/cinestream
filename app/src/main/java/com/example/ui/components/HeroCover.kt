package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Film/dizi detayının üstündeki geniş görsel.
 *
 * Yatay sahne görseli (backdrop) varsa keskin gösterilir. Yoksa elimizde yalnızca dikey ve küçük bir poster vardır; onu
 * geniş alana sığdırmak için büyütüp kırpmak görüntüyü bulanık gösteriyordu. Bu durumda poster bilerek yumuşatılıp
 * karartılır (atmosfer arka planı); keskin poster zaten başlığın yanındaki kartta görünür.
 */
@Composable
fun HeroCover(backdropUrl: String?, posterUrl: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    if (!backdropUrl.isNullOrBlank()) {
        SafeAsyncImage(
            model = backdropUrl,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            SafeAsyncImage(
                model = posterUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().blur(24.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
    }
}
