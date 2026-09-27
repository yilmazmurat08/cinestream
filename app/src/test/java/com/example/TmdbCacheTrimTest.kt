package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.PersonDetailsEntity
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TMDB oyuncu/yönetmen önbelleği sınırı atlayan kayıtlarla da 200'ü geçmemeli. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TmdbCacheTrimTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun personCache_isTrimmedTo200() = runBlocking {
        val dao = db.iptvDao()
        repeat(260) { i ->
            dao.insertPersonDetails(PersonDetailsEntity(name = "kisi$i", displayName = "Kişi $i", role = "Oyuncu", biography = ""))
        }
        IPTVRepository(dao, db).trimTmdbCaches(cutoffTime = 0L)
        var count = 0
        repeat(260) { i -> if (dao.getPersonDetails("kisi$i") != null) count++ }
        assertEquals(200, count)
        assertNotNull("Playlists tablosu etkilenmemeli", dao.getAllPlaylists())
    }
}
