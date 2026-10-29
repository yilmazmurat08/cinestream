package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.IPTVGroup
import com.example.data.model.IPTVItem
import com.example.ui.screens.FolderCard
import com.example.ui.screens.LegendsSection
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NextEpisodeCountdown
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepPurpleBg
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocalTmdbRatings
import com.example.util.TmdbRatings
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ekran bekçisi: önemli ekranların onaylı görüntüleri `src/test/screenshots/golden/` klasöründe durur. GitHub'daki
 * otomatik denetim her gönderimde ekranları yeniden çizip bunlarla karşılaştırır; istemeden değişen ekran hata verir.
 * Bilerek değiştirilen ekranın görüntüsü yenilenir:
 * ./gradlew recordRoborazziDebug --tests 'com.example.UiGoldenTest'
 * Normal test çalıştırmasında görüntü kaydedilmez ve karşılaştırılmaz (yalnızca ekranın çizilebildiği denenir).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w360dp-h780dp-port-xxhdpi")
class UiGoldenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Farklı bilgisayarlarda yazı kenar yumuşatması birkaç pikseli değiştirebilir: %1'e kadar fark sayılmaz.
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    private fun golden(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { MyApplicationTheme(appTheme = AppTheme.PURE_BLACK, dynamicColor = false) { content() } }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/golden/$name.png", roborazziOptions = options)
    }

    // Afişler internetten (Coil) gecikmeli yüklendiği için görüntü her seferinde aynı olsun diye afişsiz çizilir.
    private fun movie(id: Int, name: String) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://10.255.255.1/m/$id.mp4", category = "Aksiyon", type = "MOVIE"
    )

    @Test
    fun login() = golden("giris") { LoginScreen(onLoginSuccess = { _, _ -> }) }

    @Test
    fun folderCards() = golden("klasor-kartlari") {
        val sample = listOf(movie(1, "Örnek"))
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().background(DeepPurpleBg).padding(16.dp)
        ) {
            FolderCard(IPTVGroup(id = "a", name = "Aksiyon Filmleri", type = "MOVIE", items = sample), onClick = {})
            FolderCard(IPTVGroup(id = "b", name = "Yerli Diziler", type = "SERIES", items = sample), onClick = {})
            FolderCard(IPTVGroup(id = "c", name = "SPOR", type = "LIVE", items = sample), onClick = {})
            FolderCard(IPTVGroup(id = "d", name = "YETİŞKİN: TÜRK", type = "LIVE", items = sample), isAdult = true, isLocked = true, onClick = {})
        }
    }

    @Test
    fun topRatedRow() = golden("en-yuksek-puanlilar") {
        val items = listOf(
            movie(11, "Gece Yarısı Treni"),
            movie(12, "Son Kale"),
            movie(13, "Mavi Dalga")
        )
        val ratings = items.mapIndexed { i, m -> TmdbRatings.key(m) to listOf(9.4, 9.1, 8.9)[i] }.toMap()
        CompositionLocalProvider(LocalTmdbRatings provides ratings) {
            Column(Modifier.fillMaxSize().background(DeepPurpleBg)) {
                LegendsSection(items = items, onItemClick = {}, onLongClick = {})
            }
        }
    }

    @Test
    fun nextEpisodeCountdown() = golden("sonraki-bolum") {
        Box(Modifier.fillMaxSize().background(Color(0xFF15101F))) {
            NextEpisodeCountdown(
                title = "Kuzey Yıldızı S01E03", onPlayNow = {}, onCancel = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
