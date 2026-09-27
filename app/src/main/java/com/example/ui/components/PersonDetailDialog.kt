package com.example.ui.components
import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.api.PersonDetails
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonWorkMatch
import com.example.data.model.tmdb.PersonWorksPhase
import com.example.data.model.tmdb.PersonWorksUiState
import com.example.ui.IPTVViewModel
import com.example.ui.theme.BrokenWhite
import com.example.ui.theme.CineOrange
import com.example.ui.theme.CineRed
import com.example.ui.theme.LiveGold
import com.example.ui.theme.PalePurpleText
import com.example.ui.theme.SlateGray

private val DialogSurface = Color(0xFF160E1E)
private val RolePurple = Color(0xFFA855F7)
private val RolePurpleText = Color(0xFFC084FC)

/**
 * Oyuncu/yönetmen penceresi. Kişiye dokununca uygulama kendi kütüphanesinde arama yapar:
 * arama çubuğunda kişinin adı ve o anki adım görünür, sonuçlar geldikçe listelenir.
 */
@Composable
fun PersonDetailDialog(
    personName: String,
    role: String,
    onDismiss: () -> Unit,
    viewModel: IPTVViewModel? = null,
    excludeItemId: Int = -1,
    onItemClick: (IPTVItem) -> Unit = {}
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    var retryToken by remember(personName) { mutableIntStateOf(0) }
    var searchState by remember(personName) {
        mutableStateOf(PersonWorksUiState(query = personName, phase = PersonWorksPhase.SEARCHING_LIBRARY))
    }
    var personDetails by remember(personName) { mutableStateOf<PersonDetails?>(null) }

    LaunchedEffect(personName, retryToken) {
        if (viewModel == null) {
            searchState = searchState.copy(phase = PersonWorksPhase.DONE)
            return@LaunchedEffect
        }
        viewModel.searchPersonWorks(personName, role, excludeItemId).collect { searchState = it }
    }

    LaunchedEffect(personName) {
        personDetails = null
        viewModel?.getPersonDetailsFlow(personName, role)?.collect { personDetails = it }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DialogSurface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = layout.dialogMaxWidth)
                .heightIn(max = if (layout.isLandscape) 420.dp else 620.dp)
                .wrapContentHeight()
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(listOf(CineOrange.copy(alpha = 0.4f), Color.Transparent)),
                    shape = RoundedCornerShape(28.dp)
                )
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .testTag("person_detail_dialog")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PersonHeader(
                        name = personName,
                        role = role,
                        photoUrl = searchState.profileUrl ?: personDetails?.imageUrl
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PersonSearchBar(state = searchState)
                    SearchStatus(state = searchState, onRetry = { retryToken++ })
                    Spacer(modifier = Modifier.height(12.dp))
                    PersonWorksGrid(
                        state = searchState,
                        onItemClick = { item ->
                            onItemClick(item)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onDismiss, onClickLabel = stringResource(R.string.action_close))
                        .testTag("close_person_dialog"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(name: String, role: String, photoUrl: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(CineOrange, CineRed))),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                SafeAsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = BrokenWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RolePurple.copy(alpha = 0.15f))
                    .border(0.8.dp, RolePurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = role.uppercase(),
                    color = RolePurpleText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** Uygulamanın arama kutusu gibi görünen, aranan adı ve aramanın sürdüğünü gösteren çubuk. */
@Composable
private fun PersonSearchBar(state: PersonWorksUiState) {
    val searching = state.phase != PersonWorksPhase.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = CineOrange.copy(alpha = if (searching) 0.6f else 0.22f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("person_search_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = CineOrange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = state.query,
            color = BrokenWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (searching) {
            CircularProgressIndicator(
                color = CineOrange,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
        } else if (state.inLibrary.isNotEmpty()) {
            Text(
                text = stringResource(R.string.person_result_count, state.inLibrary.size),
                color = PalePurpleText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SearchStatus(state: PersonWorksUiState, onRetry: () -> Unit) {
    val text = when (state.phase) {
        PersonWorksPhase.SEARCHING_LIBRARY -> stringResource(R.string.person_status_library)
        PersonWorksPhase.FETCHING_TMDB -> stringResource(R.string.person_status_tmdb)
        PersonWorksPhase.MATCHING -> stringResource(R.string.person_status_matching, state.tmdbCreditCount ?: 0)
        PersonWorksPhase.DONE -> null
    }
    if (text != null) {
        Text(
            text = text,
            color = PalePurpleText,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
        )
    }

    val problem = state.tmdbProblem
    if (state.phase == PersonWorksPhase.DONE && problem != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.person_tmdb_unreachable, problem),
                color = LiveGold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(text = "Tekrar dene", color = CineOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PersonWorksGrid(
    state: PersonWorksUiState,
    onItemClick: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val searching = state.phase != PersonWorksPhase.DONE
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.testTag("person_works_grid")
    ) {
        if (state.inLibrary.isNotEmpty()) {
            item(key = "header_in_library", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.person_in_library))
            }
            items(state.inLibrary, key = { it.uiKey }) { match ->
                LibraryWorkCard(match = match, onClick = { onItemClick(match.item) })
            }
        }

        if (searching && state.inLibrary.isEmpty()) {
            items(6, key = { "skeleton_$it" }) { PosterSkeleton() }
        }

        if (state.notInLibrary.isNotEmpty()) {
            item(key = "header_not_in_library", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(stringResource(R.string.person_not_in_library))
            }
            items(state.notInLibrary, key = { "tmdb_${it.key}" }) { credit ->
                MissingWorkCard(credit = credit)
            }
        }

        if (!searching && state.inLibrary.isEmpty() && state.notInLibrary.isEmpty()) {
            item(key = "empty_state", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = SlateGray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.person_no_other_works),
                        color = SlateGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = BrokenWhite,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun PosterBox(url: String?, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            SafeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MovieFilter,
                contentDescription = null,
                tint = SlateGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun workSubtitle(isSeries: Boolean, year: Int?): String? = when {
    isSeries && year != null -> stringResource(R.string.person_series_year, year)
    isSeries -> stringResource(R.string.person_series)
    year != null -> year.toString()
    else -> null
}

@Composable
private fun LibraryWorkCard(match: PersonWorkMatch, onClick: () -> Unit) {
    val item = match.item
    val title = match.credit?.title ?: item.cleanedName
    val poster = item.logoUrl?.takeIf { it.isNotBlank() } ?: match.credit?.posterUrl
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        PosterBox(url = poster, contentDescription = title)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = BrokenWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp
        )
        workSubtitle(item.type == "SERIES", match.credit?.year)?.let { subtitle ->
            Text(text = subtitle, color = SlateGray, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MissingWorkCard(credit: PersonCredit) {
    Column(modifier = Modifier.alpha(0.45f)) {
        PosterBox(url = credit.posterUrl, contentDescription = credit.title)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = credit.title,
            color = BrokenWhite,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp
        )
        workSubtitle(credit.mediaType == "tv", credit.year)?.let { subtitle ->
            Text(text = subtitle, color = SlateGray, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PosterSkeleton() {
    val transition = rememberInfiniteTransition(label = "poster_skeleton")
    val shade by transition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.11f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 850), repeatMode = RepeatMode.Reverse),
        label = "poster_skeleton_shade"
    )
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = shade))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(9.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = shade))
        )
    }
}
