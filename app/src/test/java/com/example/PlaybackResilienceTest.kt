package com.example

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.test.core.app.ApplicationProvider
import com.example.FakeStreamServer.Behavior
import com.example.player.ExoPlayerConfigurator
import com.example.player.PlaybackStallGuard
import com.example.player.StallWatchdog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Gerçek oynatıcıyla (uygulamanın kendi ayarlarıyla kurulan ExoPlayer) takılma, donma ve çökme testleri.
 * Video gerçek bir dosyadır (H.264, 20 sn, ~26 KB/sn). Test ortamında ses kartı taklidi ilerlemediği için
 * video sessizdir (oynatıcının saati görüntüye göre işler). Sunucu IPTV sunucusu gibi davranır ve istenince
 * yavaşlar, ortada askıda kalır, boş veri gönderir, hata verir ya da bağlantıyı koparır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackResilienceTest {

    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.forAllSupportedMimeTypes()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ts = resource("/player/sample.ts")
    private val mp4 = resource("/player/sample.mp4")
    private val servers = mutableListOf<FakeStreamServer>()
    private val rawServers = mutableListOf<RawStreamServer>()
    private val players = mutableListOf<ExoPlayer>()

    private fun resource(path: String) = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }

    @After
    fun tearDown() {
        players.forEach { runCatching { it.release() } }
        servers.forEach { it.shutdown() }
        rawServers.forEach { it.shutdown() }
    }

    private fun server(bytes: ByteArray, type: String, behavior: (Int) -> Behavior = { Behavior.Normal }) =
        FakeStreamServer(bytes, type).also {
            it.behavior = behavior
            it.server.start()
            servers += it
        }

    private fun raw(bytes: ByteArray, type: String, live: Boolean, plan: (Int) -> RawStreamServer.Plan) =
        RawStreamServer(bytes, type, live).also {
            it.plan = plan
            rawServers += it
        }

    private fun player(url: String, buffer: String = "Normal"): ExoPlayer =
        ExoPlayerConfigurator.buildConfiguredPlayer(context, true, buffer, false).also {
            players += it
            it.setMediaItem(ExoPlayerConfigurator.buildMediaItemForUrl(url))
            it.prepare()
            it.play()
        }

    /** Oynatıcının ve bekçinin saatini gerçek zamanla birlikte ilerletir. Koşul sağlanınca true. */
    private fun runFor(maxMs: Long, until: () -> Boolean = { false }): Boolean {
        val t0 = System.currentTimeMillis()
        var last = t0
        while (true) {
            if (until()) return true
            val now = System.currentTimeMillis()
            if (now - t0 >= maxMs) return false
            ShadowLooper.idleMainLooper((now - last).coerceAtLeast(1), TimeUnit.MILLISECONDS)
            last = now
            Thread.sleep(5)
        }
    }

    /** Oynarken kaç kez takıldığını (READY → BUFFERING) sayar. */
    private class RebufferCounter(player: Player) : Player.Listener {
        var rebuffers = 0
        var wasReady = false
        init { player.addListener(this) }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) wasReady = true
            if (state == Player.STATE_BUFFERING && wasReady) rebuffers++
        }
    }

    private fun guard(p: ExoPlayer, live: Boolean, watchdog: StallWatchdog = StallWatchdog(), onGiveUp: () -> Unit = {}) =
        PlaybackStallGuard(p, isLive = { live }, watchdog = watchdog, onGiveUp = onGiveUp).also { it.start() }

    // ------------------------------------------------------------------ Normal oynatma

    @Test
    fun liveTs_playsSmoothly_withoutReconnects() {
        val s = server(ts, "video/mp2t")
        val p = player(s.url("/live/user/pass/1.ts"))
        val counter = RebufferCounter(p)
        val g = guard(p, live = true)
        assertTrue("canlı yayın oynamadı", runFor(30_000) { p.currentPosition >= 6_000 || p.playerError != null })
        assertNull(p.playerError)
        assertEquals("sağlam yayında yeniden bağlanılmamalı", 0, g.totalReconnects)
        assertEquals("sağlam yayında takılma olmamalı", 0, counter.rebuffers)
        g.stop()
    }

    @Test
    fun vodMp4_playsSmoothly_withoutReconnects() {
        val s = server(mp4, "video/mp4")
        val p = player(s.url("/movie/user/pass/1.mp4"))
        val g = guard(p, live = false)
        assertTrue("film oynamadı", runFor(30_000) { p.currentPosition >= 6_000 || p.playerError != null })
        assertNull(p.playerError)
        assertEquals(0, g.totalReconnects)
        g.stop()
    }

    // ------------------------------------------------------------------ Donma (askıda kalan sunucu)

    @Test
    fun serverHangsMidStream_liveChannelRecoversByItself() {
        // Sunucu ~10 sn'lik veriyi gönderip bağlantıyı açık tutarak susuyor; sonraki bağlantılar düzgün.
        val r = raw(ts, "video/mp2t", live = true) { n ->
            if (n == 1) RawStreamServer.Plan.HangAfter(250_000) else RawStreamServer.Plan.Healthy
        }
        val p = player(r.url("/live/user/pass/2.ts"))
        val g = guard(p, live = true)
        assertTrue("yayın başlamadı", runFor(30_000) { p.playbackState == Player.STATE_READY })
        assertTrue("yayın takılmadı", runFor(30_000) { p.playbackState == Player.STATE_BUFFERING })
        val stalledAt = System.currentTimeMillis()
        // Kısa zaman aşımı (15 sn) ya da bekçi (8 sn) yayını yeniden bağlar; kullanıcı bir şey yapmaz.
        assertTrue("yayın kendiliğinden düzelmedi", runFor(30_000) { p.isPlaying })
        val recoveredInMs = System.currentTimeMillis() - stalledAt
        assertTrue("toparlanma çok uzun sürdü: $recoveredInMs ms", recoveredInMs <= 20_000)
        assertTrue(r.connections.get() >= 2)
        assertNull(p.playerError)
        println("Donan canlı yayın ${recoveredInMs} ms'de düzeldi (bekçi: ${g.totalReconnects} kez)")
        g.stop()
    }

    @Test
    fun sourceDown_serverSendsFiller_guardReconnectsAndPictureReturns() {
        // Kaynak düştü, sunucu bağlantıyı kapatmadan boş paket yolluyor: zaman aşımı hiç dolmaz.
        val junk = raw(ts, "video/mp2t", live = true) { n ->
            if (n == 1) RawStreamServer.Plan.FillerAfter(188 * 1330) else RawStreamServer.Plan.Healthy
        }
        val p = player(junk.url("/live/user/pass/8.ts"))
        val g = guard(p, live = true)
        assertTrue("yayın başlamadı", runFor(30_000) { p.playbackState == Player.STATE_READY })
        assertTrue("yayın takılmadı", runFor(30_000) { p.playbackState == Player.STATE_BUFFERING })
        assertTrue("bekçi yeniden bağlanmadı", runFor(20_000) { g.totalReconnects >= 1 })
        assertTrue("görüntü geri gelmedi", runFor(30_000) { p.isPlaying && p.currentPosition >= 3_000 })
        assertNull(p.playerError)
        g.stop()
    }

    @Test
    fun withoutTheGuard_sourceDownLeavesTheSpinnerForever() {
        // Karşılaştırma (eski davranış): bekçi olmadan aynı durumda yayın "yükleniyor"da kalır, hata da gelmez.
        val junk = raw(ts, "video/mp2t", live = true) { n ->
            if (n == 1) RawStreamServer.Plan.FillerAfter(188 * 1330) else RawStreamServer.Plan.Healthy
        }
        val p = player(junk.url("/live/user/pass/3.ts"))
        assertTrue(runFor(30_000) { p.playbackState == Player.STATE_READY })
        assertTrue("yayın takılmadı", runFor(30_000) { p.playbackState == Player.STATE_BUFFERING })
        assertFalse("bekçisiz yayın kendiliğinden düzelmemeli", runFor(25_000) { p.isPlaying || p.playerError != null })
        assertEquals("tek bağlantıda takılı kaldı", 1, junk.connections.get())
    }

    @Test
    fun movieHangs_reconnectResumesWhereItStopped_notFromTheStart() {
        // Sunucu ~10 sn'lik veriden sonra 2 sn'de bir tek bayt gönderiyor: bağlantı canlı, zaman aşımı
        // dolmaz, görüntü gelmez. Bekçi kaldığı yerden yeniden bağlanmalı (Range isteği).
        val r = raw(mp4, "video/mp4", live = false) { n ->
            if (n == 1) RawStreamServer.Plan.TrickleAfter(190_000) else RawStreamServer.Plan.Healthy
        }
        val p = player(r.url("/movie/user/pass/4.mp4"))
        var positionAtReconnect = -1L
        val g = PlaybackStallGuard(p, isLive = { false }, onReconnect = {
            positionAtReconnect = p.currentPosition
        }).also { it.start() }

        assertTrue("bekçi yeniden bağlanmadı", runFor(60_000) { g.totalReconnects >= 1 })
        assertTrue("film kaldığı yerden sürmedi", runFor(30_000) { p.isPlaying && p.currentPosition > positionAtReconnect + 1_000 })
        assertTrue("film baştan başladı ($positionAtReconnect)", positionAtReconnect >= 3_000)
        assertNull(p.playerError)
        g.stop()
    }

    @Test
    fun serverNeverRecovers_guardTriesThreeTimesThenShowsRetry() {
        val r = raw(mp4, "video/mp4", live = false) { n ->
            if (n == 1) RawStreamServer.Plan.TrickleAfter(190_000) else RawStreamServer.Plan.NoResponse
        }
        val p = player(r.url("/movie/user/pass/5.mp4"))
        var gaveUp = false
        // Test kısa sürsün diye 3-4 sn'lik bekçi (uygulamada 8 ve 15 sn).
        val g = guard(p, live = false, watchdog = StallWatchdog(stallMs = 3_000, restartGraceMs = 4_000)) { gaveUp = true }
        assertTrue("bekçi pes etmedi", runFor(90_000) { gaveUp })
        assertEquals(StallWatchdog.MAX_RECONNECTS, g.totalReconnects)
        g.stop()
    }

    // ------------------------------------------------------------------ Takılma (yavaş internet)

    @Test
    fun slowConnection_isNotInterrupted_andPlaybackKeepsGoing() {
        // Film ~19 KB/sn; internet 13 KB/sn: film takıla takıla oynar. Veri geldiği sürece bekçi karışmamalı.
        val s = server(mp4, "video/mp4") { Behavior.Slow(13_000) }
        val p = player(s.url("/movie/user/pass/6.mp4"))
        val counter = RebufferCounter(p)
        val g = guard(p, live = false)
        assertTrue("yavaş bağlantıda film ilerlemedi", runFor(60_000) { p.currentPosition >= 12_000 || p.playerError != null })
        assertNull(p.playerError)
        assertTrue("senaryo takılma üretmedi", counter.rebuffers >= 1)
        assertEquals("veri gelirken yeniden bağlanılmamalı", 0, g.totalReconnects)
        println("Yavaş bağlantı: ${counter.rebuffers} takılma, konum ${p.currentPosition} ms")
        g.stop()
    }

    // ------------------------------------------------------------------ Hatalar ve çökme

    @Test
    fun serverErrors_endInAnErrorNotAHangOrCrash() {
        for (code in listOf(404, 500, 503)) {
            val s = server(ts, "video/mp2t") { Behavior.Error(code) }
            val p = player(s.url("/live/user/pass/$code.ts"))
            val g = guard(p, live = true)
            assertTrue("$code hatası oynatıcıya ulaşmadı", runFor(40_000) { p.playerError != null })
            assertEquals(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, p.playerError!!.errorCode)
            g.stop()
        }
    }

    @Test
    fun droppedConnections_endInAnErrorNotACrash() {
        val s = server(ts, "video/mp2t") { Behavior.Disconnect }
        val p = player(s.url("/live/user/pass/7.ts"))
        val g = guard(p, live = true)
        assertTrue("kopan bağlantı hataya dönmedi", runFor(60_000) { p.playerError != null })
        assertNotNull(p.playerError)
        g.stop()
    }

    // ------------------------------------------------------------------ Stres

    @Test
    fun stress_rapidChannelZapping_thenPlaysTheLastChannel() {
        val a = server(ts, "video/mp2t")
        val b = server(mp4, "video/mp4")
        val p = player(a.url("/live/user/pass/100.ts"))
        val g = guard(p, live = true)
        // Kumandada tuşa basılı tutar gibi: 60 kez art arda kanal değiştir.
        repeat(60) { i ->
            val url = if (i % 2 == 0) b.url("/movie/user/pass/$i.mp4") else a.url("/live/user/pass/$i.ts")
            p.setMediaItem(ExoPlayerConfigurator.buildMediaItemForUrl(url))
            p.prepare()
            p.play()
            runFor((20L..120L).random())
        }
        p.setMediaItem(ExoPlayerConfigurator.buildMediaItemForUrl(a.url("/live/user/pass/last.ts")))
        p.prepare()
        assertTrue("son kanal oynamadı", runFor(30_000) { p.isPlaying && p.currentPosition >= 2_000 || p.playerError != null })
        assertNull(p.playerError)
        g.stop()
    }

    @Test
    fun stress_openAndCloseThePlayerManyTimes_noLeaks() {
        val s = server(ts, "video/mp2t")
        // Isınma: ilk oynatıcının açtığı kalıcı iş parçacıkları sayılmasın.
        player(s.url("/live/user/pass/warm.ts")).also { w ->
            runFor(20_000) { w.playbackState == Player.STATE_READY }
            w.release()
            players.remove(w)
        }
        runFor(500)
        fun playerThreads() = Thread.getAllStackTraces().keys.count { t ->
            t.isAlive && (t.name.startsWith("ExoPlayer:Playback") || t.name.startsWith("ExoPlayer:Loader"))
        }
        val threadsBefore = playerThreads()
        repeat(15) { i ->
            val p = player(s.url("/live/user/pass/$i.ts"))
            val g = guard(p, live = true)
            assertTrue("$i. açılışta oynamadı", runFor(20_000) { p.playbackState == Player.STATE_READY || p.playerError != null })
            assertNull(p.playerError)
            runFor((50L..400L).random())
            g.stop()
            p.release()
            players.remove(p)
        }
        runFor(3_000) { playerThreads() <= threadsBefore }
        val threadsAfter = playerThreads()
        assertTrue("oynatıcı iş parçacığı sızıntısı: $threadsBefore → $threadsAfter", threadsAfter <= threadsBefore)
    }

    @Test
    fun highBufferSetting_withMemoryCap_stillPlays() {
        // "Yüksek" tampon + bellek sınırı + akıllı tampon birlikte oynatmayı kilitlememeli.
        val big = ByteArray(ts.size * 6).also { arr -> for (i in 0 until 6) System.arraycopy(ts, 0, arr, i * ts.size, ts.size) }
        val s = server(big, "video/mp2t")
        val p = player(s.url("/live/user/pass/big.ts"), buffer = "Yüksek")
        assertTrue(runFor(30_000) { p.playbackState == Player.STATE_READY || p.playerError != null })
        assertNull(p.playerError)
        assertTrue(runFor(30_000) { p.isPlaying && p.currentPosition >= 3_000 })
    }
}
