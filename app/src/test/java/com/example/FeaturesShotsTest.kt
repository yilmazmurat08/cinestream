package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.features.SportsHost
import com.example.ui.features.games.GamesHost
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.TvApp
import com.example.ui.tv.TvAppState
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
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/** Oyunlar ve Spor ekran görüntüleri (telefon ve TV). Yalnızca CINESTREAM_SHOTS verilince çalışır. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
open class FeaturesShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected open val tv = false
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val outDir = System.getenv("CINESTREAM_SHOTS")?.let { File(it) }
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val palette = listOf(0xFF7C3AED to 0xFF1E1B4B, 0xFFDB2777 to 0xFF3B0764, 0xFF0EA5E9 to 0xFF172554, 0xFFF59E0B to 0xFF451A03, 0xFF10B981 to 0xFF022C22)

    @After
    fun cleanUp() {
        db.close()
    }

    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(40)
            left -= 100
        }
    }

    /** Yer tutucu poster (gerçek poster yok; uygulamada kullanıcının listesinden gelir). */
    private fun poster(i: Int): String {
        val file = File(app.cacheDir, "shot_poster_$i.png")
        if (!file.exists()) {
            val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
            val (a, b) = palette[i % palette.size]
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP) }
            Canvas(bmp).drawRect(0f, 0f, 300f, 450f, paint)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return file.toURI().toString()
    }

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(content: @Composable () -> Unit) {
        if (screen == null) {
            screen = content
            compose.setContent { MyApplicationTheme(dynamicColor = false) { screen?.invoke() } }
        } else {
            compose.runOnUiThread { screen = content }
        }
        settle(2_000)
    }

    private fun click(tag: String) {
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle(1_500)
    }

    private fun shot(name: String) = captureScreenRoboImage(File(outDir, "$name.png").absolutePath)

    @Test
    fun shots() {
        assumeTrue("Ekran görüntüleri yalnızca CINESTREAM_SHOTS ile", outDir != null)
        outDir!!.mkdirs()
        val cats = listOf("Korku", "Savaş", "Aksiyon", "Komedi", "Bilim Kurgu", "Dram", "Animasyon", "Gerilim")
        val items = (0 until 96).map { i ->
            IPTVItem(
                id = 83000 + i, playlistId = 1, name = "Örnek Film ${i + 1}", cleanedName = "Örnek Film ${i + 1}",
                logoUrl = poster(i), streamUrl = "http://10.255.255.1/v/$i", category = cats[i % cats.size], type = "MOVIE",
                summary = "Bu bir yer tutucu özet. Uygulamada filmin gerçek konusu görünür.", releaseDate = "20${10 + i % 15}-05-01",
                genre = cats[i % cats.size], rating = 7.0 + (i % 20) / 10.0
            )
        } + (0 until 30).map { i ->
            IPTVItem(id = 84000 + i, playlistId = 1, name = "TR | BEIN SPORTS ${i % 4 + 1} HD", cleanedName = "beIN Sports ${i % 4 + 1}",
                logoUrl = null, streamUrl = "http://10.255.255.1/l/$i", category = "Spor", type = "LIVE")
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
        compose.mainClock.autoAdvance = false
        val prefix = if (tv) "tv" else "telefon"

        if (tv) {
            show { TvApp(viewModel = vm!!, state = TvAppState(com.example.ui.tv.TV_HOME, com.example.ui.tv.TvSection.LIVE, null, null, null),onPlayItem = {}, onPlayContinue = {}, onOpenAssistant = {}) }
            settle(1_500)
            shot("$prefix-0-ana-ekran-kartlar")
        }
        show { GamesHost(viewModel = vm!!, tvMode = tv, onGoSeriesFolders = {}) }
        shot("$prefix-1-oyunlar")
        click("game_card_SWIPE")
        settle(2_000)
        shot("$prefix-2-kaydirma")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle(800)
        click("game_card_WHEEL")
        settle(1_500)
        click("wheel_spin")
        settle(5_000)
        shot("$prefix-3-cark")
        click("wheel_choose")
        settle(800)
        shot("$prefix-3b-cark-secim")
        click("wheel_choose_cancel")
        settle(500)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle(800)
        click("game_card_PUZZLE")
        settle(4_000)
        click("puzzle_tile_0")
        settle(500)
        shot("$prefix-5-puzzle")
        show { SportsHost(viewModel = vm!!, onPlayItem = {}, tvMode = tv) }
        settle(2_500)
        shot("$prefix-4-spor")
    }
}

@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
class FeaturesShotsTvTest : FeaturesShotsTest() {
    override val tv = true
}
