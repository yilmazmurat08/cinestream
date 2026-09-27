package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MetadataEnricher
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.repository.BoundedInputStream
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * M3U / Xtream ayrıştırma uç durumları: boş, bozuk, çok büyük listeler ve Xtream bağlantıları.
 * Ortak kural: hiçbir girdi çökme üretmez ve bozuk bir güncelleme kullanıcının mevcut listesini silmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class M3uXtreamEdgeCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: IPTVRepository
    private val playlistId = 11

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = IPTVRepository(database.iptvDao())
        runBlocking {
            database.iptvDao().insertPlaylist(Playlist(id = playlistId, name = "Edge", url = "http://example.com/list.m3u"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun parse(text: String): Int = runBlocking {
        repository.parseAndSaveM3UStream(playlistId, ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    private fun seedThreeChannels() {
        parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="Haber",Kanal A
            http://example.com/a.ts
            #EXTINF:-1 group-title="Haber",Kanal B
            http://example.com/b.ts
            #EXTINF:-1 group-title="Spor",Kanal C
            http://example.com/c.ts
            """.trimIndent()
        )
        assertEquals(3, runBlocking { database.iptvDao().getItemCount() })
    }

    @Test
    fun emptyStream_returnsZero_andKeepsExistingList() {
        seedThreeChannels()
        assertEquals(0, parse(""))
        assertEquals(0, parse("#EXTM3U\n"))
        assertEquals(0, parse("\n\n   \n"))
        assertEquals("Boş liste mevcut kanalları silmemeli", 3, runBlocking { database.iptvDao().getItemCount() })
        assertEquals(0, runBlocking { database.iptvDao().getStagingItemCount(playlistId) })
    }

    @Test
    fun htmlErrorPageInsteadOfPlaylist_keepsExistingList() {
        seedThreeChannels()
        val html = "<!DOCTYPE html><html><body><h1>403 Forbidden</h1></body></html>"
        assertEquals(0, parse(html))
        assertEquals(3, runBlocking { database.iptvDao().getItemCount() })
    }

    @Test
    fun brokenLines_areSkipped_withoutCrash() {
        val broken = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Eksik URL" group-title="X",Sadece Başlık
            #EXTINF:-1 tvg-logo="http://logo" group-title="Film",Geçerli Film
            http://example.com/movie/u/p/10.mp4
            http://example.com/basliksiz.ts
            #EXTINF:
            #EXTINF:-1 group-title="Canlı" tvg-logo="yarım,Yarım Tırnak
            http://example.com/yarim.ts
            ${"\u0000\u0001\u0002"}
            #EXTGRP:
            #EXTINF:-1,
            http://example.com/isimsiz.ts
        """.trimIndent()
        val count = parse(broken)
        val names = runBlocking { database.iptvDao().getAllItemsDirect() }.map { it.name }
        assertTrue("En az geçerli film ayrıştırılmalı: $names", "Geçerli Film" in names)
        assertEquals(count, names.size)
        assertTrue("URL'siz EXTINF bir sonraki URL'yi yanlış başlıkla almamalı", "Sadece Başlık" !in names)
    }

    @Test
    fun windowsLineEndingsAndBom_areHandled() {
        val crlf = "﻿#EXTM3U\r\n#EXTINF:-1 group-title=\"Haber\",TRT Haber\r\nhttp://example.com/trt.ts\r\n"
        assertEquals(1, parse(crlf))
        val item = runBlocking { database.iptvDao().getAllItemsDirect() }.single()
        assertEquals("TRT Haber", item.name)
        assertEquals("http://example.com/trt.ts", item.streamUrl)
    }

    @Test
    fun veryLargePlaylist_50k_isStreamedIntoDatabase() {
        // 40.000+ öğeli Xtream listeleri: satır satır, belleğe tamamen almadan.
        val total = 50_000
        val stream = object : InputStream() {
            private var index = 0
            private var chunk: ByteArray = "#EXTM3U\n".toByteArray()
            private var pos = 0
            override fun read(): Int {
                if (pos >= chunk.size) {
                    if (index >= total) return -1
                    index++
                    val group = when (index % 3) { 0 -> "Canlı TV"; 1 -> "Filmler"; else -> "Diziler" }
                    chunk = ("#EXTINF:-1 tvg-id=\"ch$index\" tvg-logo=\"http://logo/$index.png\" " +
                        "group-title=\"$group\",Kanal $index HD\nhttp://example.com/live/u/p/$index.ts\n").toByteArray()
                    pos = 0
                }
                return chunk[pos++].toInt() and 0xFF
            }
        }
        val started = System.nanoTime()
        val count = runBlocking { repository.parseAndSaveM3UStream(playlistId, stream) }
        val seconds = (System.nanoTime() - started) / 1e9
        assertEquals(total, count)
        assertEquals(total, runBlocking { database.iptvDao().getItemCount() })
        assertEquals(0, runBlocking { database.iptvDao().getStagingItemCount(playlistId) })
        assertTrue("50.000 öğe makul sürede işlenmeli ($seconds sn)", seconds < 120)
    }

    @Test
    fun oversizedDownload_isRejected_andExistingListKept() {
        seedThreeChannels()
        val huge = buildString {
            append("#EXTM3U\n")
            repeat(2_000) { append("#EXTINF:-1 group-title=\"X\",Yeni $it\nhttp://example.com/$it.ts\n") }
        }
        val limited = BoundedInputStream(ByteArrayInputStream(huge.toByteArray()), 4_096)
        try {
            runBlocking { repository.parseAndSaveM3UStream(playlistId, limited) }
            fail("Boyut sınırı aşılınca IOException beklenir")
        } catch (e: IOException) {
            // beklenen
        }
        val names = runBlocking { database.iptvDao().getAllItemsDirect() }.map { it.name }.toSet()
        assertEquals(setOf("Kanal A", "Kanal B", "Kanal C"), names)
    }

    // ---------------- Xtream bağlantıları ----------------

    private fun item(url: String, type: String = "MOVIE") = IPTVItem(
        id = 1, playlistId = 1, name = "x", cleanedName = "x", logoUrl = null,
        streamUrl = url, category = "c", type = type
    )

    @Test
    fun xtreamCredentials_standardPaths() {
        val movie = MetadataEnricher.parseXtreamCredentials(item("http://sunucu.tv:8080/movie/ali/s3cr3t/12345.mkv"))
        assertNotNull(movie)
        assertEquals("http://sunucu.tv:8080", movie!!.baseUrl)
        assertEquals("ali", movie.user)
        assertEquals("s3cr3t", movie.pass)
        assertEquals("12345", movie.streamId)
        assertEquals(false, movie.isSeries)

        val series = MetadataEnricher.parseXtreamCredentials(item("https://x.example/series/u/p/77.mp4", type = "SERIES"))
        assertNotNull(series)
        assertTrue(series!!.isSeries)
        assertEquals("77", series.streamId)

        val live = MetadataEnricher.parseXtreamCredentials(item("http://h:80/live/u/p/9.ts", type = "LIVE"))
        assertEquals("9", live?.streamId)

        // Kısa biçim: http://host/user/pass/id
        val short = MetadataEnricher.parseXtreamCredentials(item("http://h:8000/u/p/42"))
        assertEquals("42", short?.streamId)
    }

    @Test
    fun xtreamCredentials_invalidInputs_returnNull() {
        listOf(
            "",
            "   ",
            "http://example.com/playlist.m3u",
            "http://example.com/movie/u/p/abc.mkv",
            "rtmp://example.com/live/u/p/1",
            "not a url at all",
            "http://example.com/movie/u/p/"
        ).forEach { url ->
            assertNull("'$url' Xtream sayılmamalı", MetadataEnricher.parseXtreamCredentials(item(url)))
        }
    }
}
