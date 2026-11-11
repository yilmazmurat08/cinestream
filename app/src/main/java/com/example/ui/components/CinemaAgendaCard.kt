package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.repository.AgendaEntry
import com.example.data.repository.AgendaKind
import com.example.data.repository.CinemaAgendaRepository
import com.example.ui.theme.CineOrange

/**
 * Ana sayfa "Sinema Gündemi" kartı: her satır küçük afiş, tek cümlelik bilgi ve rozet. Satıra dokununca yapım
 * kullanıcının listesindeyse kartı, değilse TMDB bilgisiyle detayı açılır.
 */
@Composable
fun CinemaAgendaCard(entries: List<AgendaEntry>, onOpen: (AgendaEntry) -> Unit, modifier: Modifier = Modifier) {
    val language = com.example.util.LocaleHelper.getSavedLanguage(LocalContext.current)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(onSurface.copy(alpha = 0.05f))
            .border(1.dp, onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("cinema_agenda_card")
    ) {
        Text(stringResource(R.string.agenda_label), color = Color(0xFFD4A853), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text(
            stringResource(R.string.agenda_title), color = onSurface, fontSize = 20.sp,
            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
        )
        Text(stringResource(R.string.agenda_subtitle), color = onSurface.copy(alpha = 0.55f), fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                val line = entry.line
                val date = CinemaAgendaRepository.displayDate(line.date, language)
                val text = when (line.kind) {
                    AgendaKind.NOW_PLAYING -> stringResource(R.string.agenda_now_playing, line.title)
                    AgendaKind.TRENDING -> stringResource(R.string.agenda_trending, line.title)
                    AgendaKind.UPCOMING ->
                        if (date != null) stringResource(R.string.agenda_upcoming, line.title, date) else stringResource(R.string.agenda_upcoming_no_date, line.title)
                    AgendaKind.NEW_SEASON ->
                        if (date != null && line.season != null) stringResource(R.string.agenda_new_season, line.title, line.season, date)
                        else stringResource(R.string.agenda_trending, line.title)
                    AgendaKind.SEASON_STARTED ->
                        if (line.season != null) stringResource(R.string.agenda_season_started, line.title, line.season) else stringResource(R.string.agenda_trending, line.title)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(onSurface.copy(alpha = 0.04f))
                        .clickable { onOpen(entry) }
                        .padding(8.dp)
                        .testTag("agenda_row_${line.kind.name}")
                ) {
                    AsyncImage(
                        model = entry.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 40.dp, height = 58.dp).clip(RoundedCornerShape(7.dp)).background(onSurface.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text, color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (entry.libraryItem != null) {
                                AgendaBadge(stringResource(R.string.agenda_in_library), CineOrange, Color.White)
                            } else {
                                AgendaBadge(stringResource(if (line.isSeries) R.string.agenda_series else R.string.agenda_movie), onSurface.copy(alpha = 0.1f), onSurface.copy(alpha = 0.8f))
                            }
                            if (line.rating > 0.0 && line.kind != AgendaKind.UPCOMING) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.agenda_rating, String.format(java.util.Locale(language), "%.1f", line.rating)),
                                    color = onSurface.copy(alpha = 0.6f), fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaBadge(text: String, background: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = content, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}
