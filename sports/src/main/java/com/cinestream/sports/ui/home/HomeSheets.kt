package com.cinestream.sports.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.R
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.settings.LeagueFilterMode
import com.cinestream.sports.ui.SportsUiState
import com.cinestream.sports.ui.components.SheetDivider
import com.cinestream.sports.ui.components.SheetRow
import com.cinestream.sports.ui.components.SheetSectionLabel
import com.cinestream.sports.ui.components.SportsColors
import com.cinestream.sports.ui.components.glass

@Composable
private fun sheetCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = SportsColors.Accent,
    uncheckedColor = SportsColors.TextMuted,
    checkmarkColor = SportsColors.OnAccent
)

/** Filtreler: yalnızca canlı, yalnızca listemdeki kanallar, büyük ligler / tüm maçlar / seçilen ligler. */
@Composable
internal fun ColumnScope.FilterSheetContent(
    state: SportsUiState,
    onClose: () -> Unit,
    onLiveOnly: (Boolean) -> Unit,
    onMyChannelsOnly: (Boolean) -> Unit,
    onLeagueMode: (LeagueFilterMode) -> Unit,
    onToggleLeague: (String) -> Unit
) {
    SheetHeader(Icons.Filled.Tune, stringResource(R.string.sports_filters), onClose)
    SheetDivider()
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
    ) {
        SheetRow(onClick = { onLiveOnly(!state.liveOnly) }, modifier = Modifier.testTag("filter_live"), verticalPadding = 4.dp) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (state.liveOnly) SportsColors.Live else SportsColors.TextMuted)
                )
            }
            Spacer(Modifier.width(18.dp))
            Text(stringResource(R.string.sports_live_only), color = SportsColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Checkbox(state.liveOnly, onCheckedChange = onLiveOnly, colors = sheetCheckboxColors())
        }
        SheetRow(onClick = { onMyChannelsOnly(!state.myChannelsOnly) }, modifier = Modifier.testTag("filter_my_channels"), verticalPadding = 4.dp) {
            Icon(Icons.Filled.LiveTv, null, tint = SportsColors.AccentText, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sports_my_channels_only), color = SportsColors.Text, fontSize = 18.sp)
                Text(stringResource(R.string.sports_my_channels_only_desc), color = SportsColors.TextMuted, fontSize = 13.sp)
            }
            Checkbox(state.myChannelsOnly, onCheckedChange = onMyChannelsOnly, colors = sheetCheckboxColors())
        }
        SheetSectionLabel(stringResource(R.string.sports_leagues))
        ChoiceRow(Icons.Filled.EmojiEvents, stringResource(R.string.sports_major_leagues), state.leagueMode == LeagueFilterMode.MAJOR, "filter_major") {
            onLeagueMode(LeagueFilterMode.MAJOR)
        }
        ChoiceRow(Icons.Filled.AllInclusive, stringResource(R.string.sports_all_matches), state.leagueMode == LeagueFilterMode.ALL, "filter_all") {
            onLeagueMode(LeagueFilterMode.ALL)
        }
        Text(
            stringResource(R.string.sports_pick_leagues),
            color = SportsColors.TextMuted,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp)
        )
        state.leagueGroups.forEach { group ->
            Text(
                group.country.name,
                color = SportsColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 2.dp)
            )
            group.leagues.forEach { lc ->
                SheetRow(onClick = { onToggleLeague(lc.league.id) }, modifier = Modifier.testTag("filter_league_${lc.league.id}"), verticalPadding = 0.dp) {
                    Checkbox(lc.selected, onCheckedChange = { onToggleLeague(lc.league.id) }, colors = sheetCheckboxColors())
                    Spacer(Modifier.width(10.dp))
                    Text(lc.league.name, color = SportsColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(lc.count.toString(), color = SportsColors.TextMuted, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SheetHeader(icon: ImageVector, title: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .background(SportsColors.AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SportsColors.Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(18.dp))
        Text(title, color = SportsColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .testTag("sheet_close"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.sports_close), tint = SportsColors.TextMuted, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    SheetRow(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, null, tint = if (selected) SportsColors.Accent else SportsColors.TextMuted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            text,
            color = SportsColors.Text,
            fontSize = 19.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = SportsColors.Accent, modifier = Modifier.size(26.dp))
    }
}

/** "Diğer seçenekler": bildirim geçmişi, takip edilen takımlar, spor bölümünü özelleştir. */
@Composable
internal fun MoreOptionsContent(
    onNotifications: () -> Unit,
    onFollowed: () -> Unit,
    onCustomize: () -> Unit
) {
    Text(
        stringResource(R.string.sports_more),
        color = SportsColors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 18.dp)
    )
    SheetDivider()
    OptionRow(Icons.Filled.Notifications, stringResource(R.string.sports_notification_history), "more_notifications", onNotifications)
    OptionRow(Icons.Filled.Star, stringResource(R.string.sports_followed_teams), "more_followed", onFollowed)
    OptionRow(Icons.Filled.Tune, stringResource(R.string.sports_customize), "more_customize", onCustomize)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun OptionRow(icon: ImageVector, text: String, tag: String, onClick: () -> Unit) {
    SheetRow(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, null, tint = SportsColors.Text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(22.dp))
        Text(text, color = SportsColors.Text, fontSize = 19.sp)
    }
}

/** Yayıncı kanalı listede bulunamadığında kullanıcı kendi kanalını seçer; seçim kalıcıdır. */
@Composable
internal fun AssignChannelContent(
    broadcaster: String,
    hasMapping: Boolean,
    search: (String) -> List<Pair<PlaylistChannel, Boolean>>,
    onPick: (PlaylistChannel) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, broadcaster) { search(query) }
    SheetHeader(Icons.Filled.LiveTv, stringResource(R.string.sports_assign_channel), onClose)
    Text(
        stringResource(R.string.sports_assign_text, broadcaster),
        color = SportsColors.TextMuted,
        fontSize = 15.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .glass(shape, SportsColors.Background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = SportsColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.sports_assign_search), color = SportsColors.TextDim, fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = SportsColors.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(SportsColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assign_search")
            )
        }
    }
    LazyColumn(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .heightIn(max = 360.dp)
    ) {
        if (results.isEmpty()) {
            item { Text(stringResource(R.string.sports_no_channels), color = SportsColors.TextMuted, modifier = Modifier.padding(24.dp)) }
        }
        items(results, key = { it.first.id }) { (channel, similar) ->
            SheetRow(onClick = { onPick(channel) }, modifier = Modifier.testTag("assign_${channel.id}")) {
                Icon(Icons.Filled.LiveTv, null, tint = SportsColors.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    channel.name,
                    color = SportsColors.Text,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (similar) {
                    Text(
                        stringResource(R.string.sports_suggested),
                        color = SportsColors.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SportsColors.Cyan.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
    if (hasMapping) {
        SheetRow(onClick = onRemove, modifier = Modifier.testTag("assign_remove")) {
            Text(stringResource(R.string.sports_remove_mapping), color = SportsColors.Live, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(12.dp))
}
