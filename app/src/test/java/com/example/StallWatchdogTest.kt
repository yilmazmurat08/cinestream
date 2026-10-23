package com.example

import com.example.player.AdaptiveRebufferPolicy
import com.example.player.StallWatchdog
import com.example.player.StallWatchdog.Action
import com.example.player.StallWatchdog.Reason
import com.example.player.StallWatchdog.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Donma bekçisi ve akıllı tampon kuralları. */
class StallWatchdogTest {

    private fun snap(
        state: State,
        position: Long = 0,
        buffered: Long = 0,
        frames: Long = -1,
        playing: Boolean = state == State.READY,
        playWhenReady: Boolean = true,
    ) = StallWatchdog.Snapshot(playWhenReady, state, playing, position, buffered, frames)

    /** [ticks] saniye boyunca her saniye [snapshot] verir; ilk RECONNECT'in saniyesini (yoksa -1) döner. */
    private fun run(w: StallWatchdog, startSec: Int, ticks: Int, snapshot: (sec: Int) -> StallWatchdog.Snapshot): Int {
        for (sec in startSec until startSec + ticks) {
            if (w.onTick(snapshot(sec), sec * 1000L) == Action.RECONNECT) return sec
        }
        return -1
    }

    private fun playedOnce(w: StallWatchdog) {
        run(w, 0, 3) { snap(State.READY, position = it * 1000L, frames = it * 25L) }
    }

    @Test
    fun bufferingWithoutNewData_reconnectsAfterEightSeconds() {
        val w = StallWatchdog()
        playedOnce(w)
        val at = run(w, 3, 20) { snap(State.BUFFERING, position = 3000, buffered = 4000) }
        assertEquals(3 + 8, at)
        assertEquals(Reason.BUFFER_STALLED, w.lastReason)
    }

    @Test
    fun afterASeek_slowServerGetsMoreTime() {
        val w = StallWatchdog()
        playedOnce(w)
        w.onSeek()
        // İleri sarıldı, sunucu yeni konumdan geç cevap veriyor: 8 sn'de değil, 15 sn'de yeniden bağlanılır.
        val at = run(w, 3, 30) { snap(State.BUFFERING, position = 60_000, buffered = 60_000) }
        assertEquals(3 + 15, at)
        // Yayın oynayınca normal (8 sn) kurala dönülür.
        run(w, at + 1, 3) { snap(State.READY, position = 60_000L + it * 1000L, frames = it * 25L) }
        val next = run(w, at + 4, 30) { snap(State.BUFFERING, position = 70_000, buffered = 70_000) }
        assertEquals(at + 4 + 8, next)
    }

    @Test
    fun slowButMovingBuffer_isNeverInterrupted() {
        val w = StallWatchdog()
        playedOnce(w)
        // Veri yavaş geliyor (her saniye biraz): beklemek yeniden bağlanmaktan iyidir.
        assertEquals(-1, run(w, 3, 120) { snap(State.BUFFERING, position = 3000, buffered = 3000L + it * 100) })
    }

    @Test
    fun firstLoading_isLeftToTimeouts() {
        val w = StallWatchdog()
        assertEquals(-1, run(w, 0, 60) { snap(State.BUFFERING) })
    }

    @Test
    fun playingButPositionStuck_reconnects() {
        val w = StallWatchdog()
        playedOnce(w)
        val at = run(w, 3, 20) { snap(State.READY, position = 3000, frames = -1) }
        assertTrue(at in 3..12)
        assertEquals(Reason.POSITION_STUCK, w.lastReason)
    }

    @Test
    fun frozenPictureWithRunningAudio_reconnects() {
        val w = StallWatchdog()
        run(w, 0, 5) { snap(State.READY, position = it * 1000L, frames = it * 25L) }
        // Süre (ses) ilerliyor, kareler 125'te kaldı.
        val at = run(w, 5, 20) { snap(State.READY, position = it * 1000L, frames = 125) }
        assertTrue("dondu ama yeniden bağlanmadı", at in 12..14)
        assertEquals(Reason.VIDEO_FROZEN, w.lastReason)
    }

    @Test
    fun stillImageRadioStream_isNotTreatedAsFrozen() {
        val w = StallWatchdog()
        // Tek resimli radyo yayını: arada bir tek kare gelir, görüntü hiç "akmaz".
        assertEquals(-1, run(w, 0, 120) { snap(State.READY, position = it * 1000L, frames = (it / 10).toLong()) })
    }

    @Test
    fun pausedOrStoppedPlayer_isIgnored() {
        val w = StallWatchdog()
        playedOnce(w)
        assertEquals(-1, run(w, 3, 60) { snap(State.READY, position = 3000, frames = 75, playing = false, playWhenReady = false) })
        assertEquals(-1, run(w, 63, 60) { snap(State.IDLE, playWhenReady = true) })
        assertEquals(-1, run(w, 123, 60) { snap(State.BUFFERING, buffered = 5000, playWhenReady = false) })
    }

    @Test
    fun atMostThreeReconnects_thenHealthyPlayRestoresThem() {
        val w = StallWatchdog()
        playedOnce(w)
        var sec = 3
        repeat(3) {
            val at = run(w, sec, 20) { snap(State.BUFFERING, buffered = 4000) }
            assertTrue(at > 0)
            sec = at + 1
        }
        // Dördüncü kez yeniden bağlanılmaz: bir kez "pes et" (ekranda Yeniden dene), sonra sessiz.
        val actions = (sec until sec + 60).map { w.onTick(snap(State.BUFFERING, buffered = 4000), it * 1000L) }
        assertEquals(listOf(Action.GIVE_UP), actions.filter { it != Action.NONE })
        sec += 60
        // 30 sn sorunsuz oynayınca haklar yenilenir.
        run(w, sec, 31) { snap(State.READY, position = it * 1000L, frames = it * 25L) }
        sec += 31
        assertTrue(run(w, sec, 20) { snap(State.BUFFERING, buffered = 9_999) } > 0)
    }

    @Test
    fun rebufferPolicy_growsWithRepeatedStalls_andCalmsDown() {
        val p = AdaptiveRebufferPolicy(baseMs = 5_000, maxMs = 12_500)
        p.onRebufferStarted(0)
        assertEquals(5_000, p.requiredMs())
        p.onRebufferStarted(20_000)
        assertEquals(7_500, p.requiredMs())
        p.onRebufferStarted(40_000)
        p.onRebufferStarted(60_000)
        p.onRebufferStarted(80_000)
        assertEquals("üst sınır", 12_500, p.requiredMs())
        // 3 dk takılmadan oynadı: yeniden başa.
        p.onRebufferStarted(80_000 + AdaptiveRebufferPolicy.CALM_RESET_MS)
        assertEquals(5_000, p.requiredMs())
    }

    @Test
    fun rebufferPolicy_neverExceedsWhatCanBeLoaded() {
        val p = AdaptiveRebufferPolicy(baseMs = 3_000, maxMs = 2_000)
        repeat(10) { p.onRebufferStarted(it * 1000L) }
        assertEquals(3_000, p.requiredMs())
    }

    @Test
    fun bufferByteLimit_followsAppMemory() {
        val mb = 1024 * 1024
        assertEquals(16 * mb, com.example.player.ExoPlayerConfigurator.bufferByteLimit(48))
        assertEquals(32 * mb, com.example.player.ExoPlayerConfigurator.bufferByteLimit(128))
        assertEquals(64 * mb, com.example.player.ExoPlayerConfigurator.bufferByteLimit(512))
    }
}
