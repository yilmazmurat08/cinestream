package com.cinestream.sports

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cinestream.sports.model.NotificationEntry
import com.cinestream.sports.model.NotificationType
import com.cinestream.sports.settings.InMemorySportsSettingsStore
import com.cinestream.sports.settings.SportsSettings
import com.cinestream.sports.ui.SportsApp
import com.cinestream.sports.ui.SportsViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private val shotsDir: File? = System.getProperty("sports.shots").orEmpty().takeIf { it.isNotBlank() }?.let { File(it) }

private fun at(day: Int, hour: Int, minute: Int): Long =
    LocalDateTime.of(2026, 10, day, hour, minute).atZone(TestEnv.zone).toInstant().toEpochMilli()

/** Örnek bir kullanıcının tercihleri: takip edilen takımlar, kurulu hatırlatma ve bildirim geçmişi. */
private fun sampleStore() = InMemorySportsSettingsStore(
    SportsSettings(
        followedTeamIds = setOf("gs", "fb", "nap", "efes", "vkf"),
        reminderMatchIds = setOf("ita-seriea_2026-10-12_1")
    ),
    listOf(
        NotificationEntry("n1", NotificationType.GOAL, "GOL! Gaziantep FK", "Gaziantep FK 3 - 0 Alanyaspor · 78'", at(12, 20, 33), "tr-super_2026-10-12_0"),
        NotificationEntry("n2", NotificationType.KICKOFF, "Maç başladı", "Kayserispor - Rizespor · beIN SPORTS 3", at(12, 20, 1), "tr-super_2026-10-12_1"),
        NotificationEntry("n3", NotificationType.REMINDER, "Gaziantep FK - Alanyaspor", "10 dakika sonra başlıyor · Süper Lig", at(12, 18, 50), "tr-super_2026-10-12_0"),
        NotificationEntry("n4", NotificationType.FULL_TIME, "Maç sonucu", "Barcelona 1 - 0 Real Madrid", at(11, 21, 58), "esp-laliga_2026-10-11_1"),
        NotificationEntry("n5", NotificationType.REMINDER, "Barcelona - Real Madrid", "10 dakika sonra başlıyor · LaLiga", at(11, 19, 50), "esp-laliga_2026-10-11_1")
    )
)

/** Telefon ekranlarının görüntüleri (1080 px). Yalnızca SPORTS_SHOTS verilince çalışır. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w360dp-h780dp-port-xxhdpi")
class SportsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        repeat(3) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(File(shotsDir, "$name.png").absolutePath)
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun phoneScreens() {
        assumeTrue("Görüntüler yalnızca SPORTS_SHOTS ile", shotsDir != null)
        shotsDir!!.mkdirs()
        val deps = TestEnv.deps(sampleStore())
        val vm = SportsViewModel(deps)
        compose.setContent { SportsApp(dependencies = deps, onWatch = {}, viewModel = vm) }
        shot("01-ana-ekran")

        click("action_filters"); shot("02-filtreler"); click("sheet_close")
        click("action_more"); shot("03-diger-secenekler")
        click("more_followed"); shot("04-takip-ettigim-takimlar"); click("screen_back")
        click("action_more"); click("more_notifications"); shot("05-bildirim-gecmisi"); click("screen_back")
        click("action_more"); click("more_customize"); shot("06-ozellestir"); click("screen_back")

        click("match_tr-super_2026-10-12_0"); shot("07-mac-detayi")
        click("tab_stats"); shot("08-istatistik")
        click("tab_table"); shot("09-puan-durumu")
        click("tab_broadcast"); shot("10-yayin")
        click("detail_back")

        click("action_search")
        compose.onNodeWithTag("search_field").performTextInput("ka")
        shot("11-arama")
        click("search_close")

        click("sport_basketball"); shot("12-basketbol")
        click("sport_volleyball"); shot("13-voleybol")
        click("sport_football")

        click("day_next"); shot("14-yarin")
        // Galatasaray takip edildiği için maç hem üstte hem lig listesinde görünür; ilkine dokun.
        compose.onAllNodes(hasTestTag("match_uefa-cl_2026-10-13_0")).onFirst().performClick()
        compose.waitForIdle()
        shot("15-kanal-listede-yok")
        click("detail_assign"); shot("16-kanal-ata")
        check(exists("assign_sheet"))
    }
}

/** TV ekranının görüntüsü (1920×1080). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
class SportsTvScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        repeat(3) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(File(shotsDir, "$name.png").absolutePath)
    }

    @Test
    fun tvScreens() {
        assumeTrue("Görüntüler yalnızca SPORTS_SHOTS ile", shotsDir != null)
        shotsDir!!.mkdirs()
        val deps = TestEnv.deps(sampleStore())
        val vm = SportsViewModel(deps)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.setContent {
            SportsApp(dependencies = deps, onWatch = {}, viewModel = vm, tvMode = true, initialTvFocusMatchId = "tr-super_2026-10-12_1")
        }
        compose.waitForIdle()
        shot("20-tv-ana-ekran")
        compose.onNodeWithTag("tv_filters").performClick()
        compose.waitForIdle()
        shot("21-tv-filtreler")
    }
}
