package com.cinestream.sports

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import com.cinestream.sports.settings.InMemorySportsSettingsStore
import com.cinestream.sports.settings.LeagueFilterMode
import com.cinestream.sports.settings.SportsSettings
import com.cinestream.sports.ui.SportsApp
import com.cinestream.sports.ui.SportsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

private const val GAZIANTEP = "tr-super_2026-10-12_0"
private const val KAYSERI = "tr-super_2026-10-12_1"
private const val NAPOLI = "ita-seriea_2026-10-12_1"
private const val LIVERPOOL_GS = "uefa-cl_2026-10-13_0"

/** Telefon ekranlarında kullanıcı akışları: filtre, maç detayı, izle, kanal ata, takip, özelleştir, geri tuşu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w360dp-h780dp-port-xxhdpi")
class SportsUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var watched: PlaylistChannel? = null

    private fun show(settings: SportsSettings = SportsSettings()): SportsViewModel {
        val deps = TestEnv.deps(InMemorySportsSettingsStore(settings))
        val vm = SportsViewModel(deps)
        compose.setContent { SportsApp(dependencies = deps, onWatch = { watched = it }, viewModel = vm) }
        compose.waitForIdle()
        return vm
    }

    private fun waitFor(condition: () -> Boolean): Boolean {
        repeat(60) {
            if (condition()) return true
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            compose.waitForIdle()
        }
        return condition()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun tag(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag)
    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun home_majorLeagues_filterSheet_liveOnly_andBackClosesSheet() {
        val vm = show()
        assertTrue(exists("league_tr-super"))
        assertFalse("alt ligler varsayılan olarak gizli", exists("league_tr-3lig"))
        tag("action_filters").performClick()
        compose.waitForIdle()
        assertTrue(exists("filter_sheet"))
        tag("filter_live").performClick()
        compose.waitForIdle()
        assertTrue(vm.uiState.value.liveOnly)
        back()
        compose.waitForIdle()
        assertFalse("geri tuşu pencereyi kapatır", exists("filter_sheet"))
        assertTrue(exists("match_$GAZIANTEP"))
        assertFalse("başlamamış maç gizli", exists("match_$NAPOLI"))
    }

    @Test
    fun showAll_revealsLowerLeagues() {
        val vm = show()
        tag("match_list").performScrollToNode(hasTestTag("show_all"))
        tag("show_all").performClick()
        compose.waitForIdle()
        assertEquals(LeagueFilterMode.ALL, vm.uiState.value.leagueMode)
        assertTrue(exists("league_tr-1lig") || vm.uiState.value.sections.any { it.league.id == "tr-1lig" })
    }

    @Test
    fun matchDetail_showsScore_andWatchesTheChannelFromTheList() {
        show()
        tag("match_$GAZIANTEP").performClick()
        compose.waitForIdle()
        tag("detail_score").assertTextEquals("3 - 0")
        tag("tab_stats").performClick()
        tag("detail_watch").performClick()
        assertEquals("TR | BEIN SPORTS 2 HD", watched?.name)
        tag("detail_back").performClick()
        compose.waitForIdle()
        assertTrue(exists("sports_home"))
    }

    @Test
    fun watchButtonOnTheCardOpensThePlayerDirectly() {
        show()
        tag("watch_$GAZIANTEP").performClick()
        assertEquals("TR | BEIN SPORTS 2 HD", watched?.name)
    }

    @Test
    fun channelNotInList_canBeAssigned_andIsRemembered() {
        val vm = show()
        tag("day_next").performClick()
        compose.waitForIdle()
        tag("match_$LIVERPOOL_GS").performClick()
        compose.waitForIdle()
        tag("detail_assign").performClick()
        compose.waitForIdle()
        assertTrue(exists("assign_sheet"))
        tag("assign_search").performTextInput("TRT 1")
        compose.waitForIdle()
        tag("assign_c16").performClick()
        compose.waitForIdle()
        assertTrue(waitFor { exists("detail_watch") })
        tag("detail_watch").performClick()
        assertEquals("TR | TRT 1 HD", watched?.name)
        assertEquals(1, vm.settings.value.channelMappings.size)
    }

    @Test
    fun followTeamFromMoreMenu_pinsItsMatchOnHome() {
        val vm = show()
        tag("action_more").performClick()
        compose.waitForIdle()
        tag("more_followed").performClick()
        compose.waitForIdle()
        assertTrue(exists("followed_screen"))
        tag("team_search").performTextInput("Napoli")
        assertTrue(waitFor { exists("star_nap") })
        tag("star_nap").performClick()
        compose.waitForIdle()
        tag("screen_back").performClick()
        compose.waitForIdle()
        assertEquals(listOf(NAPOLI), vm.uiState.value.followedRows.map { it.match.id })
        assertTrue(exists("match_list"))
    }

    @Test
    fun customize_hidingASportRemovesItFromThePicker() {
        show()
        assertTrue(exists("sport_basketball"))
        tag("action_more").performClick()
        compose.waitForIdle()
        tag("more_customize").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag("customize_sport_basketball"))).onFirst().performClick()
        compose.waitForIdle()
        back()
        compose.waitForIdle()
        assertTrue(exists("sports_home"))
        assertFalse(exists("sport_basketball"))
    }

    @Test
    fun otherSportsLoadTheirLeagues() {
        val vm = show()
        tag("sport_basketball").performClick()
        compose.waitForIdle()
        assertEquals(Sport.BASKETBALL, vm.uiState.value.sport)
        assertTrue(exists("league_bsl"))
    }
}

/** TV: odaktaki maçın ayrıntısı sağda; OK listedeki kanalı açar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w960dp-h540dp-land-television-xhdpi")
class SportsTvUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tvLayout_panelFollowsFocus_okWatches() {
        var watched: PlaylistChannel? = null
        val deps = TestEnv.deps()
        val vm = SportsViewModel(deps)
        // Kumandalı cihaz: dokunmatik kip kapalı, öğeler odak alabilir.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.setContent {
            SportsApp(dependencies = deps, onWatch = { watched = it }, viewModel = vm, tvMode = true, initialTvFocusMatchId = KAYSERI)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_match_$KAYSERI").assertIsFocused()
        compose.onNodeWithTag("tv_panel").assert(hasAnyDescendant(hasText("Kayserispor", substring = true)))
        compose.onNodeWithTag("tv_match_$GAZIANTEP").performClick()
        assertEquals("TR | BEIN SPORTS 2 HD", watched?.name)
        compose.onNodeWithTag("tv_filters").performClick()
        compose.waitForIdle()
        assertTrue("filtreler TV'de de açılır", compose.onAllNodes(hasTestTag("filter_sheet")).fetchSemanticsNodes().isNotEmpty())
    }
}
