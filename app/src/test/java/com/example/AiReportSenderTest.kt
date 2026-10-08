package com.example

import com.example.ui.components.AiReport
import com.example.ui.components.AiReportReason
import com.example.ui.components.AiReportSender
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

/**
 * Yapay zekâ "Bildir": bildirim uygulamadan çıkmadan Google Formuna gönderilir (Play yapay zekâ içerik politikası).
 * Gönderilemezse (form kapalı, oturum açma istiyor, ağ yok) false döner ve e-posta yedeğine geçilir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiReportSenderTest {

    private val report = AiReport(AiReportReason.HARMFUL, "kısa not", "Önerilen film: Örnek", "chat")

    @Test
    fun formIsConfigured_forTheCineStreamReportsForm() {
        assertEquals(
            "https://docs.google.com/forms/d/e/1FAIpQLScktJisHhDoExfqnoGYcMG2YghS-EUlJq1SciDz1lWReI19xA/formResponse",
            AiReportSender.FORM_ACTION_URL
        )
        val fields = listOf(AiReportSender.FIELD_REASON, AiReportSender.FIELD_NOTE, AiReportSender.FIELD_RESPONSE, AiReportSender.FIELD_META)
        assertTrue(fields.all { it.matches(Regex("entry\\.\\d+")) })
        assertEquals("alanlar birbirinden farklı olmalı", 4, fields.toSet().size)
    }

    @Test
    fun sendsAllFieldsAsAFormPost() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("Your response has been recorded."))
        server.start()
        try {
            val sent = runBlocking { AiReportSender.sendTo(server.url("/formResponse").toString(), report) }
            assertTrue(sent)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            // Forma hiçbir anahtar/yetki bilgisi gitmez (TMDB anahtarı yalnızca TMDB adresine eklenir).
            assertEquals(null, request.getHeader("Authorization"))
            assertFalse(request.path.orEmpty().contains("api_key"))
            val form = request.body.readUtf8().split('&').associate {
                val (k, v) = it.split('=', limit = 2)
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }
            assertEquals(AiReportReason.HARMFUL.code, form[AiReportSender.FIELD_REASON])
            assertEquals("kısa not", form[AiReportSender.FIELD_NOTE])
            assertEquals("Önerilen film: Örnek", form[AiReportSender.FIELD_RESPONSE])
            assertTrue(form[AiReportSender.FIELD_META].orEmpty().startsWith("chat · v${BuildConfig.VERSION_NAME}"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun formThatRequiresSignIn_isReportedAsNotSent() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("Sign in to your Google Account"))
        server.start()
        try {
            assertFalse(runBlocking { AiReportSender.sendTo(server.url("/formResponse").toString(), report) })
        } finally {
            server.shutdown()
        }
    }
}
