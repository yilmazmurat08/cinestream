package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TV_SEEK_COMMIT_DELAY_MS
import com.example.ui.tv.TvDetailScreen
import com.example.ui.tv.TvHomeScreen
import com.example.ui.tv.TvSeekBar
import com.example.ui.tv.TvSection
import com.example.ui.tv.tvSeekStepSec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV düzeltmeleri (17 Ekim 2026, Mi TV Stick geri bildirimi):
 * 1) Arama ayrı sayfa değil: PRO'nun solundaki simge yerinde çubuğa açılır, ✨ hemen görünür; yazınca alttaki küçük
 *    panelde bilgi mesajı, Ara'ya basınca sonuçlar çıkar; kumandayla sonuca inilir; Geri kapatır.
 * 2) Detayda yapımın adı kesilmez: odak düğme satırındayken sayfa hep en üsttedir.
 * 3) Oynatıcıda ilerleme çubuğu kumandayla seçilir; Sol/Sağ sarar, OK ya da kısa bekleme sonrası oraya gider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television")
class TvSearchSeekTitleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun movie(id: Int, title: String) = IPTVItem(
        id = id, playlistId = 1, name = title, cleanedName = title, logoUrl = null, streamUrl = "http://s/$id.mp4",
        category = "Aksiyon", type = "MOVIE", summary = "Gerçek bir konu metni. ".repeat(12)
    )

    @Test
    fun search_opensInPlace_leftOfPro_showsHintThenResults_backCloses() {
        runBlocking { db.iptvDao().insertItems(listOf(movie(9501, "Karanlık Şehir"), movie(9502, "Mavi Deniz"))) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        var picked: IPTVItem? = null
        compose.setContent {
            TvHomeScreen(
                viewModel = vm, initialFocus = TvSection.LIVE, onFocusChanged = {}, onOpenSection = {},
                onOpenAssistant = {}, onSearchResult = { picked = it }, onPlayContinue = {}
            )
        }
        compose.waitForIdle()

        // Arama simgesi PRO'nun hemen solunda.
        val search = compose.onNodeWithTag("tv_top_search").getBoundsInRoot()
        val pro = compose.onNodeWithTag("tv_top_pro").getBoundsInRoot()
        assertTrue("arama PRO'nun solunda olmalı", search.right <= pro.left)

        // Yukarı → PRO, Sol → Arama, OK → çubuk açılır, yazı alanı odaklı, ✨ görünür; panel henüz yok.
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        compose.onNodeWithTag("tv_top_pro").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag("tv_top_search").assertIsFocused()
        press(Key.DirectionCenter)
        waitForTag("tv_search_input")
        compose.onNodeWithTag("tv_search_input").assertIsFocused()
        compose.onNodeWithTag("tv_search_ai").assertExists()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_search_dropdown")).fetchSemanticsNodes().size)
        // Çubuk hâlâ PRO'nun solunda (sola doğru uzadı).
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("tv_search_bar").getBoundsInRoot().right <= compose.onNodeWithTag("tv_top_pro").getBoundsInRoot().left)

        // Yazınca panel açılır, bilgi mesajı okunur; sonuçlar Ara'ya basınca gelir.
        compose.onNodeWithTag("tv_search_input").performTextInput("karanlik")
        waitForTag("tv_search_dropdown")
        compose.onNode(hasText("yapay zeka", substring = true)).assertExists()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_search_result")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("tv_search_input").performImeAction()
        waitForTag("tv_search_result")
        compose.onNode(hasText("Karanlık Şehir")).assertExists()

        // Aşağı → ilk sonuç; OK seçer, arama kapanır.
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_search_result").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(9501, picked?.id)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_search_input")).fetchSemanticsNodes().size)

        // Yeniden aç, Geri ile kapat: odak arama simgesine döner, yazı temizlenir.
        compose.onNodeWithTag("tv_top_search").requestFocus()
        compose.waitForIdle()
        press(Key.DirectionCenter)
        waitForTag("tv_search_input")
        compose.onNodeWithTag("tv_search_input").performTextInput("mavi")
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        waitForTag("tv_top_search")
        compose.onNodeWithTag("tv_top_search").assertIsFocused()
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun detail_titleStaysVisible_whenFocusReturnsToActionRow() {
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val item = movie(9510, "Uzun Adlı Bir Deneme Filmi").copy(cast = "Ada Oyuncu, Bora Oyuncu", director = "Can Yönetmen")
        compose.setContent { TvDetailScreen(viewModel = vm, item = item, onPlay = {}) }
        waitForTag("tv_detail_play")
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()
        val top = compose.onNodeWithTag("tv_detail_title").getBoundsInRoot().top
        assertEquals(40.dp, top)

        // Aşağıdaki satırlara inip düğmelere dönünce sayfa yine en üstte; ad görünür.
        waitForTag("tv_person_Can Yönetmen")
        repeat(3) { press(Key.DirectionDown) }
        repeat(6) { press(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(40.dp, compose.onNodeWithTag("tv_detail_title").getBoundsInRoot().top)
    }

    @Test
    fun seekBar_leftRightScrubs_thenJumps() {
        var seekedTo: Long? = null
        compose.setContent {
            TvSeekBar(positionSec = 600, durationSec = 6_500, onSeekTo = { seekedTo = it }, onUserActivity = {})
        }
        compose.onNodeWithTag("tv_seek_bar").requestFocus()
        compose.waitForIdle()
        compose.onNodeWithTag("tv_seek_thumb").assertExists()

        compose.mainClock.autoAdvance = false
        repeat(3) { press(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(100)
        assertNull("tuşa basarken hemen sarılmaz", seekedTo)
        compose.onNode(hasText("10:30")).assertExists()
        compose.mainClock.advanceTimeBy(TV_SEEK_COMMIT_DELAY_MS + 100)
        compose.waitForIdle()
        assertEquals(630L, seekedTo)

        // OK hemen gider.
        seekedTo = null
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(50)
        assertEquals(590L, seekedTo)

        // Basılı tutunca adım büyür.
        assertEquals(10L, tvSeekStepSec(0))
        assertEquals(30L, tvSeekStepSec(5))
        assertEquals(60L, tvSeekStepSec(20))
    }
}
