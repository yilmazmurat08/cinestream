package com.example

import com.example.util.CategoryArt
import org.junit.Assert.assertEquals
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

    @Test fun platformAndUnknownGetGeneral() {
        assertEquals(R.drawable.genre_general, CategoryArt.forFolder("NETFLIX FİLMLERİ"))
        assertEquals(R.drawable.genre_general, CategoryArt.forFolder("PLATFORM: AMAZON"))
        assertEquals(R.drawable.genre_mystery, CategoryArt.forFolder("GİZEM FİLMLERİ"))
        assertEquals(R.drawable.genre_classic, CategoryArt.forFolder("YEŞİLÇAM FİLMLERİ"))
        assertEquals(R.drawable.genre_thriller, CategoryArt.forFolder("Gerilim Filmleri"))
        assertEquals(R.drawable.genre_thriller, CategoryArt.forFolder("TR | Thriller"))
        assertEquals(R.drawable.genre_new, CategoryArt.forFolder("Vizyondakiler"))
        assertEquals(R.drawable.genre_new, CategoryArt.forFolder("YENİ EKLENENLER"))
        assertEquals(R.drawable.genre_new, CategoryArt.forFolder("EN | New Releases"))
        assertEquals(R.drawable.genre_general, CategoryArt.forFolder("Diğer"))
        assertEquals(R.drawable.genre_general, CategoryArt.forFolder("Karışık 2024"))
    }

    @Test fun liveFolders() {
        assertEquals(R.drawable.live_news, CategoryArt.forLive("HABER"))
        assertEquals(R.drawable.live_music, CategoryArt.forLive("MÜZİK"))
        assertEquals(R.drawable.live_sports, CategoryArt.forLive("TR | SPOR"))
        assertEquals(R.drawable.live_religious, CategoryArt.forLive("DİNİ"))
        assertEquals(R.drawable.live_relax, CategoryArt.forLive("RELAX LOOP"))
        assertEquals(R.drawable.live_radio, CategoryArt.forLive("Radyo"))
        assertEquals(R.drawable.genre_kids, CategoryArt.forLive("ÇOCUK"))
        assertEquals(R.drawable.genre_general, CategoryArt.forLive("CINEMA (7/24)"))
        assertEquals(R.drawable.live_tv, CategoryArt.forLive("TR | ULUSAL"))
        assertEquals(R.drawable.live_sports, CategoryArt.forLive("SÜPERLIG ÖZET"))
        assertEquals(R.drawable.live_sports, CategoryArt.forLive("GOL KRALLARI"))
        assertEquals(R.drawable.live_sports, CategoryArt.forLive("Süper Lig"))
        assertEquals(R.drawable.live_sports, CategoryArt.forLive("Canlı Maç"))
        assertEquals(R.drawable.live_religious, CategoryArt.forLive("Religious"))
        assertEquals(R.drawable.genre_classic, CategoryArt.forLive("YEŞİLÇAM FİLMLERİ"))
        assertEquals(R.drawable.genre_classic, CategoryArt.forFolder("YEŞİLÇAM DİZİLERİ"))
    }
}
