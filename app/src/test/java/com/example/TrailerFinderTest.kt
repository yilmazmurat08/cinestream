package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.tmdb.TmdbVideo
import com.example.util.TrailerFinder
import com.example.util.TrailerLookup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Linkte fragmanı olmayan yapım: önce TMDB'deki resmî fragman, yoksa YouTube (ad + yıl). Sonuç cihazda saklanır;
 * aynı yapım için tekrar istek atılmaz. "Bulunamadı" 3 gün saklanır; kaynak ulaşılamazsa saklanmaz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailerFinderTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var tmdbCalls = 0
    private var youtubeCalls = 0
    private var youtubeYear: String? = "unset"
    private var clock = 1_000_000L

    @Before
    fun clear() {
        app.getSharedPreferences(TrailerFinder.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun finder(tmdb: TrailerLookup, youtube: TrailerLookup) = TrailerFinder(
        context = app,
        tmdb = { _, _, _ -> tmdbCalls++; tmdb },
        youtube = { _, year -> youtubeCalls++; youtubeYear = year; youtube },
        now = { clock }
    )

    @Test
    fun picksOfficialTrailer_turkishFirst_neverClipsOrOtherSites() {
        val videos = listOf(
            TmdbVideo(key = "clip", site = "YouTube", type = "Clip", official = true, language = "tr"),
            TmdbVideo(key = "vimeo", site = "Vimeo", type = "Trailer", official = true, language = "tr"),
            TmdbVideo(key = "teaserTr", site = "YouTube", type = "Teaser", official = true, language = "tr"),
            TmdbVideo(key = "fanEn", site = "YouTube", type = "Trailer", official = false, language = "en"),
            TmdbVideo(key = "officialEn", site = "YouTube", type = "Trailer", official = true, language = "en"),
            TmdbVideo(key = "officialTr", site = "YouTube", type = "Trailer", official = true, language = "tr")
        )
        assertEquals("officialTr", TrailerFinder.pickTrailerKey(videos))
        assertEquals("officialEn", TrailerFinder.pickTrailerKey(videos.filterNot { it.key == "officialTr" }))
        assertEquals("teaserTr", TrailerFinder.pickTrailerKey(videos.filter { it.type != "Trailer" }))
        assertNull(TrailerFinder.pickTrailerKey(listOf(videos[0], videos[1])))
    }

    @Test
    fun tmdbFirst_thenCached_noSecondRequest() = runBlocking {
        val f = finder(TrailerLookup.Found("tmdbKey12345"), TrailerLookup.Found("ytKey"))
        assertEquals("tmdbKey12345", f.find("Hedda", "2025", isMovie = true))
        assertEquals("tmdbKey12345", f.find("Hedda", "2025", isMovie = true))
        assertEquals(1, tmdbCalls)
        assertEquals(0, youtubeCalls)
    }

    @Test
    fun youtubeOnlyWhenTmdbHasNone_withYear() = runBlocking {
        val f = finder(TrailerLookup.NotFound, TrailerLookup.Found("ytKey"))
        assertEquals("ytKey", f.find("Almost Human", "2013-11-17", isMovie = false))
        assertEquals("2013", youtubeYear)
        assertEquals("ytKey", f.find("Almost Human", "2013-11-17", isMovie = false))
        assertEquals(1, youtubeCalls)
    }

    @Test
    fun notFoundIsRememberedForThreeDays_failuresAreNot() = runBlocking {
        val none = finder(TrailerLookup.NotFound, TrailerLookup.NotFound)
        assertNull(none.find("Bilinmeyen Yapım", null, isMovie = true))
        assertNull(none.find("Bilinmeyen Yapım", null, isMovie = true))
        assertEquals(1, tmdbCalls)
        clock += TrailerFinder.NOT_FOUND_TTL_MS + 1
        assertNull(none.find("Bilinmeyen Yapım", null, isMovie = true))
        assertEquals(2, tmdbCalls)

        // Ulaşılamayan kaynak (ağ yok, kota doldu): saklanmaz, bir dahaki açılışta yeniden denenir.
        tmdbCalls = 0
        val offline = finder(TrailerLookup.Failed, TrailerLookup.Failed)
        assertNull(offline.find("Ağsız Yapım", null, isMovie = true))
        assertNull(offline.find("Ağsız Yapım", null, isMovie = true))
        assertEquals(2, tmdbCalls)
    }
}
