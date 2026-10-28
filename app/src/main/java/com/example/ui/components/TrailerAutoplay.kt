package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/** Kart açıldıktan sonra fragmanın kendiliğinden başlamasını bekleyen süre (önce kapak görünür). */
const val TRAILER_AUTOPLAY_DELAY_MS = 1_500L

/**
 * Fragman otomatik başlamalı mı: Ayarlar'da açık, bağlantı bulunmuş, bu kartta bitmemiş/hata vermemiş ve şu an
 * oynamıyor olmalı.
 */
fun shouldAutoplayTrailer(videoId: String?, dismissed: Boolean, playing: Boolean, enabled: Boolean = true): Boolean =
    enabled && !videoId.isNullOrBlank() && !dismissed && !playing

/**
 * Film/dizi kartı açılınca fragman, kısa bir gecikmeyle üstteki kapak görselinin yerinde kendiliğinden oynar (telefon
 * ve TV). Ayarlar'da "Fragmanı otomatik oynat" kapalıysa başlamaz ([enabled]). Fragman biterse veya hata verirse aynı
 * kartta bir daha otomatik başlamaz ([dismissed]).
 */
@Composable
fun AutoStartTrailerEffect(videoId: String?, dismissed: Boolean, playing: Boolean, enabled: Boolean, onStart: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(videoId, dismissed, enabled) {
        if (!shouldAutoplayTrailer(videoId, dismissed, playing, enabled)) return@LaunchedEffect
        // Daha önce uygulama içinde oynatılamamış fragman kendiliğinden denenmez.
        if (TrailerEmbedBlocklist.isBlocked(context, videoId)) return@LaunchedEffect
        delay(TRAILER_AUTOPLAY_DELAY_MS)
        if (videoId != null) onStart(videoId)
    }
}

/**
 * Sahibi başka uygulamaların içinde oynatılmasına izin vermeyen YouTube fragmanları. Bir kez hata veren fragman
 * cihazda hatırlanır: kart açılınca kendiliğinden denenmez, Fragman düğmesi doğrudan YouTube'da açar.
 */
object TrailerEmbedBlocklist {
    private const val PREFS = "trailer_embed_blocked"
    private const val MAX_ENTRIES = 500

    fun isBlocked(context: android.content.Context, videoId: String?): Boolean =
        !videoId.isNullOrBlank() && runCatching {
            context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).contains(videoId)
        }.getOrDefault(false)

    fun block(context: android.content.Context, videoId: String) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (prefs.all.size >= MAX_ENTRIES) editor.clear()
            editor.putLong(videoId, System.currentTimeMillis()).apply()
        }
    }
}

/** Fragmanı YouTube uygulamasında (yoksa tarayıcıda) açar. Açılamazsa false. */
fun openTrailerInYouTube(context: android.content.Context, videoId: String): Boolean = try {
    context.startActivity(
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId"))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    false
}

/**
 * Uygulama içinde oynatılamayan fragman: hatırlanır. Kullanıcı Fragman düğmesiyle başlattıysa YouTube'da açılır
 * (açılamazsa kısa bir uyarı); kart açılınca kendiliğinden başladıysa sessizce durur.
 */
fun handleTrailerEmbedError(context: android.content.Context, videoId: String, startedByUser: Boolean) {
    TrailerEmbedBlocklist.block(context, videoId)
    if (startedByUser && !openTrailerInYouTube(context, videoId)) {
        android.widget.Toast.makeText(context, context.getString(com.example.R.string.trailer_inapp_failed), android.widget.Toast.LENGTH_SHORT).show()
    }
}
