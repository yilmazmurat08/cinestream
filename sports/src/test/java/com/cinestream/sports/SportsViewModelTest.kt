package com.cinestream.sports

import com.cinestream.sports.channels.MatchReason
import com.cinestream.sports.model.Sport
import com.cinestream.sports.settings.InMemorySportsSettingsStore
import com.cinestream.sports.settings.LeagueFilterMode
import com.cinestream.sports.settings.SportsSettings
import com.cinestream.sports.ui.SportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spor ekranının iş kuralları: filtreler, arama, takip, kanal atama, hatırlatma, gün sınırları. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportsViewModelTest {

    private val main = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun test(
        settings: SportsSettings = SportsSettings(),
        reminders: FakeReminders = FakeReminders(),
        block: suspend TestScope.(SportsViewModel, FakeReminders) -> Unit
    ) = runTest(main) {
        val vm = SportsViewModel(TestEnv.deps(InMemorySportsSettingsStore(settings), reminders, main))
        advanceUntilIdle()
        block(vm, reminders)
    }

    private val SportsViewModel.leagues get() = uiState.value.sections.map { it.league.id }
    private val SportsViewModel.visibleCount get() = uiState.value.sections.sumOf { it.rows.size }

    @Test
    fun majorLeaguesByDefault_lowerLeaguesHiddenButCounted() = test { vm, _ ->
        val s = vm.uiState.value
        assertEquals(TestEnv.today, s.day)
        assertEquals(listOf("tr-super", "eng-pl", "esp-laliga", "ita-seriea"), vm.leagues)
        assertEquals(11, s.totalForDay)
        assertEquals(5, s.hiddenByLeagueFilter)
        val turkey = s.leagueGroups.first()
        assertEquals("Türkiye", turkey.country.name)
        assertEquals(listOf("Süper Lig" to 2, "1. Lig" to 2, "2. Lig" to 1, "3. Lig" to 2), turkey.leagues.map { it.league.name to it.count })
    }

    @Test
    fun liveOnlyShowsLiveMatches() = test { vm, _ ->
        vm.setLiveOnly(true)
        assertEquals(3, vm.visibleCount)
        assertTrue(vm.uiState.value.sections.flatMap { it.rows }.all { it.match.isLive })
        assertTrue(vm.uiState.value.filterActive)
    }

    @Test
    fun tickingALeagueSwitchesToCustomAndUntickingReturnsToMajor() = test { vm, _ ->
        vm.toggleLeague("tr-3lig")
        advanceUntilIdle()
        assertEquals(LeagueFilterMode.CUSTOM, vm.uiState.value.leagueMode)
        assertEquals(listOf("tr-3lig"), vm.leagues)
        vm.toggleLeague("tr-3lig")
        advanceUntilIdle()
        assertEquals(LeagueFilterMode.MAJOR, vm.uiState.value.leagueMode)
    }

    @Test
    fun searchFindsLowerLeagueTeamsDespiteTheLeagueFilter() = test { vm, _ ->
        vm.openSearch()
        vm.setQuery("karsiyaka")
        val rows = vm.uiState.value.sections.flatMap { it.rows }
        assertEquals(listOf("Eskişehirspor - Karşıyaka"), rows.map { "${it.match.home.name} - ${it.match.away.name}" })
        vm.closeSearch()
        assertEquals(6, vm.visibleCount)
    }

    @Test
    fun onlyMyChannelsHidesMatchesWithoutAChannel() = test { vm, _ ->
        vm.setLeagueMode(LeagueFilterMode.ALL)
        advanceUntilIdle()
        assertEquals(11, vm.visibleCount)
        vm.setMyChannelsOnly(true)
        // 2. Lig maçının ne yayıncısı ne de yayın akışında kaydı var.
        assertEquals(10, vm.visibleCount)
        assertTrue(vm.uiState.value.sections.flatMap { it.rows }.all { it.watchChannel != null })
    }

    @Test
    fun followedTeamsArePinnedOnTop() = test { vm, _ ->
        vm.toggleFollow("nap")
        advanceUntilIdle()
        val pinned = vm.uiState.value.followedRows
        assertEquals(listOf("Napoli"), pinned.map { it.match.home.name })
        assertEquals(setOf("nap"), pinned.single().followedTeamIds)
    }

    @Test
    fun assigningAChannelMakesTheMatchWatchable() = test { vm, _ ->
        vm.nextDay()
        advanceUntilIdle()
        fun gsRow() = vm.uiState.value.sections.flatMap { it.rows }.first { it.match.away.name == "Galatasaray" }
        assertEquals(null, gsRow().watchChannel)
        vm.assignChannel("Tabii Spor", com.cinestream.sports.model.PlaylistChannel("x", "TR | TRT 1 HD"))
        advanceUntilIdle()
        assertEquals("TR | TRT 1 HD", gsRow().watchChannel?.name)
        assertEquals(MatchReason.MANUAL, gsRow().channel?.reason)
        vm.removeMapping("Tabii Spor")
        advanceUntilIdle()
        assertEquals(null, gsRow().watchChannel)
    }

    @Test
    fun reminderIsScheduledAndCancelled() = test { vm, reminders ->
        val match = vm.uiState.value.sections.flatMap { it.rows }.first { it.match.home.name == "Napoli" }.match
        vm.toggleReminder(match)
        advanceUntilIdle()
        assertEquals(listOf(match.id to 10), reminders.scheduled)
        assertTrue(match.id in vm.uiState.value.reminderIds)
        vm.toggleReminder(match)
        advanceUntilIdle()
        assertEquals(listOf(match.id), reminders.cancelled)
        assertFalse(match.id in vm.uiState.value.reminderIds)
    }

    @Test
    fun daysStayWithinAWeek() = test { vm, _ ->
        vm.selectDay(TestEnv.today.plusDays(30))
        assertEquals(TestEnv.today.plusDays(7), vm.uiState.value.day)
        repeat(20) { vm.previousDay() }
        assertEquals(TestEnv.today.minusDays(7), vm.uiState.value.day)
    }

    @Test
    fun switchingSportLoadsItsMatches() = test { vm, _ ->
        vm.selectSport(Sport.BASKETBALL)
        advanceUntilIdle()
        assertEquals(Sport.BASKETBALL, vm.uiState.value.sport)
        assertTrue(vm.leagues.contains("bsl"))
    }

    @Test
    fun hiddenSportIsNotShown() = test(SportsSettings(sports = listOf(Sport.VOLLEYBALL, Sport.FOOTBALL))) { vm, _ ->
        assertEquals(Sport.VOLLEYBALL, vm.uiState.value.sport)
        vm.setSportEnabled(Sport.VOLLEYBALL, false)
        advanceUntilIdle()
        assertEquals(listOf(Sport.FOOTBALL), vm.settings.value.sports)
        assertEquals(Sport.FOOTBALL, vm.uiState.value.sport)
        vm.setSportEnabled(Sport.FOOTBALL, false)
        advanceUntilIdle()
        assertEquals("son spor kapatılamaz", listOf(Sport.FOOTBALL), vm.settings.value.sports)
    }

    @Test
    fun detailShowsScoreChannelAndGuideCheck() = test { vm, _ ->
        val id = vm.uiState.value.sections.flatMap { it.rows }.first { it.match.home.name == "Gaziantep FK" }.match.id
        vm.openDetail(id)
        advanceUntilIdle()
        val d = vm.detail.value
        assertNotNull(d?.detail)
        assertEquals(3, d!!.detail!!.match.score?.home)
        assertEquals("TR | BEIN SPORTS 2 HD", d.watchChannel?.name)
        assertTrue("yayın akışı doğruladı", d.epgVerified)
        assertTrue(d.detail!!.stats.isNotEmpty())
    }

    @Test
    fun teamSearchAndFollowedTeamsWithNextMatch() = test { vm, _ ->
        vm.setTeamQuery("galata")
        advanceUntilIdle()
        assertTrue(vm.teamResults.value.map { it.id }.containsAll(listOf("gs", "gsb", "gsv")))
        vm.toggleFollow("gs")
        advanceUntilIdle()
        val row = vm.followedTeams.value.single()
        assertEquals("Liverpool", row.next?.home?.name)
    }
}
