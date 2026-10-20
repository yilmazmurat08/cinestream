package com.cinestream.sports.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.settings.LeagueFilterMode
import com.cinestream.sports.ui.components.LocalSportsZone
import com.cinestream.sports.ui.components.SportsColors
import com.cinestream.sports.ui.components.SportsSheet
import com.cinestream.sports.ui.detail.MatchDetailScreen
import com.cinestream.sports.ui.home.AssignChannelContent
import com.cinestream.sports.ui.home.FilterSheetContent
import com.cinestream.sports.ui.home.MoreOptionsContent
import com.cinestream.sports.ui.home.SportsHomeScreen
import com.cinestream.sports.ui.more.CustomizeScreen
import com.cinestream.sports.ui.more.FollowedTeamsScreen
import com.cinestream.sports.ui.more.NotificationHistoryScreen
import com.cinestream.sports.ui.tv.TvSportsScreen

internal enum class SportsDestination { HOME, DETAIL, FOLLOWED, NOTIFICATIONS, CUSTOMIZE }
internal enum class SportsSheetKind { NONE, FILTERS, MORE }

/**
 * Spor bölümünün giriş noktası. CineStream bunu "Spor" sekmesinde (TV'de Spor kartında) gösterir:
 *
 * ```
 * SportsApp(
 *     dependencies = SportsDependencies(dataSource, DataStoreSportsSettingsStore(context), channelProvider, epg),
 *     onWatch = { channel -> oynatıcıyı bu kanalla aç },
 *     tvMode = TvDevice.isTv(context)
 * )
 * ```
 */
