package com.example.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Episode
import com.example.data.model.IPTVItem
import com.example.data.model.TvShow
import com.example.data.model.isPlaceholderCast
import com.example.data.model.isPlaceholderSummary
import com.example.data.repository.TMDBMediaDetails
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Detaydaki oyuncu/yönetmen. [apiRole] telefondaki kişi aramalarına verilen rol ("Oyuncu" / "Yönetmen"). */
data class TvPerson(val name: String, val apiRole: String, val character: String?, val photoUrl: String?) {
    val isDirector get() = apiRole == ROLE_DIRECTOR
    val key get() = "$apiRole:$name"

    companion object {
        const val ROLE_ACTOR = "Oyuncu"
        const val ROLE_DIRECTOR = "Yönetmen"
    }
}

private val PLACEHOLDER_NAMES = setOf("bilinmiyor", "belirtilmemiş", "popüler oyuncular")

/** Yönetmen başta; önce TMDB (karakter adı ve fotoğrafıyla), yoksa sağlayıcının listesi (telefondaki gibi). */
internal fun buildPeople(item: IPTVItem, tmdb: TMDBMediaDetails?, extraCast: List<String>, extraDirector: String): List<TvPerson> {
    fun names(raw: String, separators: Array<String>) = raw.split(*separators).map { it.trim() }
        .filter { it.isNotEmpty() && it.lowercase() !in PLACEHOLDER_NAMES }
    val directorRaw = tmdb?.director?.takeIf { it.isNotBlank() } ?: item.director.ifBlank { extraDirector }
    val directors = names(directorRaw, arrayOf(",", ";", "/", "&")).distinct()
        .map { TvPerson(it, TvPerson.ROLE_DIRECTOR, null, null) }
    val cast = tmdb?.cast?.mapNotNull { p ->
        val name = p.name?.trim().orEmpty()
        if (name.isEmpty()) null else TvPerson(name, TvPerson.ROLE_ACTOR, p.character?.trim()?.takeIf { it.isNotEmpty() }, p.profile_path?.takeIf { it.isNotBlank() })
    }?.takeIf { it.isNotEmpty() }
        ?: (if (!item.isPlaceholderCast()) names(item.cast, arrayOf(",")) else emptyList()).ifEmpty { extraCast }
            .map { TvPerson(it, TvPerson.ROLE_ACTOR, null, null) }
    return (directors + cast.take(24)).distinctBy { it.key }
}

private fun yearOf(date: String?): String? = date?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

