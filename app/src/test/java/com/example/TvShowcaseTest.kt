package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.ModeSelectionScreen
import com.example.ui.tv.TvAppState
import com.example.ui.tv.TvAssistantScreen
import com.example.ui.tv.TvBrowseScreen
import com.example.ui.tv.TvDetailScreen
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvHomeScreen
import com.example.ui.tv.TvPerson
import com.example.ui.tv.TvPersonScreen
import com.example.ui.tv.TvPlayerUiState
import com.example.ui.tv.TvSavedScreen
import com.example.ui.tv.TvSection
import com.example.ui.tv.TvSettingsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import coil.imageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TV ekranlarının görsel önizlemesi (örnek verilerle, gerçek ekran kodu). Yalnızca CINESTREAM_SHOTS ortam
 * değişkeniyle çalışır; normal test çalıştırmalarında atlanır. Görseller CINESTREAM_SHOTS klasörüne yazılır.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
class TvShowcaseTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val outDir = System.getenv("CINESTREAM_SHOTS")?.let { File(it) }
    private lateinit var vm: IPTVViewModel
    private lateinit var movies: List<IPTVItem>

    private val palette = listOf(
        0xFF7C3AED to 0xFF1E1B4B, 0xFFDB2777 to 0xFF3B0764, 0xFF0EA5E9 to 0xFF172554, 0xFFF59E0B to 0xFF451A03,
        0xFF10B981 to 0xFF022C22, 0xFFEF4444 to 0xFF450A0A, 0xFF6366F1 to 0xFF0F172A, 0xFFA855F7 to 0xFF2E1065
    )

    /** Örnek poster: degrade + başlık (gerçek içerik yerine önizleme görseli). */
    private fun poster(name: String, index: Int, w: Int = 400, h: Int = 600): String {
        val file = File(app.cacheDir, "shot_${name.hashCode()}_${w}x$h.png")
        if (file.exists()) return file.toURI().toString()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (a, b) = palette[index % palette.size]
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.6f, h.toFloat(), a.toInt(), b.toInt(), Shader.TileMode.CLAMP)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = w / 9f; isFakeBoldText = true }
        name.split(" ").chunked(2).map { it.joinToString(" ") }.forEachIndexed { i, line ->
            c.drawText(line, w * 0.08f, h * 0.72f + i * p.textSize * 1.2f, p)
        }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.toURI().toString()
    }

    private fun movie(id: Int, name: String, category: String, rating: Double, year: String, genre: String) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = poster(name, id), streamUrl = "http://10.255.255.1/m/$id.mp4",
        category = category, type = "MOVIE", rating = rating, releaseDate = "$year-05-01", genre = genre,
        summary = "$name, sıradan bir hayatı olan kahramanın beklenmedik bir yolculuğa çıkışını anlatıyor. Yol boyunca dostluk, cesaret ve fedakârlık sınanıyor.",
        cast = "Ada Yıldız, Bora Kaya, Cem Demir, Ece Ak", director = "Deniz Arslan"
    )

    @Before
    fun setUp() {
        assumeTrue("Görsel önizleme yalnızca CINESTREAM_SHOTS ile", outDir != null)
        outDir!!.mkdirs()
        TvDevice.overrideForTest = true
        val db = AppDatabase.getDatabase(app)
        val titles = listOf(
            "Gece Yarısı Treni", "Son Kale", "Mavi Dalga", "Kayıp Harita", "Yıldız Tozu", "Sessiz Şehir",
            "Kırmızı Çizgi", "Rüzgarın Oğlu", "Gölge Avcısı", "Buzul Çağı", "Altın Vadi", "Karanlık Orman"
        )
        val cats = listOf("Aksiyon", "Yeni Filmler", "Komedi")
        movies = titles.mapIndexed { i, t ->
            movie(8000 + i, t, cats[i % 3], listOf(8.4, 7.9, 7.2, 8.8, 6.9, 7.6)[i % 6], (2018 + i % 6).toString(),
                listOf("Aksiyon, Macera", "Dram", "Komedi", "Bilim Kurgu")[i % 4])
                .copy(isFavorite = i % 4 == 0)
        }
        val series = (1..3).flatMap { s ->
            listOf("Kuzey Yıldızı", "Dağların Kızı", "Eski Konak").mapIndexed { k, show ->
                IPTVItem(
                    id = 8100 + k * 10 + s, playlistId = 1, name = "$show S01E0$s", cleanedName = "$show S01E0$s",
                    logoUrl = poster(show, 20 + k), streamUrl = "http://10.255.255.1/s/${k}_$s.mp4", category = "Yerli Diziler",
                    type = "SERIES", rating = 8.1 - k * 0.3, releaseDate = "2022-01-01", genre = "Dram",
                    summary = "$show, bir ailenin kuşaklar boyu süren hikâyesi.", isFavorite = k == 0 && s == 1
                )
            }
        }
        val live = listOf("TRT 1", "Show TV", "Kanal D", "ATV", "Star TV", "TV8", "NTV", "CNN Türk", "Habertürk", "TRT Spor", "beIN Sports 1", "A Spor")
            .mapIndexed { i, n ->
                IPTVItem(
                    id = 8200 + i, playlistId = 1, name = n, cleanedName = n, logoUrl = poster(n, 30 + i, 320, 200),
                    streamUrl = "http://10.255.255.1/l/$i.ts", category = if (i < 6) "Ulusal" else if (i < 9) "Haber" else "Spor",
                    type = "LIVE", isFavorite = i == 0
                )
            }
        runBlocking {
            db.iptvDao().insertItems(movies + series + live)
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 8112, itemName = "Kuzey Yıldızı S01E02", itemType = "SERIES", itemLogo = poster("Kuzey Yıldızı", 20),
                    streamUrl = "http://10.255.255.1/s/0_2.mp4", category = "Yerli Diziler", progressSeconds = 900, totalSeconds = 2700)
            )
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 8200, itemName = "TRT 1", itemType = "LIVE", itemLogo = null,
                    streamUrl = "http://10.255.255.1/l/0.ts", category = "Ulusal", progressSeconds = 100, totalSeconds = 100,
                    lastPlayedAt = System.currentTimeMillis() - 60_000)
            )
        }
        vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        // Görseller önceden belleğe yüklenir (ekran görüntüsü anında hazır olsun)
        val urls = (movies + series + live).mapNotNull { it.logoUrl }.distinct()
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            urls.forEach { url -> runCatching { app.imageLoader.execute(coil.request.ImageRequest.Builder(app).data(url).build()) } }
        }
        var waited = 0
        while (!job.isCompleted && waited < 20_000) {
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            waited += 20
        }
    }

    @After
    fun tearDown() {
        TvDevice.overrideForTest = null
    }

    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(40)
            left -= 100
        }
        compose.waitForIdle()
    }

    private fun shot(name: String, waitMs: Long = 3_000, content: @Composable () -> Unit) {
        compose.setContent { MyApplicationTheme { content() } }
        settle(waitMs)
        compose.onRoot().captureRoboImage(filePath = File(outDir, "$name.png").absolutePath)
    }

    @Test fun s01_modeSelection() = shot("01-mod-secimi", 800) { ModeSelectionScreen(onSelect = {}) }

    @Test fun s02_homeLive() = shot("02-ana-sayfa-canli-tv", 6_000) {
        TvHomeScreen(vm, TvSection.LIVE, {}, {}, {}, {}, {})
    }

    @Test fun s03_homeMovies() = shot("03-ana-sayfa-filmler", 8_000) {
        TvHomeScreen(vm, TvSection.MOVIE, {}, {}, {}, {}, {})
    }

    @Test fun s04_movies() = shot("04-filmler", 5_000) {
        TvBrowseScreen(vm, "MOVIE", TvAppState("MOVIE", "MOVIE", "Aksiyon", null, "MOVIE_8003"))
    }

    @Test fun s05_series() = shot("05-diziler", 5_000) {
        TvBrowseScreen(vm, "SERIES", TvAppState("SERIES", "SERIES", "Yerli Diziler", null, null))
    }

    @Test fun s06_detail() = shot("06-film-detayi", 6_000) {
        TvDetailScreen(viewModel = vm, item = movies[3], onPlay = {})
    }

    @Test fun s07_person() = shot("07-kisi-sayfasi", 6_000) {
        TvPersonScreen(vm, TvPerson("Ada Yıldız", TvPerson.ROLE_ACTOR, "Elif", null), excludeItemId = movies[3].id, onOpenItem = {})
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun s08_assistant() {
        runBlocking { com.example.data.repository.SettingsRepository(app).setGeminiApiKey("AIzaShowcaseKey000000000000000000000000") }
        val field = IPTVViewModel::class.java.getDeclaredField("_chatMessages").apply { isAccessible = true }
        (field.get(vm) as MutableStateFlow<List<IPTVViewModel.ChatMessage>>).value = listOf(
            IPTVViewModel.ChatMessage("user", "Bir adam gece trende uyanıyor, herkes aynı günü tekrar yaşıyor. Filmin adı ne?"),
            IPTVViewModel.ChatMessage("ai", "Anlattığın sahneler \"Gece Yarısı Treni\" filmine çok benziyor: kahraman her gece aynı trende uyanıp olayların tekrarını çözmeye çalışıyor.",
                matchedItem = movies[0], detectedTitle = "Gece Yarısı Treni")
        )
        shot("08-asistan", 4_000) { TvAssistantScreen(viewModel = vm, onOpenItem = {}, onOpenSettings = {}) }
    }

    @Test fun s09_saved() = shot("09-kaydedilenler", 8_000) { TvSavedScreen(viewModel = vm, onPlayItem = {}) }

    @Test fun s10_livePlayer() {
        val ui = TvPlayerUiState().apply { launch = TvPlayerUiState.LAUNCH_ENTER }
        val channel = runBlocking { AppDatabase.getDatabase(app).iptvDao().getItemById(8200) }!!
        shot("10-canli-tv", 5_000) {
            PlayerScreen(
                item = channel, siblingItems = emptyList(), onBack = {}, onPlayItem = {}, onProgressUpdate = { _, _, _ -> },
                iptvViewModel = vm, tvMode = true, tvUiState = ui
            )
        }
    }

    @Test fun s11_settings() = shot("11-ayarlar", 2_000) { TvSettingsScreen(viewModel = vm) }
}
