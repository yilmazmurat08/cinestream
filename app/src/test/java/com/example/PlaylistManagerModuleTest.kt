package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.ui.tv.TvSettingsScreen
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlaylistManagerModule
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

/**
 * Liste Yönetimi modülü (Ayarlar): bütün liste işlemleri tek yerde. 1. yuva ücretsiz, 2. ve 3. yuva PRO;
 * "Aç" listeyi ekrana getirir, "Sil" onay ister.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class PlaylistManagerModuleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = AppDatabase.getDatabase(app)
    private val repo = IPTVRepository(db.iptvDao(), db)

    private fun clean() = runBlocking {
        repo.clearPlaylists()
        db.iptvDao().deleteAllIPTVItems()
        db.iptvDao().clearXtreamSeriesCatalog()
    }

    @org.junit.Before
    fun setUp() = clean()

    @After
    fun tearDown() = clean()

    private fun addM3u(name: String, channel: String): Int = runBlocking {
        repo.addPlaylistAndActivate {
            val id = repo.insertPlaylist(Playlist(name = name, url = "m3u_text_input")).toInt()
            repo.parseAndSaveM3U(id, "#EXTM3U\n#EXTINF:-1 group-title=\"Ulusal\",$channel\nhttp://e/${channel.hashCode()}.ts\n")
        }
        db.iptvDao().getAllPlaylists().first { it.name == name }.id
    }

    /** Room bildirimleri gerçek zamanla gelir: ana iş parçacığı adım adım ilerletilerek beklenir. */
    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        compose.waitForIdle()
        assertTrue("koşul zamanında sağlanmadı", condition())
    }

    private fun hasNode(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Yuvanın içinde (alt öğelerinde) bu metin var mı? */
    private fun slotShows(slot: Int, text: String) = compose.onAllNodes(
        androidx.compose.ui.test.hasText(text) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("playlist_slot_$slot"))
    ).fetchSemanticsNodes().isNotEmpty()

    private fun show(vm: IPTVViewModel) {
        // Ayarlar ekranı gibi kaydırılabilir.
        compose.setContent {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) { PlaylistManagerModule(viewModel = vm) }
        }
    }

    @Test
    fun freeUser_oneList_slotsTwoAndThreeAreLocked_andOpenThePaywall() {
        addM3u("Ev", "TRT 1")
        val vm = IPTVViewModel(app, repo)
        vm.setProUser(false)
        show(vm)
        waitUntil { hasNode("playlist_slot_1") }

        assertTrue(slotShows(1, "Ev"))
        assertTrue(slotShows(1, "Açık"))
        assertTrue("açık listede Aç düğmesi olmaz", !hasNode("playlist_open_1"))
        assertTrue(hasNode("playlist_locked_2"))
        assertTrue(hasNode("playlist_locked_3"))

        compose.onNodeWithTag("playlist_locked_2").performScrollTo().performClick()
        waitUntil { vm.showPaywallDialog.value }
        assertTrue(vm.paywallReasonMessage.value!!.contains("PRO"))
    }

    @Test
    fun freeUser_secondListIsLocked_cannotBeOpened_proButtonOpensThePaywall() {
        val ev = addM3u("Ev", "TRT 1")
        addM3u("İş", "beIN 1") // ücretsiz kullanıcı: 2. liste kilitli
        val vm = IPTVViewModel(app, repo)
        vm.setProUser(false)
        show(vm)
        // Kilitli liste ekranda kalamaz: 1. listeye dönülür.
        waitUntil { runBlocking { db.iptvDao().getActivePlaylist()?.id } == ev }
        waitUntil { hasNode("playlist_pro_2") }

        assertTrue(slotShows(1, "Açık"))
        assertTrue(slotShows(2, "İş"))
        assertTrue("kilitli listede Aç düğmesi olmaz", !hasNode("playlist_open_2"))
        assertTrue("kilitli listede Yenile olmaz", !hasNode("playlist_refresh_2"))
        assertTrue("kilitli liste silinebilir", hasNode("playlist_delete_2"))
        assertTrue(hasNode("playlist_locked_3"))

        compose.onNodeWithTag("playlist_pro_2").performScrollTo().performClick()
        waitUntil { vm.showPaywallDialog.value }
        assertEquals(listOf("TRT 1"), runBlocking { db.iptvDao().getAllItemsDirect().map { it.name } })
    }

    @Test
    fun delete_asksForConfirmation_thenDeletes() {
        addM3u("Ev", "TRT 1")
        val is_ = addM3u("İş", "beIN 1")
        val vm = IPTVViewModel(app, repo)
        show(vm)
        waitUntil { hasNode("playlist_delete_2") }

        compose.onNodeWithTag("playlist_delete_2").performScrollTo().performClick()
        waitUntil { hasNode("playlist_delete_confirm") }
        assertEquals("onaydan önce silinmez", 2, runBlocking { db.iptvDao().getAllPlaylists().size })
        compose.onNodeWithTag("playlist_delete_confirm").performClick()
        waitUntil { runBlocking { db.iptvDao().getPlaylistById(is_) } == null }
        assertEquals(listOf("Ev"), runBlocking { db.iptvDao().getAllPlaylists().map { it.name } })
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

        // 1. yuva her zaman ücretsiz; 2. ve 3. yuva ücretsizde kilitli, PRO'da açık.
        assertFalse(IPTVViewModel.isSlotLocked(0, isPro = false))
        assertTrue(IPTVViewModel.isSlotLocked(1, isPro = false))
        assertTrue(IPTVViewModel.isSlotLocked(2, isPro = false))
        assertFalse(IPTVViewModel.isSlotLocked(1, isPro = true))
        assertFalse(IPTVViewModel.isSlotLocked(2, isPro = true))
        assertEquals(3, IPTVViewModel.freePlaylistId(listOf(Playlist(id = 7, name = "b", url = "b"), Playlist(id = 3, name = "a", url = "a"))))
    }

    @Test
    fun freeUser_lockedListNeverStaysOnScreen_andCannotBeOpened() {
        val ev = addM3u("Ev", "TRT 1")
        val is_ = addM3u("İş", "beIN 1") // ekleme bitince 2. liste açık kalır; ücretsiz kullanıcıda 1. listeye dönülür
        waitUntil { vm.activePlaylist.value?.id == ev }
        assertEquals(listOf("TRT 1"), runBlocking { db.iptvDao().getAllItemsDirect().map { it.name } })
        assertEquals("kilitli liste silinmez", 1, runBlocking { db.iptvDao().getParkedItemCount(is_) })

        vm.switchPlaylist(is_)
        waitUntil { vm.showPaywallDialog.value }
        assertEquals(ev, runBlocking { db.iptvDao().getActivePlaylist()?.id })
    }
}

