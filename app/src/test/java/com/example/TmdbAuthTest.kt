package com.example

import com.example.data.api.TmdbAuth
import com.example.data.api.TmdbAuthInterceptor
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * TmdbAuth: link olarak girilmiş anahtarın reddi, jetonun Bearer başlığıyla gitmesi ve 401'de yedek
 * anahtara geçiş. TmdbAuthInterceptor yalnızca api.themoviedb.org isteklerine dokunduğu için bu alan
 * adı test DNS'iyle yerel MockWebServer'a yönlendirilir. Robolectric: gömülü jeton android Base64 ile çözülür.
 * Testler gerçek anahtar/jeton değerini hiçbir yere yazdırmaz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TmdbAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    // Her test farklı sahte değer kullanır: TmdbAuth "reddedilen anahtar" listesini süreç boyunca tutar.
    private fun fakeV3Key(seed: Char) = seed.toString().repeat(32)
    private fun fakeJwt(seed: Char) = "eyJ" + seed.toString().repeat(60) + "." + seed.toString().repeat(60) + ".sig" + seed

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname == TmdbAuth.TMDB_HOST) listOf(InetAddress.getByName("127.0.0.1")) else Dns.SYSTEM.lookup(hostname)
            })
            .addInterceptor(TmdbAuthInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tmdbUrl(apiKey: String?): String {
        val base = "http://${TmdbAuth.TMDB_HOST}:${server.port}/3/movie/550?language=tr-TR"
        return if (apiKey == null) base else "$base&api_key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"
    }

    private fun call(url: String): Int = client.newCall(Request.Builder().url(url).build()).execute().use { it.code }

    @Test
    fun linkEnteredInsteadOfKey_isRejected() {
        val link = "https://www.themoviedb.org/settings/api/request"
        assertFalse(TmdbAuth.isUsable(link))
        assertFalse(TmdbAuth.isUsable("placeholder"))
        assertFalse(TmdbAuth.isUsable(""))
        assertFalse(TmdbAuth.isUsable(null))
        assertTrue(TmdbAuth.isUsable(fakeV3Key('a')))
        assertTrue(TmdbAuth.isUsable(fakeJwt('a')))
        assertNotEquals(link, TmdbAuth.resolve(link))
        assertEquals("GEÇERSİZ: anahtar değil, bir link girilmiş", TmdbAuth.describe(link))
    }

    @Test
    fun linkAsApiKeyParameter_isNeverSentToTmdb() {
        server.enqueue(MockResponse().setBody("{}"))
        call(tmdbUrl("https://www.themoviedb.org/settings/api/request"))
        val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
        val apiKey = sent.requestUrl?.queryParameter("api_key")
        assertFalse("Link api_key olarak gönderilmemeli", apiKey.orEmpty().startsWith("http"))
        assertTrue(
            "Link yerine geçerli bir kimlik bilgisi kullanılmalı",
            TmdbAuth.isUsable(apiKey) || sent.getHeader("Authorization")?.startsWith("Bearer eyJ") == true
        )
    }

    @Test
    fun readAccessToken_goesInBearerHeader_notInQuery() {
        val jwt = fakeJwt('b')
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(200, call(tmdbUrl(jwt)))
        val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer $jwt", sent.getHeader("Authorization"))
        assertNull("Jeton URL'de görünmemeli", sent.requestUrl?.queryParameter("api_key"))
        assertEquals("tr-TR", sent.requestUrl?.queryParameter("language"))
    }

    @Test
    fun v3Key_goesInApiKeyQuery_withoutAuthorizationHeader() {
        val key = fakeV3Key('c')
        server.enqueue(MockResponse().setBody("{}"))
        call(tmdbUrl(key))
        val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(key, sent.requestUrl?.queryParameter("api_key"))
        assertNull(sent.getHeader("Authorization"))
    }

    @Test
    fun unauthorized401_retriesOnceWithFallbackCredential() {
        val badKey = fakeV3Key('d')
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"status_code\":7}"))
        server.enqueue(MockResponse().setBody("{\"id\":550}"))

        assertEquals(200, call(tmdbUrl(badKey)))
        assertEquals(2, server.requestCount)

        val first = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(badKey, first.requestUrl?.queryParameter("api_key"))

        val retry = server.takeRequest(5, TimeUnit.SECONDS)!!
        val retryKey = retry.requestUrl?.queryParameter("api_key")
        val retryAuth = retry.getHeader("Authorization")
        assertNotEquals("Reddedilen anahtar tekrar kullanılmamalı", badKey, retryKey)
        assertTrue(
            "Yedek kimlik bilgisi (gömülü jeton ya da anahtar) kullanılmalı",
            retryAuth?.startsWith("Bearer eyJ") == true || TmdbAuth.isUsable(retryKey)
        )
        assertTrue(TmdbAuth.lastUnauthorizedAt > 0)
    }

    @Test
    fun unauthorizedTwice_doesNotLoop() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(401, call(tmdbUrl(fakeV3Key('e'))))
        assertTrue("En fazla bir kez yeniden denenmeli", server.requestCount <= 2)
    }

    @Test
    fun nonTmdbHost_isNotTouched() {
        val plain = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor(TmdbAuthInterceptor()).build()
        server.enqueue(MockResponse().setBody("ok"))
        plain.newCall(Request.Builder().url(server.url("/iptv/list.m3u")).build()).execute().close()
        val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(sent.getHeader("Authorization"))
        assertNull(sent.requestUrl?.queryParameter("api_key"))
    }
}
