package com.example

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android TV başlatıcısındaki afiş ve simge. Play, 1.1.7'yi (sürüm kodu 18) Android TV kalite yönergeleri
 * yüzünden reddetti: "Tam boyutlu uygulama banner'ı ve/veya simgesi yok", "simge alanın tamamını kaplamıyor"
 * (TV-BN). İncelemecinin ekran görüntüsünde simge büyük çizilmiş, logonun etrafında koyu kırmızı zemin çerçeve
 * gibi kalmıştı: logo sabit 72dp'ydi, simge büyüdükçe küçük kalıyordu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TvLauncherArtTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Afiş: xhdpi klasöründe 320x180 px PNG, saydamlıksız; uygulamada ve TV'de açılan ekranda tanımlı. */
    @Test
    @Config(qualifiers = "xhdpi")
    fun bannerIsFullSizeOpaqueXhdpiBitmap() {
        assertEquals(R.drawable.tv_banner, context.applicationInfo.banner)
        val launcher = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(R.drawable.tv_banner, launcher.banner)

        val options = BitmapFactory.Options().apply { inScaled = false }
        val banner = BitmapFactory.decodeResource(context.resources, R.drawable.tv_banner, options)
        assertNotNull("Afiş PNG olmalı (vektör değil)", banner)
        assertEquals(320, banner.width)
        assertEquals(180, banner.height)
        assertFalse("Afişte saydam alan olmamalı", banner.hasAlpha())
    }

    /**
     * TV simgesi: logo, simge hangi boyutta çizilirse çizilsin katmanın ortadaki %75'ini kaplar. Standart maske
     * ortadaki 2/3'ü (72dp/108dp) gösterir; Google TV'nin döşemesi biraz daha fazlasını (yaklaşık 80dp) gösteriyordu.
     */
    @Test
    @Config(qualifiers = "television-xhdpi")
    fun tvIconLogoFillsVisibleAreaAtAnySize() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val foreground = icon.foreground as InsetDrawable
        for (size in intArrayOf(216, 432, 1080)) {
            foreground.setBounds(0, 0, size, size)
            val logo = foreground.drawable!!.bounds
            val start = size / 8
            val end = size - size / 8
            assertTrue(
                "Boyut $size: logo $logo, görünen alan $start..$end",
                logo.left <= start && logo.top <= start && logo.right >= end && logo.bottom >= end,
            )
        }
    }
}
