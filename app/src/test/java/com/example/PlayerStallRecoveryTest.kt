package com.example

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import com.example.data.model.IPTVItem
import com.example.ui.screens.PlayerScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Oynatıcı ekranının kendisiyle donma testi (telefon, realme C55 ölçüleri, yatay). Kaynağı düşen canlı yayında
 * sunucu boş paket gönderir: bağlantı zaman aşımı hiç dolmaz, yalnızca donma bekçisi kurtarabilir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp-land-xxhdpi")
class PlayerStallRecoveryTest {

    @get:Rule(order = 0)
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.forAllSupportedMimeTypes()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ts = javaClass.getResourceAsStream("/player/sample.ts")!!.use { it.readBytes() }
    private var server: RawStreamServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Ekranın, oynatıcının ve bekçinin saatini gerçek zamanla birlikte ilerletir. */
    private fun runFor(maxMs: Long, until: () -> Boolean): Boolean {
        val t0 = System.currentTimeMillis()
        var last = t0
        while (true) {
            if (until()) return true
            val now = System.currentTimeMillis()
            if (now - t0 >= maxMs) return false
            val step = (now - last).coerceAtLeast(1)
            last = now
            val before = SystemClock.uptimeMillis()
            compose.mainClock.advanceTimeBy(step)
            // Ekran saati sistem saatini ilerletmediyse (oynatıcı ve bekçi onu kullanır) aradaki farkı kapat.
            val moved = SystemClock.uptimeMillis() - before
            if (moved < step) ShadowLooper.idleMainLooper(step - moved, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
    }

    private fun open(plan: (Int) -> RawStreamServer.Plan) {
        val s = RawStreamServer(ts, "video/mp2t", live = true).also { it.plan = plan; server = it }
        val item = IPTVItem(
            id = 99001, playlistId = 1, name = "Test Kanalı", cleanedName = "Test Kanalı", logoUrl = null,
            streamUrl = s.url("/live/u/p/99001.ts"), category = "Ulusal", type = "LIVE"
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerScreen(item = item, siblingItems = emptyList(), onBack = {}, onPlayItem = {}, onProgressUpdate = { _, _, _ -> })
        }
    }

    @Test
    fun sourceDown_screenRecoversByItself_noRetryNeeded() {
        open { n -> if (n == 1) RawStreamServer.Plan.FillerAfter(188 * 1330) else RawStreamServer.Plan.Healthy }
        val s = server!!
        assertTrue("yayın başlamadı", runFor(30_000) { s.connections.get() >= 1 && !exists("buffering_overlay") })
        // Yayın ~10 sn sonra donar: dönen daire çıkar.
        assertTrue("donma görünmedi", runFor(30_000) { exists("buffering_overlay") })
        // Bekçi yeniden bağlanır, görüntü geri gelir; kullanıcı bir şey yapmaz.
        assertTrue("bekçi yeniden bağlanmadı", runFor(20_000) { s.connections.get() >= 2 })
        assertTrue("dönen daire kaybolmadı", runFor(20_000) { !exists("buffering_overlay") })
        assertFalse("hata ekranı çıkmamalı", exists("player_stream_failed_retry"))
    }

    @Test
    fun sourceNeverComesBack_retryButtonInsteadOfEndlessSpinner() {
        // Kaynak hiç geri gelmiyor: sonraki bağlantılar baştan beri boş paket.
        open { n -> RawStreamServer.Plan.FillerAfter(if (n == 1) 188 * 1330 else 0) }
        val s = server!!
        assertTrue("donma görünmedi", runFor(40_000) { exists("buffering_overlay") && s.connections.get() >= 1 })
        // 3 yeniden bağlanmadan sonra sonsuz dönen daire yerine "Yeniden dene" çıkar.
        assertTrue("Yeniden dene çıkmadı", runFor(90_000) { exists("player_stream_failed_retry") })
        assertEquals("ilk bağlantı + 3 yeniden bağlanma", 4, s.connections.get())
    }
}
