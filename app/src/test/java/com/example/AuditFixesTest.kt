package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MetadataEnricher
import com.example.data.epg.EPGProvider
import com.example.data.epg.RealEpgProvider
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.data.repository.preferencesCorruptionHandler
import com.example.ui.screens.MultiScreenStatusOverlay
import com.example.ui.theme.MyApplicationTheme
import com.example.util.XtreamUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Son kullanıcı öncesi denetimde bulunan hatalar (önce hatayı gösteren test, sonra düzeltme). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class AuditFixesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    // ---------------------------------------------------------------- Ayar dosyası bozulması

    /** Bozuk ayar dosyası her kayıtta hata fırlatıyordu (giriş ekranında uygulamayı kapatıyordu); artık sıfırlanır. */
    @Test
    fun corruptedSettingsFile_isReplaced_insteadOfFailingEveryWrite() = runBlocking {
        val key = stringPreferencesKey("k")
        val garbage = "bu bir ayar dosyasi degil".toByteArray()

        val plainScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val plainFile = tmp.newFile("plain.preferences_pb").apply { writeBytes(garbage) }
        val plain = PreferenceDataStoreFactory.create(scope = plainScope) { plainFile }
        val failure = runCatching { plain.edit { it[key] = "x" } }.exceptionOrNull()
        assertTrue("korumasız dosya kayıtta hata fırlatır: $failure", failure is IOException)
        plainScope.cancel()

        val safeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val safeFile = tmp.newFile("safe.preferences_pb").apply { writeBytes(garbage) }
        val safe = PreferenceDataStoreFactory.create(corruptionHandler = preferencesCorruptionHandler(), scope = safeScope) { safeFile }
        safe.edit { it[key] = "x" }
        assertEquals("x", safe.data.first()[key])
        safeScope.cancel()
    }

    // ---------------------------------------------------------------- Xtream şifresi (& # +)

    @Test
    fun xtreamQueryValue_encodesOnlyCharactersThatBreakTheAddress() {
        assertEquals("abc123", XtreamUrls.queryValue("abc123"))
        assertEquals("a%2Bb%26c%23d", XtreamUrls.queryValue("a+b&c#d"))
        // Zaten kodlanmış / sunucunun doğru okuduğu değerler değişmez.
        assertEquals("p%40ss", XtreamUrls.queryValue("p%40ss"))
        assertEquals("p@ss", XtreamUrls.queryValue("p@ss"))
    }

    /** Şifrede & # + varsa sunucuya yanlış şifre gidiyordu (& adresi bölüyor, # kesiyor, + boşluk sayılıyor). */
    @Test
    fun xtreamApiRequests_sendSpecialCharacterCredentialsIntact() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<RecordedRequest>())
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen.add(request)
                return if (request.path.orEmpty().contains("get_live_streams")) {
                    MockResponse().setBody("""[{"stream_id":7,"name":"Kanal 7","category_id":"1"}]""")
                } else {
                    MockResponse().setBody("[]")
                }
            }
        }
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            MetadataEnricher.fetchXtreamLiveStreamsAsItems(app, host, "us&er", "a+b&c#d", 1)
            assertTrue(seen.isNotEmpty())
            seen.forEach { r ->
                assertEquals("kullanıcı adı: ${r.path}", "us&er", r.requestUrl?.queryParameter("username"))
                assertEquals("şifre: ${r.path}", "a+b&c#d", r.requestUrl?.queryParameter("password"))
                assertNotNull("action kaybolmamalı: ${r.path}", r.requestUrl?.queryParameter("action"))
            }
        } finally {
            server.shutdown()
        }
    }

    // ---------------------------------------------------------------- EPG

    private fun xmltvTime(epochMs: Long): String =
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs))

    private fun programme(channel: String, start: Long, stop: Long, title: String, desc: String = "") =
        """<programme start="${xmltvTime(start)}" stop="${xmltvTime(stop)}" channel="$channel"><title>$title</title><desc>$desc</desc></programme>"""

    /**
     * Büyük sağlayıcıların XMLTV'si geçmiş günler dahil 7+ günlük programı içerir; hepsi açıklamalarıyla belleğe
     * alınınca düşük bellekli TV kutularında uygulama kapanabiliyordu. Yalnızca gerekli aralık tutulur.
     */
    @Test
    fun epg_keepsOnlyTheUsefulTimeWindow_andShortensVeryLongDescriptions() = runBlocking {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val longDesc = "uzun ".repeat(400)
        val xml = """<?xml version="1.0" encoding="UTF-8"?><tv>""" +
            programme("trt1", now - 72 * hour, now - 71 * hour, "Üç gün önce") +
            programme("trt1", now - 30 * 60_000, now + 30 * 60_000, "Şu an", longDesc) +
            programme("trt1", now + 24 * hour, now + 25 * hour, "Yarın") +
            programme("trt1", now + 6 * 24 * hour, now + 6 * 24 * hour + hour, "Altı gün sonra") +
            "</tv>"
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(xml))
        server.start()
        try {
            val programs = RealEpgProvider.fetchProgramsFromUrl(server.url("/xmltv.php").toString())["trt1"].orEmpty()
            assertEquals(listOf("Şu an", "Yarın"), programs.map { it.title })
            assertTrue("açıklama kısaltılır: ${programs.first().description.length}", programs.first().description.length <= 401)
        } finally {
            server.shutdown()
            RealEpgProvider.clearCache()
        }
    }

    /** Saat dilimi boşluksuz yazılmış XMLTV tarihleri ("20261012180000+0300") okunamıyor, rehber boş kalıyordu. */
    @Test
    fun epg_readsDatesWithoutASpaceBeforeTheTimeZone() = runBlocking {
        val now = System.currentTimeMillis()
        fun noSpace(epochMs: Long) =
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMs))
        val xml = """<?xml version="1.0" encoding="UTF-8"?><tv>""" +
            """<programme start="${noSpace(now - 600_000)}" stop="${noSpace(now + 600_000)}" channel="trt1"><title>Boşluksuz</title></programme>""" +
            programme("trt1", now + 600_000, now + 1_800_000, "Boşluklu") +
            "</tv>"
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(xml))
        server.start()
        try {
            val programs = runCatching { RealEpgProvider.fetchProgramsFromUrl(server.url("/xmltv.php").toString()) }
                .getOrNull()?.get("trt1").orEmpty()
            assertEquals(listOf("Boşluksuz", "Boşluklu"), programs.map { it.title })
        } finally {
            server.shutdown()
            RealEpgProvider.clearCache()
        }
    }

    /** "Şimdi canlı" indirme anına göre sabitleniyordu; saatler sonra rehber yanlış programı canlı gösteriyordu. */
    @Test
    fun epg_liveNow_isComputedFromTheCurrentTime() {
        val now = System.currentTimeMillis()
        val channel = IPTVItem(id = 1, playlistId = 1, name = "TRT 1", cleanedName = "TRT 1", logoUrl = null,
            streamUrl = "http://h/1.ts", category = "Ulusal", type = "LIVE", tvgId = "trt1")
        fun prog(title: String, start: Long, end: Long, live: Boolean) = EPGProgram(
            id = title, channelId = 0, channelName = "trt1", title = title, description = "", category = "",
            startTimeFormatted = "", endTimeFormatted = "", startEpochMillis = start, endEpochMillis = end, isLiveNow = live
        )
        // İndirildiğinde "Eski" canlıydı; şimdi "Şimdiki" canlı.
        val stale = listOf(prog("Eski", now - 4 * 3_600_000, now - 3 * 3_600_000, live = true), prog("Şimdiki", now - 600_000, now + 600_000, live = false))
        val epg = EPGProvider.generateEPGForSingleChannel(channel, mapOf("trt1" to stale))
        assertEquals("Şimdiki", epg.currentProgram?.title)
        assertFalse(epg.programs.first { it.title == "Eski" }.isLiveNow)
        assertTrue(epg.programs.first { it.title == "Şimdiki" }.isLiveNow)
    }

    // ---------------------------------------------------------------- Çoklu ekran

    /** Çoklu ekranda kare donunca veya yayın açılamayınca ekran sadece siyah kalıyordu. */
    @Test
    fun multiScreen_showsBufferingAndFailureWithRetry() {
        var retried = 0
        var state by androidx.compose.runtime.mutableStateOf(Pair(true, false))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MyApplicationTheme {
                MultiScreenStatusOverlay(isBuffering = state.first, failed = state.second, onRetry = { retried++ })
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("multi_buffering").assertExists()
        compose.onNodeWithTag("multi_failed").assertDoesNotExist()

        state = Pair(false, true)
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("multi_buffering").assertDoesNotExist()
        compose.onNodeWithTag("multi_retry").performClick()
        assertEquals(1, retried)

        state = Pair(false, false)
        // Son durumda dönen animasyon yok: saat serbest bırakılıp ekranın oturması beklenir.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("multi_failed").assertDoesNotExist()
    }
}
