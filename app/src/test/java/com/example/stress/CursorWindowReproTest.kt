package com.example.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import kotlinx.coroutines.flow.first
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

/**
 * Kullanıcı günlüğündeki çökmenin yeniden üretimi: tek satır 2 MB CursorWindow'u aşarsa ham DAO okuması
 * "Couldn't read row" ile patlar; depo (repository) katmanı ise kırpıp listeyi yine de verir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CursorWindowReproTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun movie(id: Int, logo: String? = "https://img/$id.jpg") = IPTVItem(
        id = id, playlistId = 1, name = "Film $id", cleanedName = "Film $id", logoUrl = logo,
        streamUrl = "http://x/$id.mp4", category = "Aile", type = "MOVIE"
    )

    @Test
    fun rawDaoRead_failsOnHugeRow_butRepositoryRecovers() = runBlocking {
        val dao = db.iptvDao()
        dao.insertItems((1..2000).map { movie(it) } + movie(5000, "data:image/png;base64," + "A".repeat(3_000_000)))

        val rawError = runCatching { dao.getItemsByTypeFlow("MOVIE").first() }.exceptionOrNull()
        println("Ham DAO okuması: ${rawError?.javaClass?.simpleName}: ${rawError?.message}")

        val items = withTimeout(20_000) { IPTVRepository(dao, db).getItemsByType("MOVIE").first() }
        assertEquals(2001, items.size)
        assertTrue(items.first { it.id == 5000 }.logoUrl == null)
    }
}
