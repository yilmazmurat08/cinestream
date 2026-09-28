package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonCreditsLookup
import com.example.data.repository.PersonWorksMatcher
import com.example.data.repository.TvCatalogRepository
import com.example.data.repository.TvSpecialSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Filmler/Diziler verisi: sayfalama, özel bölümler, yetişkin süzmesi ve kişi filmografisinde veritabanı süzmesinin
 * telefondaki tüm-liste eşleştirmesiyle AYNI sonucu vermesi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvCatalogTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repo = TvCatalogRepository(db.iptvDao())
    private var nextId = 1

    @After
    fun close() = db.close()

    private fun item(title: String, type: String = "MOVIE", category: String = "Aksiyon", rating: Double = 0.0, cast: String = "", director: String = "") =
        IPTVItem(
            id = nextId++, playlistId = 1, name = title, cleanedName = title, logoUrl = "http://p/$nextId.jpg",
            streamUrl = "http://s/$nextId.mp4", category = category, type = type, rating = rating, cast = cast, director = director
        )

    @Test
    fun moviePages_areLoadedInOrderWithoutDuplicates() = runBlocking {
        db.iptvDao().insertItems((1..130).map { item("Film %03d".format(it)) })
        val first = repo.moviePage("Aksiyon", 0, 60)
        val second = repo.moviePage("Aksiyon", 60, 60)
        val third = repo.moviePage("Aksiyon", 120, 60)
        assertEquals(60, first.size)
        assertEquals(60, second.size)
        assertEquals(10, third.size)
        assertEquals("Film 001", first.first().title)
        assertEquals(130, (first + second + third).map { it.key }.toSet().size)
    }

    @Test
    fun categories_specialsFirst_adultFlagged_andTop10SkipsAdult() = runBlocking {
        db.iptvDao().insertItems(
            listOf(
                item("Yetişkin", category = "XXX Adult", rating = 9.9),
                item("İyi Film", category = "Yeni Filmler", rating = 8.5),
                item("Orta Film", category = "Aksiyon", rating = 7.6)
            )
        )
        val cats = repo.categories("MOVIE") { 0 to it.lowercase() }
        assertEquals(TvSpecialSection.TOP10, cats[0].special)
        assertEquals(TvSpecialSection.TOP_RATED, cats[1].special)
        assertTrue(cats.first { it.name == "XXX Adult" }.isAdult)
        val top = repo.special("MOVIE", TvSpecialSection.TOP10).map { it.title }
        assertEquals(listOf("İyi Film", "Orta Film"), top)
        assertEquals(listOf("İyi Film"), repo.special("MOVIE", TvSpecialSection.TOP_RATED).map { it.title })
    }

    @Test
    fun personWorks_dbFilteredPool_matchesPhoneFullPoolResult() = runBlocking {
        val library = listOf(
            item("KARADAYI S01 E01", type = "SERIES", category = "Dizi"),
            item("KARADAYI S01 E02", type = "SERIES", category = "Dizi"),
            item("Ayla (2017) Türkçe Dublaj 1080p"),
            item("TR: Ayla 4K"),
            item("ESARETİN BEDELİ"),
            item("Kış Uykusu"),
            item("Spider-Man: No Way Home"),
            item("Alakasız Film"),
            item("Başka Bir Dizi S01 E01", type = "SERIES", category = "Dizi"),
            item("Oyuncu Filmi", cast = "Haluk Bilginer, Melisa Sözen"),
            item("KIŞ UYKUSU 4K")
        )
        db.iptvDao().insertItems(library)
        val lookup = PersonCreditsLookup(
            1, "Haluk Bilginer", null,
            listOf(
                PersonCredit(10, "tv", "Karadayı", "Karadayı", 2012, "http://i/10", 50.0),
                PersonCredit(11, "movie", "Ayla", "Ayla: The Daughter of War", 2017, "http://i/11", 40.0),
                PersonCredit(12, "movie", "Esaretin Bedeli", "The Shawshank Redemption", 1994, "http://i/12", 90.0),
                PersonCredit(13, "movie", "Kış Uykusu", "Winter Sleep", 2014, "http://i/13", 30.0),
                PersonCredit(14, "movie", "Örümcek-Adam: Eve Dönüş Yok", "Spider-Man: No Way Home", 2021, "http://i/14", 80.0),
                PersonCredit(15, "movie", "Hiç Olmayan Film", "Never Made", 2000, "http://i/15", 5.0)
            )
        )
        // Telefon: tüm kütüphane belleğe alınıp eşleştirilir.
        val phone = PersonWorksMatcher.merge(
            PersonWorksMatcher.match(lookup, library, -1).inLibrary,
            PersonWorksMatcher.searchByCast("Haluk Bilginer", library, -1)
        )
        // TV: adaylar veritabanından sorguyla süzülür, aynı eşleştirme uygulanır.
        val tv = repo.personWorks(lookup, "Haluk Bilginer", -1, null)

        assertEquals(phone.map { it.item.id }.toSet(), tv.inLibrary.map { it.item.id }.toSet())
        val names = tv.inLibrary.map { it.item.cleanedName }
        assertTrue(names.any { it.startsWith("KARADAYI") })
        assertTrue(names.contains("ESARETİN BEDELİ"))
        assertTrue(names.contains("Oyuncu Filmi"))
        assertFalse(names.contains("Alakasız Film"))
        assertEquals(listOf("Hiç Olmayan Film"), tv.notInLibrary.map { it.title })
    }
}
