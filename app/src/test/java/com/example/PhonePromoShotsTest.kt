package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import coil.imageLoader
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MovieFinderChatScreen
import com.example.ui.theme.AppTheme
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * Telefon ekranlarının tanıtım görselleri (dikey, 1080 px genişlik). Yalnızca CINESTREAM_SHOTS ortam değişkeniyle
 * çalışır; normal test çalıştırmalarında atlanır. Hayali içerikle gerçek ekran kodu çizilir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w360dp-h780dp-port-xxhdpi")
class PhonePromoShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val outDir = System.getenv("CINESTREAM_SHOTS")?.let { File(it) }
    private lateinit var vm: IPTVViewModel
    private lateinit var movies: List<IPTVItem>
    private lateinit var series: List<IPTVItem>

    private val palette = listOf(
        0xFF7C3AED to 0xFF1E1B4B, 0xFFDB2777 to 0xFF3B0764, 0xFF0EA5E9 to 0xFF172554, 0xFFF59E0B to 0xFF451A03,
        0xFF10B981 to 0xFF022C22, 0xFFEF4444 to 0xFF450A0A, 0xFF6366F1 to 0xFF0F172A, 0xFFA855F7 to 0xFF2E1065
    )

    private fun poster(name: String, index: Int, w: Int = 400, h: Int = 600): String {
        val file = File(app.cacheDir, "promo_${name.hashCode()}_${w}x$h.png")
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

    @Before
    fun setUp() {
        assumeTrue("Tanıtım görselleri yalnızca CINESTREAM_SHOTS ile", outDir != null)
        outDir!!.mkdirs()
        val db = AppDatabase.getDatabase(app)
        val titles = listOf(
            "Gece Yarısı Treni", "Son Kale", "Mavi Dalga", "Kayıp Harita", "Yıldız Tozu", "Sessiz Şehir",
            "Kırmızı Çizgi", "Rüzgarın Oğlu", "Gölge Avcısı", "Buzul Çağı", "Altın Vadi", "Karanlık Orman"
        )
        val cats = listOf("Aksiyon", "Yeni Filmler", "Komedi")
        movies = titles.mapIndexed { i, t ->
            IPTVItem(
                id = 8000 + i, playlistId = 1, name = t, cleanedName = t, logoUrl = poster(t, 8000 + i),
                streamUrl = "http://10.255.255.1/m/${8000 + i}.mp4", category = cats[i % 3], type = "MOVIE",
                rating = listOf(8.4, 7.9, 7.2, 8.8, 6.9, 7.6)[i % 6], releaseDate = "${2018 + i % 6}-05-01",
                genre = listOf("Aksiyon, Macera", "Dram", "Komedi", "Bilim Kurgu")[i % 4],
                summary = "$t, sıradan bir hayatı olan kahramanın beklenmedik bir yolculuğa çıkışını anlatıyor. Yol boyunca dostluk, cesaret ve fedakârlık sınanıyor.",
                cast = "Ada Yıldız, Bora Kaya, Cem Demir, Ece Ak", director = "Deniz Arslan", isFavorite = i % 4 == 0
            )
        }
        series = (1..4).flatMap { s ->
            listOf("Kuzey Yıldızı", "Dağların Kızı", "Eski Konak").mapIndexed { k, show ->
                IPTVItem(
                    id = 8100 + k * 10 + s, playlistId = 1, name = "$show S01E0$s", cleanedName = "$show S01E0$s",
                    logoUrl = poster(show, 20 + k), streamUrl = "http://10.255.255.1/s/${k}_$s.mp4", category = "Yerli Diziler",
                    type = "SERIES", rating = 8.1 - k * 0.3, releaseDate = "2022-01-01", genre = "Dram",
                    summary = "$show, bir ailenin kuşaklar boyu süren hikâyesi.", season = 1, episode = s,
                    cast = "Selin Aydın, Kerem Tan, Mert Uçar", director = "Deniz Arslan"
                )
            }
        }
        val live = ShowcaseData.channelNames.mapIndexed { i, n ->
            IPTVItem(
                id = 8200 + i, playlistId = 1, name = n, cleanedName = n, logoUrl = poster(n, 30 + i, 320, 200),
                streamUrl = "http://10.255.255.1/l/$i.ts", category = if (i < 6) "Ulusal" else if (i < 9) "Haber" else "Spor",
                type = "LIVE", isFavorite = i == 0
            )
        }
        runBlocking {
            db.iptvDao().insertItems(movies + series + live)
            listOf("Kuzey Yıldızı", "Dağların Kızı", "Eski Konak").forEachIndexed { k, show ->
                db.iptvDao().upsertSeriesCover(com.example.data.model.SeriesCoverEntity(show.lowercase(java.util.Locale.ROOT), poster(show, 20 + k)))
            }
            // Kuzey Yıldızı: 1. bölüm bitmiş (İzlendi), 2. bölüm yarıda.
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 8101, itemName = "Kuzey Yıldızı S01E01", itemType = "SERIES", itemLogo = poster("Kuzey Yıldızı", 20),
                    streamUrl = "http://10.255.255.1/s/0_1.mp4", category = "Yerli Diziler", progressSeconds = 2650, totalSeconds = 2700,
                    lastPlayedAt = System.currentTimeMillis() - 3_600_000)
            )
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 8102, itemName = "Kuzey Yıldızı S01E02", itemType = "SERIES", itemLogo = poster("Kuzey Yıldızı", 20),
                    streamUrl = "http://10.255.255.1/s/0_2.mp4", category = "Yerli Diziler", progressSeconds = 900, totalSeconds = 2700)
            )
            db.iptvDao().insertContinueWatching(
                ContinueWatching(itemId = 8003, itemName = "Kayıp Harita", itemType = "MOVIE", itemLogo = poster("Kayıp Harita", 8003),
                    streamUrl = "http://10.255.255.1/m/8003.mp4", category = "Aksiyon", progressSeconds = 2400, totalSeconds = 6000,
                    lastPlayedAt = System.currentTimeMillis() - 120_000)
            )
        }
        // Görseller ekran görüntüsü anında hazır olsun: yükleme test iş parçacığında hemen tamamlanır.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(app)
                .dispatcher(kotlinx.coroutines.Dispatchers.Unconfined)
                .allowHardware(false)
                // Ekranlar donanım bitmap'i isteyebilir; test çiziminde görünmediği için kapatılır.
                .components { add(coil.intercept.Interceptor { chain -> chain.proceed(chain.request.newBuilder().allowHardware(false).build()) }) }
                .build()
        )
        vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        vm.setAppTheme(AppTheme.PURE_BLACK)
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

    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(120)
            left -= 100
        }
        compose.waitForIdle()
    }

    private fun shot(name: String, waitMs: Long, content: @Composable () -> Unit) {
        compose.setContent { MyApplicationTheme(appTheme = AppTheme.PURE_BLACK, dynamicColor = false) { content() } }
        settle(waitMs)
        // Telefon görselleri ekran ilk çizildiğinde yüklenmeye başlar; önce bir kez çizilir, sonra asıl görüntü alınır.
        compose.onRoot().captureRoboImage(filePath = File(app.cacheDir, "warmup.png").absolutePath)
        settle(2_000)
        compose.onRoot().captureRoboImage(filePath = File(outDir, "$name.png").absolutePath)
    }

    @Test fun p01_home() = shot("p01-ana-sayfa", 9_000) {
        HomeScreen(viewModel = vm, onPlayItem = {}, onPlayContinueWatching = {}, onNavigateToMultiScreen = {})
    }

    @Test fun p02_movieDetail() = shot("p02-film-detayi", 6_000) {
        DetailScreen(item = movies[3], onDismiss = {}, onPlay = {}, onToggleFavorite = {}, viewModel = vm)
    }

    @Test fun p03_seriesDetail() = shot("p03-dizi-detayi", 7_000) {
        DetailScreen(item = series.first { it.id == 8101 }, seriesList = series, onDismiss = {}, onPlay = {}, onToggleFavorite = {}, viewModel = vm)
    }

    /** TV dizi detayı: bölüm kartlarında "İzlendi" işareti (bölümler satırına kaydırılmış). */
    @Test
    @Config(qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
    fun p05_tvEpisodesWatched() {
        com.example.ui.tv.TvDevice.overrideForTest = true
        try {
            val episode = series.first { it.id == 8102 }
            compose.setContent {
                MyApplicationTheme(appTheme = AppTheme.PURE_BLACK, dynamicColor = false) {
                    com.example.ui.tv.TvDetailScreen(viewModel = vm, item = episode, onPlay = {})
                }
            }
            settle(5_000)
            // Sayfa bölümler satırına kaydırılır.
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.35f, durationMillis = 400) }
            settle(1_500)
            compose.onRoot().captureRoboImage(filePath = File(app.cacheDir, "warmup.png").absolutePath)
            settle(2_000)
            compose.onRoot().captureRoboImage(filePath = File(outDir, "p05-tv-bolumler-izlendi.png").absolutePath)
        } finally {
            com.example.ui.tv.TvDevice.overrideForTest = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun p04_assistant() {
        runBlocking { com.example.data.repository.SettingsRepository(app).setGeminiApiKey("AIzaShowcaseKey000000000000000000000000") }
        val field = IPTVViewModel::class.java.getDeclaredField("_chatMessages").apply { isAccessible = true }
        (field.get(vm) as MutableStateFlow<List<IPTVViewModel.ChatMessage>>).value = listOf(
            IPTVViewModel.ChatMessage("user", "Bir adam gece trende uyanıyor, herkes aynı günü tekrar yaşıyor. Filmin adı ne?"),
            IPTVViewModel.ChatMessage("ai", "Anlattığın sahneler \"Gece Yarısı Treni\" filmine çok benziyor: kahraman her gece aynı trende uyanıp olayların tekrarını çözmeye çalışıyor. Kütüphanende var!",
                matchedItem = movies[0], detectedTitle = "Gece Yarısı Treni", matchedItems = listOf(movies[0]))
        )
        shot("p04-asistan", 4_000) { MovieFinderChatScreen(viewModel = vm, onBack = {}, onOpenItem = {}) }
    }
    @Test fun p06_legalPrivacy() = shot("p06-gizlilik", 3_000) {
        com.example.ui.legal.LegalScreen(doc = com.example.data.legal.LegalDoc.PRIVACY, language = "tr", onClose = {})
    }

    @Test fun p07_loginLinks() {
        compose.setContent { MyApplicationTheme(appTheme = AppTheme.PURE_BLACK, dynamicColor = false) { com.example.ui.screens.LoginScreen(viewModel = vm, onLoginSuccess = { _, _ -> }) } }
        settle(2_000)
        compose.onNodeWithTag("legal_link_terms").performScrollTo()
        settle(1_000)
        compose.onRoot().captureRoboImage(filePath = File(outDir, "p07-giris-baglantilar.png").absolutePath)
    }

    @Test fun p08_settingsLegal() {
        compose.setContent { MyApplicationTheme(appTheme = AppTheme.PURE_BLACK, dynamicColor = false) { com.example.ui.screens.SettingsScreen(viewModel = vm) } }
        settle(2_000)
        compose.onNodeWithTag("legal_settings_card").performScrollTo()
        settle(1_000)
        compose.onRoot().captureRoboImage(filePath = File(outDir, "p08-ayarlar-yasal.png").absolutePath)
    }

    @Test
    @Config(qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
    fun p09_tvLegal() {
        com.example.ui.tv.TvDevice.overrideForTest = true
        try {
            shot("p09-tv-hizmet-sartlari", 3_000) {
                com.example.ui.legal.LegalScreen(doc = com.example.data.legal.LegalDoc.TERMS, language = "tr", onClose = {})
            }
        } finally {
            com.example.ui.tv.TvDevice.overrideForTest = null
        }
    }
    private val paywallOffers = mapOf(
        com.example.util.SubscriptionManager.PRODUCT_MONTHLY to com.example.util.ProOffer(com.example.util.SubscriptionManager.PRODUCT_MONTHLY, "₺80,00", 80_000_000, true),
        com.example.util.SubscriptionManager.PRODUCT_YEARLY to com.example.util.ProOffer(com.example.util.SubscriptionManager.PRODUCT_YEARLY, "₺800,00", 800_000_000, true),
        com.example.util.SubscriptionManager.PRODUCT_LIFETIME to com.example.util.ProOffer(com.example.util.SubscriptionManager.PRODUCT_LIFETIME, "₺1.500,00", 1_500_000_000, false)
    )

    @Test fun p10_paywall() = shot("p10-paywall", 1_500) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSizeForShot(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            com.example.ui.components.PaywallContent(
                reasonMessage = app.getString(R.string.paywall_reason_daily_limit), isPro = false, offers = paywallOffers, busy = false,
                onDismiss = {}, onBuy = {}, onRestore = {}, onManage = {}, onOpenLegal = {}
            )
        }
    }

    @Test
    @Config(qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
    fun p11_tvPaywall() {
        com.example.ui.tv.TvDevice.overrideForTest = true
        try {
            shot("p11-tv-paywall", 1_500) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSizeForShot(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    com.example.ui.components.PaywallContent(
                        reasonMessage = null, isPro = false, offers = paywallOffers, busy = false,
                        onDismiss = {}, onBuy = {}, onRestore = {}, onManage = {}, onOpenLegal = {}
                    )
                }
            }
        } finally {
            com.example.ui.tv.TvDevice.overrideForTest = null
        }
    }
}

private fun androidx.compose.ui.Modifier.fillMaxSizeForShot(): androidx.compose.ui.Modifier =
    this.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF07040F))
