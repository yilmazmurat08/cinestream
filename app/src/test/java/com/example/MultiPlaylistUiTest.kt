package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.components.PlaylistSwitcherSheet
import com.example.ui.screens.CineStreamTopBar
import com.example.ui.tv.TvPlaylistSwitcherDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Çoklu liste arayüzü (telefon) ve PRO sınırı: 1 liste ücretsiz, 2. ve 3. liste PRO. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class MultiPlaylistUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val ev = Playlist(id = 1, name = "Ev Listesi", url = "http://a", isActive = true)
    private val is_ = Playlist(id = 2, name = "İş Listesi", url = "http://b", isXtream = true)

    @Test
    fun topBar_showsActiveListName_andOpensSwitcher() {
        var opened = false
        compose.setContent {
            CineStreamTopBar(
                isProUser = false, isScrolled = false, onLogoClick = {}, onProClick = {}, onSearchClick = {},
                activePlaylistName = "Ev Listesi", onPlaylistClick = { opened = true }
            )
        }
        compose.onNodeWithTag("playlist_switcher_chip").assertTextContains("Ev Listesi")
        compose.onNodeWithTag("playlist_switcher_chip").performClick()
        assertTrue(opened)
    }

    @Test
    fun topBar_withoutAnyList_hasNoSwitcher() {
        compose.setContent {
            CineStreamTopBar(isProUser = false, isScrolled = false, onLogoClick = {}, onProClick = {}, onSearchClick = {})
        }
        assertTrue(compose.onAllNodes(hasTestTag("playlist_switcher_chip")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun sheet_marksActiveList_selectsOther_andShowsProOnAdd() {
        var selected: Playlist? = null
        var addClicked = false
        compose.setContent {
            PlaylistSwitcherSheet(
                playlists = listOf(ev, is_), activeId = 1, addNeedsPro = true,
                onSelect = { selected = it }, onAdd = { addClicked = true }, onDismiss = {}
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("playlist_switcher_sheet")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("playlist_option_1").assertTextContains("Açık", substring = true)
        compose.onNodeWithTag("playlist_switcher_add").assertTextContains("PRO", substring = true)
        compose.onNodeWithTag("playlist_option_2").performClick()
        assertEquals(2, selected?.id)
        compose.onNodeWithTag("playlist_switcher_add").performClick()
        assertTrue(addClicked)
    }
}

/** TV'de "Listelerim": odak açık listede başlar, kumandayla başka listeye geçilir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvPlaylistSwitcherTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Pencere ayrı bir kök açar; tuşlar pencereye gönderilir. */
    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onNodeWithTag("tv_playlist_switcher").performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun focusStartsOnActiveList_downAndOkSelectsNext() {
        var selected: Playlist? = null
        val lists = listOf(
            Playlist(id = 1, name = "Ev Listesi", url = "a", isActive = true),
            Playlist(id = 2, name = "İş Listesi", url = "b")
        )
        compose.setContent {
            TvPlaylistSwitcherDialog(
                playlists = lists, activeId = 1, addNeedsPro = false,
                onSelect = { selected = it }, onAdd = {}, onDismiss = {}
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_playlist_option_1")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_playlist_option_1").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_playlist_option_2").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(2, selected?.id)
        compose.onNodeWithText("İş Listesi").assertExists()
    }
}

/** Ekran mantığı: PRO sınırı ve liste değiştirme (ekranlar gibi akışları dinleyerek). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class MultiPlaylistViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var repo: IPTVRepository
    private lateinit var vm: IPTVViewModel
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @org.junit.Before
    fun setUp() {
        db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = IPTVRepository(db.iptvDao(), db)
        vm = IPTVViewModel(app, repo)
        vm.setProUser(false)
        // Ekranlar gibi dinle (stateIn yalnızca dinleyici varken çalışır).
        scope.launch { vm.playlists.collect {} }
        scope.launch { vm.activePlaylist.collect {} }
        scope.launch { vm.isProUser.collect {} }
        waitUntil { !vm.isProUser.value }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("koşul zamanında sağlanmadı", condition())
    }

    private fun addM3u(name: String, channel: String): Int = runBlocking {
        repo.addPlaylistAndActivate {
            val id = repo.insertPlaylist(Playlist(name = name, url = "m3u_text_input")).toInt()
            repo.parseAndSaveM3U(id, "#EXTM3U\n#EXTINF:-1 group-title=\"Ulusal\",$channel\nhttp://e/${channel.hashCode()}.ts\n")
        }
        db.iptvDao().getAllPlaylists().first { it.name == name }.id
    }

    @Test
    fun freeUser_canAddOneList_secondNeedsPro_proCanAddUpToThree() {
        assertEquals(IPTVViewModel.PlaylistAddGate.ALLOWED, vm.playlistAddGate())

        addM3u("Ev", "TRT 1")
        waitUntil { vm.playlists.value.size == 1 }
        assertEquals(IPTVViewModel.PlaylistAddGate.NEEDS_PRO, vm.playlistAddGate())

        // Ücretsiz kullanıcı ikinci listeyi eklemek isteyince satın alma ekranı nedeniyle açılır; ekleme çalışmaz.
        var allowed = false
        vm.requestAddPlaylist { allowed = true }
        assertFalse(allowed)
        assertTrue(vm.showPaywallDialog.value)
        assertTrue(vm.paywallReasonMessage.value!!.contains("PRO"))
        // Düğme dışından gelen ekleme de engellenir; var olan liste yine tek başına kalır.
        vm.loadM3UPlaylist("Gizli", "#EXTM3U\n#EXTINF:-1,X\nhttp://x/1.ts\n")
        Thread.sleep(200); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(listOf("Ev"), runBlocking { db.iptvDao().getAllPlaylists().map { it.name } })

        // Var olan listeler hiçbir zaman kilitlenmez; 3 listeye ulaşınca (PRO olsa da) yeni liste eklenmez.
        addM3u("İş", "beIN 1")
        addM3u("Spor", "S Sport")
        waitUntil { vm.playlists.value.size == 3 }
        assertEquals(IPTVViewModel.PlaylistAddGate.LIMIT_REACHED, vm.playlistAddGate())
    }

    @Test
    fun gateRule_freeOne_proThree() {
        val gate = IPTVViewModel.Companion::playlistAddGate
        assertEquals(IPTVViewModel.PlaylistAddGate.ALLOWED, gate(0, false))
        assertEquals(IPTVViewModel.PlaylistAddGate.NEEDS_PRO, gate(1, false))
        assertEquals(IPTVViewModel.PlaylistAddGate.ALLOWED, gate(1, true))
        assertEquals(IPTVViewModel.PlaylistAddGate.ALLOWED, gate(2, true))
        assertEquals(IPTVViewModel.PlaylistAddGate.LIMIT_REACHED, gate(3, true))
        assertEquals(IPTVViewModel.PlaylistAddGate.LIMIT_REACHED, gate(3, false))
    }

    @Test
    fun switchPlaylist_changesWhatIsOnScreen() {
        val ev = addM3u("Ev", "TRT 1")
        addM3u("İş", "beIN 1")
        waitUntil { vm.activePlaylist.value?.name == "İş" }

        vm.switchPlaylist(ev)
        waitUntil { vm.activePlaylist.value?.id == ev && !vm.isSwitchingPlaylist.value }
        assertEquals(listOf("TRT 1"), runBlocking { db.iptvDao().getAllItemsDirect().map { it.name } })
    }
}
