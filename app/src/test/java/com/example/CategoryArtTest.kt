package com.example

import com.example.util.CategoryArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryArtTest {
    @Test fun genresMatchInTurkishAndEnglish() {
        assertEquals(R.drawable.genre_action, CategoryArt.forFolder("TR | AKSİYON FİLMLERİ"))
        assertEquals(R.drawable.genre_horror, CategoryArt.forFolder("Horror Movies"))
        assertEquals(R.drawable.genre_scifi, CategoryArt.forFolder("Bilim Kurgu"))
        assertEquals(R.drawable.genre_drama, CategoryArt.forFolder("DRAM"))
    }

    @Test fun genreWinsOverSpecialAndPlatform() {
        assertEquals(R.drawable.genre_horror, CategoryArt.forFolder("4K Korku"))
        assertEquals(R.drawable.genre_comedy, CategoryArt.forFolder("Netflix Komedi"))
    }

    @Test fun specialFolders() {
        assertEquals(R.drawable.genre_4k, CategoryArt.forFolder("4K UHD Filmler"))
        assertEquals(R.drawable.genre_local, CategoryArt.forFolder("Yerli Filmler"))
        assertEquals(R.drawable.genre_classic, CategoryArt.forFolder("Klasikler"))
    }

    @Test fun platformKeepsBrandColor_unknownGetsGeneral() {
        assertNull(CategoryArt.forFolder("NETFLIX FİLMLERİ"))
        assertEquals(R.drawable.genre_thriller, CategoryArt.forFolder("Gerilim Filmleri"))
        assertEquals(R.drawable.genre_thriller, CategoryArt.forFolder("TR | Thriller"))
        assertEquals(R.drawable.genre_new, CategoryArt.forFolder("Vizyondakiler"))
        assertEquals(R.drawable.genre_new, CategoryArt.forFolder("YENİ EKLENENLER"))
        assertEquals(R.drawable.genre_new, CategoryArt.forFolder("EN | New Releases"))
        assertEquals(R.drawable.genre_general, CategoryArt.forFolder("Diğer"))
        assertEquals(R.drawable.genre_general, CategoryArt.forFolder("Karışık 2024"))
    }
}
