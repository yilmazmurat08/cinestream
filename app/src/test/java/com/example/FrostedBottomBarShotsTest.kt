package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Yeni alt barın ekran görüntüleri (realme C55 ölçüleri). Yalnızca CINESTREAM_SHOTS verilince çalışır. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
class FrostedBottomBarShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val outDir = System.getenv("CINESTREAM_SHOTS")?.let { File(it) }
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun cleanUp() {
        db.close()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    @Test
    fun shots() {
        assumeTrue("Ekran görüntüleri yalnızca CINESTREAM_SHOTS ile", outDir != null)
        outDir!!.mkdirs()
        val items = (0 until 60).map { i ->
            val type = listOf("LIVE", "MOVIE", "SERIES")[i % 3]
            IPTVItem(
                id = 79500 + i, playlistId = 1, name = "Yapım $i", cleanedName = "Yapım $i", logoUrl = null,
                streamUrl = "http://10.255.255.1/x/$i", category = "Kategori ${i % 4}", type = type
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
            // Kullanıcının telefonundaki gibi koyu tema.
            MyApplicationTheme(appTheme = com.example.ui.theme.AppTheme.PURE_BLACK, dynamicColor = false) {
                HomeScreen(viewModel = vm!!, onNavigateToChat = {}, onPlayItem = {}, onPlayContinueWatching = {}, onNavigateToMultiScreen = {})
            }
        }
        settle()
        captureScreenRoboImage(File(outDir, "alt-bar-1-ana-sayfa.png").absolutePath)

        compose.onAllNodes(hasTestTag("bottom_nav_MOVIE"), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        captureScreenRoboImage(File(outDir, "alt-bar-2-filmler.png").absolutePath)

        compose.onAllNodes(hasTestTag("bottom_nav_LIVE"), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        captureScreenRoboImage(File(outDir, "alt-bar-2b-canli-tv.png").absolutePath)

        // Kaydırılınca içerik üst ve alt barın arkasından geçer.
        compose.onAllNodes(hasTestTag("folder_grid_view"), useUnmergedTree = true)[0].performTouchInput {
            swipe(start = center, end = center.copy(y = center.y - 380f), durationMillis = 400)
        }
        settle()
        captureScreenRoboImage(File(outDir, "alt-bar-2c-kaydirilmis.png").absolutePath)

        vm!!.updateSearchQuery("Yapım")
        settle()
        captureScreenRoboImage(File(outDir, "alt-bar-4-arama.png").absolutePath)
        vm!!.updateSearchQuery("")
        settle()

        compose.onAllNodes(hasTestTag("bottom_nav_profile_menu"), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        captureScreenRoboImage(File(outDir, "alt-bar-3-profil-menusu.png").absolutePath)
    }
}
