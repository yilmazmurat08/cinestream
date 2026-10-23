package com.example.player

/**
 * Donma bekçisinin kararları (oynatıcıdan ve ekrandan bağımsız, test edilebilir).
 *
 * Saniyede bir oynatıcının durumuna bakılır ([onTick]). Yayın bir kez oynadıktan sonra şu durumlarda
 * [Action.RECONNECT] döner (oynatıcı aynı yayına yeniden bağlanır):
 * - Yükleniyor ama [stallMs] boyunca hiç yeni veri gelmedi (sunucu bağlantıyı askıda bıraktı).
 * - Oynuyor görünüyor ama süre [stallMs] boyunca ilerlemedi.
 * - Ses/süre ilerliyor ama akan görüntünün kareleri [stallMs] boyunca durdu (görüntü dondu).
 *
 * Veri yavaş da olsa geliyorsa yeniden bağlanılmaz (beklemek daha hızlıdır). İlk açılıştaki yükleme
 * bağlantı zaman aşımlarına bırakılır. Sarmadan ya da yeniden bağlanmadan sonra sunucunun ilk cevabı
 * için daha uzun beklenir ([restartGraceMs]): bazı sunucular ileri sarınca geç cevap verir. Art arda en çok [maxReconnects] kez denenir; yayın [healthyMs]
 * boyunca sorunsuz oynarsa sayaç sıfırlanır. Haklar bittikten sonra yayın yine ilerlemezse bir kez
 * [Action.GIVE_UP] döner: dönen daire sonsuza kadar kalmasın, kullanıcıya "Yeniden dene" gösterilsin.
 */
class StallWatchdog(
    private val stallMs: Long = STALL_MS,
    private val maxReconnects: Int = MAX_RECONNECTS,
    private val healthyMs: Long = HEALTHY_MS,
    private val restartGraceMs: Long = RESTART_GRACE_MS,
) {
    enum class State { IDLE, BUFFERING, READY, ENDED }
    enum class Action { NONE, RECONNECT, GIVE_UP }
    enum class Reason { BUFFER_STALLED, POSITION_STUCK, VIDEO_FROZEN }

    /** Oynatıcının o anki durumu. [videoFrames]: işlenen görüntü karesi sayısı; görüntü yoksa -1. */
    data class Snapshot(
        val playWhenReady: Boolean,
        val state: State,
        val isPlaying: Boolean,
        val positionMs: Long,
        val bufferedPositionMs: Long,
        val videoFrames: Long,
    )

    /** Son yeniden bağlanmanın nedeni (günlük için). */
    var lastReason: Reason? = null
        private set

    /** Bu yayında yapılan (sıfırlanmamış) yeniden bağlanma sayısı. */
    var reconnects = 0
        private set

    private var hasPlayed = false
    private var lastBuffered = UNSET
    private var bufferedSince = 0L
    private var lastPosition = UNSET
    private var positionSince = 0L
    private var lastFrames = UNSET
    private var framesSince = 0L
    // Görüntü gerçekten akıyor mu (art arda kare ilerlemesi görüldü mü)? Tek resimli radyo yayınlarında
    // kareler hiç akmaz; bunlar "donmuş" sayılmaz.
    private var frameAdvanceStreak = 0
    private var videoActive = false
    private var healthySince = UNSET
    private var gaveUp = false
    // Sarma ya da yeniden bağlanma oldu, yayın henüz yeniden oynamadı: daha sabırlı beklenir.
    private var restarting = false

    /** Yeni yayın açıldı: her şey baştan. */
    fun reset() {
        hasPlayed = false
        reconnects = 0
        gaveUp = false
        restarting = false
        lastReason = null
        healthySince = UNSET
        clearTimers()
    }

    /** Kullanıcı sardı: sunucunun yeni konumdan cevap vermesi için daha uzun beklenir. */
    fun onSeek() {
        restarting = true
        lastBuffered = UNSET
    }

    fun onTick(s: Snapshot, nowMs: Long): Action {
        if (!s.playWhenReady || s.state == State.IDLE || s.state == State.ENDED) {
            clearTimers()
            healthySince = UNSET
            return Action.NONE
        }
        return when (s.state) {
            State.READY -> onReady(s, nowMs)
            else -> onBuffering(s, nowMs)
        }
    }

    private fun onReady(s: Snapshot, nowMs: Long): Action {
        hasPlayed = true
        restarting = false
        lastBuffered = UNSET
        if (!s.isPlaying) {
            // Ses odağı kaybı gibi geçici duraklamalar: süre zaten ilerlemez, bekçi karışmaz.
            clearTimers()
            healthySince = UNSET
            return Action.NONE
        }
        if (healthySince == UNSET) healthySince = nowMs
        if (nowMs - healthySince >= healthyMs) {
            reconnects = 0
            gaveUp = false
        }

        if (s.positionMs != lastPosition) {
            lastPosition = s.positionMs
            positionSince = nowMs
        } else if (nowMs - positionSince >= stallMs) {
            return reconnect(Reason.POSITION_STUCK)
        }

        if (s.videoFrames < 0) {
            lastFrames = UNSET
            frameAdvanceStreak = 0
            videoActive = false
        } else if (s.videoFrames != lastFrames) {
            if (lastFrames != UNSET && ++frameAdvanceStreak >= VIDEO_ACTIVE_TICKS) videoActive = true
            lastFrames = s.videoFrames
            framesSince = nowMs
        } else {
            frameAdvanceStreak = 0
            if (videoActive && nowMs - framesSince >= stallMs) return reconnect(Reason.VIDEO_FROZEN)
        }
        return Action.NONE
    }

    private fun onBuffering(s: Snapshot, nowMs: Long): Action {
        healthySince = UNSET
        lastPosition = UNSET
        lastFrames = UNSET
        frameAdvanceStreak = 0
        videoActive = false
        if (!hasPlayed) return Action.NONE
        if (s.bufferedPositionMs != lastBuffered) {
            lastBuffered = s.bufferedPositionMs
            bufferedSince = nowMs
            return Action.NONE
        }
        val limit = if (restarting) maxOf(stallMs, restartGraceMs) else stallMs
        return if (nowMs - bufferedSince >= limit) reconnect(Reason.BUFFER_STALLED) else Action.NONE
    }

    private fun reconnect(reason: Reason): Action {
        clearTimers()
        healthySince = UNSET
        if (reconnects >= maxReconnects) {
            if (gaveUp) return Action.NONE
            gaveUp = true
            lastReason = reason
            return Action.GIVE_UP
        }
        reconnects++
        lastReason = reason
        restarting = true
        return Action.RECONNECT
    }

    private fun clearTimers() {
        lastBuffered = UNSET
        lastPosition = UNSET
        lastFrames = UNSET
        frameAdvanceStreak = 0
        videoActive = false
    }

    companion object {
        /** Bu kadar süre hiçbir şey ilerlemezse yayın yeniden bağlanır. */
        const val STALL_MS = 8_000L
        const val MAX_RECONNECTS = 3
        /** Bu kadar sorunsuz oynayan yayında deneme hakları yenilenir. */
        const val HEALTHY_MS = 30_000L
        /** Sarma / yeniden bağlanmadan sonra ilk veri için en uzun bekleme (okuma zaman aşımı kadar). */
        const val RESTART_GRACE_MS = 15_000L
        /** Görüntünün "akıyor" sayılması için art arda kare ilerlemesi görülen yoklama sayısı. */
        private const val VIDEO_ACTIVE_TICKS = 3
        private const val UNSET = Long.MIN_VALUE
    }
}
