package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.api.PersonDetails
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.PersonWorksPhase
import com.example.data.model.tmdb.PersonWorksUiState
import com.example.ui.IPTVViewModel

/**
 * Kişi sayfası: fotoğraf, ad, rol, doğum bilgisi ve biyografi (telefondaki kişi penceresinin bilgileri); altında
 * oynadığı/yönettiği yapımlar 6 sütunlu poster ızgarasında. Kütüphanede olanlar başta ve "Kütüphanende var"
 * etiketli, OK ile detayları açılır; olmayanlar soluk görünür ve açılmaz.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvPersonScreen(
    viewModel: IPTVViewModel,
    person: TvPerson,
    excludeItemId: Int,
    onOpenItem: (IPTVItem) -> Unit
) {
    var works by remember(person.key) {
        mutableStateOf(PersonWorksUiState(query = person.name, phase = PersonWorksPhase.SEARCHING_LIBRARY))
    }
    var details by remember(person.key) { mutableStateOf<PersonDetails?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(person.key) { viewModel.tvPersonWorks(person.name, person.apiRole, excludeItemId).collect { works = it } }
    LaunchedEffect(person.key) { viewModel.getPersonDetailsFlow(person.name, person.apiRole).collect { details = it } }
    LaunchedEffect(person.key, works.inLibrary.isNotEmpty() || works.notInLibrary.isNotEmpty()) { requestFocusWhenReady(firstFocus) }

    val photo = person.photoUrl ?: works.profileUrl ?: details?.imageUrl
    Box(
        Modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .testTag("tv_person_screen")
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(128.dp).clip(CircleShape)) {
                        var failed by remember(photo) { mutableStateOf(photo.isNullOrBlank()) }
                        if (failed) {
                            TvInitials(person.name, Modifier.fillMaxSize())
                        } else {
                            AsyncImage(model = photo, contentDescription = person.name, contentScale = ContentScale.Crop,
                                onError = { failed = true }, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, color = TvTheme.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        val role = if (person.isDirector) stringResource(R.string.tv_director) else person.character
                        if (role != null) Text(role, color = TvTheme.Accent, fontSize = 17.sp)
                        val born = listOfNotNull(details?.birthDate?.takeIf { it.isNotBlank() }, details?.birthPlace?.takeIf { it.isNotBlank() })
                        if (born.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.tv_born, born.joinToString(" · ")), color = TvTheme.TextSecondary, fontSize = 15.sp)
                        }
                        details?.biography?.takeIf { it.isNotBlank() }?.let { bio ->
                            Spacer(Modifier.height(8.dp))
                            Text(bio, color = TvTheme.TextPrimary.copy(alpha = 0.85f), fontSize = 16.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.tv_person_works), color = TvTheme.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    if (works.phase != PersonWorksPhase.DONE) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(color = TvTheme.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }
            // Kütüphanede olanlar başta
            itemsIndexed(works.inLibrary, key = { _, m -> "in_" + m.uiKey }) { index, match ->
                val year = match.credit?.year?.toString()
                TvPosterCard(
                    title = match.item.cleanedName.ifBlank { match.item.name },
                    posterUrl = match.item.logoUrl ?: match.credit?.posterUrl,
                    inLibrary = true,
                    subtitle = year,
                    focusRequester = if (index == 0) firstFocus else null,
                    onClick = { onOpenItem(match.item) },
                    tag = "tv_work_in_${match.item.id}"
                )
            }
            itemsIndexed(works.notInLibrary, key = { _, c -> "out_" + c.key }) { index, credit ->
                TvPosterCard(
                    title = credit.title.ifBlank { credit.originalTitle },
                    posterUrl = credit.posterUrl,
                    dimmed = true,
                    subtitle = credit.year?.toString(),
                    focusRequester = if (index == 0 && works.inLibrary.isEmpty()) firstFocus else null,
                    onClick = {}, // kütüphanede yok: açılmaz
                    tag = "tv_work_out_${credit.key}"
                )
            }
            if (works.phase == PersonWorksPhase.DONE && works.inLibrary.isEmpty() && works.notInLibrary.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.tv_person_no_works), color = TvTheme.TextSecondary, fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                }
            }
        }
    }
}
