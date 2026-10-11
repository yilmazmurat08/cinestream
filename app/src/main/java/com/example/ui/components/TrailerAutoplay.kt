package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/** Kart açıldıktan sonra fragmanın kendiliğinden başlamasını bekleyen süre (önce kapak görünür). */
const val TRAILER_AUTOPLAY_DELAY_MS = 1_500L

/** Fragman otomatik başlamalı mı: bağlantı bulunmuş, kullanıcı kapatmamış/bitmemiş ve şu an oynamıyor olmalı. */
fun shouldAutoplayTrailer(videoId: String?, dismissed: Boolean, playing: Boolean): Boolean =
    !videoId.isNullOrBlank() && !dismissed && !playing

/**
 * Film/dizi kartı açılınca fragman, kısa bir gecikmeyle üstteki kapak görselinin yerinde kendiliğinden oynar (telefon
 * ve TV). Kullanıcı kapatırsa, fragman biterse veya hata verirse aynı kartta bir daha otomatik başlamaz ([dismissed]).
 */
@Composable
fun AutoStartTrailerEffect(videoId: String?, dismissed: Boolean, playing: Boolean, onStart: (String) -> Unit) {
    LaunchedEffect(videoId, dismissed) {
        if (!shouldAutoplayTrailer(videoId, dismissed, playing)) return@LaunchedEffect
        delay(TRAILER_AUTOPLAY_DELAY_MS)
        if (videoId != null) onStart(videoId)
    }
}
