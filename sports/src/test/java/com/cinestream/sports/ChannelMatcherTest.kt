package com.cinestream.sports

import com.cinestream.sports.channels.ChannelIndex
import com.cinestream.sports.channels.ChannelKeys
import com.cinestream.sports.channels.MatchReason
import com.cinestream.sports.model.Broadcast
import com.cinestream.sports.model.PlaylistChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yayıncı adını kullanıcının IPTV listesindeki kanala bağlayan motor (prototipteki 5 hata dahil). */
class ChannelMatcherTest {

    private val playlist = listOf(
        "TR | BEIN SPORTS 1 SD (YEDEK)", "TR | BEIN SPORTS 1 HD", "BEIN SPORTS 1 4K", "TR | BEIN SPORTS MAX 1",
        "[TURK] S SPORT 1 FHD", "TR || S SPORT 2 4K", "TR - TIVIBU SPOR 1 HD", "TR | EXXEN SPORTS 1 (1080p)",
        "TR: TRT SPOR YILDIZ HD", "TR | A SPOR HD", "TR | SPOR SMART HD", "TR | TRT 1 HD"
    ).mapIndexed { i, name -> PlaylistChannel("c${i + 1}", name) }

    private val index = ChannelIndex(playlist)

    @Test
    fun everyBroadcasterGoesToTheRightChannelOrNowhere() {
        val expected = mapOf(
            "beIN Sports 1" to "c2", "S Sport 1" to "c5", "S Sport 2" to "c6", "beIN Sports 3" to null,
            "TRT Spor" to null, "Tivibu Spor 1" to "c7", "Exxen Spor 1" to "c8", "TRT Spor Yıldız" to "c9",
            "TRT 1" to "c12", "beIN Sports 2" to null, "Smart Spor" to "c11", "A Spor" to "c10",
            "beIN Sports MAX 1" to "c4"
        )
        expected.forEach { (broadcaster, id) -> assertEquals(broadcaster, id, index.match(broadcaster)?.channel?.id) }
    }

    @Test
    fun channelNumbersNeverMix() {
        assertEquals("c4", index.match("beIN SPORTS MAX 1")?.channel?.id)
        assertTrue(index.match("beIN SPORTS 1")!!.alternates.none { it.id == "c4" })
        assertNull("beIN 10 listede yok, beIN 1 açılmamalı", index.match("beIN SPORTS 10"))
    }

    @Test
    fun hdComesFirstThen4kThenBackup() {
        val m = index.match("beIN Sports 1")!!
        assertEquals("c2", m.channel.id)
        assertEquals(listOf("c3", "c1"), m.alternates.map { it.id })
    }

    @Test
    fun turkishLettersAreFolded() {
        assertEquals("trt spor yildiz", ChannelKeys.fold("TRT Spor Yıldız"))
        assertEquals("istanbul cagri", ChannelKeys.fold("İSTANBUL ÇAĞRI"))
        assertEquals(ChannelKeys.of("TRT SPOR YILDIZ").id, ChannelKeys.of("trt spor yıldız").id)
    }

    @Test
    fun manualMappingWinsAndSurvivesAListRefresh() {
        val key = ChannelKeys.of("TRT Spor").id
        val manual = mapOf(key to "TR: TRT SPOR YILDIZ HD")
        assertEquals(MatchReason.MANUAL, index.match("TRT Spor", manual)?.reason)
        // Liste yenilendi: kimlikler değişti, ad aynı.
        val refreshed = ChannelIndex(playlist.mapIndexed { i, ch -> ch.copy(id = "new$i") })
        assertEquals("new8", refreshed.match("TRT Spor", manual)?.channel?.id)
    }

    @Test
    fun nearMissesAreOnlySuggested() {
        assertNull(index.match("TRT Spor"))
        assertEquals("c9", index.suggestions("TRT Spor").first().id)
    }

    @Test
    fun broadcastsOfTheUsersCountryAreTriedFirst() {
        val broadcasts = listOf(Broadcast("Sky Sports Main Event", "GB"), Broadcast("S Sport 1", "TR"))
        assertEquals("c5", index.matchFirst(broadcasts, emptyMap(), "TR")?.channel?.id)
    }

    @Test
    fun largeListStaysFast() {
        val big = (1..20_000).map { PlaylistChannel("x$it", "TR | KANAL $it HD") } + playlist
        val start = System.nanoTime()
        val bigIndex = ChannelIndex(big)
        repeat(300) { bigIndex.match("beIN Sports ${it % 5 + 1}") }
        val ms = (System.nanoTime() - start) / 1_000_000
        println("20 bin kanal + 300 eşleştirme: $ms ms")
        assertEquals("c2", bigIndex.match("beIN Sports 1")?.channel?.id)
        assertTrue("çok yavaş: $ms ms", ms < 3_000)
    }
}
