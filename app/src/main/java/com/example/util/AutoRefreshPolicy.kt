package com.example.util

import kotlinx.coroutines.delay

/**
 * Otomatik liste güncellemesinin zamanı. Eskiden liste her açılışta baştan indiriliyordu: gün içinde uygulamayı
 * 10 kez açan 10 kez indiriyor, indirme videoyla aynı anda olunca (özellikle mobil veride) yayın donuyordu.
 * Artık en fazla günde bir kez ve video ya da radyo çalmıyorken güncellenir. Elle yenileme bundan etkilenmez.
 */
object AutoRefreshPolicy {

    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Uygulama açıldıktan sonra güncellemeden önce beklenir: kullanıcı hemen bir yayın açarsa önce o başlar. */
    const val STARTUP_DELAY_MS = 30_000L

    /** Son başarılı güncellemeden bu yana yeterli süre geçti mi? Saat geri alındıysa (now < last) güncellenir. */
    fun isDue(lastRefreshMs: Long, nowMs: Long, intervalMs: Long = INTERVAL_MS): Boolean =
        lastRefreshMs <= 0L || nowMs < lastRefreshMs || nowMs - lastRefreshMs >= intervalMs

    /** Şu an video ya da radyo çalıyor mu (oynatıcı bildirim servisini kullanıyor ya da radyo açık). */
    fun isPlaybackActive(): Boolean =
        com.example.player.PlaybackForegroundService.isInUse || com.example.player.RadioPlayerManager.isPlaying.value

    /** Oynatma bitene kadar bekler (oynatma yoksa hemen döner). */
    suspend fun awaitNoPlayback(isActive: () -> Boolean = ::isPlaybackActive, pollMs: Long = 15_000L) {
        while (isActive()) delay(pollMs)
    }
}
