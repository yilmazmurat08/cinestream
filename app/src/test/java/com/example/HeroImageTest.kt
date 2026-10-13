package com.example

import com.example.ui.components.HeroImage
import com.example.ui.components.pickHeroImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Detay üst görseli: önce yatay görseller (keskin), poster yalnızca son çare ve "geniş değil" olarak (kırpılmaz). */
class HeroImageTest {
    @Test fun providerBackdrop_comesFirst() =
        assertEquals(HeroImage("http://p/wide.jpg", true), pickHeroImage("http://p/wide.jpg", "http://t/wide.jpg", "http://p/poster.jpg"))

    @Test fun tmdbBackdrop_isUsed_whenProviderHasNone() =
        assertEquals(HeroImage("http://t/wide.jpg", true), pickHeroImage(null, "http://t/wide.jpg", "http://p/poster.jpg"))

    @Test fun posterOnly_isNotStretchedAsWide() =
        assertEquals(HeroImage("http://p/poster.jpg", false), pickHeroImage("", "  ", "http://p/poster.jpg"))

    @Test fun nothing_givesNull() = assertNull(pickHeroImage(null, null, null))
}
