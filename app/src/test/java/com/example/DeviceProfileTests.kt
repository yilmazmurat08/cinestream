package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.FolderGridScreen
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Kullanıcının test cihazları (bkz. CLAUDE.md). Mevcut telefon ve TV testleri bu cihazların sürümü ve ekranıyla
 * yeniden çalışır:
 * - Telefon: realme C55, Android 15 (API 35), 1080×2400 (xxhdpi → 360×800 dp), ekran kenardan kenara.
 * - TV: Xiaomi Mi TV Stick, Android TV 9 (API 28), 1080p (960×540 dp), 1 GB bellek.
 */

/** realme C55: telefon ana ekranları zorlayıcı veriyle açılır, kaydırılır, arka plana alınıp geri getirilir. */
@Config(sdk = [35], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
class RealmeC55PhoneSmokeTest : PhoneSmokeTest()

/** realme C55: uygulama yeniden oluşturulunca (döndürme, dil, arka plan) ekran kaybolmaz. */
@Config(sdk = [35], qualifiers = "w360dp-h800dp-port-xxhdpi")
class RealmeC55RecreateTest : MainActivityRecreateTest()

/** Mi TV Stick: TV ana sayfası kumandayla gezilir. */
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television")
class MiTvStickHomeTest : TvHomeScreenTest()

/** Mi TV Stick: canlı yayın oynatıcısı, kanal paneli, OK ile kanal listesi, numarayla kanal. */
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television")
class MiTvStickPlayerTest : TvPlayerTest()

/** Mi TV Stick: Geri tuşu ve çıkış onay kartı. */
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television")
class MiTvStickExitDialogTest : TvExitDialogTest()

/**
 * Büyük liste (1 GB bellekli TV kutusu için): 20.000 kanallı, 250 kategorili liste içe aktarılır, kanallar sağlayıcı
 * sırasıyla gruplanır ve Canlı TV klasör ekranı açılır. Hiçbir adım çökmemeli ya da donmamalı.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television")
class MiTvStickLargeListTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repo = IPTVRepository(db.iptvDao(), db)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private val channelCount = 20_000
    private val categoryCount = 250

    private fun bigM3u() = buildString(channelCount * 90) {
        appendLine("#EXTM3U")
        for (i in 1..channelCount) {
            appendLine("#EXTINF:-1 tvg-id=\"k$i.tr\" group-title=\"Kategori ${i % categoryCount}\",HD: Kanal $i")
            appendLine("http://10.255.255.1/live/u/p/$i.ts")
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertTrue("koşul ${timeoutMs / 1000} sn içinde sağlanmadı", condition())
    }

    @Test
    fun twentyThousandChannels_importGroupAndOpen() {
        val started = System.currentTimeMillis()
        val count = runBlocking {
            repo.addPlaylistAndActivate {
                val id = repo.insertPlaylist(Playlist(name = "Büyük", url = "m3u_text_input")).toInt()
                repo.parseAndSaveM3U(id, bigM3u())
            }
        }
        assertEquals(channelCount, count)

        // Uygulamadaki gibi ViewModel etkinliğin deposunda durur (alt ekranlar viewModel() ile aynı örneği bulur).
        lateinit var vm: IPTVViewModel
        compose.runOnUiThread {
            vm = androidx.lifecycle.ViewModelProvider(compose.activity, object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = IPTVViewModel(app, repo) as T
            })[IPTVViewModel::class.java]
        }
        scope.launch { vm.liveGroups.collect {} }
        waitUntil(30_000) { vm.liveGroups.value.size == categoryCount }
        // Her kategoride kanallar sağlayıcı sırasıyla (dosyadaki sıra).
        val first = vm.liveGroups.value.first { it.name == "Kategori 1" }.items.take(3).map { it.name }
        assertEquals(listOf("HD: Kanal 1", "HD: Kanal 251", "HD: Kanal 501"), first)

        compose.setContent {
            FolderGridScreen(groups = vm.liveGroups.value, title = "Canlı Yayın", viewModel = vm, onPlayItem = {})
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("folder_grid_view")).fetchSemanticsNodes().isNotEmpty() }
        val seconds = (System.currentTimeMillis() - started) / 1000
        assertTrue("içe aktarma + gruplama + ekran $seconds sn sürdü", seconds < 90)
    }
}
