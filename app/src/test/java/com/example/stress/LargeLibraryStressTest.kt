package com.example.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

/**
 * Büyük kütüphane: 200.000 öğeli liste içe aktarma, tür bazlı okuma, liste yenileme sırasında okuma,
 * favori/yenileme yarışları. Bellek ve süre ölçülür.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LargeLibraryStressTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: IPTVRepository
    private val playlistId = 21

    @Before
    fun setUp() {
        requireStressMode()
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = IPTVRepository(db.iptvDao(), db)
        runBlocking { db.iptvDao().insertPlaylist(Playlist(id = playlistId, name = "Stres", url = "http://example.com/s.m3u")) }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    /** Belleğe almadan üretilen M3U: canlı / film / dizi karışık, arada yetişkin ve bozuk satırlar. */
    private fun m3u(total: Int, seed: Int = 0): InputStream = object : InputStream() {
        private var index = 0
        private var chunk = "#EXTM3U\n".toByteArray()
        private var pos = 0
        override fun read(): Int {
            if (pos >= chunk.size) {
                if (index >= total) return -1
                index++
                val n = index + seed
                val line = when (index % 5) {
                    0 -> "#EXTINF:-1 tvg-logo=\"http://logo/$n.png\" group-title=\"Spor\",Spor Kanal $n\nhttp://h/live/u/p/$n.ts\n"
                    1 -> "#EXTINF:-1 tvg-logo=\"http://logo/$n.png\" group-title=\"Filmler\",Film $n (2020)\nhttp://h/movie/u/p/$n.mp4\n"
                    2 -> "#EXTINF:-1 tvg-logo=\"http://logo/$n.png\" group-title=\"Diziler\",Dizi ${n % 700} S01 E${(n % 20) + 1}\nhttp://h/series/u/p/$n.mkv\n"
                    3 -> "#EXTINF:-1 group-title=\"XX: Yetişkin\",XX: Kanal $n\nhttp://h/live/u/p/$n.ts\n"
                    else -> if (index % 1000 == 4) "#EXTINF bozuk satır\nbu bir adres değil\n"
                    else "#EXTINF:-1 tvg-logo=\"http://logo/$n.png\" group-title=\"Ulusal\",Ulusal $n HD\nhttp://h/live/u/p/$n.ts\n"
                }
                chunk = line.toByteArray()
                pos = 0
            }
            return chunk[pos++].toInt() and 0xFF
        }
    }

    @Test
    fun import200k_thenReadEveryType() = runBlocking {
        val heapBefore = usedHeapMb()
        val (count, importSec) = timed("200.000 öğe içe aktarma") { repository.parseAndSaveM3UStream(playlistId, m3u(200_000)) }
        assertTrue("En az 199.000 öğe kaydedilmeli, $count", count >= 199_000)
        assertTrue("İçe aktarma çok yavaş: $importSec sn", importSec < 240)

        var total = 0
        for (type in listOf("LIVE", "MOVIE", "SERIES")) {
            val (items, readSec) = timed("$type okuma") { withTimeout(120_000) { repository.getItemsByType(type).first() } }
            println("[STRES] $type: ${items.size} öğe")
            assertTrue("$type okuma çok yavaş: $readSec sn", readSec < 60)
            total += items.size
        }
        assertEquals("Tüm öğeler okunabilmeli", db.iptvDao().getItemCount(), total)
        println("[STRES] Yığın (heap): önce ${heapBefore} MB, sonra ${usedHeapMb()} MB")
    }

    @Test
    fun refreshingPlaylistRepeatedly_whileReading_keepsFavoritesAndNeverCrashes() = runBlocking {
        repository.parseAndSaveM3UStream(playlistId, m3u(20_000))
        val favId = repository.getItemsByType("MOVIE").first().first().id
        db.iptvDao().updateFavorite(favId, true)

        // Okuyucular: her tür için art arda emisyonlar toplanır, yazmalar sürerken.
        val readers = listOf("LIVE", "MOVIE", "SERIES").map { type ->
            async(Dispatchers.IO) { withTimeout(180_000) { repository.getItemsByType(type).take(4).toList() } }
        }
        val (_, refreshSec) = timed("5 kez liste yenileme") {
            repeat(5) { round -> repository.parseAndSaveM3UStream(playlistId, m3u(20_000)) ; println("[STRES] yenileme ${round + 1} tamam") }
        }
        val emissions = readers.awaitAll()
        emissions.forEach { assertTrue(it.isNotEmpty()) }
        println("[STRES] 5 yenileme: %.1f sn".format(refreshSec))

        val favorite = repository.getItemsByType("MOVIE").first().firstOrNull { it.id == favId }
        assertTrue("Yenilemeden sonra favori korunmalı", favorite?.isFavorite == true)
    }

    @Test
    fun hundredsOfFavoriteToggles_whileFlowsAreCollected() = runBlocking {
        repository.parseAndSaveM3UStream(playlistId, m3u(30_000))
        val ids = repository.getItemsByType("LIVE").first().take(300).map { it.id }
        val collector = launch(Dispatchers.IO) { repository.getItemsByType("LIVE").collect { } }
        timed("300 favori değişikliği (okuma sürerken)") {
            ids.forEachIndexed { i, id -> db.iptvDao().updateFavorite(id, i % 2 == 0) }
        }
        collector.cancel()
        val live = repository.getItemsByType("LIVE").first().associateBy { it.id }
        assertEquals(150, ids.count { live[it]?.isFavorite == true })
    }
}
