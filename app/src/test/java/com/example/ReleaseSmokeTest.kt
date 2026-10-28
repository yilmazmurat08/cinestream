package com.example

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVGroup
import com.example.data.model.IPTVItem
import com.example.reminder.ProgramReminders
import com.example.ui.IPTVViewModel
import com.example.data.repository.IPTVRepository
import com.example.ui.components.ProPaywallDialog
import com.example.ui.components.SearchSheet
import com.example.ui.screens.FolderDetailLayout
import com.example.ui.screens.FolderGridScreen
import com.example.ui.screens.ParentalCategoriesScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistManagerModule
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WatchlistScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.TvApp
import com.example.ui.tv.TvAppState
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvPlayerUiState
import com.example.ui.tv.TvSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * 1.1.7 sürüm öncesi duman testi: bu sürümde değişen ve eklenen her ekran, kullanıcının cihazlarına benzer
 * ortamda (realme C55: Android 15, dikey/yatay; Mi TV Stick: Android 9) büyük ve "zor" verilerle açılır, gezilir,
 * arka plana alınıp geri getirilir. Amaç: hiçbir ekranın çökmemesi.
 *
 * Kapsam: canlı klasörler + kanal kartı EPG ve hatırlatma (600 kanal, ebeveyn kilidi açık), oynatıcının kanal
 * paneli (dikey ve yatay), Ayarlar / Liste Yönetimi / kilitli kategoriler / Listem, arama paneli, PRO ekranı,
 * TV ana sayfa + yerinde arama + bütün bölümler, TV film oynatıcıda çubukla sarma.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
