package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.HomeScreen
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

/**
 * Yeni buzlu alt bar (telefon, realme C55 ölçüleri): dört sekme ve profil menüsü çalışıyor mu,
 * menüden Ayarlar / Listem / Profil açılıyor mu, Oyunlar ve Spor ücretsizde PRO ekranını açıyor mu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
class FrostedBottomBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    // Kendi bellek içi veritabanı: diğer testlerin arka plan işleri bu teste karışmaz.
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun cleanUp() {
        db.close()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(200)
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun start(categoryOf: (Int) -> String = { "Kategori" }): IPTVViewModel {
        val items = (0 until 120).map { i ->
            val type = listOf("LIVE", "MOVIE", "SERIES")[i % 3]
            IPTVItem(
                id = 79000 + i, playlistId = 1, name = "Öğe $i", cleanedName = "Öğe $i", logoUrl = null,
                streamUrl = "http://10.255.255.1/x/$i", category = categoryOf(i), type = type
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
        compose.setContent {
            MyApplicationTheme(dynamicColor = false) {
                HomeScreen(viewModel = vm!!, onNavigateToChat = {}, onPlayItem = {}, onPlayContinueWatching = {}, onNavigateToMultiScreen = {})
            }
        }
        settle()
        return vm!!
    }

    @Test
    fun tabs_and_profileMenu() {
        assertTrue("Anahtar bu dalda açık olmalı", BuildConfig.NEW_BOTTOM_BAR)
        val vm = start()
        assertTrue(exists("frosted_bottom_bar"))
        // Eski bardaki İzleme Listesi ve Profil sekmeleri artık barda yok (menüye taşındı).
        assertTrue(!exists("bottom_nav_WATCHLIST") && !exists("bottom_nav_SETTINGS"))

        for (tab in listOf("MOVIE", "SERIES", "LIVE", "ALL")) {
            click("bottom_nav_$tab")
            assertEquals(tab, vm.selectedTypeFilter.value)
        }

        click("bottom_nav_profile_menu")
        assertTrue(exists("frosted_profile_menu"))
        click("menu_settings")
        assertEquals("SETTINGS", vm.selectedTypeFilter.value)
        assertTrue(!exists("frosted_profile_menu"))

        click("bottom_nav_profile_menu")
        click("menu_my_list")
        assertEquals("WATCHLIST", vm.selectedTypeFilter.value)

        // Oyunlar ve Spor PRO: ücretsiz kullanıcıda sayfa değişmez, PRO ekranı açılır.
        click("bottom_nav_profile_menu")
        click("menu_games")
        assertTrue(vm.showPaywallDialog.value)
        compose.runOnUiThread { vm.closePaywall() }
        click("bottom_nav_profile_menu")
        click("menu_sport")
        assertTrue(vm.showPaywallDialog.value)
        compose.runOnUiThread { vm.closePaywall() }
        assertEquals("WATCHLIST", vm.selectedTypeFilter.value)
        assertTrue(!exists("frosted_profile_menu"))

        // Profil: Ayarlar'daki profil düzenleme penceresi açılır. Bu pencere açıkken Robolectric'te Compose hiç
        // "boşta" sayılmıyor (Ayarlar > Profil'de de aynı); saat elle ilerletilir.
        click("bottom_nav_profile_menu")
        compose.mainClock.autoAdvance = false
        compose.onAllNodes(hasTestTag("menu_profile"), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        repeat(20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(50)
        }
        assertTrue(exists("edit_profile_pick_photo"))
    }

    /**
     * Bar içeriğin üstünde yüzer: arkasında düz şerit yok, liste barın arkasına kadar uzanır.
     * Liste sonuna kaydırılınca son klasör barın üstünde görünür (barın altında kalmaz).
     */
    @Test
    fun contentScrollsBehindTheBar_lastItemStaysAboveIt() {
        start { i -> "Klasör ${i % 30}" }
        click("bottom_nav_LIVE")
        val bar = compose.onAllNodes(hasTestTag("frosted_bottom_bar"), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        val list = compose.onAllNodes(hasTestTag("folder_grid_view"), useUnmergedTree = true)[0]
        assertTrue("Liste barın arkasına uzanmalı", list.fetchSemanticsNode().boundsInRoot.bottom > bar.top)

        repeat(15) {
            list.performTouchInput { swipeUp() }
            settle()
        }
        // Ekrandaki en alttaki klasör kartı (liste sonuna gelindi).
        val lastBottom = (0 until 30).map { "folder_card_Klasör $it" }.filter { exists(it) }
            .maxOf { compose.onAllNodes(hasTestTag(it), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.bottom }
        val pillTop = bar.top + 8 * compose.activity.resources.displayMetrics.density
        assertTrue("Son klasör (alt $lastBottom) barın (üst $pillTop) üstünde kalmalı", lastBottom <= pillTop)
    }

    /**
     * Üst bar da buzlu ve içeriğin üstünde yüzer: ilk açılışta başlık ve ilk klasör barın altında kalmaz,
     * liste barın arkasına uzanır (kaydırınca içerik barın arkasından geçer).
     */
    @Test
    fun topBar_contentStartsBelowIt_andScrollsBehindIt() {
        start { i -> "Klasör ${i % 30}" }
        click("bottom_nav_LIVE")
        val topBar = compose.onAllNodes(hasTestTag("home_top_bar"), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        val list = compose.onAllNodes(hasTestTag("folder_grid_view"), useUnmergedTree = true)[0]
        assertTrue("Liste üst barın arkasına uzanmalı", list.fetchSemanticsNode().boundsInRoot.top < topBar.bottom)
        val firstCardTop = (0 until 30).map { "folder_card_Klasör $it" }.filter { exists(it) }
            .minOf { compose.onAllNodes(hasTestTag(it), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.top }
        assertTrue("İlk klasör (üst $firstCardTop) üst barın (alt ${topBar.bottom}) altında kalmamalı", firstCardTop >= topBar.bottom)

        list.performTouchInput { swipeUp() }
        settle()
        val anyBehind = (0 until 30).map { "folder_card_Klasör $it" }.filter { exists(it) }
            .any { compose.onAllNodes(hasTestTag(it), useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.top < topBar.bottom }
        assertTrue("Kaydırınca içerik üst barın arkasından geçmeli", anyBehind)
    }
}
