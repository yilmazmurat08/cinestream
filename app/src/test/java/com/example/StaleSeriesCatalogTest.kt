package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Eski (Xtream) liste silinip yalnızca canlı yayın içeren bir liste yüklenince, dizi kataloğu eski listeden kalıp
 * "açılmayan dizi kartları" olarak görünmemeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaleSeriesCatalogTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var repo: IPTVRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = IPTVRepository(db.iptvDao(), db)
        runBlocking {
            db.iptvDao().insertXtreamSeriesCatalog(
                listOf(XtreamSeriesCatalogEntity(seriesId = 1, name = "Away", canonicalKey = "away", coverUrl = "", categoryId = "Netflix"))
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun live(id: Int, playlist: Int, url: String) = IPTVItem(
        id = id, playlistId = playlist, name = "Kanal $id", cleanedName = "Kanal $id", logoUrl = null,
        streamUrl = url, category = "Ulusal", type = "LIVE"
    )

    private fun catalogSize() = runBlocking { db.iptvDao().seriesCatalogCount() }

    @Test
    fun deletingTheXtreamPlaylist_clearsItsSeriesCatalog() = runBlocking {
        val oldId = db.iptvDao().insertPlaylist(Playlist(name = "Eski", url = "http://h", isXtream = true, xtreamUsername = "u", xtreamPassword = "p")).toInt()
        db.iptvDao().insertItems(listOf(live(1, oldId, "http://h/live/u/p/10.ts")))
        repo.deletePlaylist(oldId)
        assertEquals("eski listenin dizileri kalmamalı", 0, catalogSize())
    }

    @Test
    fun catalogIsKept_whileAnXtreamSourceRemains() = runBlocking {
        val id = db.iptvDao().insertPlaylist(Playlist(name = "Xtream", url = "http://h", isXtream = true)).toInt()
        db.iptvDao().insertItems(listOf(live(1, id, "http://h/live/u/p/10.ts")))
        repo.dropSeriesCatalogIfNoXtreamSource()
        assertEquals(1, catalogSize())
    }

    @Test
    fun catalogLeftBehindByOldList_isDroppedWhenOnlyPlainM3uRemains() = runBlocking {
        // Eski liste zaten silinmiş; kalan liste Xtream adresi içermeyen düz bir canlı yayın listesi.
        val id = db.iptvDao().insertPlaylist(Playlist(name = "Canlı", url = "https://raw.githubusercontent.com/x/y.m3u")).toInt()
        db.iptvDao().insertItems(listOf(live(1, id, "http://yayin.example.com/kanal1.m3u8"), live(2, id, "https://cdn.example.com/a/b.m3u8")))
        repo.dropSeriesCatalogIfNoXtreamSource()
        assertEquals(0, catalogSize())
    }
}
