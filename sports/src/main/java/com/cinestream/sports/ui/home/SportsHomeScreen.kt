package com.cinestream.sports.ui.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.R
import com.cinestream.sports.model.League
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.model.Sport
import com.cinestream.sports.ui.MatchRow
import com.cinestream.sports.ui.SportsUiState
import com.cinestream.sports.ui.components.MatchCard
import com.cinestream.sports.ui.components.SportsBackground
import com.cinestream.sports.ui.components.SportsColors
import com.cinestream.sports.ui.components.dayTitle
import com.cinestream.sports.ui.components.daySubtitle
import com.cinestream.sports.ui.components.glass
import com.cinestream.sports.ui.components.sportIcon
import com.cinestream.sports.ui.components.sportName
import java.time.LocalDate

/** Ana spor ekranı: spor seçici, arama/filtre/menü, gün seçici ve lige göre maçlar. */
@Composable
internal fun SportsHomeScreen(
    state: SportsUiState,
    onSelectSport: (Sport) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenMatch: (Match) -> Unit,
    onWatch: (PlaylistChannel) -> Unit,
    onShowAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(SportsBackground)
            .testTag("sports_home")
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding())
            if (state.searchActive) {
                SearchBar(state.query, onQueryChange, onCloseSearch)
            } else {
                TopBar(state, onSelectSport, onOpenSearch, onOpenFilters, onOpenMore)
            }
            DayPager(state, onSelectDay, onPreviousDay, onNextDay)
            Box(
                Modifier
                    .fillMaxSize()
                    .pitchLines()
            ) {
                when {
                    state.loading -> CircularProgressIndicator(color = SportsColors.Accent, modifier = Modifier.align(Alignment.Center))
                    state.error -> CenterMessage(stringResource(R.string.sports_load_error), stringResource(R.string.sports_retry), onRetry)
                    state.isEmpty -> EmptyState(state, onShowAll)
                    else -> MatchList(state, onOpenMatch, onWatch, onShowAll)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    state: SportsUiState,
    onSelectSport: (Sport) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenMore: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pill = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .glass(pill, SportsColors.Surface.copy(alpha = 0.85f))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            state.sports.forEach { sport ->
                val selected = sport == state.sport
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (selected) SportsColors.AccentSoft else Color.Transparent)
                        .clickable { onSelectSport(sport) }
                        .testTag("sport_${sport.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        sportIcon(sport),
                        contentDescription = sportName(sport),
                        tint = if (selected) SportsColors.Accent else SportsColors.TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .glass(pill, SportsColors.Surface.copy(alpha = 0.85f))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BarIcon(Icons.Filled.Search, stringResource(R.string.sports_search), "action_search", onClick = onOpenSearch)
            BarIcon(Icons.Filled.Tune, stringResource(R.string.sports_filters), "action_filters", badge = state.filterActive, onClick = onOpenFilters)
            BarIcon(Icons.Filled.MoreVert, stringResource(R.string.sports_more), "action_more", onClick = onOpenMore)
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, tag: String, badge: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = SportsColors.Text, modifier = Modifier.size(24.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 9.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SportsColors.Accent)
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val pill = RoundedCornerShape(30.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(pill, SportsColors.Surface.copy(alpha = 0.95f), SportsColors.Accent.copy(alpha = 0.5f))
            .padding(start = 18.dp, end = 6.dp)
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = SportsColors.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.sports_search_hint), color = SportsColors.TextDim, fontSize = 17.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = SportsColors.Text, fontSize = 17.sp),
                cursorBrush = SolidColor(SportsColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .testTag("search_field")
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .testTag("search_close"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.sports_close_search), tint = SportsColors.Text)
        }
    }
}

