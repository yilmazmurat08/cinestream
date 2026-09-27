package com.example.stress

import com.example.data.model.IPTVItem
import com.example.ui.buildChannelRing
import com.example.util.AdultContentFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 60.000 kanalda hızlı kanal değiştirme ve yetişkin filtresi: süre ve doğruluk. */
class ChannelSwitchStressTest {

    private lateinit var channels: List<IPTVItem>

    @Before
    fun setUp() {
        requireStressMode()
        val folders = (1..300).map { "Klasör $it" } + listOf("Spor", "Ulusal", "XX: Yetişkin", "Adult VIP", "Belgesel")
        channels = (1..60_000).map { i ->
            val folder = folders[i % folders.size]
            val name = when {
                i % 97 == 0 -> "XX: Gizli $i"          // yanlış klasörde yetişkin kanal
                folder.startsWith("XX") || folder.startsWith("Adult") -> "Kanal $i +18"
                else -> "Kanal $i HD"
            }
            IPTVItem(id = i, playlistId = 1, name = name, cleanedName = name, logoUrl = null,
                streamUrl = "http://h/$i.ts", category = folder, type = "LIVE")
        }
    }

    private fun isAdult(i: IPTVItem) = AdultContentFilter.isAdult(i.category, i.cleanedName)

    @Test
    fun adultFilter_over60kChannels_isFastAndConsistent() {
        val (adultCount, sec) = timed("60.000 kanalda yetişkin filtresi") { channels.count { isAdult(it) } }
        println("[STRES] yetişkin olarak işaretlenen: $adultCount")
        assertTrue(adultCount > 0)
        assertTrue("Filtre çok yavaş: $sec sn", sec < 10)
    }

    @Test
    fun fiveThousandChannelSwitches_stayInFolder_andNeverHitAdult() {
        var current = channels.first { it.category == "Spor" && !isAdult(it) }
        val (_, sec) = timed("5.000 kanal değiştirme") {
            repeat(5_000) { step ->
                val ring = buildChannelRing(current, channels) { isAdult(it) }
                val index = ring.indexOfFirst { it.id == current.id }
                current = ring[(index + if (step % 3 == 0) -1 else 1).mod(ring.size)]
                assertTrue(current.category == "Spor")
                assertFalse(isAdult(current))
            }
        }
        assertTrue("Kanal değiştirme çok yavaş: $sec sn", sec < 60)
    }
}
