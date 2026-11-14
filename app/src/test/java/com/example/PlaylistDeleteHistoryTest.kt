package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ContinueWatching
import com.example.data.model.IPTVItem
import com.example.data.model.Playlist
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kullanıcı (14 Kasım 2026): listeyi sildikten sonra o listenin yapımları ("Kaldığın yerden devam") hâlâ görünüp
 * oynatılıyordu. Silinen listenin izleme kayıtları silinir; kalan listede de bulunan yapımın kaydı korunur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDeleteHistoryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.iptvDao()
    private val repo = IPTVRepository(dao, db)

    @After fun tearDown() = db.close()

    private fun item(id: Int, pid: Int, url: String) =
        IPTVItem(id = id, playlistId = pid, name = "F$id", cleanedName = "F$id", logoUrl = null, streamUrl = url, category = "Film", type = "MOVIE")

    private fun cw(itemId: Int, url: String) =
        ContinueWatching(itemId = itemId, itemName = "F$itemId", itemType = "MOVIE", itemLogo = null, streamUrl = url, category = "Film", progressSeconds = 100, totalSeconds = 1000)

    private suspend fun historyUrls() = dao.getAllContinueWatchingOnce().map { it.streamUrl }.sorted()

    @Test
    fun deletingOnlyPlaylist_removesItsHistory() = runBlocking {
        val pid = dao.insertPlaylist(Playlist(name = "A", url = "http://a/list.m3u", isActive = true)).toInt()
        dao.insertItems(listOf(item(1, pid, "http://a/1.mp4"), item(2, pid, "http://a/2.mp4")))
        dao.insertContinueWatching(cw(1, "http://a/1.mp4"))
        dao.insertContinueWatching(cw(9, "http://other/9.mp4")) // başka kaynaktan (listeye ait değil): kalır
        repo.deletePlaylist(pid)
        assertEquals(listOf("http://other/9.mp4"), historyUrls())
    }

    @Test
    fun xtreamCatalogEpisodes_removedByCredentials() = runBlocking {
        val pid = dao.insertPlaylist(Playlist(name = "X", url = "http://x.tv", isXtream = true, xtreamUsername = "ali", xtreamPassword = "123", isActive = true)).toInt()
        dao.insertItems(listOf(item(1, pid, "http://x.tv/movie/ali/123/1.mp4")))
        dao.insertContinueWatching(cw(-55, "http://x.tv/series/ali/123/55.mkv")) // katalog bölümü: tabloda satırı yok
        repo.deletePlaylist(pid)
        assertEquals(emptyList<String>(), historyUrls())
    }

    @Test
    fun sharedTitleInRemainingPlaylist_keepsHistory() = runBlocking {
        val a = dao.insertPlaylist(Playlist(name = "A", url = "http://a/list.m3u", isActive = true)).toInt()
        dao.insertItems(listOf(item(1, a, "http://shared/1.mp4"), item(2, a, "http://a/2.mp4")))
        dao.insertContinueWatching(cw(1, "http://shared/1.mp4"))
        dao.insertContinueWatching(cw(2, "http://a/2.mp4"))
        val b = dao.insertPlaylist(Playlist(name = "B", url = "http://b/list.m3u")).toInt()
        dao.switchActivePlaylist(b) // A kenara alınır
        dao.insertItems(listOf(item(11, b, "http://shared/1.mp4")))
        repo.deletePlaylist(a)   // kenardaki A silinir
        assertEquals(listOf("http://shared/1.mp4"), historyUrls())
    }
}
