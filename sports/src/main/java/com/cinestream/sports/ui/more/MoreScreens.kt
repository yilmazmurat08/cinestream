package com.cinestream.sports.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.R
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.NotificationEntry
import com.cinestream.sports.model.NotificationType
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.Team
import com.cinestream.sports.settings.LeagueFilterMode
import com.cinestream.sports.settings.SportsSettings
import com.cinestream.sports.ui.FollowedTeamRow
import com.cinestream.sports.ui.components.LocalSportsZone
import com.cinestream.sports.ui.components.SportsBackground
import com.cinestream.sports.ui.components.SportsColors
import com.cinestream.sports.ui.components.TeamCrest
import com.cinestream.sports.ui.components.dayTitle
import com.cinestream.sports.ui.components.formatTime
import com.cinestream.sports.ui.components.glass
import com.cinestream.sports.ui.components.localDate
import com.cinestream.sports.ui.components.shortDateTime
import com.cinestream.sports.ui.components.sportIcon
import com.cinestream.sports.ui.components.sportName
import com.cinestream.sports.ui.components.statusText
import java.time.LocalDate

@Composable
private fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    tag: String,
    actionText: String? = null,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SportsBackground)
            .statusBarsPadding()
            .testTag(tag)
    ) {
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
                    .testTag("screen_back"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sports_back), tint = SportsColors.Text)
            }
            Spacer(Modifier.width(6.dp))
            Text(title, color = SportsColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (actionText != null) {
                Text(
                    actionText,
                    color = SportsColors.AccentText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("screen_action")
                )
            }
        }
        content()
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit, tag: String) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(shape, SportsColors.Surface)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = SportsColors.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = SportsColors.TextDim, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = SportsColors.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(SportsColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(tag)
            )
        }
    }
}

