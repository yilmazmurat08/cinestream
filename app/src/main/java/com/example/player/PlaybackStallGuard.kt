package com.example.player

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Donma bekçisi: oynatıcıyı saniyede bir yoklar ([StallWatchdog]); yayın askıda kaldıysa ya da görüntü
 * donduysa aynı yayına sessizce yeniden bağlanır. Canlı yayın en güncel noktadan, film/dizi kaldığı
 * yerden devam eder. Kaldığı yere dönülemeyen (sarılamayan) film baştan başlamasın diye yeniden
 * bağlanılmaz; o durumda normal hata akışı devreye girer.
 *
 * Oynatıcının kendi iş parçacığında (ana iş parçacığı) çalışır. [start] ile başlar, [stop] ile biter.
 */
@OptIn(UnstableApi::class)
class PlaybackStallGuard(
    private val player: ExoPlayer,
    private val isLive: () -> Boolean,
    private val watchdog: StallWatchdog = StallWatchdog(),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val tickMs: Long = TICK_MS,
    private val onReconnect: (StallWatchdog.Reason) -> Unit = {},
    /** Yeniden bağlanma hakları bitti ve yayın hâlâ ilerlemiyor: ekran hata ve "Yeniden dene" göstermeli. */
    private val onGiveUp: () -> Unit = {},
) {
    private val handler = Handler(player.applicationLooper)
    private var running = false

    // Kullanıcı sarınca bekçi sunucuya daha uzun süre tanır.
    private val seekListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) watchdog.onSeek()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                check()
            } catch (e: Exception) {
                // Bekçi oynatmayı asla bozmamalı.
                Log.w(TAG, "Donma bekçisi yoklaması başarısız", e)
            }
            if (running) handler.postDelayed(this, tickMs)
        }
    }

    // Film/dizi bir kez sarılabilir oynadı mı? Yeniden bağlanırken yayın henüz yüklenmediyse de
    // (sarılabilirliği bilinmiyor) kaldığı yerden devam edilir.
    private var knownSeekable = false

    /** Bu oynatıcıda bekçinin yaptığı yeniden bağlanma sayısı (toplam). */
    var totalReconnects = 0
        private set

    fun start() {
        if (running) return
        running = true
        player.addListener(seekListener)
        handler.postDelayed(tick, tickMs)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        player.removeListener(seekListener)
    }

    /** Bir yoklama yapar (testler saati kendileri ilerletip bunu çağırır). */
    @androidx.annotation.VisibleForTesting
    internal fun check() {
        when (watchdog.onTick(snapshot(), clock())) {
            StallWatchdog.Action.NONE -> return
            StallWatchdog.Action.GIVE_UP -> {
                Log.w(TAG, "Yayın ${StallWatchdog.MAX_RECONNECTS} denemede düzelmedi (${watchdog.lastReason})")
                onGiveUp()
                // Kullanıcı "Yeniden dene" deyip yayın yeniden oynarsa bekçi tam haklarla baştan başlar.
                watchdog.reset()
                return
            }
            StallWatchdog.Action.RECONNECT -> Unit
        }
        val reason = watchdog.lastReason ?: return
        if (reconnect()) {
            totalReconnects++
            Log.i(TAG, "Yayın takıldı ($reason), yeniden bağlanıldı (${watchdog.reconnects}. deneme)")
            onReconnect(reason)
        }
    }

    private fun snapshot(): StallWatchdog.Snapshot {
        if (player.playbackState == Player.STATE_READY && player.isCurrentMediaItemSeekable) knownSeekable = true
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> StallWatchdog.State.BUFFERING
            Player.STATE_READY -> StallWatchdog.State.READY
            Player.STATE_ENDED -> StallWatchdog.State.ENDED
            else -> StallWatchdog.State.IDLE
        }
        return StallWatchdog.Snapshot(
            playWhenReady = player.playWhenReady,
            state = state,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            bufferedPositionMs = player.bufferedPosition,
            videoFrames = videoFrames()
        )
    }

    /** İşlenen görüntü kareleri (gösterilen + atlanan + düşürülen); görüntü yoksa -1. */
    private fun videoFrames(): Long {
        if (player.videoFormat == null) return -1
        val counters = player.videoDecoderCounters ?: return -1
        counters.ensureUpdated()
        return counters.renderedOutputBufferCount.toLong() +
            counters.skippedOutputBufferCount + counters.droppedBufferCount
    }

    private fun reconnect(): Boolean {
        val mediaItem = player.currentMediaItem ?: return false
        val live = isLive() || player.isCurrentMediaItemLive
        when {
            live -> player.setMediaItem(mediaItem)
            player.isCurrentMediaItemSeekable || knownSeekable -> player.setMediaItem(mediaItem, player.currentPosition)
            else -> return false
        }
        player.prepare()
        player.playWhenReady = true
        return true
    }

    companion object {
        private const val TAG = "PlaybackStallGuard"
        const val TICK_MS = 1_000L
    }
}