/**
 * TV detay ekranı (film ve dizi). İlk odak "İzle". Dizilerde sezonlar ve bölümler kumandayla gezilir.
 * "Oyuncular ve yönetmen" satırında OK kişi sayfasını açar; Geri ile dönünce odak aynı kişidedir.
 * Yalnızca gerçek veriler gösterilir; olmayan bilgi yazılmaz.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvDetailScreen(
    viewModel: IPTVViewModel,
    item: IPTVItem,
    onPlay: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSeries = item.type == "SERIES"
    val favorites by viewModel.favorites.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val covers by viewModel.seriesCoversMap.collectAsState()
    val isFavorite = favorites.any { it.id == item.id } || item.isFavorite

    var show by remember(item.id) { mutableStateOf<TvShow?>(null) }
    var tmdb by remember(item.id) { mutableStateOf<TMDBMediaDetails?>(null) }
    var trailerUrl by remember(item.id) { mutableStateOf(com.example.ui.components.formatYouTubeWatchUrl(item.trailerUrl)) }
    // Netflix tarzı: fragman YouTube'a gitmeden arka plan görselinin yerinde oynar.
    var trailerPlaying by remember(item.id) { mutableStateOf(false) }
    var trailerFailed by remember(item.id) { mutableStateOf(false) }
    BackHandler(enabled = trailerPlaying) { trailerPlaying = false }
    val pageScroll = rememberScrollState()
    // Video ayrı pencerede çizildiği için aşağıdaki satırlar (sezonlar, oyuncular) kaydırılınca videonun altında
    // kalmasın: sayfa aşağı kaydırılınca fragman kapanır ve arka plan görseli geri gelir.
    LaunchedEffect(trailerPlaying) {
        if (trailerPlaying) {
            androidx.compose.runtime.snapshotFlow { pageScroll.value }.collect { if (it > 24) trailerPlaying = false }
        }
    }
    var extraCast by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }
    var extraDirector by remember(item.id) { mutableStateOf("") }
    var aiText by remember(item.id) { mutableStateOf<String?>(null) }
    var aiLoading by remember(item.id) { mutableStateOf(false) }
    var recapOpen by remember(item.id) { mutableStateOf(false) }
    var similar by remember(item.id) { mutableStateOf<List<com.example.data.repository.TvPoster>>(emptyList()) }
    var openPerson by remember(item.id) { mutableStateOf<TvPerson?>(null) }
    var selectedSeason by remember(item.id) { mutableStateOf(0) }

    val playFocus = remember { FocusRequester() }
    val personFocus = remember(item.id) { mutableMapOf<String, FocusRequester>() }
    fun personRequester(key: String) = personFocus.getOrPut(key) { FocusRequester() }

    val title = show?.title ?: item.cleanedName.ifBlank { item.name }

    LaunchedEffect(item.id) {
        if (isSeries) {
            show = viewModel.tvShowForItem(item, covers)
            similar = viewModel.tvSimilar(item, show, covers)
        }
    }
    LaunchedEffect(title) {
        tmdb = viewModel.tvTmdbDetails(title, if (isSeries) "SERIES" else "MOVIE")
    }
    LaunchedEffect(title) {
        if (trailerUrl == null) {
            val id = runCatching { viewModel.fetchTrailerVideoId(title) }.getOrNull()
            if (!id.isNullOrBlank()) trailerUrl = "https://www.youtube.com/watch?v=$id"
        }
    }
    LaunchedEffect(item.id) {
        // Dizilerde benzerler, dizi bilgisi yüklenince (yukarıda) aynı klasördeki dizilerden hesaplanır.
        if (!isSeries) similar = viewModel.tvSimilar(item, null, covers)
    }
    LaunchedEffect(item.id) { requestFocusWhenReady(playFocus) }

    val people = remember(item, tmdb, extraCast, extraDirector) { buildPeople(item, tmdb, extraCast, extraDirector) }
    // Oyuncu/yönetmen bilgisi hiç yoksa telefondaki gibi yapay zekadan istenir.
    LaunchedEffect(item.id, tmdb == null) {
        if (people.isEmpty() && title.isNotBlank()) {
            viewModel.fetchCastAndDirectorInfo(title, item) { info ->
                extraCast = info.cast
                extraDirector = info.director
            }
        }
    }

    // Dizi: son izlenen bölüm (Devam et) ve sezon seçimi
    val seasons = show?.seasons?.sortedBy { it.seasonNumber }.orEmpty()
    val lastWatched: Pair<Int, Episode>? = remember(show, continueWatching) {
        val cw = continueWatching.filter { c -> seasons.any { s -> s.episodes.any { it.item.id == c.itemId } } }.maxByOrNull { it.lastPlayedAt }
        cw?.let { c -> seasons.flatMap { s -> s.episodes.map { s.seasonNumber to it } }.firstOrNull { it.second.item.id == c.itemId } }
    }
    LaunchedEffect(show) {
        lastWatched?.let { (seasonNumber, _) -> selectedSeason = seasons.indexOfFirst { it.seasonNumber == seasonNumber }.coerceAtLeast(0) }
    }
    val firstEpisode = seasons.firstOrNull()?.episodes?.minByOrNull { it.episodeNumber }?.item

    val summary = when {
        isSeries && show != null && show!!.summary.isNotBlank() && !show!!.summary.contains("özet bilgisi bulunmuyor", true) -> show!!.summary
        !item.isPlaceholderSummary() && item.summary != "Açıklama bulunamadı." -> item.summary
        else -> tmdb?.overview?.takeIf { it.isNotBlank() && it != "Açıklama bulunamadı." }
    }
    val backdrop = tmdb?.backdropUrl?.takeIf { it.isNotBlank() }
    val poster = show?.logoUrl ?: item.logoUrl ?: tmdb?.posterUrl
    val rating = (show?.rating ?: 0.0).takeIf { it > 0.0 } ?: item.rating.takeIf { it > 0.0 }
    val year = yearOf(item.releaseDate) ?: yearOf(tmdb?.releaseDate)
    val genre = item.genre.takeIf { it.isNotBlank() } ?: tmdb?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .focusProperties { exit = { FocusRequester.Cancel } } // odak detayın arkasına kaçmaz
            .focusGroup()
            .testTag("tv_detail_screen")
    ) {
        // Arka plan: yatay görsel sağda; yoksa poster kendi oranında sağda. Fragman açıkken aynı yerde video oynar.
        val trailerId = if (trailerPlaying) com.example.ui.components.extractYouTubeVideoId(trailerUrl) else null
        if (trailerId != null) {
            com.example.ui.components.InAppTrailerPlayer(
                videoId = trailerId,
                showControls = false,
                onEnded = { trailerPlaying = false },
                onError = {
                    trailerPlaying = false
                    trailerFailed = true
                },
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.64f).aspectRatio(16f / 9f).testTag("tv_detail_trailer_player")
            )
        } else if (backdrop != null) {
            AsyncImage(
                model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.7f).fillMaxHeight(0.72f)
            )
        } else if (poster != null) {
            AsyncImage(
                model = poster, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 64.dp).fillMaxHeight(0.6f).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp))
            )
        }
        if (trailerId != null) {
            // Video net görünsün: sadece sol kenarı ve alt kenarı yazıya doğru yumuşakça kararır.
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvTheme.Background, 0.36f to TvTheme.Background, 0.46f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.56f to Color.Transparent, 0.7f to TvTheme.Background)))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvTheme.Background, 0.45f to TvTheme.Background.copy(alpha = 0.9f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 0.75f to TvTheme.Background)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(start = 48.dp, top = 40.dp, bottom = 48.dp)
        ) {
            Column(Modifier.fillMaxWidth(if (trailerId != null) 0.34f else 0.55f)) {
                Text(title, color = TvTheme.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 38.sp)
                val meta = listOfNotNull(
                    rating?.let { "★ " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
                    year,
                    genre,
                    seasons.size.takeIf { isSeries && it > 0 }?.let { stringResource(R.string.tv_season_count, it) }
                )
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(meta.joinToString(" · "), color = TvTheme.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (summary != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(summary, color = TvTheme.TextPrimary.copy(alpha = 0.9f), fontSize = 17.sp, lineHeight = 24.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(22.dp))

            // Düğmeler: İzle (ilk odak), Fragman, Favori, AI
            LazyRow(modifier = Modifier.offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 6.dp, bottom = 6.dp)) {
                item {
                    val resume = lastWatched
                    val label = when {
                        !isSeries -> stringResource(R.string.tv_play)
                        resume != null -> stringResource(R.string.tv_resume_episode, resume.first, resume.second.episodeNumber)
                        else -> stringResource(R.string.detail_start_first_episode)
                    }
                    TvActionButton(Icons.Filled.PlayArrow, label, "tv_detail_play", playFocus, primary = true) {
                        when {
                            !isSeries -> onPlay(item)
                            resume != null -> onPlay(resume.second.item)
                            else -> onPlay(firstEpisode ?: item)
                        }
                    }
                }
                trailerUrl?.let { url ->
                    item {
                        TvActionButton(
                            if (trailerPlaying) Icons.Filled.Close else Icons.Filled.Movie,
                            stringResource(if (trailerPlaying) R.string.trailer_close else R.string.trailer),
                            "tv_detail_trailer"
                        ) {
                            trailerFailed = false
                            trailerPlaying = !trailerPlaying && com.example.ui.components.extractYouTubeVideoId(url) != null
                        }
                    }
                }
                item {
                    TvActionButton(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        stringResource(if (isFavorite) R.string.tv_remove_favorite else R.string.tv_add_favorite),
                        "tv_detail_favorite"
                    ) { viewModel.toggleFavorite(item) }
                }
            }

            if (trailerFailed) {
                Text(
                    stringResource(R.string.trailer_inapp_failed), color = TvTheme.TextSecondary, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp).testTag("tv_detail_trailer_failed")
                )
            }

            // Dizi: izlenen bölümlerin spoilersız AI özeti (telefondaki "AI ile Önceki Bölümleri Özetle" ile
            // aynı mantık: son izlenen bölümden sonraki bölüme kadar olan bölümler özetlenir).
            if (isSeries) {
                val recapSeason = lastWatched?.first ?: 1
                val recapEpisode = (lastWatched?.second?.episodeNumber ?: 0) + 1
                val notWatched = lastWatched == null || (recapSeason <= 1 && recapEpisode <= 1)
                TvRowTitle(stringResource(R.string.detail_ai_summarize_previous))
                TvGlassButton(
                    onClick = {
                        recapOpen = true
                        if (!notWatched && aiText == null && !aiLoading) {
                            aiLoading = true
                            scope.launch {
                                val text = runCatching {
                                    com.example.data.api.GeminiAiService.generatePreviousEpisodesSummary(context, title, recapEpisode)
                                }.getOrNull()
                                aiText = text?.takeIf { it.isNotBlank() } ?: context.getString(R.string.series_recap_unavailable)
                                aiLoading = false
                            }
                        }
                    },
                    shape = TvTheme.RowShape,
                    focusScale = 1.03f,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth(0.6f).testTag("tv_series_recap")
                ) { focused ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = if (focused) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (lastWatched != null) {
                                stringResource(R.string.series_recap_last_watched, lastWatched.first, lastWatched.second.episodeNumber)
                            } else {
                                stringResource(R.string.series_recap_start)
                            },
                            color = TvTheme.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (recapOpen) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth(0.6f).testTag("tv_series_recap_panel")) {
                        Text(
                            if (notWatched) title else stringResource(R.string.series_recap_before, title, recapSeason.toString(), recapEpisode.toString()),
                            color = TvTheme.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            notWatched -> {
                                TvReadableText(
                                    stringResource(R.string.detail_start_of_journey_title) + "\n" + stringResource(R.string.detail_start_of_journey_desc),
                                    "tv_series_recap_start"
                                )
                                Spacer(Modifier.height(10.dp))
                                TvActionButton(Icons.Filled.PlayArrow, stringResource(R.string.detail_start_first_episode), "tv_series_recap_first") {
                                    onPlay(firstEpisode ?: item)
                                }
                            }
                            aiLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = TvTheme.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.detail_ai_analyzing), color = TvTheme.TextSecondary, fontSize = 15.sp)
                            }
                            else -> TvReadableText(aiText ?: stringResource(R.string.series_recap_unavailable), "tv_series_recap_text")
                        }
                    }
                }
            }

            // Dizi: sezonlar ve bölümler
            if (isSeries && seasons.isNotEmpty()) {
                TvRowTitle(stringResource(R.string.tv_seasons))
                LazyRow(modifier = Modifier.offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 4.dp, bottom = 4.dp)) {
                    itemsIndexed(seasons) { index, season ->
                        TvGlassButton(
                            onClick = { selectedSeason = index },
                            onFocused = { selectedSeason = index },
                            shape = TvTheme.PillShape,
                            focusScale = 1.05f,
                            glassColor = if (index == selectedSeason) TvTheme.Accent.copy(alpha = 0.4f) else TvTheme.Glass,
                            modifier = Modifier.height(42.dp).testTag("tv_season_${season.seasonNumber}")
                        ) {
                            Text(stringResource(R.string.tv_season_n, season.seasonNumber), color = TvTheme.TextPrimary, fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 18.dp))
                        }
                    }
                }
                val episodes = seasons.getOrNull(selectedSeason)?.episodes?.sortedBy { it.episodeNumber }.orEmpty()
                TvRowTitle(stringResource(R.string.tv_episodes))
                LazyRow(modifier = Modifier.offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 6.dp, bottom = 6.dp)) {
                    items(episodes, key = { it.item.id }) { episode ->
                        val cw = continueWatching.firstOrNull { it.itemId == episode.item.id }
                        TvEpisodeCard(episode, progress = cw?.let { if (it.totalSeconds > 0) it.progressSeconds.toFloat() / it.totalSeconds else null }) {
                            onPlay(episode.item)
                        }
                    }
                }
            }

            // Oyuncular ve yönetmen
            if (people.isNotEmpty()) {
                TvRowTitle(stringResource(R.string.tv_cast_and_director))
                LazyRow(modifier = Modifier.offset(x = (-12).dp).testTag("tv_people_row"), horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp)) {
                    items(people, key = { it.key }) { person ->
                        TvPersonCircle(viewModel, person, personRequester(person.key)) { openPerson = person }
                    }
                }
            }

            // Benzer yapımlar
            if (similar.isNotEmpty()) {
                TvRowTitle(stringResource(R.string.tv_similar))
                LazyRow(modifier = Modifier.offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 6.dp, bottom = 6.dp)) {
                    items(similar, key = { it.key }) { other ->
                        TvPosterCard(
                            title = other.title,
                            posterUrl = other.posterUrl,
                            onClick = {
                                val catalogShow = other.catalogShow
                                if (catalogShow != null) viewModel.openCatalogShow(catalogShow) else viewModel.selectItem(other.item)
                            },
                            modifier = Modifier.width(130.dp).testTag("tv_similar_${other.key}")
                        )
                    }
                }
            }
        }

        openPerson?.let { person ->
            BackHandler { openPerson = null }
            TvPersonScreen(
                viewModel = viewModel,
                person = person,
                excludeItemId = item.id,
                onOpenItem = { work ->
                    openPerson = null
                    viewModel.selectItem(work)
                }
            )
        }
    }

    // Kişi sayfasından dönünce odak aynı kişiye
    var lastPersonKey by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(openPerson) {
        val current = openPerson
        if (current != null) {
            lastPersonKey = current.key
        } else {
            lastPersonKey?.let { requestFocusWhenReady(personRequester(it)) }
        }
    }
}

@Composable
private fun TvRowTitle(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, color = TvTheme.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TvActionButton(
    icon: ImageVector,
    label: String,
    tag: String,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.PillShape,
        focusRequester = focusRequester,
        glassColor = if (primary) TvTheme.Accent.copy(alpha = 0.85f) else TvTheme.Glass,
        modifier = Modifier.height(52.dp).testTag(tag)
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp)) {
            Icon(icon, null, tint = if (primary) Color.White else if (focused) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = TvTheme.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Odaklanabilir (kaydırma için) okunabilir metin paneli. */
