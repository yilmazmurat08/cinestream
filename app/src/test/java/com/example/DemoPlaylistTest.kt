package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Google Play incelemecisi için hazırlanan deneme listesi (play-store/demo-playlist.m3u; serbest lisanslı test
 * yayınları ve Blender filmleri) uygulamada doğru okunur: 2 canlı test yayını, 3 film.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoPlaylistTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: IPTVRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = IPTVRepository(database.iptvDao())
        runBlocking { database.iptvDao().insertPlaylist(Playlist(id = 21, name = "Demo", url = "https://example.com/demo.m3u")) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun demoPlaylist_parsesIntoLiveTestStreamsAndMovies() {
        val file = listOf(File("../play-store/demo-playlist.m3u"), File("play-store/demo-playlist.m3u")).first { it.exists() }
        val count = runBlocking { repository.parseAndSaveM3UStream(21, file.inputStream()) }
        assertEquals(5, count)
        val items = runBlocking { database.iptvDao().getAllItemsDirect() }
        assertEquals(2, items.count { it.type == "LIVE" })
        assertEquals(3, items.count { it.type == "MOVIE" })
    }
}