/** Takip edilen takımlar: arayıp yıldızla ekle, sıradaki maçı gör. */
@Composable
internal fun FollowedTeamsScreen(
    rows: List<FollowedTeamRow>,
    query: String,
    results: List<Team>,
    followedIds: Set<String>,
    onQuery: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onOpenMatch: (Match) -> Unit,
    onBack: () -> Unit
) {
    ScreenScaffold(stringResource(R.string.sports_followed_teams), onBack, "followed_screen") {
        SearchField(query, stringResource(R.string.sports_team_search_hint), onQuery, "team_search")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (query.isNotBlank()) {
                items(results, key = { "r_" + it.id }) { team ->
                    TeamCard(team, team.id in followedIds, onToggleFollow, subtitle = team.leagueName.ifEmpty { null }, sport = team.sport)
                }
            } else if (rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.sports_no_followed),
                        color = SportsColors.TextMuted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
            } else {
                items(rows, key = { it.team.id }) { row ->
                    val next = row.next
                    TeamCard(
                        team = row.team,
                        followed = true,
                        onToggleFollow = onToggleFollow,
                        subtitle = next?.let { nextMatchText(row.team, it) } ?: stringResource(R.string.sports_no_next_match),
                        sport = row.team.sport,
                        live = next?.isLive == true,
                        onClick = next?.let { { onOpenMatch(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun nextMatchText(team: Team, m: Match): String {
    val opponent = if (m.home.id == team.id) m.away.name else m.home.name
    return if (m.isLive) {
        stringResource(R.string.sports_live_now, "${statusText(m).orEmpty()} · $opponent")
    } else {
        stringResource(R.string.sports_next_match, shortDateTime(m.kickoffEpochMillis, LocalSportsZone.current) + " · " + opponent)
    }
}

@Composable
private fun TeamCard(
    team: Team,
    followed: Boolean,
    onToggleFollow: (String) -> Unit,
    subtitle: String?,
    sport: Sport,
    live: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .glass(shape, SportsColors.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("team_${team.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamCrest(team.colors, crestSize = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(team.name, color = SportsColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Icon(sportIcon(sport), sportName(sport), tint = SportsColors.TextDim, modifier = Modifier.size(15.dp))
            }
            if (subtitle != null) {
                Text(subtitle, color = if (live) SportsColors.Live else SportsColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onToggleFollow(team.id) }
                .testTag("star_${team.id}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (followed) Icons.Filled.Star else Icons.Filled.StarBorder,
                stringResource(if (followed) R.string.sports_unfollow else R.string.sports_follow),
                tint = if (followed) SportsColors.Gold else SportsColors.TextMuted
            )
        }
    }
}

/** Bildirim geçmişi: maç hatırlatmaları (ileride canlı veri servisiyle gol ve sonuçlar). */
@Composable
internal fun NotificationHistoryScreen(
    items: List<NotificationEntry>,
    today: LocalDate,
    onClear: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit
) {
    val zone = LocalSportsZone.current
    ScreenScaffold(
        stringResource(R.string.sports_notification_history), onBack, "notifications_screen",
        actionText = if (items.isEmpty()) null else stringResource(R.string.sports_clear),
        onAction = onClear
    ) {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.sports_no_notifications),
                color = SportsColors.TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            )
            return@ScreenScaffold
        }
        val groups = items.sortedByDescending { it.timeEpochMillis }.groupBy { localDate(it.timeEpochMillis, zone) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEach { (day, entries) ->
                item(key = "d_$day") {
                    Text(
                        dayTitle(day, today),
                        color = SportsColors.TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
                    )
                }
                items(entries, key = { it.id + it.timeEpochMillis }) { n ->
                    NotificationRow(n, formatTime(n.timeEpochMillis, zone)) { n.matchId?.let(onOpenMatch) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationEntry, time: String, onClick: () -> Unit) {
    val (icon, color) = when (n.type) {
        NotificationType.REMINDER -> Icons.Filled.NotificationsActive to SportsColors.Accent
        NotificationType.KICKOFF -> Icons.Filled.Timer to SportsColors.Cyan
        NotificationType.GOAL -> Icons.Filled.SportsSoccer to SportsColors.Gold
        NotificationType.FULL_TIME -> Icons.Filled.SportsScore to SportsColors.Success
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .glass(shape, SportsColors.Card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = SportsColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.body, color = SportsColors.TextMuted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(time, color = SportsColors.TextDim, fontSize = 13.sp)
    }
}

/** Spor bölümünü özelleştir: sporlar ve sırası, lig görünümü, bildirimler, kanallar. */
@Composable
internal fun CustomizeScreen(
    settings: SportsSettings,
    onSportEnabled: (Sport, Boolean) -> Unit,
    onMoveSport: (Sport, Boolean) -> Unit,
    onLeagueMode: (LeagueFilterMode) -> Unit,
    onUpdate: ((SportsSettings) -> SportsSettings) -> Unit,
    onResetMappings: () -> Unit,
    onBack: () -> Unit
) {
    ScreenScaffold(stringResource(R.string.sports_customize), onBack, "customize_screen") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "sports") {
                Section(stringResource(R.string.sports_customize_sports), stringResource(R.string.sports_customize_sports_desc)) {
                    val ordered = settings.sports + Sport.entries.filter { it !in settings.sports }
                    ordered.forEach { sport ->
                        val enabled = sport in settings.sports
                        val index = settings.sports.indexOf(sport)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("customize_sport_${sport.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(sportIcon(sport), null, tint = if (enabled) SportsColors.Accent else SportsColors.TextDim, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(sportName(sport), color = if (enabled) SportsColors.Text else SportsColors.TextMuted, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (enabled) {
                                SmallIcon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.sports_move_up), index > 0) { onMoveSport(sport, true) }
                                SmallIcon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.sports_move_down), index < settings.sports.lastIndex) { onMoveSport(sport, false) }
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { onSportEnabled(sport, it) },
                                enabled = !(enabled && settings.sports.size == 1),
                                colors = switchColors()
                            )
                        }
                    }
                }
            }
            item(key = "leagues") {
                Section(stringResource(R.string.sports_customize_default_leagues)) {
                    Choice(Icons.Filled.EmojiEvents, stringResource(R.string.sports_major_leagues), settings.leagueMode == LeagueFilterMode.MAJOR) {
                        onLeagueMode(LeagueFilterMode.MAJOR)
                    }
                    Choice(Icons.Filled.AllInclusive, stringResource(R.string.sports_all_matches), settings.leagueMode == LeagueFilterMode.ALL) {
                        onLeagueMode(LeagueFilterMode.ALL)
                    }
                }
            }
            item(key = "notifications") {
                Section(stringResource(R.string.sports_customize_notifications), stringResource(R.string.sports_notify_live_note)) {
                    SwitchLine(stringResource(R.string.sports_notify_reminder), settings.notifyReminder) { v -> onUpdate { it.copy(notifyReminder = v) } }
                    SwitchLine(stringResource(R.string.sports_notify_kickoff), settings.notifyKickoff) { v -> onUpdate { it.copy(notifyKickoff = v) } }
                    SwitchLine(stringResource(R.string.sports_notify_goals), settings.notifyGoals) { v -> onUpdate { it.copy(notifyGoals = v) } }
                    SwitchLine(stringResource(R.string.sports_notify_full_time), settings.notifyFullTime) { v -> onUpdate { it.copy(notifyFullTime = v) } }
                    Text(stringResource(R.string.sports_reminder_time), color = SportsColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30).forEach { minutes ->
                            val selected = settings.reminderMinutes == minutes
                            val shape = RoundedCornerShape(16.dp)
                            Text(
                                stringResource(R.string.sports_reminder_minutes, minutes),
                                color = if (selected) SportsColors.Accent else SportsColors.TextMuted,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(shape)
                                    .glass(shape, if (selected) SportsColors.AccentSoft else Color.Transparent, if (selected) SportsColors.Accent.copy(alpha = 0.5f) else SportsColors.Stroke)
                                    .clickable { onUpdate { it.copy(reminderMinutes = minutes) } }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            item(key = "channels") {
                Section(stringResource(R.string.sports_customize_channels)) {
                    SwitchLine(stringResource(R.string.sports_show_my_channel), settings.showMyChannel) { v -> onUpdate { it.copy(showMyChannel = v) } }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sports_mappings_count, settings.channelMappings.size), color = SportsColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (settings.channelMappings.isNotEmpty()) {
                            Text(
                                stringResource(R.string.sports_reset),
                                color = SportsColors.Live,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onResetMappings)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = SportsColors.OnAccent,
    checkedTrackColor = SportsColors.Accent,
    uncheckedThumbColor = SportsColors.TextMuted,
    uncheckedTrackColor = SportsColors.Surface,
    uncheckedBorderColor = SportsColors.StrokeStrong
)

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape, SportsColors.Card)
            .padding(18.dp)
    ) {
        Text(title, color = SportsColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (description != null) {
            Text(description, color = SportsColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SwitchLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = SportsColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

@Composable
private fun Choice(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) SportsColors.Accent else SportsColors.TextMuted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = SportsColors.Text, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = SportsColors.Accent, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (enabled) SportsColors.Text else SportsColors.TextDim.copy(alpha = 0.5f))
    }
}
