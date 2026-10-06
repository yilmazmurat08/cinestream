package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.IPTVRepository
import com.example.ui.components.AiReportStore
import com.example.util.AppUserAgent
import com.example.util.GeminiApi
import com.example.util.SecretCipher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Google Play yayın öncesi denetimde yapılan düzeltmelerin testleri. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayReleaseAuditTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun geminiRequest_keyInHeader_safetyAndSystemInstructionAdded() {
        val body = """{"contents":[{"parts":[{"text":"hi"}]}]}""".toRequestBody("application/json".toMediaType())
        val request = GeminiApi.request(GeminiApi.PRIMARY_MODEL, " AIzaTESTKEY ", body)

        assertNull("anahtar URL'de olmamalı", request.url.queryParameter("key"))
        assertEquals("AIzaTESTKEY", request.header("x-goog-api-key"))
        assertTrue(request.url.toString().endsWith("/models/gemini-2.5-flash:generateContent"))

        val sent = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        val json = JSONObject(sent)
        assertEquals(4, json.getJSONArray("safetySettings").length())
        assertTrue(json.getJSONObject("systemInstruction").toString().contains("film and TV assistant"))
        assertEquals("hi", json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test
    fun geminiPolicy_keepsExistingSettings_andModelsAreStable() {
        val custom = """{"safetySettings":[],"systemInstruction":{"parts":[{"text":"x"}]}}"""
        val out = JSONObject(GeminiApi.withPolicy(custom))
        assertEquals(0, out.getJSONArray("safetySettings").length())
        assertEquals("x", out.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        assertFalse(GeminiApi.MODELS.any { it.contains("1.5") || it.contains("latest") })
    }

    @Test
    fun secretCipher_neverCrashes_andReadsLegacyPlainValues() {
        // Robolectric'te Android Keystore yok: değer düz kalır ama okunabilir; çökme olmaz.
        val stored = SecretCipher.encrypt("AIzaPLAIN")
        assertEquals("AIzaPLAIN", SecretCipher.decrypt(stored))
        assertEquals("AIzaOLD", SecretCipher.decrypt("AIzaOLD"))
        assertEquals("", SecretCipher.decrypt(null))
        assertEquals("", SecretCipher.decrypt("enc1:bozuk-veri"))
    }

    @Test
    fun userAgent_isOwnIdentity_notAnotherApp() {
        listOf("TiviMate", "Smarters", "OTT Navigator", "VLC").forEach {
            assertFalse(AppUserAgent.app.contains(it, ignoreCase = true))
            assertFalse(AppUserAgent.BROWSER.contains(it, ignoreCase = true))
        }
        assertTrue(AppUserAgent.app.startsWith("CineStream/"))
    }

    @Test
    fun channelNameCleanup_doesNotRewriteTitlesIntoBrands() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = IPTVRepository(db.iptvDao())
            assertEquals("Now You See Me", repo.cleanChannelName("Now You See Me"))
            assertEquals("Snowden", repo.cleanChannelName("Snowden"))
            assertEquals("Again", repo.cleanChannelName("again"))
            assertEquals("Cinema Paradiso", repo.cleanChannelName("Cinema Paradiso"))
            // Gerçek temizlik kuralları devam eder: ülke öneki ve kalite eki.
            assertEquals("Haber Kanalı", repo.cleanChannelName("TR: Haber Kanalı HD"))
        } finally {
            db.close()
        }
    }

    @Test
    fun gemini_usesOnlyTheUsersOwnKey() {
        // Uygulamaya gömülü anahtar yok: kullanıcı anahtar girmediyse yapay zekâ kapalıdır.
        assertFalse(com.example.ui.tv.hasUsableGeminiKey(""))
        assertTrue(com.example.ui.tv.hasUsableGeminiKey("AIzaUSERKEY"))
        assertFalse(BuildConfig::class.java.declaredFields.any { it.name == "GEMINI_API_KEY" })
    }

    @Test
    fun aiReport_hidesReportedResponse() {
        val text = "Önerdiğim film: Örnek"
        assertFalse(AiReportStore.isHidden(context, text))
        AiReportStore.hide(context, text)
        assertTrue(AiReportStore.isHidden(context, text))
        assertFalse(AiReportStore.isHidden(context, "başka yanıt"))
    }
}
