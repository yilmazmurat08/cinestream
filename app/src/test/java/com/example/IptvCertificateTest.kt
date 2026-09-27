package com.example

import com.example.data.api.HttpsCertificateFallbackInterceptor
import com.example.data.api.NetworkModule
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Google Play kuralı: IPTV istemcisi artık her sertifikaya güvenmiyor. Sertifikası bozuk https sunucu
 * reddedilir; bu durumda aynı adres bir kez http ile denenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IptvCertificateTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun selfSignedHttpsServer_isNotTrusted() {
        val selfSigned = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(selfSigned).build().sslSocketFactory(), false)
        server.enqueue(MockResponse().setBody("#EXTM3U"))
        server.start()

        val url = server.url("/list.m3u").newBuilder().host("localhost").build()
        val error = runCatching {
            NetworkModule.provideIptvOkHttpClient().newBuilder().connectTimeout(5, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).build()).execute().close()
        }.exceptionOrNull()

        // Eski "her şeye güven" istemcisi burada başarıyla "#EXTM3U" okurdu.
        assertTrue("Kendinden imzalı sertifika kabul edilmemeli", error is IOException)
    }

    @Test
    fun certificateError_retriesSameAddressOverHttp() {
        val tried = mutableListOf<String>()
        val chain = FakeChain(Request.Builder().url("https://iptv.example.com:8443/get.php?u=a").build()) { req ->
            tried += req.url.toString()
            if (req.url.isHttps) throw SSLPeerUnverifiedException("Hostname iptv.example.com not verified (certificate)")
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
        }
        val response = HttpsCertificateFallbackInterceptor().intercept(chain)
        assertEquals(200, response.code)
        assertEquals(listOf("https://iptv.example.com:8443/get.php?u=a", "http://iptv.example.com:8443/get.php?u=a"), tried)
    }

    @Test
    fun defaultHttpsPort_fallsBackToDefaultHttpPort_andOtherErrorsAreNotRetried() {
        val tried = mutableListOf<String>()
        val chain = FakeChain(Request.Builder().url("https://iptv.example.com/list.m3u").build()) { req ->
            tried += req.url.toString()
            if (req.url.isHttps) throw SSLPeerUnverifiedException("certificate")
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
        }
        HttpsCertificateFallbackInterceptor().intercept(chain)
        assertEquals("http://iptv.example.com/list.m3u", tried.last())

        val timeout = FakeChain(Request.Builder().url("https://iptv.example.com/list.m3u").build()) { throw java.net.SocketTimeoutException("timeout") }
        val error = runCatching { HttpsCertificateFallbackInterceptor().intercept(timeout) }.exceptionOrNull()
        assertTrue(error is java.net.SocketTimeoutException)
        assertFalse(HttpsCertificateFallbackInterceptor.isCertificateError(java.net.SocketTimeoutException()))
    }

    private class FakeChain(private val request: Request, private val handler: (Request) -> Response) : Interceptor.Chain {
        override fun request() = request
        override fun proceed(request: Request) = handler(request)
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connection(): Connection? = null
        override fun connectTimeoutMillis() = 0
        override fun readTimeoutMillis() = 0
        override fun writeTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
}
