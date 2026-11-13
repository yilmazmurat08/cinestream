package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.PlayerScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Kullanıcı testi (13 Kasım 2026, realme C55, büyük yazı tipi): "İzlemeye devam et?" penceresinde dikeyde düğme
 * yazıları satırlara bölünüyordu ("Baştan / n / Başlat"), yatayda pencere ekrana sığmıyor ve düğmeler hiç
 * görünmüyordu. Artık dikeyde düğmeler alt alta tam genişlik, yatayda pencere sığar (gerekirse kaydırılır).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhoneResumeDialogTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val shots = System.getenv("CINESTREAM_SHOTS")

    @After
    fun tearDown() = db.close()

    private fun open() {
        RuntimeEnvironment.setFontScale(1.3f)
        val episode = IPTVItem(
            id = 8901, playlistId = 1, name = "Gece Treni S01E01", cleanedName = "Gece Treni S01E01", logoUrl = null,
            streamUrl = "http://10.255.255.1/series/8901.mp4", category = "Dizi", type = "SERIES", season = 1, episode = 1
        )
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent {
            PlayerScreen(
                item = episode, siblingItems = emptyList(), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm, initialProgressSeconds = 18L
            )
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "tr-w392dp-h850dp-port-xxhdpi")
    fun portrait_largeFont_buttonsStackedFullWidth() {
        open()
        val cont = compose.onNodeWithTag("resume_continue").assertIsDisplayed().getUnclippedBoundsInRoot()
        val over = compose.onNodeWithTag("resume_start_over").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Devam Et üstte", cont.bottom <= over.top)
        assertTrue("tam genişlik", cont.width >= 250.dp && over.width >= 250.dp)
        shots?.let { compose.onRoot().captureRoboImage("$it/resume-portrait.png") }
    }

    @Test
    @Config(qualifiers = "tr-w850dp-h392dp-land-xxhdpi")
    fun landscape_largeFont_buttonsVisible() {
        open()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val cont = compose.onNodeWithTag("resume_continue").assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithTag("resume_start_over").assertIsDisplayed()
        assertTrue("düğme ekranın içinde", cont.bottom <= root.bottom)
        shots?.let { compose.onRoot().captureRoboImage("$it/resume-landscape.png") }
    }
}
