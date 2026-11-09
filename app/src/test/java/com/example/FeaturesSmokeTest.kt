package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.features.SportsHost
import com.example.ui.features.games.GamesHost
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
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
 * Oyunlar ve Spor: telefon (realme C55 ölçüleri) ve TV (Mi TV Stick ölçüleri) üzerinde açılır, oynanır,
 * arka plana alınıp geri getirilir. Amaç: çökme olmaması ve temel akışların çalışması.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
open class FeaturesSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected open val tvMode = false
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun cleanUp() {
        db.close()
    }

    /** Saat elle ilerletilir: yükleniyor göstergeleri gibi sonsuz animasyonlar testi bekletmez. */
    private fun settle(ms: Long = 600) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(50)
            Thread.sleep(5)
            left -= 50
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Arka planda (gerçek zamanda) hesaplanan içerik gelene kadar bekler (en çok 8 sn). */
    private fun waitFor(tag: String): Boolean {
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end) {
            if (exists(tag)) return true
            settle(100)
            Thread.sleep(40)
        }
        return exists(tag)
    }

    private fun click(tag: String) {
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun press(key: Key) {
        compose.onAllNodes(isRoot()).let { it[it.fetchSemanticsNodes().size - 1] }.performKeyInput { pressKey(key) }
        settle(400)
    }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(content: @Composable () -> Unit) {
        if (screen == null) {
            screen = content
            compose.setContent { MyApplicationTheme(dynamicColor = false) { screen?.invoke() } }
        } else {
            compose.runOnUiThread { screen = content }
        }
        settle(1_500)
    }

    /** Cihazdaki küçük bir poster dosyası (puzzle'ın parçalara bölebilmesi için gerçek bir resim). */
    private fun localPoster(): String {
        val file = java.io.File(app.cacheDir, "smoke_poster.png")
        if (!file.exists()) {
            val bmp = android.graphics.Bitmap.createBitmap(120, 180, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).drawColor(android.graphics.Color.MAGENTA)
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        return android.net.Uri.fromFile(file).toString()
    }

    private fun seed(): IPTVViewModel {
        val poster = localPoster()
        val cats = listOf("Korku", "Savaş", "Aksiyon", "Komedi", "XXX Yetişkin")
        val items = (0 until 120).map { i ->
            val type = if (i % 4 == 3) "SERIES" else "MOVIE"
            IPTVItem(
                id = 81000 + i, playlistId = 1, name = "Yapım $i", cleanedName = "Yapım $i",
                logoUrl = poster, streamUrl = "http://10.255.255.1/v/$i",
                category = cats[i % cats.size], type = type, summary = "Özet $i", releaseDate = "20${10 + i % 15}-01-01",
                genre = "Dram", rating = 7.5
            )
        } + (0 until 30).map { i ->
            IPTVItem(
                id = 82000 + i, playlistId = 1, name = "Kanal $i", cleanedName = "Kanal $i", logoUrl = null,
                streamUrl = "http://10.255.255.1/l/$i", category = "Spor", type = "LIVE"
            )
        }
        runBlocking { db.iptvDao().insertItems(items) }
        var vm: IPTVViewModel? = null
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    IPTVViewModel(app, IPTVRepository(db.iptvDao(), db)) as T
            })[IPTVViewModel::class.java]
        }
        compose.mainClock.autoAdvance = false
        return vm!!
    }

    @Test
    fun games_allThree_playWithoutCrash() {
        val vm = seed()
        var wentToSeries = false
        show { GamesHost(viewModel = vm, tvMode = tvMode, onGoSeriesFolders = { wentToSeries = true }) }
        assertTrue(exists("game_card_SWIPE") && exists("game_card_WHEEL") && exists("game_card_QUIZ"))

        // 1. Kaydırma: düğmeler, parmakla kaydırma ve (TV'de) sol/sağ tuşları.
        click("game_card_SWIPE")
        assertTrue("Kaydırma kartı görünmeli", waitFor("swipe_card"))
        click("swipe_like")
        click("swipe_skip")
        if (tvMode) {
            press(Key.DirectionRight)
            press(Key.DirectionLeft)
        } else {
            compose.onAllNodes(hasTestTag("swipe_card"), useUnmergedTree = true)[0].performTouchInput { swipeRight() }
            settle()
            compose.onAllNodes(hasTestTag("swipe_card"), useUnmergedTree = true)[0].performTouchInput { swipeLeft() }
            settle()
        }
        click("swipe_filter_SERIES")
        settle(800)
        click("swipe_filter_MOVIE")
        assertTrue(waitFor("swipe_card"))
        // Beğenilen yapımlar Listeme eklendi.
        val liked = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM iptv_items WHERE isFavorite = 1").use { c ->
            c.moveToFirst(); c.getInt(0)
        }
        assertTrue("Beğenilen yapım Listeme eklenmeli", liked >= 1)
        back()
        assertTrue(exists("game_card_WHEEL"))

        // 2. Çark: döner, durduğu kategoriden 3 film çıkar; yetişkin kategori çarkta yok.
        click("game_card_WHEEL")
        assertTrue("Çark görünmeli", waitFor("wheel"))
        click("wheel_spin")
        settle(4_500)
        assertTrue("Çark durunca 3 film gelmeli", waitFor("wheel_pick_0") && exists("wheel_pick_2"))
        click("wheel_pick_0")
        assertTrue(vm.selectedItem.value != null)
        compose.runOnUiThread { vm.selectItem(null) }
        settle()
        // Kategoriler değiştirilebilir: rastgele yenileri ya da kullanıcının kendi seçimi (en az 2, kaydedilir).
        click("wheel_shuffle")
        assertTrue(exists("wheel"))
        click("wheel_choose")
        assertTrue("Seçim penceresi açılmalı", waitFor("wheel_choose_dialog"))
        click("wheel_choice_0")
        click("wheel_choose_save")
        assertTrue(!exists("wheel_choose_dialog"))
        assertTrue(exists("wheel"))
        click("wheel_spin")
        settle(4_500)
        assertTrue("Seçilen kategorilerle çark yine film vermeli", waitFor("wheel_pick_0"))
        back()

        // 4. Poster puzzle: poster parçalara bölünür; iki parçaya dokununca yer değişir; ipucu tüm posteri gösterir.
        click("game_card_PUZZLE")
        assertTrue("Puzzle tahtası görünmeli", waitFor("puzzle_tile_0"))
        if (tvMode) {
            press(Key.DirectionCenter)
            press(Key.DirectionRight)
            press(Key.DirectionCenter)
        } else {
            click("puzzle_tile_0")
            click("puzzle_tile_4")
        }
        assertTrue(exists("puzzle_tile_8"))
        click("puzzle_hint")
        assertTrue(exists("puzzle_hint_image"))
        settle(3_000)
        click("puzzle_grid_4")
        assertTrue("4×4 tahtada 16 parça olmalı", waitFor("puzzle_tile_15"))
        back()
        assertTrue(exists("game_card_PUZZLE"))

        // 3. Yarışma: listede Xtream dizi kataloğu yok; çökmeden "yeterli bilgi yok" der.
        click("game_card_QUIZ")
        settle(2_000)
        assertTrue(exists("games_host"))
        back()
        assertTrue(exists("game_card_QUIZ"))
        assertTrue(!wentToSeries)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        settle(300)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle(500)
        assertEquals(Lifecycle.State.RESUMED, compose.activityRule.scenario.state)
    }

    @Test
    fun sports_opensWithUserChannels_withoutCrash() {
        val vm = seed()
        show { SportsHost(viewModel = vm, onPlayItem = {}, tvMode = tvMode) }
        settle(2_000)
        assertTrue(exists("sports_host"))
        if (tvMode) {
            press(Key.DirectionDown)
            press(Key.DirectionRight)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        settle(300)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle(500)
        assertTrue(exists("sports_host"))
    }
}

/** Mi TV Stick: Android 9, 1080p TV, kumanda. */
@Config(sdk = [28], qualifiers = "tr-w960dp-h540dp-land-television")
class FeaturesSmokeTvTest : FeaturesSmokeTest() {
    override val tvMode = true
}
