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
}
