package com.example

import com.example.data.model.ContinueWatching
import com.example.util.ResumeShelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeShelfTest {
    private fun cw(id: Int, name: String, type: String = "MOVIE", progress: Long = 600, total: Long = 6000, at: Long = id.toLong()) =
        ContinueWatching(itemId = id, itemName = name, itemType = type, itemLogo = null, streamUrl = "u$id", category = "", progressSeconds = progress, totalSeconds = total, lastPlayedAt = at)

    @Test fun keepsOnlyLastFive_newestFirst_oldestDropsOut() {
        val history = (1..6).map { cw(it, "Film $it") }
        assertEquals(listOf(6, 5, 4, 3, 2), ResumeShelf.from(history).map { it.itemId })
    }

    @Test fun skipsLiveChannels_finishedAndBarelyStarted() {
        val history = listOf(
            cw(1, "Kanal", type = "LIVE"),
            cw(2, "Bitti", progress = 5900, total = 6000),
            cw(3, "Açıp kapattı", progress = 10),
            cw(4, "Yarım")
        )
        assertEquals(listOf(4), ResumeShelf.from(history).map { it.itemId })
    }

    @Test fun oneCardPerSeries_latestEpisode() {
        val history = listOf(
            cw(1, "Dizi S01E01", type = "SERIES", at = 1),
            cw(2, "Dizi S01E02", type = "SERIES", at = 2),
            cw(3, "Başka Dizi S02E05", type = "SERIES", at = 3)
        )
        assertEquals(listOf(3, 2), ResumeShelf.from(history).map { it.itemId })
    }

    @Test fun minutesLeftAndProgress() {
        val c = cw(1, "Film", progress = 3000, total = 6000)
        assertEquals(50, ResumeShelf.minutesLeft(c))
        assertEquals(0.5f, ResumeShelf.progress(c)!!, 0.001f)
        assertNull(ResumeShelf.minutesLeft(cw(2, "Süresiz", total = 0)))
    }

    @Test fun hiddenItemsLeaveTheShelf_andComeBackWhenWatchedAgain() {
        val history = listOf(cw(1, "Film 1", at = 10), cw(2, "Film 2", at = 20), cw(3, "Dizi S01E02", type = "SERIES", at = 30), cw(4, "Dizi S01E01", type = "SERIES", at = 5))
        val hidden = mapOf(ResumeShelf.key(history[1]) to 20L, ResumeShelf.key(history[2]) to 30L)
        // Film 2 ve dizinin tamamı (eski bölüm de) şeritte görünmez.
        assertEquals(listOf(1), ResumeShelf.from(history, hidden = hidden).map { it.itemId })
        // Film 2 sonradan yeniden izlenince şeride döner.
        val again = history.map { if (it.itemId == 2) it.copy(lastPlayedAt = 40) else it }
        assertEquals(listOf(2, 1), ResumeShelf.from(again, hidden = hidden).map { it.itemId })
    }

    @Test fun plan_latestRecordPerShowDecides_finishedEpisodeNeedsNext_finishedMovieDrops() {
        val history = listOf(
            cw(1, "Dizi S01E01", type = "SERIES", progress = 2000, total = 2400, at = 10), // eski bölüm yarım
            cw(2, "Dizi S01E02", type = "SERIES", progress = 2390, total = 2400, at = 20), // son bölüm bitti
            cw(3, "Film Bitti", progress = 5990, total = 6000, at = 30),
            cw(4, "Film Yarım", at = 40)
        )
        val plan = ResumeShelf.plan(history)
        assertEquals(listOf(4 to false, 2 to true), plan.map { it.cw.itemId to it.needsNext })
    }

    @Test fun nextEpisode_followsSeasonsInOrder_lastEpisodeHasNone() {
        fun ep(id: Int, s: Int, e: Int) = com.example.data.model.Episode(
            id = id, name = "B$e", seasonNumber = s, episodeNumber = e,
            item = com.example.data.model.IPTVItem(id = id, playlistId = 1, name = "Dizi S0${s}E0$e", cleanedName = "Dizi", logoUrl = null, streamUrl = "u$id", category = "", type = "SERIES")
        )
        val show = com.example.data.model.TvShow(
            id = 1, title = "Dizi", logoUrl = null, category = "", rating = 0.0, summary = "", cast = "", director = "", isFavorite = false,
            seasons = listOf(
                com.example.data.model.Season(2, listOf(ep(21, 2, 1))),
                com.example.data.model.Season(1, listOf(ep(12, 1, 2), ep(11, 1, 1)))
            )
        )
        assertEquals(12, ResumeShelf.nextEpisode(show, cw(11, "Dizi S01E01", type = "SERIES"))?.id)
        // Sezon sonu: sonraki sezonun ilk bölümü
        assertEquals(21, ResumeShelf.nextEpisode(show, cw(12, "Dizi S01E02", type = "SERIES"))?.id)
        // Numarası değişmiş kayıt: sezon/bölümden bulunur
        assertEquals(12, ResumeShelf.nextEpisode(show, cw(999, "Dizi S01E01", type = "SERIES"))?.id)
        assertNull(ResumeShelf.nextEpisode(show, cw(21, "Dizi S02E01", type = "SERIES")))
    }
}
