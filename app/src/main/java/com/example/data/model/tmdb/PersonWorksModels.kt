package com.example.data.model.tmdb

import androidx.compose.runtime.Immutable
import com.example.data.model.IPTVItem

/** Kişinin TMDB'deki tek bir yapımı (film ya da dizi). */
@Immutable
data class PersonCredit(
    val tmdbId: Int,
    val mediaType: String,      // "movie" ya da "tv"
    val title: String,          // tr-TR başlık
    val originalTitle: String,
    val year: Int?,
    val posterUrl: String?,
    val popularity: Double
) {
    val key: String get() = "$mediaType:$tmdbId"
}

@Immutable
data class PersonCreditsLookup(
    val personId: Int,
    val name: String,
    val profileUrl: String?,
    val credits: List<PersonCredit>
)

sealed interface PersonCreditsOutcome {
    data class Found(val lookup: PersonCreditsLookup) : PersonCreditsOutcome
    data object NotFound : PersonCreditsOutcome
    data class Failed(val reason: String) : PersonCreditsOutcome
}

/**
 * Kütüphanede bulunan bir yapım.
 * credit == null ise eşleşme TMDB'den değil, IPTV sağlayıcısının verdiği oyuncu listesinden geldi.
 */
@Immutable
data class PersonWorkMatch(
    val item: IPTVItem,
    val credit: PersonCredit?
) {
    val uiKey: String get() = "${item.type}_${item.id}"
}

@Immutable
data class PersonWorksResult(
    val inLibrary: List<PersonWorkMatch>,
    val notInLibrary: List<PersonCredit>
)

enum class PersonWorksPhase {
    SEARCHING_LIBRARY,  // sağlayıcının oyuncu bilgisinde aranıyor (ağ yok, anında)
    FETCHING_TMDB,      // TMDB'den filmografi alınıyor
    MATCHING,           // TMDB listesi kütüphaneyle eşleştiriliyor
    DONE
}

/** Oyuncu penceresinin tek durum nesnesi; her adımda yenisi yayınlanır. */
@Immutable
data class PersonWorksUiState(
    val query: String,
    val phase: PersonWorksPhase,
    val tmdbCreditCount: Int? = null,
    val profileUrl: String? = null,
    val inLibrary: List<PersonWorkMatch> = emptyList(),
    val notInLibrary: List<PersonCredit> = emptyList(),
    val tmdbProblem: String? = null
)
