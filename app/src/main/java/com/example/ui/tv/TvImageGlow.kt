package com.example.ui.tv

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Görselin ana rengi (poster/logo arkasındaki yumuşak ışık için). Görsel 24×24 küçültülerek arka planda
 * ortalaması alınır; saydam ve neredeyse siyah/beyaz pikseller atlanır. Bulunamazsa [fallback].
 */
@Composable
fun rememberDominantColor(url: String?, fallback: Color = TvTheme.Accent): Color {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(24, 24)
            .allowHardware(false) // piksel okumak için yazılım bitmap
            .build()
        val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            ?.let { (it as? BitmapDrawable)?.bitmap }
            ?: return@LaunchedEffect
        averageColor(bitmap)?.let { color = it }
    }
    return color
}

private suspend fun averageColor(bitmap: Bitmap): Color? = withContext(Dispatchers.Default) {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    val w = bitmap.width.coerceAtMost(48)
    val h = bitmap.height.coerceAtMost(48)
    for (y in 0 until h) for (x in 0 until w) {
        val p = bitmap.getPixel(x, y)
        val a = p ushr 24
        if (a < 128) continue
        val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
        val max = maxOf(pr, pg, pb); val min = minOf(pr, pg, pb)
        if (max < 24 || min > 235) continue // siyah/beyaz zemin
        r += pr; g += pg; b += pb; n++
    }
    if (n == 0L) null else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}
