package com.cinestream.sports.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.R
import com.cinestream.sports.channels.MatchReason
import com.cinestream.sports.model.EventType
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchDetail
import com.cinestream.sports.model.MatchEvent
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.StandingRow
import com.cinestream.sports.model.StatKey
import com.cinestream.sports.model.Team
import com.cinestream.sports.ui.BroadcastOption
import com.cinestream.sports.ui.DetailUiState
import com.cinestream.sports.ui.components.LocalSportsZone
import com.cinestream.sports.ui.components.SportsBackground
import com.cinestream.sports.ui.components.SportsColors
import com.cinestream.sports.ui.components.TeamCrest
import com.cinestream.sports.ui.components.cleanChannelName
import com.cinestream.sports.ui.components.formatTime
import com.cinestream.sports.ui.components.glass
import com.cinestream.sports.ui.components.longDate
import com.cinestream.sports.ui.components.statusText

private enum class DetailTab { SUMMARY, STATS, TABLE, BROADCAST }

/** Maç detayı: skor, izleme, olaylar, istatistik, puan durumu ve yayıncılar. */
@Composable
internal fun MatchDetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onWatch: (PlaylistChannel) -> Unit,
    onAssign: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onToggleReminder: (Match) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable(state.matchId) { mutableIntStateOf(0) }
    val detail = state.detail
    Column(
        modifier
            .fillMaxSize()
            .background(SportsBackground)
            .statusBarsPadding()
            .testTag("match_detail")
    ) {
        DetailTopBar(detail?.match, onBack)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SportsColors.Accent)
            }
            detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.sports_load_error), color = SportsColors.TextMuted)
            }
            else -> {
                val tabs = availableTabs(detail)
                val current = tabs.getOrElse(tab) { tabs.first() }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header") { ScoreHeader(detail.match, state.followedTeamIds, onToggleFollow) }
                    item(key = "watch") { WatchPanel(state, onWatch, onAssign, onToggleReminder) }
                    item(key = "tabs") { TabChips(tabs, current) { tab = tabs.indexOf(it) } }
                    when (current) {
                        DetailTab.SUMMARY -> summaryItems(detail)
                        DetailTab.STATS -> item(key = "stats") { StatsBlock(detail) }
                        DetailTab.TABLE -> item(key = "table") { StandingsBlock(detail) }
                        DetailTab.BROADCAST -> item(key = "broadcast") { BroadcastBlock(state, onWatch, onAssign) }
                    }
                }
            }
        }
    }
}

private fun availableTabs(detail: MatchDetail): List<DetailTab> = buildList {
    add(DetailTab.SUMMARY)
    if (detail.stats.isNotEmpty()) add(DetailTab.STATS)
    if (detail.standings.isNotEmpty()) add(DetailTab.TABLE)
    add(DetailTab.BROADCAST)
}

