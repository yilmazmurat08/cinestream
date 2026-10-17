package com.example.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import com.example.R
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel
import com.example.ui.screens.FolderGridScreen

/**
 * TV gezinme durumu: açık bölüm, ana sayfada son odaklanan öğe, Filmler/Diziler'de seçili kategori ve son
 * odaklanan poster. Oynatıcı/asistan açılınca TvApp ekrandan çıktığı için bu durum bir üst seviyede
 * ([rememberTvAppState]) tutulur; dönüşte aynı yer ve odak geri gelir.
 */
class TvAppState(destination: String, homeFocus: String, movieCategory: String?, seriesCategory: String?, browseFocus: String?) {
    var destination by mutableStateOf(destination)
    var homeFocus by mutableStateOf(homeFocus)
    var movieCategory by mutableStateOf(movieCategory)
    var seriesCategory by mutableStateOf(seriesCategory)
    var browseFocus by mutableStateOf(browseFocus)

    companion object {
        val Saver = androidx.compose.runtime.saveable.listSaver<TvAppState, String?>(
            save = { listOf(it.destination, it.homeFocus, it.movieCategory, it.seriesCategory, it.browseFocus) },
            restore = { TvAppState(it[0] ?: TV_HOME, it[1] ?: TvSection.LIVE, it[2], it[3], it[4]) }
        )
    }
}

@Composable
fun rememberTvAppState(): TvAppState = rememberSaveable(saver = TvAppState.Saver) { TvAppState(TV_HOME, TvSection.LIVE, null, null, null) }

/**
 * TV modunun kök ekranı. Telefon arayüzünden ayrıdır; aynı ViewModel, veri ve oynatıcıyı kullanır.
 * Ana sayfadan açılan bölümlerden Geri ile ana sayfaya, açılan kartın üzerine dönülür. Ana sayfada Geri,
 * çıkış onay kartını açar ([TvExitDialog]); yanlışlıkla çıkılmaz.
 */
@Composable
fun TvApp(
    viewModel: IPTVViewModel,
    state: TvAppState,
    onPlayItem: (IPTVItem) -> Unit,
    onPlayContinue: (ContinueWatching) -> Unit,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dil için LocalContext Activity'den türetilmiş ayrı bir bağlamdır; çıkış için Activity görünümden bulunur.
    val hostActivity = com.example.ui.components.rememberHostActivity()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var destination by state::destination
    var homeFocus by state::homeFocus
    var exitOpen by rememberSaveable { mutableStateOf(false) }
    // Detay paneli açıkken Geri önce paneli kapatır (MainActivity'deki işleyici).
    val detailOpen = viewModel.selectedItem.collectAsState().value != null

    BackHandler(enabled = !detailOpen && destination != TV_HOME) { destination = TV_HOME }
    BackHandler(enabled = !detailOpen && destination == TV_HOME) { exitOpen = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .testTag("tv_app")
    ) {
        when (destination) {
            TV_HOME -> TvHomeScreen(
                viewModel = viewModel,
                initialFocus = homeFocus,
                onFocusChanged = { if (it in TvSection.cards || it == TvSection.CONTINUE || it == TvSection.ASSISTANT) homeFocus = it },
                onOpenSection = { section ->
                    if (section == TvSection.LIVE) {
                        // Canlı TV: son izlenen (yoksa ilk) kanal tam ekran, kanal paneli ve kontroller açık.
                        scope.launch {
                            val channel = viewModel.tvStartChannel()
                            if (channel != null) onPlayItem(channel) else destination = TvSection.LIVE
                        }
                    } else {
                        destination = section
                    }
                },
                onOpenAssistant = onOpenAssistant,
                onSearchResult = { item ->
                    val matched = viewModel.findMatchedItem(item)
                    val target = if (matched.streamUrl.isNotEmpty()) matched else item
                    if (target.type == "LIVE" || target.type == "LIVE_TV") onPlayItem(target) else viewModel.selectItem(target)
                },
                onPlayContinue = onPlayContinue
            )
            TvSection.LIVE -> {
                val groups by viewModel.liveGroups.collectAsState()
                FolderGridScreen(
                    groups = groups,
                    title = "Canlı Yayın",
                    displayTitle = stringResource(R.string.home_folder_live),
                    viewModel = viewModel,
                    onPlayItem = onPlayItem
                )
            }
            TvSection.MOVIE -> TvBrowseScreen(viewModel = viewModel, type = "MOVIE", state = state)
            TvSection.SERIES -> TvBrowseScreen(viewModel = viewModel, type = "SERIES", state = state)
            TvSection.SAVED -> TvSavedScreen(viewModel = viewModel, onPlayItem = onPlayItem)
            else -> TvSettingsScreen(viewModel = viewModel)
        }

        if (exitOpen) {
            val continueItem = viewModel.continueWatching.collectAsState().value.firstOrNull()
            TvExitDialog(
                continueItem = continueItem,
                onStay = { exitOpen = false },
                onContinue = { item ->
                    exitOpen = false
                    onPlayContinue(item)
                },
                onExit = {
                    exitOpen = false
                    hostActivity?.finish()
                }
            )
        }
    }
}

internal const val TV_HOME = "HOME"
