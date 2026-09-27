package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.HeroShelfRepository
import com.example.data.repository.ShelfImageKind
import com.example.data.repository.TMDBRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TV hero: rastgele seçim veritabanından yapılır, görseli olmayan ve yetişkin içerik asla seçilmez. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeroShelfTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val shelf = HeroShelfRepository(db.iptvDao(), TMDBRepository(app), tmdbApiKey = { "" })

    @After
    fun close() = db.close()

    private fun channel(id: Int, name: String, category: String, logo: String?) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = logo,
        streamUrl = "http://example.invalid/$id", category = category, type = "LIVE", tvgId = "ch$id"
    )

    @Test
    fun randomChannel_skipsAdultAndLogoless_andCarriesEpg() = runBlocking {
        db.iptvDao().insertItems(
            listOf(
                channel(1, "Haber TV", "Haber", "http://example.invalid/1.png"),
                channel(2, "Gece", "XXX Adult", "http://example.invalid/2.png"),
                channel(3, "Logosuz", "Haber", null),
                channel(4, "Boş Logo", "Haber", "")
            )
        )
        repeat(20) {
            val content = shelf.randomChannel { "Akşam Haberleri" }
            assertNotNull(content)
            assertEquals("Haber TV", content!!.title)
            assertEquals(ShelfImageKind.LOGO, content.imageKind)
            assertEquals("Akşam Haberleri", content.nowPlaying)
            assertEquals("Haber", content.genre)
            assertNull(content.rating) // uydurma puan yok
        }
    }

    @Test
    fun emptyDatabase_returnsNothing() = runBlocking {
        assertNull(shelf.randomChannel { null })
        assertNull(shelf.randomMovie())
        assertNull(shelf.randomSeries())
        assertTrue(db.iptvDao().randomItemsWithImage("MOVIE", 10).isEmpty())
    }
}