@Composable
fun SportsApp(
    dependencies: SportsDependencies,
    onWatch: (PlaylistChannel) -> Unit,
    modifier: Modifier = Modifier,
    tvMode: Boolean = false,
    viewModel: SportsViewModel = viewModel(factory = SportsViewModel.factory(dependencies)),
    initialTvFocusMatchId: String? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val followed by viewModel.followedTeams.collectAsStateWithLifecycle()
    val teamQuery by viewModel.teamQueryText.collectAsStateWithLifecycle()
    val teamResults by viewModel.teamResults.collectAsStateWithLifecycle()

    var stack by rememberSaveable { mutableStateOf(listOf(SportsDestination.HOME)) }
    var sheet by rememberSaveable { mutableStateOf(SportsSheetKind.NONE) }
    var assignFor by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAssign by remember { mutableStateOf("") }
    val current = stack.last()

    fun push(destination: SportsDestination) {
        stack = stack + destination
    }

    fun pop() {
        if (stack.last() == SportsDestination.DETAIL) viewModel.closeDetail()
        stack = stack.dropLast(1).ifEmpty { listOf(SportsDestination.HOME) }
    }

    fun openMatch(match: Match) {
        viewModel.openDetail(match.id)
        push(SportsDestination.DETAIL)
    }

    fun openMatchId(matchId: String) {
        viewModel.openDetail(matchId)
        push(SportsDestination.DETAIL)
    }

    fun assign(broadcaster: String) {
        lastAssign = broadcaster
        assignFor = broadcaster
    }

    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = current == SportsDestination.HOME && state.searchActive) { viewModel.closeSearch() }

    // Süreç yeniden başlarsa detay durumu kaybolur; boş ekran yerine listeye dönülür.
    LaunchedEffect(current, detail == null) {
        if (current == SportsDestination.DETAIL && detail == null) pop()
    }

    CompositionLocalProvider(LocalSportsZone provides dependencies.zone) {
        Box(
            modifier
                .fillMaxSize()
                .background(SportsColors.Background)
        ) {
            when (current) {
                SportsDestination.HOME -> if (tvMode) {
                    TvSportsScreen(
                        state = state,
                        onSelectSport = viewModel::selectSport,
                        onSelectDay = viewModel::selectDay,
                        onOpenFilters = { sheet = SportsSheetKind.FILTERS },
                        onOpenFollowed = { push(SportsDestination.FOLLOWED) },
                        onOpenNotifications = { push(SportsDestination.NOTIFICATIONS) },
                        onOpenCustomize = { push(SportsDestination.CUSTOMIZE) },
                        onOpenMatch = ::openMatch,
                        onWatch = onWatch,
                        onToggleReminder = viewModel::toggleReminder,
                        initialFocusMatchId = initialTvFocusMatchId
                    )
                } else {
                    SportsHomeScreen(
                        state = state,
                        onSelectSport = viewModel::selectSport,
                        onSelectDay = viewModel::selectDay,
                        onPreviousDay = viewModel::previousDay,
                        onNextDay = viewModel::nextDay,
                        onOpenSearch = viewModel::openSearch,
                        onQueryChange = viewModel::setQuery,
                        onCloseSearch = viewModel::closeSearch,
                        onOpenFilters = { sheet = SportsSheetKind.FILTERS },
                        onOpenMore = { sheet = SportsSheetKind.MORE },
                        onOpenMatch = ::openMatch,
                        onWatch = onWatch,
                        onShowAll = { viewModel.setLeagueMode(LeagueFilterMode.ALL) },
                        onRetry = viewModel::retry
                    )
                }
                SportsDestination.DETAIL -> detail?.let {
                    MatchDetailScreen(
                        state = it,
                        onBack = { pop() },
                        onWatch = onWatch,
                        onAssign = ::assign,
                        onToggleFollow = viewModel::toggleFollow,
                        onToggleReminder = viewModel::toggleReminder
                    )
                }
                SportsDestination.FOLLOWED -> FollowedTeamsScreen(
                    rows = followed,
                    query = teamQuery,
                    results = teamResults,
                    followedIds = settings.followedTeamIds,
                    onQuery = viewModel::setTeamQuery,
                    onToggleFollow = viewModel::toggleFollow,
                    onOpenMatch = ::openMatch,
                    onBack = { pop() }
                )
                SportsDestination.NOTIFICATIONS -> NotificationHistoryScreen(
                    items = notifications,
                    today = state.today,
                    onClear = viewModel::clearNotifications,
                    onOpenMatch = ::openMatchId,
                    onBack = { pop() }
                )
                SportsDestination.CUSTOMIZE -> CustomizeScreen(
                    settings = settings,
                    onSportEnabled = viewModel::setSportEnabled,
                    onMoveSport = viewModel::moveSport,
                    onLeagueMode = viewModel::setLeagueMode,
                    onUpdate = viewModel::updateSettings,
                    onResetMappings = viewModel::resetMappings,
                    onBack = { pop() }
                )
            }

            SportsSheet(sheet == SportsSheetKind.FILTERS, { sheet = SportsSheetKind.NONE }, "filter_sheet") {
                FilterSheetContent(
                    state = state,
                    onClose = { sheet = SportsSheetKind.NONE },
                    onLiveOnly = viewModel::setLiveOnly,
                    onMyChannelsOnly = viewModel::setMyChannelsOnly,
                    onLeagueMode = viewModel::setLeagueMode,
                    onToggleLeague = viewModel::toggleLeague
                )
            }
            SportsSheet(sheet == SportsSheetKind.MORE, { sheet = SportsSheetKind.NONE }, "more_sheet") {
                MoreOptionsContent(
                    onNotifications = { sheet = SportsSheetKind.NONE; push(SportsDestination.NOTIFICATIONS) },
                    onFollowed = { sheet = SportsSheetKind.NONE; push(SportsDestination.FOLLOWED) },
                    onCustomize = { sheet = SportsSheetKind.NONE; push(SportsDestination.CUSTOMIZE) }
                )
            }
            SportsSheet(assignFor != null, { assignFor = null }, "assign_sheet") {
                val broadcaster = assignFor ?: lastAssign
                AssignChannelContent(
                    broadcaster = broadcaster,
                    hasMapping = viewModel.hasMapping(broadcaster),
                    search = { q -> viewModel.searchChannels(q, broadcaster) },
                    onPick = { channel ->
                        viewModel.assignChannel(broadcaster, channel)
                        assignFor = null
                    },
                    onRemove = {
                        viewModel.removeMapping(broadcaster)
                        assignFor = null
                    },
                    onClose = { assignFor = null }
                )
            }
        }
    }
}
