package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.IPTVItem
import com.example.data.repository.AgendaEntry
import com.example.data.repository.AgendaKind
import com.example.data.repository.AgendaLine
import com.example.data.repository.CinemaAgendaRepository
import com.example.ui.components.CinemaAgendaCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ana sayfa "Sinema Gündemi": satırların kütüphaneyle eşleşmesi, tarih biçimi ve kartın görünümü. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr-w360dp-h800dp-port-xxhdpi")
class CinemaAgendaTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun line(kind: AgendaKind, id: Int, title: String, series: Boolean = false, date: String? = "2026-11-14", season: Int? = null) =
        AgendaLine(kind, id, series, title, title, "/p$id.jpg", date, season, 8.1, "Konu")

    private val lines = listOf(
        line(AgendaKind.NOW_PLAYING, 1, "Taş Kale", date = "2026-11-06"),
        line(AgendaKind.NEW_SEASON, 2, "Mavi Fener", series = true, season = 3),
        line(AgendaKind.UPCOMING, 3, "Kızıl Sınır", date = "2026-11-28")
    )

    @Test
    fun matchToLibrary_linksOnlyTitlesInTheLibrary() {
        val pool = listOf(
            IPTVItem(id = 501, playlistId = 1, name = "Taş Kale (2026)", cleanedName = "Taş Kale", logoUrl = null, streamUrl = "http://s/1.mp4", category = "Film", type = "MOVIE"),
            IPTVItem(id = 502, playlistId = 1, name = "Bambaşka Film", cleanedName = "Bambaşka Film", logoUrl = null, streamUrl = "http://s/2.mp4", category = "Film", type = "MOVIE")
        )
        val entries = CinemaAgendaRepository.matchToLibrary(lines, pool)
        assertEquals(3, entries.size)
        assertEquals(501, entries[0].libraryItem?.id)
        assertNull(entries[1].libraryItem)
        assertNull(entries[2].libraryItem)
        assertEquals("SERIES", entries[1].tmdbItem.type)
    }

    @Test
    fun displayDate_usesAppLanguage() {
        assertEquals("14 Kasım", CinemaAgendaRepository.displayDate("2026-11-14", "tr"))
        assertEquals("14 November", CinemaAgendaRepository.displayDate("2026-11-14", "en"))
        assertNull(CinemaAgendaRepository.displayDate(null, "tr"))
        assertNull(CinemaAgendaRepository.displayDate("bozuk", "tr"))
    }

    @Test
    fun card_showsLines_badge_andOpensTappedRow() {
        val entries = CinemaAgendaRepository.matchToLibrary(
            lines,
            listOf(IPTVItem(id = 501, playlistId = 1, name = "Taş Kale", cleanedName = "Taş Kale", logoUrl = null, streamUrl = "http://s/1.mp4", category = "Film", type = "MOVIE"))
        )
        var opened: AgendaEntry? = null
        compose.setContent { MyApplicationTheme { CinemaAgendaCard(entries = entries, onOpen = { opened = it }) } }
        compose.onNode(hasText("“Taş Kale” bu hafta vizyonda")).assertExists()
        compose.onNode(hasText("“Mavi Fener” 3. sezon 14 Kasım tarihinde başlıyor")).assertExists()
        compose.onNode(hasText("Yakında: “Kızıl Sınır” · 28 Kasım")).assertExists()
        compose.onNode(hasText("LİSTENDE VAR")).assertExists()
        compose.onNodeWithTag("agenda_row_NOW_PLAYING").performClick()
        assertEquals(501, opened?.libraryItem?.id)
    }
}