@Composable
private fun DayPager(
    state: SportsUiState,
    onSelectDay: (LocalDate) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val pill = RoundedCornerShape(34.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glass(pill, SportsColors.Surface.copy(alpha = 0.85f))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArrowButton(Icons.Filled.ChevronLeft, stringResource(R.string.sports_prev_day), state.day > state.minDay, "day_prev", onPreviousDay)
        listOf(state.day.minusDays(1), state.day, state.day.plusDays(1)).forEach { day ->
            val inRange = !day.isBefore(state.minDay) && !day.isAfter(state.maxDay)
            DayCell(
                day = day,
                today = state.today,
                selected = day == state.day,
                visible = inRange,
                onClick = { onSelectDay(day) },
                modifier = Modifier.weight(1f)
            )
        }
        ArrowButton(Icons.Filled.ChevronRight, stringResource(R.string.sports_next_day), state.day < state.maxDay, "day_next", onNextDay)
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 40.dp, height = 56.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (enabled) SportsColors.Text else SportsColors.TextDim, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, selected: Boolean, visible: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) SportsColors.AccentSoft else Color.Transparent)
            .clickable(enabled = visible && !selected, onClick = onClick)
            .testTag("day_$day"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (visible) {
            Text(
                dayTitle(day, today),
                color = if (selected) SportsColors.AccentText else SportsColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                daySubtitle(day),
                color = if (selected) SportsColors.Accent else SportsColors.Text.copy(alpha = 0.75f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MatchList(
    state: SportsUiState,
    onOpenMatch: (Match) -> Unit,
    onWatch: (PlaylistChannel) -> Unit,
    onShowAll: () -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("match_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.followedRows.isNotEmpty()) {
            item(key = "followed_header") { FollowedHeader() }
            items(state.followedRows, key = { "f_" + it.match.id }) { row -> Card(row, state, onOpenMatch, onWatch) }
        }
        state.sections.forEach { section ->
            item(key = "h_" + section.league.id) { LeagueHeader(section.league) }
            items(section.rows, key = { it.match.id }) { row -> Card(row, state, onOpenMatch, onWatch) }
        }
        item(key = "footer") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.hiddenByLeagueFilter > 0) {
                    PillButton(stringResource(R.string.sports_show_all, state.hiddenByLeagueFilter), "show_all", onShowAll)
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    stringResource(R.string.sports_disclaimer),
                    color = SportsColors.TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun Card(row: MatchRow, state: SportsUiState, onOpenMatch: (Match) -> Unit, onWatch: (PlaylistChannel) -> Unit) {
    MatchCard(
        row = row,
        showChannel = state.showMyChannel,
        reminderSet = row.match.id in state.reminderIds,
        onClick = { onOpenMatch(row.match) },
        onWatch = onWatch
    )
}

@Composable
private fun FollowedHeader() {
    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = SportsColors.Gold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.sports_followed_section), color = SportsColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun LeagueHeader(league: League) {
    Row(
        Modifier
            .padding(top = 14.dp, bottom = 2.dp)
            .testTag("league_${league.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SportsColors.Accent)
        )
        Spacer(Modifier.width(12.dp))
        Text(league.name, color = SportsColors.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(league.country.name, color = SportsColors.TextMuted, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun EmptyState(state: SportsUiState, onShowAll: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.EventBusy, null, tint = SportsColors.TextDim, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        val text = when {
            state.searchActive && state.query.isNotBlank() -> stringResource(R.string.sports_no_results, state.query.trim())
            state.hiddenByLeagueFilter > 0 -> stringResource(R.string.sports_no_matches_filtered)
            else -> stringResource(R.string.sports_no_matches)
        }
        Text(text, color = SportsColors.TextMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
        if (state.hiddenByLeagueFilter > 0 && !state.searchActive) {
            Spacer(Modifier.height(18.dp))
            PillButton(stringResource(R.string.sports_show_all, state.hiddenByLeagueFilter), "show_all", onShowAll)
        }
    }
}

@Composable
private fun CenterMessage(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = SportsColors.TextMuted, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PillButton(action, "retry", onAction)
    }
}

@Composable
internal fun PillButton(text: String, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .clip(shape)
            .glass(shape, SportsColors.AccentSoft, SportsColors.Accent.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
            .testTag(tag)
    ) {
        Text(text, color = SportsColors.AccentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Liste arkasında silik saha çizgileri. */
private fun Modifier.pitchLines(): Modifier = drawBehind {
    val line = Color.White.copy(alpha = 0.035f)
    val stroke = Stroke(width = 2.dp.toPx())
    val inset = 24.dp.toPx()
    val w = size.width - inset * 2
    val h = size.height - inset
    drawRect(line, Offset(inset, 0f), Size(w, h), style = stroke)
    drawLine(line, Offset(inset, h / 2f), Offset(inset + w, h / 2f), strokeWidth = stroke.width)
    drawCircle(line, radius = w * 0.18f, center = Offset(size.width / 2f, h / 2f), style = stroke)
    val boxW = w * 0.56f
    val boxH = h * 0.12f
    drawRect(line, Offset((size.width - boxW) / 2f, 0f), Size(boxW, boxH), style = stroke)
    drawRect(line, Offset((size.width - boxW) / 2f, h - boxH), Size(boxW, boxH), style = stroke)
}
