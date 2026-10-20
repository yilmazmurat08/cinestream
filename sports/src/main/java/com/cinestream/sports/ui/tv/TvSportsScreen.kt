package com.cinestream.sports.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.R
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import com.cinestream.sports.ui.MatchRow
import com.cinestream.sports.ui.SportsUiState
import com.cinestream.sports.ui.components.LocalSportsZone
import com.cinestream.sports.ui.components.SportsBackground
import com.cinestream.sports.ui.components.SportsColors
import com.cinestream.sports.ui.components.TeamCrest
import com.cinestream.sports.ui.components.cleanChannelName
import com.cinestream.sports.ui.components.dayTitle
import com.cinestream.sports.ui.components.daySubtitle
import com.cinestream.sports.ui.components.formatTime
import com.cinestream.sports.ui.components.glass
import com.cinestream.sports.ui.components.sportIcon
import com.cinestream.sports.ui.components.sportName
import com.cinestream.sports.ui.components.statusText
import java.time.LocalDate

/**
 * TV düzeni: solda spor ve menü, ortada gün ve maç listesi, sağda odaktaki maçın ayrıntısı. Kumandayla gezilir;
 * OK izler (kanal listedeyse) ya da ayrıntıyı açar. Telefondaki tüm işlevlere menüden ulaşılır.
 */
