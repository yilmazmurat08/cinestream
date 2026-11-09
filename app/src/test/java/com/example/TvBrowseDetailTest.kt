package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollToIndex
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvAppState
import com.example.ui.tv.TvBrowseScreen
import com.example.ui.tv.TvDetailScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Filmler ekranı ve detay: kategori listesinden Sağ ile ızgaraya, ilk sütundan Sol ile seçili kategoriye dönülür;
 * poster OK ile detay açılır. Detayda ilk odak "İzle"; oyuncu satırında OK kişi sayfasını açar, Geri ile dönünce
 * odak aynı kişidedir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvBrowseDetailTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    // Kendi veritabanı: uygulamanın ortak veritabanını kullanan önceki testlerin arka plan işleri bu testin ilk
    // çizimine karışıyordu (tüm testler birlikte çalışınca ara sıra "performMeasureAndLayout called during measure
    // layout"). Ayrı bir bellek içi veritabanıyla test yalnızca kendi verisini görür.
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() = db.close()

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun pressUntilFocused(key: Key, tag: String, max: Int = 6) {
        for (i in 0 until max) {
            if (runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess) return
            press(key)
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun movie(id: Int, title: String, category: String, cast: String = "", director: String = "") = IPTVItem(
        id = id, playlistId = 1, name = title, cleanedName = title, logoUrl = null, streamUrl = "http://s/$id.mp4",
        category = category, type = "MOVIE", summary = "Gerçek bir konu metni burada yer alıyor.", cast = cast,
        director = director, trailerUrl = "https://www.youtube.com/watch?v=abc"
    )

    @Test
    fun categoryToGrid_andLeftFromFirstColumnReturnsToCategory_okOpensDetail() {
        runBlocking {
            db.iptvDao().insertItems((1..8).map { movie(9000 + it, "Tv Test Film $it", "TvTest Kategori") } +
                // Seçili kategori listenin ekran dışında kalan kısmında olsun (liste oraya kaydırılıp odak verilmeli)
                (1..30).map { movie(9500 + it, "Aa Film $it", "Aa Film Kat %02d".format(it)) })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val state = TvAppState("MOVIE", "MOVIE", "TvTest Kategori", null, null)
        compose.setContent { TvBrowseScreen(viewModel = vm, type = "MOVIE", state = state) }
        waitForTag("tv_poster_MOVIE_9001")
        compose.onNodeWithTag("tv_category_TvTest Kategori").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_poster_MOVIE_9001").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_poster_MOVIE_9002").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        compose.onNodeWithTag("tv_category_TvTest Kategori").assertIsFocused()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(5_000) { vm.selectedItem.value != null }
        assertEquals(9001, vm.selectedItem.value?.id)
    }

    @Test
    fun detail_focusStartsOnPlay_personPageBackRestoresSamePerson() {
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val item = movie(9100, "Tv Detay Filmi", "TvDetay", cast = "Ada Oyuncu, Bora Oyuncu", director = "Can Yönetmen")
        var played: IPTVItem? = null
        compose.setContent {
            TvDetailScreen(viewModel = vm, item = item, onPlay = { played = it })
        }
        waitForTag("tv_detail_play")
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()

        // Aşağı: oyuncular satırı (yönetmen başta)
        waitForTag("tv_person_Can Yönetmen")
        pressUntilFocused(Key.DirectionDown, "tv_person_Can Yönetmen")
        compose.onNodeWithTag("tv_person_Can Yönetmen").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_person_Ada Oyuncu").assertIsFocused()

        press(Key.DirectionCenter)
        waitForTag("tv_person_screen")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_person_screen")).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_person_Ada Oyuncu").assertIsFocused()

        // Yukarı çıkıp İzle
        // Yukarı: düğme satırına (oyuncunun hizasındaki düğme), sonra Sol ile İzle.
        for (i in 0 until 6) {
            val inActions = listOf("tv_detail_play", "tv_detail_trailer", "tv_detail_favorite", "tv_detail_ai").any { tag ->
                compose.onAllNodes(hasTestTag(tag) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            if (inActions) break
            press(Key.DirectionUp)
        }
        pressUntilFocused(Key.DirectionLeft, "tv_detail_play")
        compose.onNodeWithTag("tv_detail_play").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(9100, played?.id)
    }

    /**
     * Kullanıcı videosu (9 Kasım 2026): kategoride bir film açılıp Geri ile dönülünce uygulama listenin başına (Top 10)
     * atıyordu. Detay paneli uygulamadaki gibi kayarak kapanır; dönüşte aynı kategori seçili kalmalı, odak açılan
     * posterde olmalı.
     */
    @Test
    fun backFromDetail_staysInSameCategory_focusOnOpenedPoster() {
        runBlocking {
            db.iptvDao().insertItems((1..8).map { movie(9000 + it, "Tv Test Film $it", "TvTest Kategori") } +
                (1..12).map { movie(9500 + it, "Aa Film $it", "Aa Film Kat %02d".format(it)) })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val state = TvAppState("MOVIE", "MOVIE", "TvTest Kategori", null, null)
        compose.setContent {
            androidx.compose.foundation.layout.Box {
                TvBrowseScreen(viewModel = vm, type = "MOVIE", state = state)
                val selected by vm.selectedItem.collectAsState()
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected != null,
                    modifier = com.example.ui.tv.tvModalFocus(selected?.id),
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                ) {
                    val item = selected
                    if (item != null) TvDetailScreen(viewModel = vm, item = item, onPlay = {})
                }
            }
        }
        waitForTag("tv_poster_MOVIE_9001")
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_poster_MOVIE_9002").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(5_000) { vm.selectedItem.value != null }
        waitForTag("tv_detail_play")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        // Geri: detay kapanır (MainActivity'deki Geri işleyicisi selectItem(null) çağırır).
        compose.runOnUiThread { vm.selectItem(null) }
        compose.mainClock.autoAdvance = false
        repeat(30) {
            compose.mainClock.advanceTimeBy(100)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("TvTest Kategori", state.movieCategory)
        compose.onNodeWithTag("tv_poster_MOVIE_9002").assertIsFocused()
        compose.onNodeWithTag("tv_poster_MOVIE_9001").assertExists()
    }

    /**
     * Kullanıcı (9 Kasım 2026): kumandayla hızlı gezinirken uygulama kapandı. Kategori listesinde hızla aşağı inip
     * (seçim oturmadan) Sağ ile ızgaraya, sonra Sol ile geri dönmek: Sol'un hedefi ekran dışına kaymış seçili
     * kategoriydi.
     */
    @Test
    fun fastNavigation_downManyThenRightLeft_doesNotCrash() {
        runBlocking {
            db.iptvDao().insertItems((1..40).flatMap { c ->
                (1..8).map { movie(20000 + c * 10 + it, "Hızlı Film $c-$it", "Hızlı Kat %02d".format(c)) }
            })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        val state = TvAppState("MOVIE", "MOVIE", "Hızlı Kat 01", null, null)
        compose.setContent { TvBrowseScreen(viewModel = vm, type = "MOVIE", state = state) }
        waitForTag("tv_poster_MOVIE_20011")
        compose.onNodeWithTag("tv_category_Hızlı Kat 01").assertIsFocused()
        // Izgaraya geç, sonra kategori listesini (odağa dokunmadan) en alta kaydır: seçili kategori satırı artık çizilmiyor.
        press(Key.DirectionRight)
        compose.onNodeWithTag("tv_poster_MOVIE_20011").assertIsFocused()
        compose.onNodeWithTag("tv_categories").performScrollToIndex(40)
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_category_Hızlı Kat 01")).fetchSemanticsNodes().size)
        // İlk sütundan Sol: eskiden "FocusRequester is not initialized" ile çöküyordu. Artık liste kaydırılır, odak
        // seçili kategoriye gelir.
        press(Key.DirectionLeft)
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("tv_category_Hızlı Kat 01").assertIsFocused() }.isSuccess
        }
    }
}