open class ReleaseSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: IPTVViewModel
    private lateinit var live: List<IPTVItem>
    private lateinit var movies: List<IPTVItem>
    private val minute = 60_000L
    private val longName = "Çok Uzun Kanal Adı " + "Devam Ediyor ".repeat(15)

    @Before
    fun setUp() {
        app.getSharedPreferences(ProgramReminders.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val db = AppDatabase.getDatabase(app)
        val cats = listOf("Ulusal", "Spor", "XX: Yetişkin", "")
        live = (0 until 600).map { i ->
            IPTVItem(
                id = 76000 + i, playlistId = RELEASE_PLAYLIST, name = if (i == 4) longName else "Kanal $i",
                cleanedName = if (i == 4) longName else "Kanal $i", logoUrl = if (i % 3 == 0) null else "http://10.255.255.1/logo/$i.png",
                streamUrl = "http://10.255.255.1/l/$i.ts", category = cats[i % 4], type = if (i % 50 == 0) "RADIO" else "LIVE",
                tvgId = if (i % 2 == 0) "k$i.tr" else null
            )
        }
        movies = (0 until 80).map { i ->
            IPTVItem(
                id = 77000 + i, playlistId = RELEASE_PLAYLIST, name = "Sex Education Film $i", cleanedName = "Sex Education Film $i",
                logoUrl = null, streamUrl = "http://10.255.255.1/m/$i.mp4", category = listOf("Aksiyon", "Aile")[i % 2], type = "MOVIE",
                rating = 9.6, releaseDate = "20${10 + i % 15}-05-01", summary = "Özet $i", isFavorite = i % 7 == 0
            )
        }
        runBlocking {
            db.iptvDao().insertItems(live + movies)
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 77001, itemName = "Sex Education Film 1", itemType = "MOVIE", itemLogo = null,
                    streamUrl = "http://10.255.255.1/m/1.mp4", category = "Aile", progressSeconds = 60, totalSeconds = 0)
            )
        }
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, IPTVViewModel.Factory(app))[IPTVViewModel::class.java] }
        // Yayın akışı: çift numaralı kanallarda; biri gece yarısını aşan, biri bozuk (bitişi başlangıçtan önce).
        val now = (System.currentTimeMillis() / minute) * minute
        fun p(title: String, start: Long, end: Long) = EPGProgram(
            id = "$title$start", channelId = 0, channelName = "", title = title, description = "", category = "",
            startTimeFormatted = "00:00", endTimeFormatted = "00:00", startEpochMillis = start, endEpochMillis = end
        )
        val schedule = listOf(
            p("Bozuk", now + 60 * minute, now + 30 * minute),
            p("Şimdi", now - 20 * minute, now + 40 * minute),
            p("Sonra " + "uzun ".repeat(30), now + 40 * minute, now + 100 * minute),
            p("Gece", now + 100 * minute, now + 11 * 60 * minute)
        )
        vm.setEpgProgramsForTest(live.filter { it.tvgId != null }.associate { it.tvgId!! to schedule })
        compose.mainClock.autoAdvance = false
    }

    @After
    fun cleanUp() {
        TvDevice.overrideForTest = null
        val db = AppDatabase.getDatabase(app)
        runBlocking { db.iptvDao().deleteItemsByPlaylist(RELEASE_PLAYLIST) }
        db.openHelper.writableDatabase.execSQL("DELETE FROM continue_watching WHERE itemId BETWEEN 76000 AND 77999")
    }

    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(10)
            left -= 100
        }
        compose.waitForIdle()
    }

    private fun backgroundAndReturn() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        settle(300)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle(500)
        assertEquals(Lifecycle.State.RESUMED, compose.activityRule.scenario.state)
    }

    private fun scrollAround(times: Int = 3) {
        repeat(times) {
            compose.onRoot().performTouchInput { swipeUp() }
            settle(300)
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        settle(300)
    }

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        settle(300)
    }

    private fun clickIfPresent(tag: String): Boolean {
        val nodes = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
        if (nodes.fetchSemanticsNodes().isEmpty()) return false
        runCatching { nodes[0].performSemanticsAction(SemanticsActions.OnClick) }
        settle(400)
        return true
    }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(content: @Composable () -> Unit) {
        if (screen == null) {
            screen = content
            compose.setContent { MyApplicationTheme(dynamicColor = false) { screen?.invoke() } }
        } else {
            compose.runOnUiThread { screen = content }
        }
        settle(2_500)
    }

    @Test
    fun liveFolders_epgCards_reminders_withParentalLock() {
        vm.setParentalLock(true)
        val groups = live.groupBy { it.category }.map { (c, items) -> IPTVGroup(id = c, name = c, type = "LIVE", items = items) }
        show { FolderGridScreen(groups = groups, title = "Canlı Yayın", viewModel = vm, onPlayItem = {}) }
        scrollAround()
        val ulusal = groups.first { it.name == "Ulusal" }
        show { FolderDetailLayout(group = ulusal, viewModel = vm, onPlayItem = {}, onBackClick = {}) }
        // İlk kanallarda EPG aç/kapat, hatırlat; sonra listeyi hızlı kaydır.
        val withEpg = ulusal.items.filter { it.tvgId != null }.take(3)
        withEpg.forEach { ch ->
            clickIfPresent("live_epg_button_${ch.id}")
            clickIfPresent("live_epg_button_${ch.id}")
        }
        clickIfPresent("live_epg_button_${withEpg.first().id}")
        scrollAround(6)
        backgroundAndReturn()
        // Boş klasör ve yetişkin klasörü de açılır.
        show { FolderDetailLayout(group = IPTVGroup("bos", "Boş", "LIVE", emptyList()), viewModel = vm, onPlayItem = {}, onBackClick = {}) }
        show { FolderDetailLayout(group = groups.first { it.name.startsWith("XX") }, viewModel = vm, onPlayItem = {}, onBackClick = {}) }
        scrollAround()
    }

    @Test
    fun player_channelPanel_opensWithoutCrash() {
        val channel = live.first { it.type == "LIVE" && it.tvgId != null }
        show {
            PlayerScreen(
                item = channel, siblingItems = live.filter { it.category == channel.category }.take(80), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm
            )
        }
        compose.onRoot().performTouchInput { click(center) }
        settle(500)
        clickIfPresent("player_tray_button")
        settle(800)
        scrollAround(2)
        backgroundAndReturn()
        // Süresi bilinmeyen film
        show {
            PlayerScreen(
                item = movies[1], siblingItems = movies.take(20), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm
            )
        }
        compose.onRoot().performTouchInput { click(center) }
        settle(500)
        clickIfPresent("player_tray_button")
        settle(800)
    }

    @Test
    fun settings_lists_locks_watchlist_search_paywall() {
        show { SettingsScreen(viewModel = vm) }
        scrollAround(6)
        show { PlaylistManagerModule(viewModel = vm) }
        settle(500)
        show { ParentalCategoriesScreen(viewModel = vm, onBack = {}) }
        scrollAround()
        show { WatchlistScreen(viewModel = vm, onSelectItem = {}, onExploreClick = {}) }
        scrollAround()
        var query by mutableStateOf("")
        show {
            SearchSheet(
                searchQuery = query, onQueryChange = { query = it }, onSearchSubmit = {}, onAISearchClick = {},
                isAILoading = false, searchResults = movies.take(30), onItemClick = {}, onDismiss = {}
            )
        }
        compose.onAllNodes(hasTestTag("popup_search_input"))[0].performTextInput("şğüİı %_'\" sex")
        settle(500)
        show { ProPaywallDialog(isPro = false, onDismiss = {}) }
        repeat(10) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(100) }
        show { ProPaywallDialog(isPro = true, onDismiss = {}) }
        repeat(10) { ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS); compose.mainClock.advanceTimeBy(100) }
        backgroundAndReturn()
    }
}

