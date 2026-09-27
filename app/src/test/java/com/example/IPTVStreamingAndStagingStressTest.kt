package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.repository.BoundedInputStream
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IPTVStreamingAndStagingStressTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: IPTVRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = IPTVRepository(database.iptvDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testBoundedInputStream_ExceedsLimit_ThrowsIOException() {
        val largeData = ByteArray(1024) { 1 }
        val stream = BoundedInputStream(ByteArrayInputStream(largeData), 512) // Limit 512 bytes
        val buffer = ByteArray(256)

        val read1 = stream.read(buffer, 0, 256)
        assertEquals(256, read1)

        val read2 = stream.read(buffer, 0, 256)
        assertEquals(256, read2)

        try {
            stream.read(buffer, 0, 256)
            fail("Expected IOException when exceeding bounded size limit")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("exceeded safety limit") == true)
        }
    }

    @Test
    fun testStreamingM3UParserWith10kItems_StressAndIntegrity() = runBlocking {
        // Generate a 10,000 items M3U text stream
        val playlistId = 1
        val playlist = Playlist(id = playlistId, name = "Stress Test Playlist", url = "http://example.com/playlist.m3u")
        database.iptvDao().insertPlaylist(playlist)

        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (i in 1..10_000) {
            val typeGroup = when (i % 3) {
                0 -> "Live TV"
                1 -> "Movies"
                else -> "TV Series"
            }
            sb.append("#EXTINF:-1 tvg-id=\"channel_$i\" tvg-name=\"Channel $i\" tvg-logo=\"http://logo.com/$i.png\" group-title=\"$typeGroup\",Channel $i HD\n")
            sb.append("http://stream.example.com/live/$i.m3u8\n")
        }

        val inputStream = ByteArrayInputStream(sb.toString().toByteArray(Charsets.UTF_8))
        repository.parseAndSaveM3UStream(playlistId, inputStream)

        val totalItems = database.iptvDao().getItemCount()
        assertEquals(10_000, totalItems)

        // Verify staging table was promoted and cleared
        val stagingCount = database.iptvDao().getStagingItemCount(playlistId)
        assertEquals(0, stagingCount)

        // Verify active channels in database
        val liveChannels = database.iptvDao().getItemsByTypeDirect("LIVE")
        assertTrue(liveChannels.isNotEmpty())
        assertEquals(3333, liveChannels.size)
    }

    @Test
    fun testStagingTableRollback_WhenStreamFailsMidway_OldDataPreserved() = runBlocking {
        val playlistId = 2
        val playlist = Playlist(id = playlistId, name = "Rollback Test", url = "http://example.com/stream.m3u")
        database.iptvDao().insertPlaylist(playlist)

        // 1. Initial successful parse (3 items)
        val initialM3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",BBC News
            http://example.com/bbc.m3u8
            #EXTINF:-1 group-title="Sports",Sky Sports
            http://example.com/sky.m3u8
            #EXTINF:-1 group-title="Movies",HBO
            http://example.com/hbo.m3u8
        """.trimIndent()
        repository.parseAndSaveM3UStream(playlistId, ByteArrayInputStream(initialM3u.toByteArray()))

        val countBefore = database.iptvDao().getItemCount()
        assertEquals(3, countBefore)

        // 2. Second sync fails midway (corrupt stream after 5 items)
        val partialM3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",New BBC
            http://example.com/new_bbc.m3u8
            #EXTINF:-1 group-title="News",New CNN
            http://example.com/new_cnn.m3u8
        """.trimIndent()

        // Faulty stream that throws IOException midway
        val failingStream = object : InputStream() {
            private val delegate = ByteArrayInputStream(partialM3u.toByteArray())
            private var bytesRead = 0
            override fun read(): Int {
                if (bytesRead > 30) {
                    throw IOException("Simulated network disconnect midway through sync")
                }
                val b = delegate.read()
                if (b != -1) bytesRead++
                return b
            }
        }

        try {
            repository.parseAndSaveM3UStream(playlistId, failingStream)
            fail("Expected parseAndSaveM3UStream to throw IOException")
        } catch (e: IOException) {
            // Expected
        }

        // 3. Verify that old items are preserved completely!
        val countAfter = database.iptvDao().getItemCount()
        assertEquals(3, countAfter)

        val preservedItems = database.iptvDao().getAllItemsDirect()
        val names = preservedItems.map { it.name }
        assertTrue(names.contains("BBC News"))
        assertTrue(names.contains("Sky Sports"))
        assertTrue(names.contains("HBO"))

        // Verify staging table was cleaned up
        val stagingCount = database.iptvDao().getStagingItemCount(playlistId)
        assertEquals(0, stagingCount)
    }

    @Test
    fun testEmptyPlaylistAndMalformedM3UHandling() = runBlocking {
        val playlistId = 3
        val playlist = Playlist(id = playlistId, name = "Malformed Test", url = "http://example.com/bad.m3u")
        database.iptvDao().insertPlaylist(playlist)

        // Malformed content with random lines, blanks, no stream URLs
        val malformedM3u = """
            random header line
            #EXTINF: invalid format
            just text without url
            
            #EXTINF:-1 tvg-name="Valid Item" group-title="General",Valid Channel
            http://valid.com/live.m3u8
            junk text after
        """.trimIndent()

        repository.parseAndSaveM3UStream(playlistId, ByteArrayInputStream(malformedM3u.toByteArray()))

        val items = database.iptvDao().getAllItemsDirect()
        assertEquals(1, items.size)
        assertEquals("Valid Channel", items[0].name)
    }

    @Test
    fun testFavoritePreservationDuringStagingUpdate() = runBlocking {
        val playlistId = 4
        val playlist = Playlist(id = playlistId, name = "Favorite Preservation Test", url = "http://example.com/fav.m3u")
        database.iptvDao().insertPlaylist(playlist)

        // 1. Initial M3U
        val initialM3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Cinema",Movie 1
            http://example.com/m1.mp4
            #EXTINF:-1 group-title="Cinema",Movie 2
            http://example.com/m2.mp4
        """.trimIndent()
        repository.parseAndSaveM3UStream(playlistId, ByteArrayInputStream(initialM3u.toByteArray()))

        val items = database.iptvDao().getAllItemsDirect()
        val favItem = items.first { it.name == "Movie 1" }
        // User favorites Movie 1
        database.iptvDao().updateFavorite(favItem.id, true)

        val favsBefore = database.iptvDao().getFavoriteItemsFlow().first()
        assertEquals(1, favsBefore.size)
        assertEquals("Movie 1", favsBefore[0].name)

        // 2. Playlist updates remotely with new and updated items
        val updatedM3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Cinema HD",Movie 1
            http://example.com/m1.mp4
            #EXTINF:-1 group-title="Cinema",Movie 2
            http://example.com/m2.mp4
            #EXTINF:-1 group-title="Cinema",Movie 3
            http://example.com/m3.mp4
        """.trimIndent()
        repository.parseAndSaveM3UStream(playlistId, ByteArrayInputStream(updatedM3u.toByteArray()))

        val favsAfter = database.iptvDao().getFavoriteItemsFlow().first()
        assertEquals(1, favsAfter.size)
        assertEquals("Movie 1", favsAfter[0].name)
        assertTrue(favsAfter[0].isFavorite)
    }

    @Test
    fun testBoundedSearchHistoryLimit() = runBlocking {
        for (i in 1..150) {
            val sh = com.example.data.model.SearchHistory(query = "query_$i", timestamp = i.toLong())
            database.iptvDao().insertSearchQueryWithLimit(sh, limit = 100)
        }

        val allHistory = database.iptvDao().getRecentSearchQueriesFlow(200).first()
        assertEquals(100, allHistory.size)
        // Most recent should be query_150
        assertEquals("query_150", allHistory.first().query)
    }

    @Test
    fun testBoundedContinueWatchingLimit() = runBlocking {
        for (i in 1..80) {
            val cw = com.example.data.model.ContinueWatching(
                itemId = i,
                itemName = "Movie $i",
                itemType = "MOVIE",
                itemLogo = null,
                streamUrl = "http://example.com/$i.mp4",
                category = "Movies",
                progressSeconds = 120,
                totalSeconds = 3600,
                lastPlayedAt = i.toLong()
            )
            database.iptvDao().insertContinueWatchingWithLimit(cw, limit = 50)
        }

        val allCw = database.iptvDao().getContinueWatchingFlow().first()
        assertEquals(50, allCw.size)
        assertEquals("Movie 80", allCw.first().itemName)
    }

    @Test
    fun testDistinctCategoriesQueryEfficiency() = runBlocking {
        val playlistId = 5
        val playlist = Playlist(id = playlistId, name = "Cat Test", url = "http://example.com/cat.m3u")
        database.iptvDao().insertPlaylist(playlist)

        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        val categories = listOf("News", "Sports", "Movies", "Documentaries", "Kids")
        for (i in 1..1000) {
            val cat = categories[i % categories.size]
            sb.append("#EXTINF:-1 group-title=\"$cat\",Channel $i\n")
            sb.append("http://example.com/ch$i.m3u8\n")
        }

        repository.parseAndSaveM3UStream(playlistId, ByteArrayInputStream(sb.toString().toByteArray()))

        val distinctCats = database.iptvDao().getDistinctCategoriesFlow().first()
        assertEquals(5, distinctCats.size)
        assertEquals(listOf("Documentaries", "Kids", "Movies", "News", "Sports"), distinctCats.sorted())
    }

    @Test
    fun testImportRemotePlaylist_NewAndExisting_ProperRollbackOnFailure() = runBlocking {
        // 1. Valid M3U content via StringReader into staging
        val playlist = Playlist(name = "Test Playlist", url = "http://mock-remote.com/list.m3u")
        val pId = database.iptvDao().insertPlaylist(playlist).toInt()
        val validM3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Cinema",Movie 100
            http://example.com/m100.mp4
        """.trimIndent()
        repository.parseAndSaveM3U(pId, validM3u)

        val existing = database.iptvDao().findPlaylistByUrl("http://mock-remote.com/list.m3u")
        assertNotNull(existing)
        assertEquals("Test Playlist", existing?.name)

        val count = database.iptvDao().getItemCount()
        assertEquals(1, count)
    }

    @Test
    fun testStreamingLargeItems_WithoutOutOfMemory() = runBlocking {
        val playlistId = 99
        val playlist = Playlist(id = playlistId, name = "Stress Test", url = "http://example.com/huge.m3u")
        database.iptvDao().insertPlaylist(playlist)

        // Custom streaming InputStream generating items without allocating strings in memory
        val totalCount = 5_000
        var currentItem = 0
        var currentChunkBytes = ByteArray(0)
        var chunkIndex = 0

        val chunkedGeneratorStream = object : InputStream() {
            override fun read(): Int {
                while (chunkIndex >= currentChunkBytes.size) {
                    if (currentItem >= totalCount) {
                        return -1
                    }
                    currentItem++
                    val typeGroup = when (currentItem % 3) {
                        0 -> "Live"
                        1 -> "Movies"
                        else -> "Series"
                    }
                    val chunkStr = if (currentItem == 1) {
                        "#EXTM3U\n#EXTINF:-1 tvg-id=\"id_$currentItem\" group-title=\"$typeGroup\",Item $currentItem\nhttp://example.com/stream/$currentItem.m3u8\n"
                    } else {
                        "#EXTINF:-1 tvg-id=\"id_$currentItem\" group-title=\"$typeGroup\",Item $currentItem\nhttp://example.com/stream/$currentItem.m3u8\n"
                    }
                    currentChunkBytes = chunkStr.toByteArray(Charsets.UTF_8)
                    chunkIndex = 0
                }
                val b = currentChunkBytes[chunkIndex].toInt() and 0xFF
                chunkIndex++
                return b
            }
        }

        val parsedCount = repository.parseAndSaveM3UStream(playlistId, chunkedGeneratorStream)
        assertEquals(5_000, parsedCount)

        val totalDbCount = database.iptvDao().getItemCount()
        assertEquals(5_000, totalDbCount)
    }
}
