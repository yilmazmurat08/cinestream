package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.SubscriptionExpiryBanner
import com.example.util.XtreamAccount
import com.example.util.XtreamAccountInfo
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Abonelik bitiş uyarısı: sunucu cevabını okuma, kalan gün, uyarı kararı ve günde bir kez gösterim. */
@RunWith(RobolectricTestRunner::class)
class XtreamAccountTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun info(expiresIn: Long?) = XtreamAccountInfo(expiresIn?.let { now + it }, "Active", 1, "Europe/Istanbul", now)

    @Test fun parse_readsExpiryStatusAndTimezone() {
        val json = """{"user_info":{"auth":1,"status":"Active","exp_date":"1800432000","max_connections":"2"},
            "server_info":{"timezone":"Europe/Istanbul"}}"""
        val parsed = XtreamAccount.parse(json, now)!!
        assertEquals(1_800_432_000_000L, parsed.expiresAtMillis)
        assertEquals("Active", parsed.status)
        assertEquals(2, parsed.maxConnections)
        assertEquals("Europe/Istanbul", parsed.serverTimezone)
    }

    @Test fun parse_unlimitedOrBrokenAccount() {
        assertNull(XtreamAccount.parse("""{"user_info":{"status":"Active","exp_date":null}}""", now)!!.expiresAtMillis)
        assertNull(XtreamAccount.parse("""{"user_info":{"exp_date":"0"}}""", now)!!.expiresAtMillis)
        assertNull(XtreamAccount.parse("not json", now))
        assertNull(XtreamAccount.parse("""{"other":1}""", now))
    }

    @Test fun daysLeftAndWarning() {
        assertEquals(5, XtreamAccount.daysLeft(info(5 * day + 3_600_000), now))
        assertEquals(0, XtreamAccount.daysLeft(info(3_600_000), now))
        assertEquals(-1, XtreamAccount.daysLeft(info(-day), now))
        assertNull(XtreamAccount.daysLeft(info(null), now))
        assertTrue(XtreamAccount.shouldWarn(info(6 * day), now))
        assertTrue(XtreamAccount.shouldWarn(info(-day), now))
        assertFalse(XtreamAccount.shouldWarn(info(30 * day), now))
        assertFalse(XtreamAccount.shouldWarn(info(null), now))
    }

    @Test fun saveAndLoad_perPlaylist() {
        XtreamAccount.save(app, 7, info(3 * day))
        assertEquals(now + 3 * day, XtreamAccount.load(app, 7)?.expiresAtMillis)
        assertNull(XtreamAccount.load(app, 8))
    }

    @Test fun api_fetchesFromServer() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"user_info":{"exp_date":"1800432000","status":"Active"},"server_info":{"timezone":"UTC"}}"""))
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            val fetched = runBlocking { com.example.data.api.XtreamAccountApi.fetch(host, "u", "p", now) }
            assertNotNull(fetched)
            assertEquals(1_800_432_000_000L, fetched!!.expiresAtMillis)
            assertTrue(server.takeRequest().path!!.startsWith("/player_api.php?username=u&password=p"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun banner_showsOncePerDay() {
        val soon = info(4 * day)
        var shown by androidx.compose.runtime.mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { if (shown) SubscriptionExpiryBanner(account = soon, now = now) }
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(compose.onAllNodes(hasTestTag("subscription_expiry_banner")).fetchSemanticsNodes().isNotEmpty())
        // 12 sn sonra kendiliğinden kapanır (TV'de dokunmadan gider).
        compose.mainClock.advanceTimeBy(13_000)
        assertTrue(compose.onAllNodes(hasTestTag("subscription_expiry_banner")).fetchSemanticsNodes().isEmpty())
        // Aynı gün yeniden açılınca gösterilmez.
        shown = false
        compose.mainClock.advanceTimeBy(100)
        shown = true
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(compose.onAllNodes(hasTestTag("subscription_expiry_banner")).fetchSemanticsNodes().isEmpty())
    }
}
