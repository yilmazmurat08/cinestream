package com.example

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import com.example.data.model.IPTVItem
import com.example.ui.screens.PlayerScreen
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvPlayerUiState
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
 * TV'de ses ve altyazı seçimi, gerçek oynatıcıyla (10 altyazılı film): kumandayla aşağı inerken odaktaki satır hep
 * ekranda kalır, yukarı tuşuyla en üste geri dönülür.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvTrackSheetTest {

    @get:Rule(order = 0)
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.forAllSupportedMimeTypes()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val mkv = javaClass.getResourceAsStream("/player/sample-subs.mkv")!!.use { it.readBytes() }
    private var server: RawStreamServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
        TvDevice.overrideForTest = null
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        // Seçim penceresi ayrı bir pencere (iletişim kutusu): tuşlar en üstteki köke gönderilir.
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { it[it.fetchSemanticsNodes().size - 1] }.performKeyInput { pressKey(key) }
        runFor(300) { false }
    }

    private fun tagOf(node: androidx.compose.ui.semantics.SemanticsNode): String? = node.config.let { c ->
        if (androidx.compose.ui.semantics.SemanticsProperties.TestTag in c) c[androidx.compose.ui.semantics.SemanticsProperties.TestTag] else null
    }

    /** Seçim penceresinde odaktaki satır (oynatıcı penceresinin kendi odağı ayrıca "odaklı" görünür). */
    private fun focusedRow(): androidx.compose.ui.semantics.SemanticsNode? =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull { tagOf(it)?.startsWith("track_") == true }

    private fun focusedTag(): String? = focusedRow()?.let(::tagOf)

    @Test
    fun manySubtitles_dpadDownKeepsFocusOnScreen_upReturnsToTop() {
        TvDevice.overrideForTest = true
        val s = RawStreamServer(mkv, "video/x-matroska", live = false).also { server = it }
        val movie = IPTVItem(
            id = 99301, playlistId = 1, name = "Altyazılı Film", cleanedName = "Altyazılı Film", logoUrl = null,
            streamUrl = s.url("/movie/u/p/99301.mkv"), category = "Film", type = "MOVIE"
        )
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = com.example.data.db.AppDatabase.getDatabase(app)
        val vm = com.example.ui.IPTVViewModel(app, com.example.data.repository.IPTVRepository(db.iptvDao(), db))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerScreen(
                item = movie, siblingItems = emptyList(), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm, tvMode = true, tvUiState = TvPlayerUiState()
            )
        }
        assertTrue("kontroller açılmadı", runFor(30_000) { exists("tv_bar_tracks") })
        compose.onAllNodes(hasTestTag("tv_bar_tracks"), useUnmergedTree = true)[0].performClick()
        assertTrue("altyazılar listede yok", runFor(15_000) { exists("track_sub_3") })
        runFor(500) { false }

        val rootBottom = compose.onAllNodes(androidx.compose.ui.test.isRoot())[0].fetchSemanticsNode().boundsInRoot.bottom
        // Altyazı sütununa geç ve en alta kadar in: odaktaki satır her adımda ekranda görünmeli.
        press(Key.DirectionRight)
        val visited = mutableListOf<String?>()
        repeat(14) {
            press(Key.DirectionDown)
            val tag = focusedTag()
            visited += tag
            val node = focusedRow()
            assertTrue("odak kayboldu (adım $it)", node != null)
            val b = node!!.boundsInWindow
            assertTrue("odaktaki satır ($tag) ekranda görünmüyor: $b (ekran yüksekliği $rootBottom)", b.height > 1f && b.top >= 0f && b.bottom <= rootBottom + 1f)
        }
        assertEquals("en alttaki altyazıya inilmeli: $visited", "track_sub_9", visited.last())
        repeat(14) { press(Key.DirectionUp) }
        assertEquals("yukarı tuşuyla en üste dönülmeli", "track_sub_off", focusedTag())
    }
}
