package com.example

import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonCreditsLookup
import com.example.data.repository.PersonWorksMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TMDB filmografisini kütüphaneyle eşleştiren saf mantığın testleri (JVM, Android gerektirmez). */
class PersonWorksMatcherTest {

    private var nextId = 1

    private fun movie(title: String, cast: String = "", logo: String? = "http://poster") = IPTVItem(
        id = nextId++, playlistId = 1, name = title, cleanedName = title, logoUrl = logo,
        streamUrl = "http://example.com/movie/${nextId}.mp4", category = "Film", type = "MOVIE", cast = cast
    )

    private fun credit(
        id: Int,
        title: String,
        original: String = title,
        year: Int? = null,
        type: String = "movie",
        popularity: Double = 10.0
    ) = PersonCredit(id, type, title, original, year, "http://img/$id.jpg", popularity)

    private fun lookup(vararg credits: PersonCredit) = PersonCreditsLookup(1, "Kişi", null, credits.toList())

    // ---------------- Türkçe harfler ----------------

    @Test
    fun fold_turkishLettersAndCaseCollapseToSameKey() {
        val expected = PersonWorksMatcher.fold("karadayi")
        assertEquals(expected, PersonWorksMatcher.fold("KARADAYI"))
        assertEquals(expected, PersonWorksMatcher.fold("Karadayı"))
        // "İ" küçük harfe çevrilince i + birleşik nokta olur; katlama bunu da sadeleştirmeli.
        assertEquals(PersonWorksMatcher.fold("esaretin bedeli"), PersonWorksMatcher.fold("ESARETİN BEDELİ"))
        assertEquals("sevgili gecmis", PersonWorksMatcher.fold("Sevgili Geçmiş"))
        assertEquals("gunesi beklerken", PersonWorksMatcher.fold("Güneşi Beklerken"))
        assertEquals("", PersonWorksMatcher.fold(null))
        assertEquals("", PersonWorksMatcher.fold("   "))
    }

    @Test
    fun match_turkishUppercaseLibraryTitle_findsTmdbCredit() {
        val library = listOf(movie("KARADAYI"), movie("Başka Bir Film"))
        val result = PersonWorksMatcher.match(lookup(credit(100, "Karadayı", type = "tv")), library, excludeItemId = -1)
        assertEquals(listOf("KARADAYI"), result.inLibrary.map { it.item.name })
        assertTrue("Kütüphanede olan yapım 'kütüphanede yok' listesinde tekrar görünmemeli", result.notInLibrary.isEmpty())
    }

    @Test
    fun match_providerTagsAreIgnored() {
        val library = listOf(movie("TR: Ayla 4K"), movie("Ayla (2017) Türkçe Dublaj 1080p"))
        val result = PersonWorksMatcher.match(lookup(credit(7, "Ayla", year = 2017)), library, excludeItemId = -1)
        assertEquals("Aynı yapımın iki sürümü tek sonuç vermeli", 1, result.inLibrary.size)
    }

    // ---------------- Çift dilli adlar ----------------

    @Test
    fun match_originalTitleAndBilingualLibraryName() {
        val library = listOf(
            movie("The Shawshank Redemption"),
            movie("Esaretin Bedeli - The Shawshank Redemption")
        )
        val shawshank = credit(278, "Esaretin Bedeli", original = "The Shawshank Redemption", year = 1994)
        val result = PersonWorksMatcher.match(lookup(shawshank), library, excludeItemId = -1)
        assertEquals(1, result.inLibrary.size)
        assertEquals(278, result.inLibrary.single().credit?.tmdbId)
    }

    @Test
    fun match_shortTitleDoesNotMatchInsideLongerOne() {
        // Eski contains() tabanlı eşleştirme "Red" → "Predator" yanlış eşleşmesi üretiyordu.
        val library = listOf(movie("Predator"), movie("Red Notice"))
        val result = PersonWorksMatcher.match(lookup(credit(1, "Red")), library, excludeItemId = -1)
        assertTrue(result.inLibrary.isEmpty())
    }

    // ---------------- Yıl kontrolü ----------------

    @Test
    fun match_movieYearMustBeWithinOneYear() {
        val library = listOf(movie("Dune 1984"), movie("Dune 2021"))
        val dune2021 = credit(438631, "Dune", year = 2021)
        val result = PersonWorksMatcher.match(lookup(dune2021), library, excludeItemId = -1)
        assertEquals(listOf("Dune 2021"), result.inLibrary.map { it.item.name })

        val offByOne = PersonWorksMatcher.match(lookup(credit(5, "Yedi", year = 1995)), listOf(movie("Yedi 1996")), -1)
        assertEquals(1, offByOne.inLibrary.size)
    }

    @Test
    fun match_seriesIgnoreYearBecauseLaterSeasonsDiffer() {
        val library = listOf(movie("Ezel 2011"))
        val ezel = credit(9, "Ezel", year = 2009, type = "tv")
        assertEquals(1, PersonWorksMatcher.match(lookup(ezel), library, -1).inLibrary.size)
    }

    // ---------------- Diğer ----------------

    @Test
    fun match_excludesCurrentlyOpenContent() {
        val open = movie("Ayla")
        val library = listOf(open, movie("Müslüm"))
        val result = PersonWorksMatcher.match(
            lookup(credit(1, "Ayla", year = 2017), credit(2, "Müslüm", year = 2018)),
            library,
            excludeItemId = open.id
        )
        assertEquals(listOf("Müslüm"), result.inLibrary.map { it.item.name })
        assertFalse(result.notInLibrary.any { it.tmdbId == 1 })
    }

    @Test
    fun searchByCast_matchesTurkishNamesRegardlessOfCase() {
        val library = listOf(
            movie("Film 1", cast = "KIVANÇ TATLITUĞ, Beren Saat"),
            movie("Film 2", cast = "Başka Oyuncu"),
            movie("Film 3", cast = "kivanc tatlitug")
        )
        val found = PersonWorksMatcher.searchByCast("Kıvanç Tatlıtuğ", library, excludeItemId = -1)
        assertEquals(setOf("Film 1", "Film 3"), found.map { it.item.name }.toSet())
        assertTrue(PersonWorksMatcher.searchByCast("Al", library, -1).isEmpty())
    }

    @Test
    fun primaryName_usesFirstOfMultipleNames() {
        assertEquals("Sema Ergenekon", PersonWorksMatcher.primaryName("Sema Ergenekon, Eylem Canpolat"))
        assertEquals("Tek İsim", PersonWorksMatcher.primaryName("  Tek İsim  "))
    }
}
