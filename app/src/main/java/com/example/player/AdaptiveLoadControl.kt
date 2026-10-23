package com.example.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * Akıllı tampon kuralı (test edilebilir): yayın takıldıkça, oynatma yeniden başlamadan önce biraz daha
 * fazla veri biriktirilir. Böylece dalgalı bağlantıda arka arkaya kısa takılmalar yerine tek ve kısa bir
 * bekleme olur. İlk takılmada bugünkü değer ([baseMs]) kullanılır; her yeni takılmada [stepMs] eklenir,
 * en çok [maxMs]. Yayın [calmResetMs] boyunca takılmazsa yeniden başa dönülür.
 */
class AdaptiveRebufferPolicy(
    private val baseMs: Long,
    private val maxMs: Long,
    private val stepMs: Long = STEP_MS,
    private val calmResetMs: Long = CALM_RESET_MS,
) {
    private var level = 0
    private var lastRebufferAtMs = Long.MIN_VALUE

    fun onRebufferStarted(nowMs: Long) {
        val calm = lastRebufferAtMs != Long.MIN_VALUE && nowMs - lastRebufferAtMs >= calmResetMs
        level = if (calm) 1 else level + 1
        lastRebufferAtMs = nowMs
    }

    /** Takılmadan sonra oynatmaya devam etmek için gereken tampon (ms). */
    fun requiredMs(): Long = (baseMs + stepMs * (level - 1).coerceAtLeast(0)).coerceIn(baseMs, maxOf(baseMs, maxMs))

    companion object {
        const val STEP_MS = 2_500L
        const val CALM_RESET_MS = 180_000L
    }
}

/**
 * [DefaultLoadControl] ile aynı; yalnızca takılmadan sonra oynatmanın ne zaman süreceğine
 * [AdaptiveRebufferPolicy] karar verir. Gereken tampon hiçbir zaman yüklenebilecek miktarı aşmaz:
 * politikanın üst sınırı en az tampon süresidir (oynatıcı oraya kadar her zaman yükler; süre önceliği
 * açık olmalı) ve canlı yayında canlı ucun yarısıyla sınırlanır. Böylece oynatma asla kilitlenmez.
 */
@OptIn(UnstableApi::class)
class AdaptiveLoadControl(
    private val delegate: DefaultLoadControl,
    private val policy: AdaptiveRebufferPolicy,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : LoadControl {

    // Yalnızca oynatma iş parçacığından çağrılır.
    private var inRebuffer = false

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val defaultDecision = delegate.shouldStartPlayback(parameters)
        if (!parameters.rebuffering) {
            inRebuffer = false
            return defaultDecision
        }
        if (!inRebuffer) {
            inRebuffer = true
            policy.onRebufferStarted(clock())
        }
        if (!defaultDecision) return false
        var requiredUs = policy.requiredMs() * 1000
        if (parameters.targetLiveOffsetUs != C.TIME_UNSET) {
            requiredUs = minOf(requiredUs, parameters.targetLiveOffsetUs / 2)
        }
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(parameters.bufferedDurationUs, parameters.playbackSpeed)
        val start = bufferedUs >= requiredUs
        if (start) inRebuffer = false
        return start
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean = delegate.shouldContinueLoading(parameters)

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection>
    ) = delegate.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(): Allocator = delegate.allocator

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = delegate.retainBackBufferFromKeyframe(playerId)
}
