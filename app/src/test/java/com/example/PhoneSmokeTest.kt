package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.ui.IPTVViewModel
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MultiScreen
import com.example.ui.screens.MultiScreenViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Yayın öncesi duman testi: telefonun ana ekranları "zorlayıcı" veriyle açılır (tekrar eden adlar, "Tümü" adlı
 * kategori, çok uzun adlar, boş kategori, yarım izlenmiş bölümler). Her ekranda kaydırılır ve uygulama arka plana
 * alınıp geri getirilir (ekran durumu kaydedilir). Hiçbir ekran çökmemeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w360dp-h780dp-port")
class PhoneSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: IPTVViewModel
    private lateinit var movies: List<IPTVItem>
    private lateinit var episodes: List<IPTVItem>

    private val longName = "Çok Uzun Bir Film Adı " + "Devam Ediyor ".repeat(20)

    @Before
    fun setUp() {
        val db = AppDatabase.getDatabase(app)
        // Ağa çıkılmasın: adresler Xtream kalıbına uymaz ve yönlendirilemeyen bir IP'ye gider.
        val movieCategories = listOf("Aksiyon", "Tümü", "", "Yeni Filmler")
        movies = (0 until 120).map { i ->
            IPTVItem(
                id = 71000 + i, playlistId = SMOKE_PLAYLIST, name = if (i == 7) longName else "Film $i", cleanedName = if (i == 7) longName else "Film $i",
                logoUrl = null, streamUrl = "http://10.255.255.1/m/${71000 + i}.mp4", category = movieCategories[i % 4], type = "MOVIE",
                rating = (i % 10).toDouble(), releaseDate = "${2000 + i % 25}-01-01", genre = "Aksiyon, Dram",
                summary = "Film $i özeti.", cast = "Ada Yıldız, Ada Yıldız, Bora Kaya", director = "Deniz Arslan, Deniz Arslan",
                isFavorite = i % 9 == 0
            )
        }
        episodes = listOf("Kuzey Yıldızı", "Dağların Kızı").flatMapIndexed { k, show ->
            (1..6).map { e ->
                IPTVItem(
                    id = 72000 + k * 20 + e, playlistId = SMOKE_PLAYLIST, name = "$show S01E0$e", cleanedName = "$show S01E0$e", logoUrl = null,
                    streamUrl = "http://10.255.255.1/s/${k}_$e.mp4", category = if (k == 0) "Yerli Diziler" else "Tümü", type = "SERIES",
                    season = 1, episode = e, cast = "Selin Aydın, Selin Aydın"
                )
            }
        }
        val live = (0 until 60).map { i ->
            IPTVItem(
                id = 73000 + i, playlistId = SMOKE_PLAYLIST, name = if (i == 3) longName else "Kanal $i", cleanedName = if (i == 3) longName else "Kanal $i",
                logoUrl = null, streamUrl = "http://10.255.255.1/l/$i.ts", category = listOf("Ulusal", "Tümü", "Haber", "")[i % 4],
                type = if (i % 15 == 0) "RADIO" else "LIVE", tvgId = "k$i.tr", isFavorite = i == 1
            )
        }
        val catalog = (0 until 30).map { i ->
            XtreamSeriesCatalogEntity(
                seriesId = 74000 + i, name = if (i == 5) longName else "Dizi $i", canonicalKey = "dizi $i", coverUrl = "",
                rating = (i % 10).toDouble(), cast = "Ece Ak, Ece Ak", categoryId = listOf("Amazon", "Tümü", "Diğer")[i % 3]
            )
        }
        runBlocking {
            val dao = db.iptvDao()
            dao.insertItems(movies + episodes + live)
            dao.insertXtreamSeriesCatalog(catalog)
            dao.insertContinueWatching(ContinueWatching(itemId = 72001, itemName = "Kuzey Yıldızı S01E01", itemType = "SERIES", itemLogo = null,
                streamUrl = "http://10.255.255.1/s/0_1.mp4", category = "Yerli Diziler", progressSeconds = 1200, totalSeconds = 2700))
            dao.insertContinueWatching(ContinueWatching(itemId = 71003, itemName = "Film 3", itemType = "MOVIE", itemLogo = null,
                streamUrl = "http://10.255.255.1/m/71003.mp4", category = "Aksiyon", progressSeconds = 600, totalSeconds = 0))
            dao.insertContinueWatching(ContinueWatching(itemId = 73001, itemName = "Kanal 1", itemType = "LIVE", itemLogo = null,
                streamUrl = "http://10.255.255.1/l/1.ts", category = "Tümü", progressSeconds = 0, totalSeconds = 0))
        }
        // Uygulamadaki gibi: ViewModel etkinliğin deposunda fabrikayla oluşturulur; viewModel() çağıran
        // alt bileşenler (ör. CompactEPGSection) aynı örneği bulur.
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity, IPTVViewModel.Factory(app))[IPTVViewModel::class.java] }
        // Yükleme animasyonları (sonsuz) test saatini kilitlemesin: saat elle ilerletilir. Gerçek bir sonsuz
        // yeniden çizim döngüsü olsaydı yine de durgunluğa ulaşılamazdı.
        compose.mainClock.autoAdvance = false
    }

    /**
     * Testler aynı veritabanı örneğini paylaşır: başka testlerin verisini kirletmemek için (ör. "izlemeye devam"
     * kaydı başka bir testin "izlenmemiş" bölümünü izlenmiş gösteriyordu) bu testin eklediği her şey silinir.
     */
    @After
    fun cleanUp() {
        val db = AppDatabase.getDatabase(app)
        runBlocking { db.iptvDao().deleteItemsByPlaylist(SMOKE_PLAYLIST) }
        db.openHelper.writableDatabase.execSQL("DELETE FROM continue_watching WHERE itemId BETWEEN 71000 AND 74999")
        db.openHelper.writableDatabase.execSQL("DELETE FROM xtream_series_catalog WHERE seriesId BETWEEN 74000 AND 74999")
    }

    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(15)
            left -= 100
        }
        compose.waitForIdle()
    }

    /** Uygulama arka plana alınır (ekran durumu kaydedilir) ve geri getirilir. */
    private fun backgroundAndReturn() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        settle(300)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle(500)
        assertEquals(Lifecycle.State.RESUMED, compose.activityRule.scenario.state)
    }

    private fun scrollAround() {
        repeat(3) {
            compose.onRoot().performTouchInput { swipeUp() }
            settle(400)
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        settle(400)
    }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** Ekranı değiştirir (setContent test başına bir kez çağrılabilir; içerik durum üzerinden değişir). */
    private fun show(content: @Composable () -> Unit) {
        if (screen == null) {
            screen = content
            compose.setContent { MyApplicationTheme(dynamicColor = false) { screen?.invoke() } }
        } else {
            compose.runOnUiThread { screen = content }
        }
        settle(3_000)
    }

    @Test
    fun homeTabs_withTrickyData_doNotCrash() {
        show { HomeScreen(viewModel = vm, onPlayItem = {}, onPlayContinueWatching = {}, onNavigateToMultiScreen = {}) }
        for (tab in listOf("ALL", "LIVE", "MOVIE", "SERIES", "EPG", "RADIO", "WATCHLIST", "SETTINGS")) {
            compose.runOnUiThread { vm.setSelectedTypeFilter(tab) }
            settle(2_000)
            scrollAround()
        }
        backgroundAndReturn()
    }

    @Test
    fun movieAndSeriesDetail_withDuplicateCast_doNotCrash() {
        show { DetailScreen(item = movies[7], onDismiss = {}, onPlay = {}, onToggleFavorite = {}, viewModel = vm) }
        scrollAround()
        backgroundAndReturn()
        show { DetailScreen(item = episodes[1], seriesList = episodes, onDismiss = {}, onPlay = {}, onToggleFavorite = {}, viewModel = vm) }
        scrollAround()
        backgroundAndReturn()
    }

    @Test
    fun multiScreen_channelSelector_withAllCategory_doesNotCrash() {
        val multi = MultiScreenViewModel(app)
        show { MultiScreen(iptvViewModel = vm, multiScreenViewModel = multi, onBack = {}) }
        scrollAround()
        compose.runOnUiThread { multi.openChannelSelector(0) }
        // Kanal seçici bir pencere (Dialog) ve içinde arama kutusu var: Robolectric'te pencere içindeki metin kutusu
        // test ortamını hiç "durgun" saymaz (uygulamadan bağımsız; yalnızca Dialog + BasicTextField ile de oluyor).
        // Bu yüzden burada durgunluk beklenmez; kareler elle ilerletilir. Çizimdeki bir çökme yine burada yakalanır.
        repeat(30) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
        }
        assertTrue("kanal seçici açılmalı", org.robolectric.shadows.ShadowDialog.getLatestDialog()?.isShowing == true)
        compose.runOnUiThread { multi.closeChannelSelector() }
        settle(1_000)
        backgroundAndReturn()
    }
}

/** Duman testinin kendi listesi (temizlik bu kimlikle yapılır). */
private const val SMOKE_PLAYLIST = 71
