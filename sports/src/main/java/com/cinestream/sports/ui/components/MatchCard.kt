package com.cinestream.sports.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.R
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Team
import com.cinestream.sports.ui.MatchRow

/** Listede bir maç: solda saat ya da dakika, ortada takımlar, sağda skor ve (listende kanalı varsa) izle düğmesi. */
@Composable
internal fun MatchCard(
    row: MatchRow,
    showChannel: Boolean,
    reminderSet: Boolean,
    onClick: () -> Unit,
    onWatch: (PlaylistChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val m = row.match
    val shape = RoundedCornerShape(22.dp)
    val watch = row.watchChannel
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .glass(shape, SportsColors.Card.copy(alpha = 0.94f))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            .testTag("match_${m.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusColumn(m, reminderSet, Modifier.width(62.dp))
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(1.dp)
                .height(46.dp)
                .background(SportsColors.Stroke)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TeamLine(m.home, m.score?.home, m, row.followedTeamIds.contains(m.home.id), isHome = true)
            TeamLine(m.away, m.score?.away, m, row.followedTeamIds.contains(m.away.id), isHome = false)
            if (showChannel && watch != null) ChannelLine(row)
        }
        if (watch != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SportsColors.Accent)
                    .clickable { onWatch(watch) }
                    .testTag("watch_${m.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.sports_watch), tint = SportsColors.OnAccent, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun StatusColumn(m: Match, reminderSet: Boolean, modifier: Modifier) {
    val zone = LocalSportsZone.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (m.status) {
            MatchStatus.LIVE, MatchStatus.HALF_TIME -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(SportsColors.Live))
                    Spacer(Modifier.width(5.dp))
                    Text(statusText(m, short = true).orEmpty(), color = SportsColors.Live, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(stringResource(R.string.sports_live), color = SportsColors.Live.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            MatchStatus.FINISHED, MatchStatus.POSTPONED -> {
                Text(statusText(m).orEmpty(), color = SportsColors.TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(formatTime(m.kickoffEpochMillis, zone), color = SportsColors.TextDim, fontSize = 11.sp)
            }
            MatchStatus.SCHEDULED -> {
                Text(formatTime(m.kickoffEpochMillis, zone), color = SportsColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (reminderSet) {
                    Icon(Icons.Filled.NotificationsActive, stringResource(R.string.sports_reminder_on), tint = SportsColors.Accent, modifier = Modifier.padding(top = 3.dp).size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun TeamLine(team: Team, score: Int?, m: Match, followed: Boolean, isHome: Boolean) {
    val lost = m.status == MatchStatus.FINISHED && m.score != null &&
        (if (isHome) m.score.home < m.score.away else m.score.away < m.score.home)
    val color = if (lost) SportsColors.TextMuted else SportsColors.Text
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(team.colors, crestSize = 24.dp)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                team.name,
                color = color,
                fontSize = 16.sp,
                fontWeight = if (lost) FontWeight.Medium else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (followed) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Star, null, tint = SportsColors.Gold, modifier = Modifier.size(14.dp))
            }
        }
        if (score != null) {
            Text(
                score.toString(),
                color = if (m.isLive) SportsColors.Text else color,
                fontSize = 17.sp,
                fontWeight = if (lost) FontWeight.Medium else FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ChannelLine(row: MatchRow) {
    val channel = row.watchChannel ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LiveTv, null, tint = SportsColors.AccentText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            cleanChannelName(channel.name),
            color = SportsColors.AccentText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        val backups = row.channel?.alternates?.size ?: 0
        if (backups > 0) {
            Text("  ·  " + stringResource(R.string.sports_backups, backups), color = SportsColors.TextDim, fontSize = 12.sp, maxLines = 1)
        }
        if (row.epgVerified) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.CheckCircle, stringResource(R.string.sports_epg_verified), tint = SportsColors.Success, modifier = Modifier.size(13.dp))
        }
    }
}