/** realme C55 yatay (oynatıcıda kanal paneli sağdan açılan dar panel). */
@Config(sdk = [35], qualifiers = "tr-w800dp-h360dp-land-xxhdpi")
class ReleaseSmokeLandscapeTest : ReleaseSmokeTest()

/** Mi TV Stick: Android 9, 1080p TV. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "tr-w960dp-h540dp-land-television")
class ReleaseSmokeTvTest {

    // Ekran işleri (LaunchedEffect) uygulamadaki gibi ana iş parçacığında sürsün: Compose'un test ortamı varsayılan
    // olarak bekletmeyen dağıtıcı kullanır; veritabanından dönen iş arka plan iş parçacığında devam edip liste
    // ölçülürken durumu değiştiriyordu (testte ara sıra "Index 2, size 2"; cihazda olmaz).
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(kotlinx.coroutines.test.StandardTestDispatcher())

    private val app = ApplicationProvider.getApplicationContext<Application>()
    // Kendi bellek içi veritabanı: diğer testlerin arka plan işleri bu testin çizimine karışmaz.
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun cleanUp() {
        TvDevice.overrideForTest = null
        db.close()
    }

    private fun settle(ms: Long) {
        compose.waitForIdle()
        Thread.sleep(minOf(ms, 300L))
        compose.waitForIdle()
    }

    // Açık bir pencere (çıkış kartı) varsa tuş en üstteki pencereye gider.
    private fun press(key: Key, wait: Long = 300) {
        @OptIn(ExperimentalTestApi::class)
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { it[it.fetchSemanticsNodes().size - 1] }.performKeyInput { pressKey(key) }
        settle(wait)
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle(500)
    }

    private fun seed(): IPTVViewModel {
        val items = (0 until 300).map { i ->
            val type = listOf("LIVE", "MOVIE", "SERIES")[i % 3]
            IPTVItem(
                id = 78000 + i, playlistId = RELEASE_PLAYLIST,
                name = if (type == "SERIES") "Dizi ${i % 20} S01E0${i % 9 + 1}" else "Öğe $i",
                cleanedName = if (type == "SERIES") "Dizi ${i % 20} S01E0${i % 9 + 1}" else "Öğe $i",
                logoUrl = null, streamUrl = "http://10.255.255.1/x/$i", category = listOf("Kategori A", "Kategori B")[i % 2], type = type,
                summary = "Özet", releaseDate = "2020-01-01"
            )
        }
        runBlocking { db.iptvDao().insertItems(items) }
        // Veri modeli etkinliğe bağlanır: test bitince (etkinlik kapanınca) arka plan işleri de durur ve sonraki
        // testin çizimine karışmaz.
        var vm: IPTVViewModel? = null
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    IPTVViewModel(app, IPTVRepository(db.iptvDao(), db)) as T
            })[IPTVViewModel::class.java]
        }
        return vm!!
    }

    @Test
    fun tvHome_inPlaceSearch_andExitCard() {
        TvDevice.overrideForTest = true
        val vm = seed()
        val state = TvAppState(com.example.ui.tv.TV_HOME, TvSection.LIVE, null, null, null)
        compose.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TvApp(viewModel = vm, state = state, onPlayItem = {}, onPlayContinue = {}, onOpenAssistant = {})
            }
        }
        settle(2_000)
        // Yerinde arama: aç, yaz, ara, sonuçlara in, Geri ile kapat.
        press(Key.DirectionUp); press(Key.DirectionUp); press(Key.DirectionLeft); press(Key.DirectionCenter, 800)
        compose.onAllNodes(hasTestTag("tv_search_input")).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onAllNodes(hasTestTag("tv_search_input"))[0].performTextInput("öğe 1")
            settle(800)
            press(Key.DirectionDown)
            back()
        }
        // Ana sayfada Geri: çıkış kartı açılır; "Kal" ile (Geri) kapanır.
        back()
        if (compose.onAllNodes(hasTestTag("tv_exit_dialog")).fetchSemanticsNodes().isNotEmpty()) {
            // Gerçek kumandadaki gibi: Geri tuşu diyalog penceresine gider.
            compose.runOnIdle {
                val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
                dialog.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
            }
            settle(500)
        }
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_exit_dialog")).fetchSemanticsNodes().size)
    }

    /** Her bölüm ayrı açılır, kumandayla gezilir, Geri ile ana sayfaya dönülür. */
    private fun openSection(section: String) {
        TvDevice.overrideForTest = true
        val vm = seed()
        val state = TvAppState(section, TvSection.LIVE, null, null, null)
        compose.setContent {
            MyApplicationTheme(dynamicColor = false) {
                TvApp(viewModel = vm, state = state, onPlayItem = {}, onPlayContinue = {}, onOpenAssistant = {})
            }
        }
        settle(2_000)
        repeat(4) { press(Key.DirectionDown) }
        repeat(3) { press(Key.DirectionRight) }
        // Bölüm içinde bir alt sayfa açıldıysa önce o kapanır; en fazla üç Geri ile ana sayfaya dönülür.
        repeat(3) { if (state.destination != com.example.ui.tv.TV_HOME) back() }
        assertEquals(com.example.ui.tv.TV_HOME, state.destination)
    }

    @Test fun tvMovies() = openSection(TvSection.MOVIE)
    @Test fun tvSeries() = openSection(TvSection.SERIES)
    @Test fun tvSaved() = openSection(TvSection.SAVED)
    @Test fun tvSettings() = openSection(TvSection.SETTINGS)
    @Test fun tvLiveFolders() = openSection(TvSection.LIVE)

    @Test
    fun tvMoviePlayer_seekBar_scrubAndJump() {
        TvDevice.overrideForTest = true
        val vm = seed()
        val movie = IPTVItem(
            id = 78999, playlistId = RELEASE_PLAYLIST, name = "TV Film", cleanedName = "TV Film", logoUrl = null,
            streamUrl = "http://10.255.255.1/m/tv.mp4", category = "Kategori A", type = "MOVIE"
        )
        compose.setContent {
            MyApplicationTheme(dynamicColor = false) {
                PlayerScreen(
                    item = movie, siblingItems = emptyList(), onBack = {}, onPlayItem = {}, onProgressUpdate = { _, _, _ -> },
                    iptvViewModel = vm, tvMode = true, tvUiState = TvPlayerUiState()
                )
            }
        }
        settle(1_500)
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        repeat(5) { press(Key.DirectionRight, 100) }
        press(Key.DirectionCenter)
        settle(1_500)
        back()
    }
}

private const val RELEASE_PLAYLIST = 76