@Composable
internal fun TvReadableText(text: String, tag: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text, color = TvTheme.TextPrimary, fontSize = 17.sp, lineHeight = 25.sp,
        modifier = Modifier
            .clip(TvTheme.RowShape)
            .background(TvTheme.Glass)
            .border(if (focused) 2.dp else 1.dp, if (focused) SolidColor(TvTheme.FocusGlow) else TvTheme.GlassBorder, TvTheme.RowShape)
            .focusable(interactionSource = interaction)
            .padding(18.dp)
            .testTag(tag)
    )
}

@Composable
private fun TvEpisodeCard(episode: Episode, progress: Float?, onClick: () -> Unit) {
    Column(Modifier.width(220.dp)) {
        TvGlassButton(
            onClick = onClick,
            shape = TvTheme.RowShape,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("tv_episode_${episode.item.id}")
        ) { focused ->
            val image = episode.logoUrl ?: episode.item.logoUrl
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Icon(Icons.Filled.PlayArrow, null, tint = if (focused) TvTheme.FocusGlow else Color.White, modifier = Modifier.size(36.dp))
            if (progress != null) {
                if (progress >= WATCHED_THRESHOLD) {
                    // İzlenmiş bölüm: köşede onay ve "İzlendi" etiketi
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(TvTheme.PillShape)
                            .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("tv_episode_watched_${episode.item.id}")
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = TvTheme.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.detail_watched).replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(TvTheme.Accent))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.tv_episode_n, episode.episodeNumber), color = TvTheme.TextMuted, fontSize = 13.sp)
        val name = episode.name.takeIf { it.isNotBlank() }
        if (name != null) Text(name, color = TvTheme.TextPrimary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Yuvarlak kişi fotoğrafı; altında ad ve rol (karakter adı veya "Yönetmen"). Fotoğraf yoksa baş harfler. */
@Composable
private fun TvPersonCircle(viewModel: IPTVViewModel, person: TvPerson, focusRequester: FocusRequester, onClick: () -> Unit) {
    var photo by remember(person.key) { mutableStateOf(person.photoUrl) }
    LaunchedEffect(person.key) {
        if (photo == null) photo = runCatching { viewModel.lookupPersonPhoto(person.name, person.apiRole) }.getOrNull()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
        TvGlassButton(
            onClick = onClick,
            shape = CircleShape,
            focusRequester = focusRequester,
            modifier = Modifier.size(92.dp).testTag("tv_person_${person.name}")
        ) {
            // Baş harfler her zaman altta: fotoğraf yüklenirken ya da yüklenemezse daire boş kalmaz.
            TvInitials(person.name, Modifier.fillMaxSize())
            if (!photo.isNullOrBlank()) {
                AsyncImage(model = photo, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(person.name, color = TvTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        val role = if (person.isDirector) stringResource(R.string.tv_director) else person.character
        if (role != null) {
            Text(role, color = TvTheme.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
