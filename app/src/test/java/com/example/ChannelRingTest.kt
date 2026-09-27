package com.example

import com.example.data.model.IPTVItem
import com.example.ui.buildChannelRing
import org.junit.Assert.assertEquals
import org.junit.Test

/** Oynatıcıdaki kanal listesi / kanal ileri-geri: izlenen klasör, yetişkin kanal karışmaz. */
class ChannelRingTest {

    private fun ch(id: Int, name: String, category: String) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://x/$id.ts", category = category, type = "LIVE"
    )

    private val isAdult = { i: IPTVItem -> i.category.contains("Yetişkin") || i.name.startsWith("XX:") }

    private val all = listOf(
        ch(1, "XX: Türk & Altyazılı-4", "Spor"),     // yanlış klasörlenmiş yetişkin kanal
        ch(2, "beIN Sports 5", "Spor"),
        ch(3, "Yetişkin 1", "Yetişkin"),
        ch(4, "S Sport 8", "Spor"),
        ch(5, "TRT 1", "Ulusal"),
        ch(6, "NATURE SPACE", "Belgesel")
    )

    @Test
    fun ringContainsOnlyTheSameFolder_withoutAdultChannels() {
        val ring = buildChannelRing(all[1], all, isAdult)
        assertEquals(listOf(2, 4), ring.map { it.id })
    }

    @Test
    fun folderWithOnlyTheCurrentChannel_fallsBackToAllNonAdultChannels() {
        val ring = buildChannelRing(all[4], all, isAdult)
        assertEquals(listOf(2, 4, 5, 6), ring.map { it.id })
    }

    @Test
    fun watchingAnAdultChannel_keepsItsOwnFolder() {
        val ring = buildChannelRing(all[2], all + ch(7, "Yetişkin 2", "Yetişkin"), isAdult)
        assertEquals(listOf(3, 7), ring.map { it.id })
    }
}