/** TV Ayarları → Listeler: modül kumandayla kullanılır; ücretsiz kullanıcıda 2. liste kilitli, "PRO ile aç" OK ile satın alma ekranını açar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television")
class TvPlaylistManagerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = AppDatabase.getDatabase(app)
    private val repo = IPTVRepository(db.iptvDao(), db)

    private fun clean() = runBlocking {
        repo.clearPlaylists()
        db.iptvDao().deleteAllIPTVItems()
        db.iptvDao().clearXtreamSeriesCatalog()
    }

    @org.junit.Before
    fun setUp() = clean()

    @After
    fun tearDown() = clean()

    private fun press(key: androidx.compose.ui.input.key.Key) {
        @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun isFocused(tag: String) =
        runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess

    @Test
    fun remote_reachesProButtonOfLockedList_andOpensThePaywall() {
        val ev = runBlocking {
            repo.addPlaylistAndActivate {
                val id = repo.insertPlaylist(Playlist(name = "Ev", url = "m3u_text_input")).toInt()
                repo.parseAndSaveM3U(id, "#EXTM3U\n#EXTINF:-1,TRT 1\nhttp://e/1.ts\n")
            }
            repo.addPlaylistAndActivate {
                val id = repo.insertPlaylist(Playlist(name = "İş", url = "m3u_text_input")).toInt()
                repo.parseAndSaveM3U(id, "#EXTM3U\n#EXTINF:-1,beIN 1\nhttp://i/1.ts\n")
            }
            db.iptvDao().getAllPlaylists().first { it.name == "Ev" }.id
        }
        val vm = IPTVViewModel(app, repo)
        vm.setProUser(false)
        compose.setContent { TvSettingsScreen(viewModel = vm) }
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end &&
            compose.onAllNodes(hasTestTag("playlist_pro_2")).fetchSemanticsNodes().isEmpty()) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(20)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_setting_PLAYLISTS").assertIsFocused()

        // OK: panele gir; aşağı inerek 2. yuvanın "PRO ile aç" düğmesine ulaş, OK ile satın alma ekranı açılır.
        press(androidx.compose.ui.input.key.Key.DirectionCenter)
        for (i in 0 until 10) {
            if (isFocused("playlist_pro_2")) break
            press(androidx.compose.ui.input.key.Key.DirectionDown)
        }
        compose.onNodeWithTag("playlist_pro_2").assertIsFocused()
        press(androidx.compose.ui.input.key.Key.DirectionCenter)
        for (i in 0 until 100) {
            if (vm.showPaywallDialog.value) break
            org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(20)
        }
        assertTrue(vm.showPaywallDialog.value)
        assertEquals("ücretsiz kullanıcıda 1. liste açık kalır", ev, runBlocking { db.iptvDao().getActivePlaylist()?.id })
    }
}
