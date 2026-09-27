package com.example

import com.example.ui.tv.PhoneEntryData
import com.example.ui.tv.PhoneEntryError
import com.example.ui.tv.PhoneEntryMode
import com.example.ui.tv.PhoneEntryServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.net.URLEncoder

/**
 * TV'deki "Telefonla gir" mini sunucusu: gerçek yerel soket üzerinden HTTP istekleri (JVM, Android gerektirmez).
 */
class PhoneEntryServerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: PhoneEntryServer? = null

    @After
    fun tearDown() {
        server?.stop()
        scope.cancel()
    }

    private fun startServer(
        mode: PhoneEntryMode = PhoneEntryMode.PLAYLIST,
        onSubmit: suspend (PhoneEntryData) -> PhoneEntryError? = { null }
    ): Pair<PhoneEntryServer, Int> {
        val s = PhoneEntryServer(mode, english = false, onSubmit = onSubmit)
        assertTrue(s.start(scope, hostAddress = "127.0.0.1"))
        server = s
        val port = s.url()!!.substringAfter("127.0.0.1:").substringBefore('/').toInt()
        return s to port
    }

    /** Ham HTTP/1.1 isteği gönderir, (durum satırı, gövde) döndürür. */
    private fun http(port: Int, method: String, target: String, body: String? = null): Pair<String, String> {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            val bytes = body?.toByteArray(Charsets.UTF_8)
            val request = buildString {
                append("$method $target HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                if (bytes != null) {
                    append("Content-Type: application/x-www-form-urlencoded\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                }
                append("Connection: close\r\n\r\n")
            }
            val out = socket.getOutputStream()
            out.write(request.toByteArray(Charsets.UTF_8))
            if (bytes != null) out.write(bytes)
            out.flush()
            val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            return response.substringBefore("\r\n") to response.substringAfter("\r\n\r\n", "")
        }
    }

    private fun form(vararg pairs: Pair<String, String>) =
        pairs.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    @Test
    fun requestWithoutToken_isForbidden() {
        val (_, port) = startServer()
        val (status, _) = http(port, "GET", "/")
        assertTrue(status, status.contains("403"))
    }

    @Test
    fun requestWithWrongToken_isForbidden() {
        val (s, port) = startServer()
        val (status, _) = http(port, "GET", "/?t=${s.token}x")
        assertTrue(status, status.contains("403"))
        val (postStatus, _) = http(port, "POST", "/submit?t=yanlis", form("m3u_url" to "http://x"))
        assertTrue(postStatus, postStatus.contains("403"))
    }

    @Test
    fun requestWithToken_servesForm() {
        val (s, port) = startServer()
        val (status, body) = http(port, "GET", "/?t=${s.token}")
        assertTrue(status, status.contains("200"))
        assertTrue(body.contains("<form", ignoreCase = true))
    }

    @Test
    fun playlistFormSubmission_isParsedCorrectly() {
        val received = CompletableDeferred<PhoneEntryData>()
        val (s, port) = startServer { data -> received.complete(data); null }
        val (status, _) = http(
            port, "POST", "/submit?t=${s.token}",
            form(
                "m3u_url" to "http://liste.example.com/get.php?username=ali&password=ş1f&type=m3u_plus",
                "epg_url" to "http://epg.example.com/guide.xml",
                "xtream_host" to "http://sunucu.tv:8080",
                "xtream_user" to "Ayşe Öz",
                "xtream_pass" to "p@ss w0rd&=+"
            )
        )
        assertTrue(status, status.contains("200"))
        val data = runBlocking { withTimeout(5_000) { received.await() } }
        assertEquals("http://liste.example.com/get.php?username=ali&password=ş1f&type=m3u_plus", data.m3uUrl)
        assertEquals("http://epg.example.com/guide.xml", data.epgUrl)
        assertEquals("http://sunucu.tv:8080", data.xtreamHost)
        assertEquals("Ayşe Öz", data.xtreamUser)
        assertEquals("p@ss w0rd&=+", data.xtreamPass)
    }

    @Test
    fun geminiKeySubmission_andValidationErrorIsShownAgain() {
        val received = CompletableDeferred<PhoneEntryData>()
        val (s, port) = startServer(PhoneEntryMode.GEMINI_KEY) { data ->
            received.complete(data)
            PhoneEntryError.INVALID_KEY
        }
        val (status, body) = http(port, "POST", "/submit?t=${s.token}", form("gemini_key" to "kisa"))
        assertTrue(status, status.contains("200"))
        assertEquals("kisa", runBlocking { withTimeout(5_000) { received.await() } }.geminiKey)
        assertTrue("Hata mesajı formla birlikte gösterilmeli", body.contains(PhoneEntryServer.errorMessage(PhoneEntryError.INVALID_KEY, false)))
    }

    @Test
    fun emptyOrOversizedBody_isRejected() {
        val (s, port) = startServer()
        val (status, _) = http(port, "POST", "/submit?t=${s.token}", "")
        assertTrue(status, status.contains("400"))
    }

    @Test
    fun unknownPath_returns404() {
        val (s, port) = startServer()
        val (status, _) = http(port, "GET", "/admin?t=${s.token}")
        assertTrue(status, status.contains("404"))
    }

    @Test
    fun startWithoutNetwork_returnsFalse() {
        val s = PhoneEntryServer(PhoneEntryMode.PLAYLIST, english = true) { null }
        assertFalse(s.start(scope, hostAddress = null))
    }

    @Test
    fun apiKeyFormatCheck() {
        assertTrue(PhoneEntryData.looksLikeApiKey("AIza" + "x".repeat(35)))
        assertFalse(PhoneEntryData.looksLikeApiKey("kisa"))
        assertFalse(PhoneEntryData.looksLikeApiKey("içinde boşluk olan uzun bir anahtar değeri"))
        assertEquals("liste.example.com", PhoneEntryData.defaultName("http://www.liste.example.com/get.php"))
        assertEquals("IPTV", PhoneEntryData.defaultName("bozuk adres"))
    }
}