@Composable
private fun DetailTopBar(match: Match?, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .testTag("detail_back"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sports_back), tint = SportsColors.Text)
        }
        if (match != null) {
            Spacer(Modifier.width(6.dp))
            Column {
                Text(match.league.name, color = SportsColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(match.league.country.name, match.round).joinToString(" · "),
                    color = SportsColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ScoreHeader(m: Match, followed: Set<String>, onToggleFollow: (String) -> Unit) {
    val zone = LocalSportsZone.current
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(SportsColors.SurfaceHigh, SportsColors.Card)))
            .glass(shape, Color.Transparent)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Top) {
            TeamBlock(m.home, m.home.id in followed, onToggleFollow, Modifier.weight(1f))
            Column(
                Modifier
                    .widthIn(min = 104.dp)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (m.score != null) {
                    Text(
                        "${m.score.home} - ${m.score.away}",
                        color = SportsColors.Text,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("detail_score")
                    )
                } else {
                    Text(formatTime(m.kickoffEpochMillis, zone), color = SportsColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(6.dp))
                StatusChip(m)
            }
            TeamBlock(m.away, m.away.id in followed, onToggleFollow, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            longDate(m.kickoffEpochMillis, zone) + " · " + formatTime(m.kickoffEpochMillis, zone),
            color = SportsColors.TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun StatusChip(m: Match) {
    val (text, color) = when (m.status) {
        MatchStatus.LIVE -> "${stringResource(R.string.sports_live)} ${statusText(m).orEmpty()}" to SportsColors.Live
        MatchStatus.HALF_TIME -> stringResource(R.string.sports_half_time_long) to SportsColors.Live
        MatchStatus.FINISHED -> stringResource(R.string.sports_finished) to SportsColors.TextMuted
        MatchStatus.POSTPONED -> stringResource(R.string.sports_postponed) to SportsColors.Warning
        MatchStatus.SCHEDULED -> stringResource(R.string.sports_not_started) to SportsColors.TextMuted
    }
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun TeamBlock(team: Team, followed: Boolean, onToggleFollow: (String) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamCrest(team.colors, crestSize = 58.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            team.name,
            color = SportsColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onToggleFollow(team.id) }
                .testTag("follow_${team.id}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (followed) Icons.Filled.Star else Icons.Filled.StarBorder,
                stringResource(if (followed) R.string.sports_unfollow else R.string.sports_follow),
                tint = if (followed) SportsColors.Gold else SportsColors.TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun WatchPanel(
    state: DetailUiState,
    onWatch: (PlaylistChannel) -> Unit,
    onAssign: (String) -> Unit,
    onToggleReminder: (Match) -> Unit
) {
    val match = state.detail?.match ?: return
    val channel = state.watchChannel
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            channel != null -> {
                BigButton(
                    text = stringResource(R.string.sports_watch_on, cleanChannelName(channel.name)),
                    icon = Icons.Filled.PlayArrow,
                    filled = true,
                    tag = "detail_watch"
                ) { onWatch(channel) }
                val notes = buildList {
                    val alternates = state.watchMatch?.alternates?.size ?: 0
                    if (alternates > 0) add(stringResource(R.string.sports_backups, alternates))
                    if (state.watchMatch?.reason == MatchReason.MANUAL) add(stringResource(R.string.sports_manual_mapping))
                    if (state.epgVerified) add(stringResource(R.string.sports_epg_verified))
                    if (state.watchMatch == null && state.epgChannel != null) add(stringResource(R.string.sports_epg_found))
                }
                if (notes.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = SportsColors.Success, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(notes.joinToString(" · "), color = SportsColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }
            state.options.isNotEmpty() -> {
                val first = state.options.first()
                BigButton(
                    text = stringResource(R.string.sports_assign_channel) + " · " + first.broadcast.channelName,
                    icon = Icons.Filled.LiveTv,
                    filled = false,
                    tag = "detail_assign"
                ) { onAssign(first.broadcast.channelName) }
                Text(
                    first.broadcast.channelName + " · " + stringResource(R.string.sports_not_in_list),
                    color = SportsColors.Warning,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            else -> Text(stringResource(R.string.sports_no_broadcast), color = SportsColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        }
        if (match.status == MatchStatus.SCHEDULED) {
            BigButton(
                text = if (state.reminderSet) stringResource(R.string.sports_reminder_on) + " · " + stringResource(R.string.sports_reminder_minutes, state.reminderMinutes)
                else stringResource(R.string.sports_remind) + " · " + stringResource(R.string.sports_reminder_minutes, state.reminderMinutes),
                icon = if (state.reminderSet) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                filled = false,
                tag = "detail_remind"
            ) { onToggleReminder(match) }
        }
    }
}

@Composable
private fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (filled) Modifier.background(SportsColors.Accent) else Modifier.glass(shape, SportsColors.Surface, SportsColors.Accent.copy(alpha = 0.45f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val color = if (filled) SportsColors.OnAccent else SportsColors.AccentText
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TabChips(tabs: List<DetailTab>, current: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { t ->
            val selected = t == current
            val shape = RoundedCornerShape(20.dp)
            Text(
                stringResource(
                    when (t) {
                        DetailTab.SUMMARY -> R.string.sports_tab_summary
                        DetailTab.STATS -> R.string.sports_tab_stats
                        DetailTab.TABLE -> R.string.sports_tab_table
                        DetailTab.BROADCAST -> R.string.sports_tab_broadcast
                    }
                ),
                color = if (selected) SportsColors.Accent else SportsColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(shape)
                    .glass(shape, if (selected) SportsColors.AccentSoft else SportsColors.Surface.copy(alpha = 0.6f), if (selected) SportsColors.Accent.copy(alpha = 0.5f) else SportsColors.Stroke)
                    .clickable { onSelect(t) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("tab_${t.name.lowercase()}")
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.summaryItems(detail: MatchDetail) {
    val m = detail.match
    if (detail.events.isEmpty()) {
        item(key = "no_events") {
            Text(
                stringResource(if (m.status == MatchStatus.SCHEDULED) R.string.sports_not_started else R.string.sports_no_events),
                color = SportsColors.TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
        return
    }
    items(detail.events.asReversed(), key = { "e_${it.minute}_${it.type}_${it.player}_${it.isHome}" }) { e -> EventRow(e) }
}

@Composable
private fun EventRow(e: MatchEvent) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (e.isHome) EventContent(e, alignEnd = true) }
        Text(
            "${e.minute}'",
            color = SportsColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SportsColors.Surface)
                .padding(vertical = 5.dp)
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { if (!e.isHome) EventContent(e, alignEnd = false) }
    }
}

@Composable
private fun EventContent(e: MatchEvent, alignEnd: Boolean) {
    val icon: @Composable () -> Unit = {
        when (e.type) {
            EventType.GOAL, EventType.PENALTY_GOAL -> Icon(Icons.Filled.SportsSoccer, null, tint = SportsColors.Text, modifier = Modifier.size(18.dp))
            EventType.OWN_GOAL -> Icon(Icons.Filled.SportsSoccer, null, tint = SportsColors.Live, modifier = Modifier.size(18.dp))
            EventType.YELLOW_CARD -> Box(Modifier.size(width = 12.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(SportsColors.Gold))
            EventType.RED_CARD -> Box(Modifier.size(width = 12.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(SportsColors.Live))
            EventType.SUBSTITUTION -> Icon(Icons.Filled.SwapVert, null, tint = SportsColors.Success, modifier = Modifier.size(18.dp))
        }
    }
    val suffix = when (e.type) {
        EventType.PENALTY_GOAL -> " (" + stringResource(R.string.sports_penalty) + ")"
        EventType.OWN_GOAL -> " (" + stringResource(R.string.sports_own_goal) + ")"
        else -> ""
    }
    val isGoal = e.type == EventType.GOAL || e.type == EventType.PENALTY_GOAL || e.type == EventType.OWN_GOAL
    val texts: @Composable () -> Unit = {
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(
                e.player + suffix,
                color = SportsColors.Text,
                fontSize = 14.sp,
                fontWeight = if (isGoal) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (e.type == EventType.SUBSTITUTION && e.detail != null) {
                Text(stringResource(R.string.sports_sub_out, e.detail), color = SportsColors.TextDim, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (alignEnd) {
            texts()
            Spacer(Modifier.width(8.dp))
            icon()
        } else {
            icon()
            Spacer(Modifier.width(8.dp))
            texts()
        }
    }
}

@Composable
private fun StatsBlock(detail: MatchDetail) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape, SportsColors.Card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        detail.stats.forEach { s ->
            val label = stringResource(
                when (s.key) {
                    StatKey.POSSESSION -> R.string.sports_stat_possession
                    StatKey.SHOTS -> R.string.sports_stat_shots
                    StatKey.SHOTS_ON_TARGET -> R.string.sports_stat_shots_on_target
                    StatKey.CORNERS -> R.string.sports_stat_corners
                    StatKey.FOULS -> R.string.sports_stat_fouls
                    StatKey.OFFSIDES -> R.string.sports_stat_offsides
                }
            )
            val suffix = if (s.key == StatKey.POSSESSION) "%" else ""
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.home}$suffix", color = SportsColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                    Text(label, color = SportsColors.TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("${s.away}$suffix", color = SportsColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
                Spacer(Modifier.height(6.dp))
                val total = (s.home + s.away).coerceAtLeast(1)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val homeShare = s.home.toFloat() / total
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(SportsColors.Stroke)
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(homeShare.coerceIn(0.02f, 1f))
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(SportsColors.Accent)
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(SportsColors.Stroke)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((1f - homeShare).coerceIn(0.02f, 1f))
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(SportsColors.Cyan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingsBlock(detail: MatchDetail) {
    val m = detail.match
    val showDraws = m.sport == Sport.FOOTBALL
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape, SportsColors.Card)
            .padding(vertical = 10.dp)
    ) {
        TableRow(
            pos = "#", team = null, name = stringResource(R.string.sports_table_team),
            cells = listOfNotNull(
                stringResource(R.string.sports_table_played), stringResource(R.string.sports_table_won),
                if (showDraws) stringResource(R.string.sports_table_drawn) else null, stringResource(R.string.sports_table_lost),
                stringResource(R.string.sports_table_gd), stringResource(R.string.sports_table_points)
            ),
            header = true, highlight = false
        )
        detail.standings.forEach { r: StandingRow ->
            TableRow(
                pos = r.position.toString(), team = r.team, name = r.team.name,
                cells = listOfNotNull(
                    r.played.toString(), r.won.toString(), if (showDraws) r.drawn.toString() else null, r.lost.toString(),
                    (if (r.goalDifference > 0) "+" else "") + r.goalDifference, r.points.toString()
                ),
                header = false, highlight = r.team.id == m.home.id || r.team.id == m.away.id
            )
        }
    }
}

@Composable
private fun TableRow(pos: String, team: Team?, name: String, cells: List<String>, header: Boolean, highlight: Boolean) {
    val color = if (header) SportsColors.TextMuted else SportsColors.Text
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) SportsColors.AccentSoft else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = if (header) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(pos, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        if (team != null) {
            TeamCrest(team.colors, crestSize = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            name, color = color, fontSize = 14.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        cells.forEachIndexed { i, c ->
            Text(
                c, color = if (i == cells.lastIndex && !header) SportsColors.Text else color,
                fontSize = 13.sp, fontWeight = if (i == cells.lastIndex) FontWeight.ExtraBold else FontWeight.Normal,
                textAlign = TextAlign.Center, modifier = Modifier.width(30.dp)
            )
        }
    }
}

@Composable
private fun BroadcastBlock(state: DetailUiState, onWatch: (PlaylistChannel) -> Unit, onAssign: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.options.isEmpty() && state.epgChannel == null) {
            Text(stringResource(R.string.sports_no_broadcast), color = SportsColors.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(8.dp))
        }
        state.options.forEach { option -> BroadcastCard(option, onWatch, onAssign) }
        state.epgChannel?.let { channel ->
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(shape, SportsColors.Card)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sports_epg_found), color = SportsColors.Success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(cleanChannelName(channel.name), color = SportsColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                SmallButton(stringResource(R.string.sports_watch), true) { onWatch(channel) }
            }
        }
        Text(
            stringResource(R.string.sports_disclaimer),
            color = SportsColors.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BroadcastCard(option: BroadcastOption, onWatch: (PlaylistChannel) -> Unit, onAssign: (String) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape, SportsColors.Card)
            .padding(16.dp)
            .testTag("broadcast_${option.broadcast.channelName}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LiveTv, null, tint = SportsColors.TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(option.broadcast.channelName, color = SportsColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        val found = option.match
        if (found != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sports_in_your_list, cleanChannelName(found.channel.name)),
                        color = SportsColors.Success, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    val extra = buildList {
                        if (found.reason == MatchReason.MANUAL) add(stringResource(R.string.sports_manual_mapping))
                        if (found.alternates.isNotEmpty()) add(found.alternates.joinToString(", ") { cleanChannelName(it.name) })
                    }
                    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = SportsColors.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(stringResource(R.string.sports_change_channel), false) { onAssign(option.broadcast.channelName) }
                Spacer(Modifier.width(8.dp))
                SmallButton(stringResource(R.string.sports_watch), true) { onWatch(found.channel) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sports_not_in_list), color = SportsColors.Warning, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    option.suggestions.firstOrNull()?.let {
                        Text(stringResource(R.string.sports_similar, cleanChannelName(it.name)), color = SportsColors.TextDim, fontSize = 12.sp)
                    }
                }
                SmallButton(stringResource(R.string.sports_assign_channel), false) { onAssign(option.broadcast.channelName) }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        color = if (filled) SportsColors.OnAccent else SportsColors.AccentText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(SportsColors.Accent) else Modifier.glass(shape, Color.Transparent, SportsColors.Accent.copy(alpha = 0.45f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
