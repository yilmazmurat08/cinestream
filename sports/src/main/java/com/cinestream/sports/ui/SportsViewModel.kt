package com.cinestream.sports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cinestream.sports.channels.ChannelIndex
import com.cinestream.sports.channels.ChannelKeys
import com.cinestream.sports.channels.ChannelMatch
import com.cinestream.sports.channels.EpgMatchFinder
import com.cinestream.sports.data.ChannelProvider
import com.cinestream.sports.data.EpgLookup
import com.cinestream.sports.data.EpgProgramme
import com.cinestream.sports.data.SportsDataSource
import com.cinestream.sports.model.Broadcast
import com.cinestream.sports.model.Country
import com.cinestream.sports.model.League
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchDetail
import com.cinestream.sports.model.NotificationEntry
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.Team
import com.cinestream.sports.notify.ReminderScheduler
import com.cinestream.sports.settings.LeagueFilterMode
import com.cinestream.sports.settings.SportsSettings
import com.cinestream.sports.settings.SportsSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Modülün dışarıya açtığı bağlantı noktaları. CineStream kendi veri kaynağını, kanal listesini ve yayın akışını
 * buradan verir; modülün içi değişmez.
 */
class SportsDependencies(
    val dataSource: SportsDataSource,
    val settings: SportsSettingsStore,
    val channels: ChannelProvider,
    val epg: EpgLookup? = null,
    val reminders: ReminderScheduler = ReminderScheduler.None,
    val clock: Clock = Clock.systemDefaultZone(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val io: CoroutineDispatcher = Dispatchers.IO,
    /** Yayıncılar arasında önce bu ülkeninkiler denenir. */
    val country: String = "TR"
)

/** Listede bir maç satırı. */
data class MatchRow(
    val match: Match,
    /** Yayıncının kullanıcının listesindeki karşılığı. */
    val channel: ChannelMatch?,
    /** Yayıncı bilgisi yokken yayın akışında bulunan kanal. */
    val epgChannel: PlaylistChannel?,
    /** Kanal yayın akışında da görünüyor. */
    val epgVerified: Boolean,
    val followedTeamIds: Set<String>
) {
    val watchChannel: PlaylistChannel? get() = channel?.channel ?: epgChannel
    val isFollowed: Boolean get() = followedTeamIds.isNotEmpty()
}

data class LeagueSection(val league: League, val rows: List<MatchRow>)
data class LeagueCount(val league: League, val count: Int, val selected: Boolean)
data class CountryLeagues(val country: Country, val leagues: List<LeagueCount>)

data class SportsUiState(
    val sports: List<Sport> = Sport.entries,
    val sport: Sport = Sport.FOOTBALL,
    val today: LocalDate = LocalDate.MIN,
    val day: LocalDate = LocalDate.MIN,
    val minDay: LocalDate = LocalDate.MIN,
    val maxDay: LocalDate = LocalDate.MAX,
    val loading: Boolean = true,
    val error: Boolean = false,
    val followedRows: List<MatchRow> = emptyList(),
    val sections: List<LeagueSection> = emptyList(),
    /** Seçili gün ve spordaki tüm maçlar (filtreden önce). */
    val totalForDay: Int = 0,
    /** Lig filtresi yüzünden gizlenen maçlar ("Tüm maçları göster"). */
    val hiddenByLeagueFilter: Int = 0,
    val liveOnly: Boolean = false,
    val myChannelsOnly: Boolean = false,
    val leagueMode: LeagueFilterMode = LeagueFilterMode.MAJOR,
    val leagueGroups: List<CountryLeagues> = emptyList(),
    val searchActive: Boolean = false,
    val query: String = "",
    val showMyChannel: Boolean = true,
    val reminderIds: Set<String> = emptySet()
) {
    val filterActive: Boolean get() = liveOnly || myChannelsOnly || leagueMode == LeagueFilterMode.CUSTOM
    val isEmpty: Boolean get() = followedRows.isEmpty() && sections.isEmpty()
}

/** Maç detayındaki bir yayıncı ve kullanıcının listesindeki karşılığı. */
data class BroadcastOption(
    val broadcast: Broadcast,
    val match: ChannelMatch?,
    val suggestions: List<PlaylistChannel>
)

data class DetailUiState(
    val matchId: String,
    val loading: Boolean = true,
    val error: Boolean = false,
    val detail: MatchDetail? = null,
    val options: List<BroadcastOption> = emptyList(),
    val epgChannel: PlaylistChannel? = null,
    val epgVerified: Boolean = false,
    val followedTeamIds: Set<String> = emptySet(),
    val reminderSet: Boolean = false,
    val reminderMinutes: Int = 10
) {
    /** "İzle" düğmesinin açacağı kanal. */
    val watchChannel: PlaylistChannel? get() = options.firstNotNullOfOrNull { it.match?.channel } ?: epgChannel
    val watchMatch: ChannelMatch? get() = options.firstNotNullOfOrNull { it.match }
}

data class FollowedTeamRow(val team: Team, val next: Match?)

class SportsViewModel(private val deps: SportsDependencies) : ViewModel() {

    private val today: LocalDate = Instant.now(deps.clock).atZone(deps.zone).toLocalDate()
    private val minDay = today.minusDays(7)
    private val maxDay = today.plusDays(7)

    private val selectedSport = MutableStateFlow<Sport?>(null)
    private val selectedDay = MutableStateFlow(today)
    private val liveOnly = MutableStateFlow(false)
    private val myChannelsOnly = MutableStateFlow(false)
    private val searchActive = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val reloadTick = MutableStateFlow(0)

    val settings: StateFlow<SportsSettings> =
        deps.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SportsSettings())

    private val channelIndex: StateFlow<ChannelIndex> = deps.channels.channels
        .map { ChannelIndex(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChannelIndex(emptyList()))

    private val sport: StateFlow<Sport> = combine(selectedSport, settings) { chosen, s ->
        chosen?.takeIf { it in s.sports } ?: s.sports.firstOrNull() ?: Sport.FOOTBALL
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, Sport.FOOTBALL)

    private class DayData(val sport: Sport, val day: LocalDate, val matches: List<Match>, val programmes: List<EpgProgramme>)
    private enum class Load { LOADING, READY, ERROR }

    private val dayData = MutableStateFlow<DayData?>(null)
    private val load = MutableStateFlow(Load.LOADING)

    val notifications: StateFlow<List<NotificationEntry>> =
        deps.settings.notifications.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val followed = MutableStateFlow<List<FollowedTeamRow>>(emptyList())
    val followedTeams: StateFlow<List<FollowedTeamRow>> = followed

    private val teamQuery = MutableStateFlow("")
    private val teamResultsState = MutableStateFlow<List<Team>>(emptyList())
    val teamQueryText: StateFlow<String> = teamQuery
    val teamResults: StateFlow<List<Team>> = teamResultsState

    private val detailState = MutableStateFlow<DetailUiState?>(null)

    init {
        viewModelScope.launch {
            combine(sport, selectedDay, reloadTick) { s, d, _ -> s to d }.collectLatest { (s, d) -> loadDay(s, d) }
        }
        viewModelScope.launch {
            settings.map { it.followedTeamIds }.distinctUntilChanged().collectLatest { ids -> followed.value = loadFollowed(ids) }
        }
        viewModelScope.launch {
            teamQuery.collectLatest { q ->
                if (q.isBlank()) {
                    teamResultsState.value = emptyList()
                } else {
                    delay(200)
                    teamResultsState.value = runSafely { withContext(deps.io) { deps.dataSource.searchTeams(q) } } ?: emptyList()
                }
            }
        }
    }

    private suspend fun <T> runSafely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun loadDay(sport: Sport, day: LocalDate) {
        load.value = Load.LOADING
        val matches = runSafely { withContext(deps.io) { deps.dataSource.matches(sport, day, deps.zone) } }
        if (matches == null) {
            load.value = Load.ERROR
            return
        }
        val programmes = deps.epg?.let { epg ->
            val start = day.atStartOfDay(deps.zone).toInstant().toEpochMilli()
            runSafely { withContext(deps.io) { epg.programmes(start - 3_600_000L, start + 25 * 3_600_000L) } }
        }.orEmpty()
        dayData.value = DayData(sport, day, matches, programmes)
        load.value = Load.READY
    }

    private suspend fun loadFollowed(ids: Set<String>): List<FollowedTeamRow> {
        if (ids.isEmpty()) return emptyList()
        val teams = runSafely { withContext(deps.io) { deps.dataSource.teams(ids) } } ?: return emptyList()
        val now = deps.clock.millis()
        return teams.map { team ->
            FollowedTeamRow(team, runSafely { withContext(deps.io) { deps.dataSource.nextMatch(team.id, now) } })
        }.sortedWith(compareBy<FollowedTeamRow>({ it.next?.kickoffEpochMillis ?: Long.MAX_VALUE }, { it.team.name }))
    }

    private data class Filters(val liveOnly: Boolean, val myChannelsOnly: Boolean, val searchActive: Boolean, val query: String)
    private data class Selection(val sport: Sport, val day: LocalDate, val load: Load, val data: DayData?)

    val uiState: StateFlow<SportsUiState> = combine(
        combine(sport, selectedDay, load, dayData) { s, d, l, data -> Selection(s, d, l, data) },
        settings,
        channelIndex,
        combine(liveOnly, myChannelsOnly, searchActive, query) { a, b, c, d -> Filters(a, b, c, d) }
    ) { sel, s, index, f -> build(sel, s, index, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SportsUiState(today = today, day = today, minDay = minDay, maxDay = maxDay))

    private fun row(m: Match, index: ChannelIndex, s: SportsSettings, programmes: List<EpgProgramme>): MatchRow {
        val channel = index.matchFirst(m.broadcasts, s.channelMappings, deps.country)
        val epgHit = if (programmes.isEmpty()) null else EpgMatchFinder.find(m, programmes)
        return MatchRow(
            match = m,
            channel = channel,
            epgChannel = if (channel == null) epgHit?.channel else null,
            epgVerified = channel != null && epgHit != null && epgHit.channel.id == channel.channel.id,
            followedTeamIds = s.followedTeamIds.filterTo(mutableSetOf()) { m.involves(it) }
        )
    }

    private fun build(sel: Selection, s: SportsSettings, index: ChannelIndex, f: Filters): SportsUiState {
        val data = sel.data?.takeIf { it.sport == sel.sport && it.day == sel.day }
        val rows = data?.matches.orEmpty().map { row(it, index, s, data?.programmes.orEmpty()) }
        val q = ChannelKeys.fold(f.query).trim()
        val searching = f.searchActive && q.isNotEmpty()
        val base = rows
            .filter { !f.liveOnly || it.match.isLive }
            .filter { !f.myChannelsOnly || it.watchChannel != null }
            .filter { !searching || matchesQuery(it.match, q) }
        val leagueOk: (League) -> Boolean = when (s.leagueMode) {
            LeagueFilterMode.MAJOR -> { league -> league.isMajor }
            LeagueFilterMode.ALL -> { _ -> true }
            LeagueFilterMode.CUSTOM -> { league -> s.customLeagueIds.isEmpty() || league.id in s.customLeagueIds }
        }
        // Aramada lig filtresi uygulanmaz: aranan takım alt ligde olsa da bulunur.
        val visible = if (searching) base else base.filter { leagueOk(it.match.league) }
        val sections = visible.groupBy { it.match.league }.entries
            .sortedWith(compareBy<Map.Entry<League, List<MatchRow>>>({ it.key.priority }, { it.key.name }))
            .map { (league, list) -> LeagueSection(league, list.sortedBy { it.match.kickoffEpochMillis }) }
        val counts = rows.groupingBy { it.match.league }.eachCount()
        val groups = counts.keys.groupBy { it.country }.entries
            .sortedBy { (_, leagues) -> leagues.minOf { it.priority } }
            .map { (country, leagues) ->
                CountryLeagues(country, leagues.sortedBy { it.priority }.map {
                    LeagueCount(it, counts.getValue(it), s.leagueMode == LeagueFilterMode.CUSTOM && it.id in s.customLeagueIds)
                })
            }
        return SportsUiState(
            sports = s.sports,
            sport = sel.sport,
            today = today,
            day = sel.day,
            minDay = minDay,
            maxDay = maxDay,
            loading = sel.load == Load.LOADING && data == null,
            error = sel.load == Load.ERROR,
            followedRows = if (searching) emptyList() else base.filter { it.isFollowed },
            sections = sections,
            totalForDay = rows.size,
            hiddenByLeagueFilter = if (searching) 0 else base.count { !leagueOk(it.match.league) },
            liveOnly = f.liveOnly,
            myChannelsOnly = f.myChannelsOnly,
            leagueMode = s.leagueMode,
            leagueGroups = groups,
            searchActive = f.searchActive,
            query = f.query,
            showMyChannel = s.showMyChannel,
            reminderIds = s.reminderMatchIds
        )
    }

    private fun matchesQuery(m: Match, q: String): Boolean =
        ChannelKeys.fold(m.home.name).contains(q) || ChannelKeys.fold(m.away.name).contains(q) ||
            ChannelKeys.fold(m.league.name).contains(q)

    // ---- Kullanıcı işlemleri ----

    fun selectSport(sport: Sport) {
        selectedSport.value = sport
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = when {
            day.isBefore(minDay) -> minDay
            day.isAfter(maxDay) -> maxDay
            else -> day
        }
    }

    fun previousDay() = selectDay(selectedDay.value.minusDays(1))
    fun nextDay() = selectDay(selectedDay.value.plusDays(1))
    fun setLiveOnly(value: Boolean) { liveOnly.value = value }
    fun setMyChannelsOnly(value: Boolean) { myChannelsOnly.value = value }
    fun retry() { reloadTick.value++ }

    fun openSearch() { searchActive.value = true }
    fun setQuery(value: String) { query.value = value }
    fun closeSearch() {
        searchActive.value = false
        query.value = ""
    }

    fun setLeagueMode(mode: LeagueFilterMode) = updateSettings { it.copy(leagueMode = mode) }

    /** Lig işaretlenince görünüm "seçtiğim ligler"e geçer; hiç lig kalmazsa büyük liglere döner. */
    fun toggleLeague(leagueId: String) = updateSettings { s ->
        val current = if (s.leagueMode == LeagueFilterMode.CUSTOM) s.customLeagueIds else emptySet()
        val next = if (leagueId in current) current - leagueId else current + leagueId
        s.copy(leagueMode = if (next.isEmpty()) LeagueFilterMode.MAJOR else LeagueFilterMode.CUSTOM, customLeagueIds = next)
    }

    fun toggleFollow(teamId: String) = updateSettings { s ->
        s.copy(followedTeamIds = if (teamId in s.followedTeamIds) s.followedTeamIds - teamId else s.followedTeamIds + teamId)
    }

    fun setTeamQuery(value: String) { teamQuery.value = value }

    fun assignChannel(broadcaster: String, channel: PlaylistChannel) = updateSettings {
        it.copy(channelMappings = it.channelMappings + (ChannelKeys.of(broadcaster).id to channel.name))
    }

    fun removeMapping(broadcaster: String) = updateSettings {
        it.copy(channelMappings = it.channelMappings - ChannelKeys.of(broadcaster).id)
    }

    fun hasMapping(broadcaster: String): Boolean = ChannelKeys.of(broadcaster).id in settings.value.channelMappings

    fun resetMappings() = updateSettings { it.copy(channelMappings = emptyMap()) }

    fun toggleReminder(match: Match) {
        val s = settings.value
        if (match.id in s.reminderMatchIds) {
            deps.reminders.cancel(match.id)
            updateSettings { it.copy(reminderMatchIds = it.reminderMatchIds - match.id) }
        } else {
            deps.reminders.schedule(match, s.reminderMinutes)
            updateSettings { it.copy(reminderMatchIds = it.reminderMatchIds + match.id) }
        }
    }

    fun clearNotifications() {
        viewModelScope.launch { deps.settings.clearNotifications() }
    }

    fun updateSettings(transform: (SportsSettings) -> SportsSettings) {
        viewModelScope.launch { deps.settings.update(transform) }
    }

    /** Spor sırasını değiştirir; en az bir spor açık kalır. */
    fun setSportEnabled(sport: Sport, enabled: Boolean) = updateSettings { s ->
        val next = if (enabled) (s.sports + sport).distinct() else s.sports - sport
        if (next.isEmpty()) s else s.copy(sports = next)
    }

    fun moveSport(sport: Sport, up: Boolean) = updateSettings { s ->
        val list = s.sports.toMutableList()
        val i = list.indexOf(sport)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) s else s.copy(sports = list.apply { add(j, removeAt(i)) })
    }

    // ---- Maç detayı ----

    val detail: StateFlow<DetailUiState?> = combine(detailState, settings, channelIndex) { d, s, index ->
        d?.let { decorate(it, s, index) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun openDetail(matchId: String) {
        detailState.value = DetailUiState(matchId)
        viewModelScope.launch {
            val loaded = runSafely { withContext(deps.io) { deps.dataSource.matchDetail(matchId) } }
            if (detailState.value?.matchId != matchId) return@launch
            detailState.value = if (loaded == null) {
                DetailUiState(matchId, loading = false, error = true)
            } else {
                DetailUiState(matchId, loading = false, detail = loaded)
            }
        }
    }

    fun closeDetail() { detailState.value = null }

    private fun decorate(d: DetailUiState, s: SportsSettings, index: ChannelIndex): DetailUiState {
        val match = d.detail?.match ?: return d.copy(followedTeamIds = s.followedTeamIds, reminderMinutes = s.reminderMinutes)
        val options = match.broadcasts.sortedBy { if (it.countryCode == deps.country) 0 else 1 }.map { b ->
            val found = index.match(b.channelName, s.channelMappings)
            BroadcastOption(b, found, if (found == null) index.suggestions(b.channelName) else emptyList())
        }
        val programmes = dayData.value?.programmes.orEmpty()
        val epgHit = if (programmes.isEmpty()) null else EpgMatchFinder.find(match, programmes)
        val best = options.firstNotNullOfOrNull { it.match }
        return d.copy(
            options = options,
            epgChannel = if (best == null) epgHit?.channel else null,
            epgVerified = best != null && epgHit?.channel?.id == best.channel.id,
            followedTeamIds = s.followedTeamIds,
            reminderSet = match.id in s.reminderMatchIds,
            reminderMinutes = s.reminderMinutes
        )
    }

    /** Kanal atama penceresi için arama. */
    fun searchChannels(query: String, broadcaster: String?): List<Pair<PlaylistChannel, Boolean>> =
        channelIndex.value.search(query, broadcaster)

    companion object {
        fun factory(deps: SportsDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer { SportsViewModel(deps) }
        }
    }
}
