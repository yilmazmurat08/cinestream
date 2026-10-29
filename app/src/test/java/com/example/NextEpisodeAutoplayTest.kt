package com.example

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import com.example.data.model.IPTVItem
import com.example.ui.screens.PlayerScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Otomatik sonraki bölüm, gerçek oynatıcıyla: bölüm sonuna kadar oynar, 10 sn sayaç çıkar, sonra sonraki bölüm
 * başlar. "İptal" sayacı kapatır ve sonraki bölüm başlamaz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp-land-xxhdpi")
class NextEpisodeAutoplayTest {

    @get:Rule(order = 0)
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.forAllSupportedMimeTypes()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val mp4 = javaClass.getResourceAsStream("/player/sample.mp4")!!.use { it.readBytes() }
    private var server: RawStreamServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Ekranın ve oynatıcının saatini gerçek zamanla birlikte ilerletir (bkz. PlayerStallRecoveryTest). */
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
            val moved = SystemClock.uptimeMillis() - before
            if (moved < step) ShadowLooper.idleMainLooper(step - moved, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
    }

    private fun episode(id: Int, n: Int, url: String) = IPTVItem(
        id = id, playlistId = 1, name = "Dizi S01E0$n", cleanedName = "Dizi S01E0$n", logoUrl = null,
        streamUrl = url, category = "Yerli Diziler", type = "SERIES", season = 1, episode = n
    )

    private fun open(played: MutableList<IPTVItem>): IPTVItem {
        val s = RawStreamServer(mp4, "video/mp4", live = false).also { server = it }
        val first = episode(99101, 1, s.url("/series/u/p/99101.mp4"))
        val second = episode(99102, 2, s.url("/series/u/p/99102.mp4"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerScreen(
                item = first, siblingItems = listOf(first, second), onBack = {},
                onPlayItem = { played += it }, onProgressUpdate = { _, _, _ -> }
            )
        }
        return second
    }

    @Test
    fun episodeEnds_countdown_thenNextEpisodeStarts() {
        val played = mutableListOf<IPTVItem>()
        val second = open(played)
        assertTrue("bölüm bitince sayaç çıkmadı", runFor(90_000) { exists("next_episode_countdown") })
        assertTrue("sayaç bitmeden sonraki bölüm başlamamalı", played.isEmpty())
        assertTrue("sayaç bitince sonraki bölüm başlamadı", runFor(20_000) { played.isNotEmpty() })
        assertEquals(listOf(second.id), played.map { it.id })
    }

    @Test
    fun cancel_closesCountdown_andNextEpisodeDoesNotStart() {
        val played = mutableListOf<IPTVItem>()
        open(played)
        assertTrue("bölüm bitince sayaç çıkmadı", runFor(90_000) { exists("next_episode_countdown") })
        compose.onAllNodes(hasTestTag("next_episode_cancel"), useUnmergedTree = true)[0].performClick()
        runFor(12_000) { false }
        assertTrue("iptalden sonra sayaç kapanmalı", !exists("next_episode_countdown"))
        assertTrue("iptalden sonra sonraki bölüm başlamamalı", played.isEmpty())
    }
}