@Composable
internal fun TvSportsScreen(
    state: SportsUiState,
    onSelectSport: (Sport) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenFollowed: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenMatch: (Match) -> Unit,
    onWatch: (PlaylistChannel) -> Unit,
    onToggleReminder: (Match) -> Unit,
    initialFocusMatchId: String? = null
) {
    val rows = remember(state.followedRows, state.sections) { state.sections.flatMap { it.rows } }
    var focusedId by remember { mutableStateOf(initialFocusMatchId) }
    val focused = rows.firstOrNull { it.match.id == focusedId } ?: rows.firstOrNull()
    Row(
        Modifier
            .fillMaxSize()
            .background(SportsBackground)
            .testTag("tv_sports")
    ) {
        Column(
            Modifier
                .width(206.dp)
                .fillMaxHeight()
                .background(SportsColors.Surface.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(R.string.sports_title),
                color = SportsColors.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            state.sports.forEach { sport ->
                RailItem(sportIcon(sport), sportName(sport), selected = sport == state.sport, tag = "tv_sport_${sport.id}") { onSelectSport(sport) }
            }
            Spacer(Modifier.weight(1f))
            RailItem(Icons.Filled.Tune, stringResource(R.string.sports_filters), badge = state.filterActive, tag = "tv_filters", onClick = onOpenFilters)
            RailItem(Icons.Filled.Star, stringResource(R.string.sports_followed_teams), tag = "tv_followed", onClick = onOpenFollowed)
            RailItem(Icons.Filled.Notifications, stringResource(R.string.sports_notification_history), tag = "tv_notifications", onClick = onOpenNotifications)
            RailItem(Icons.Filled.Settings, stringResource(R.string.sports_customize), tag = "tv_customize", onClick = onOpenCustomize)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp, end = 12.dp, top = 22.dp)
        ) {
            DayRow(state, onSelectDay)
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.sections.forEach { section ->
                    item(key = "h_" + section.league.id) {
                        Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(width = 4.dp, height = 20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SportsColors.Accent)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(section.league.name, color = SportsColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(8.dp))
                            Text(section.league.country.name, color = SportsColors.TextMuted, fontSize = 14.sp)
                        }
                    }
                    items(section.rows, key = { it.match.id }) { row ->
                        TvMatchRow(
                            row = row,
                            requestFocus = row.match.id == initialFocusMatchId,
                            onFocused = { focusedId = row.match.id },
                            onClick = { row.watchChannel?.let(onWatch) ?: onOpenMatch(row.match) }
                        )
                    }
                }
                if (state.sections.isEmpty() && !state.loading) {
                    item {
                        Text(
                            stringResource(if (state.hiddenByLeagueFilter > 0) R.string.sports_no_matches_filtered else R.string.sports_no_matches),
                            color = SportsColors.TextMuted,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
        if (focused != null) {
            DetailPanel(focused, onWatch, onOpenMatch, onToggleReminder, reminderSet = focused.match.id in state.reminderIds)
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, text: String, selected: Boolean = false, badge: Boolean = false, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> SportsColors.Accent
                    selected -> SportsColors.AccentSoft
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when {
            focused -> SportsColors.OnAccent
            selected -> SportsColors.Accent
            else -> SportsColors.TextMuted
        }
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            color = if (focused) SportsColors.OnAccent else if (selected) SportsColors.Text else SportsColors.TextMuted,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge) Box(Modifier.size(8.dp).clip(CircleShape).background(if (focused) SportsColors.OnAccent else SportsColors.Accent))
    }
}

@Composable
private fun DayRow(state: SportsUiState, onSelectDay: (LocalDate) -> Unit) {
    val days = remember(state.minDay, state.maxDay) {
        generateSequence(state.minDay) { it.plusDays(1) }.takeWhile { !it.isAfter(state.maxDay) }.toList()
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (days.indexOf(state.day) - 2).coerceAtLeast(0))
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it.toEpochDay() }) { day ->
            var focused by remember { mutableStateOf(false) }
            val selected = day == state.day
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .width(108.dp)
                    .clip(shape)
                    .background(
                        when {
                            focused -> SportsColors.Accent
                            selected -> SportsColors.AccentSoft
                            else -> SportsColors.Surface.copy(alpha = 0.6f)
                        }
                    )
                    .border(1.dp, if (selected && !focused) SportsColors.Accent.copy(alpha = 0.6f) else SportsColors.Stroke, shape)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelectDay(day) }
                    .padding(vertical = 9.dp)
                    .testTag("tv_day_$day"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val top = if (focused) SportsColors.OnAccent else if (selected) SportsColors.AccentText else SportsColors.TextMuted
                val bottom = if (focused) SportsColors.OnAccent else if (selected) SportsColors.Accent else SportsColors.Text
                Text(dayTitle(day, state.today), color = top, fontSize = 12.sp, maxLines = 1)
                Text(daySubtitle(day), color = bottom, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TvMatchRow(row: MatchRow, requestFocus: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val m = row.match
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    }
    val shape = RoundedCornerShape(16.dp)
    val zone = LocalSportsZone.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(if (focused) SportsColors.AccentSoft else SportsColors.Card.copy(alpha = 0.9f))
            .border(if (focused) 3.dp else 1.dp, if (focused) SportsColors.Accent else SportsColors.Stroke, shape)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag("tv_match_${m.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(54.dp)) {
            val status = statusText(m, short = true)
            Text(
                status ?: formatTime(m.kickoffEpochMillis, zone),
                color = when {
                    m.isLive -> SportsColors.Live
                    m.status == MatchStatus.FINISHED -> SportsColors.TextMuted
                    else -> SportsColors.Text
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text(m.home.name, color = SportsColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            TeamCrest(m.home.colors, crestSize = 22.dp)
        }
        Text(
            m.score?.let { "${it.home} - ${it.away}" } ?: "-",
            color = if (m.isLive) SportsColors.Live else SportsColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(58.dp)
        )
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(m.away.colors, crestSize = 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(m.away.name, color = SportsColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
            if (row.watchChannel != null) Icon(Icons.Filled.LiveTv, null, tint = SportsColors.AccentText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DetailPanel(
    row: MatchRow,
    onWatch: (PlaylistChannel) -> Unit,
    onOpenMatch: (Match) -> Unit,
    onToggleReminder: (Match) -> Unit,
    reminderSet: Boolean
) {
    val m = row.match
    val zone = LocalSportsZone.current
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(SportsColors.Surface.copy(alpha = 0.7f))
            .padding(22.dp)
            .testTag("tv_panel"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(m.league.name, color = SportsColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(m.home.colors, crestSize = 58.dp)
            Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    m.score?.let { "${it.home} - ${it.away}" } ?: formatTime(m.kickoffEpochMillis, zone),
                    color = SportsColors.Text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                statusText(m)?.let { Text(it, color = if (m.isLive) SportsColors.Live else SportsColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            TeamCrest(m.away.colors, crestSize = 58.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text("${m.home.name}\n${m.away.name}", color = SportsColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        val channel = row.watchChannel
        val info = when {
            channel != null -> stringResource(R.string.sports_in_your_list, cleanChannelName(channel.name))
            m.broadcasts.isNotEmpty() -> m.broadcasts.first().channelName + " · " + stringResource(R.string.sports_not_in_list)
            else -> stringResource(R.string.sports_no_broadcast)
        }
        Text(info, color = if (channel != null) SportsColors.Success else SportsColors.TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (channel != null) PanelButton(Icons.Filled.PlayArrow, stringResource(R.string.sports_watch), true, "tv_panel_watch") { onWatch(channel) }
        PanelButton(Icons.Filled.Info, stringResource(R.string.sports_details), false, "tv_panel_details") { onOpenMatch(m) }
        if (m.status == MatchStatus.SCHEDULED) {
            PanelButton(
                if (reminderSet) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                stringResource(if (reminderSet) R.string.sports_reminder_on else R.string.sports_remind),
                false,
                "tv_panel_remind"
            ) { onToggleReminder(m) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.sports_tv_ok_watch),
            color = SportsColors.TextDim,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PanelButton(icon: ImageVector, text: String, primary: Boolean, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> SportsColors.Accent
                    primary -> SportsColors.AccentSoft
                    else -> SportsColors.Card
                }
            )
            .border(1.dp, if (focused) SportsColors.Accent else SportsColors.Stroke, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (focused) SportsColors.OnAccent else if (primary) SportsColors.AccentText else SportsColors.Text
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
