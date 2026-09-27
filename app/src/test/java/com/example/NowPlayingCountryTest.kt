package com.example

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NowPlayingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTelephonyManager

/**
 * "Sinemada Bu Hafta" için ülke tespiti: şebeke ülkesi > SIM ülkesi > sistem bölgesi > TR.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NowPlayingCountryTest {

    private lateinit var context: Context
    private lateinit var telephony: ShadowTelephonyManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        telephony = shadowOf(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
    }

    @Test
    fun networkCountryWins_evenWhenSimIsFromAnotherCountry() {
        telephony.setNetworkCountryIso("de")
        telephony.setSimCountryIso("tr")
        assertEquals("DE", NowPlayingRepository.detectCountry(context))
    }

    @Test
    fun simCountryUsed_whenNetworkCountryMissing() {
        telephony.setNetworkCountryIso("")
        telephony.setSimCountryIso("nl")
        assertEquals("NL", NowPlayingRepository.detectCountry(context))
    }

    @Test
    fun invalidCodesAreSkipped() {
        telephony.setNetworkCountryIso("x1")
        telephony.setSimCountryIso("usa")
        val code = NowPlayingRepository.detectCountry(context)
        assertTrue("İki harfli geçerli bir kod beklenir: $code", code.length == 2 && code.all { it in 'A'..'Z' })
        assertTrue(code != "X1" && code != "USA")
    }

    @Test
    fun fallsBackToSystemRegionOrTurkey_withoutTelephony() {
        telephony.setNetworkCountryIso(null)
        telephony.setSimCountryIso(null)
        val code = NowPlayingRepository.detectCountry(context)
        val system = android.content.res.Resources.getSystem().configuration.locales[0].country.uppercase()
        val expected = if (system.length == 2) system else "TR"
        assertEquals(expected, code)
    }

    @Test
    fun countryNameFollowsUiLanguage() {
        assertEquals("Almanya", NowPlayingRepository.countryName("DE", "tr"))
        assertEquals("Germany", NowPlayingRepository.countryName("DE", "en"))
    }
}
