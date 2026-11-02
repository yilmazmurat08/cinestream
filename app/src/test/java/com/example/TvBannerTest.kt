package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android TV ana ekran afişi. Play incelemesi (Android TV kalite yönergeleri, TV-BN) xhdpi klasöründe 320x180 px,
 * alanı tamamen dolduran ve uygulamanın adını taşıyan bir görsel ister; 1.1.7 (sürüm kodu 18) koyu zeminli vektör
 * afiş yüzünden reddedildi. Afiş tools/tv-banner/uret.sh ile üretilir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class TvBannerTest {

    @Test
    fun bannerIsFullSizeOpaqueXhdpiBitmap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(R.drawable.tv_banner, context.applicationInfo.banner)

        val options = BitmapFactory.Options().apply { inScaled = false }
        val banner = BitmapFactory.decodeResource(context.resources, R.drawable.tv_banner, options)
        assertNotNull("Afiş PNG olmalı (vektör değil)", banner)
        assertEquals(320, banner.width)
        assertEquals(180, banner.height)
        assertFalse("Afişte saydam alan olmamalı", banner.hasAlpha())
    }
}
