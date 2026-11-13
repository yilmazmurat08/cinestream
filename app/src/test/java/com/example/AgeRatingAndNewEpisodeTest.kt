package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.components.AgeLockGate
import com.example.ui.screens.AgeLimitSelector
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AgeRatings
import com.example.worker.NewEpisodeAlerts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kullanıcı isteği (13 Kasım 2026): TMDB yaş derecelendirmesiyle ebeveyn kilidi ve favori dizinin yeni bölüm bildirimi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class AgeRatingAndNewEpisodeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun tearDown() {
        db.close()
        AgeRatings.setLimit(app, 0)
    }

    @Test
    fun parseCertifications() {
        assertEquals(13, AgeRatings.parse("13+"))
        assertEquals(18, AgeRatings.parse("18+"))
        assertEquals(0, AgeRatings.parse("Genel İzleyici"))
        assertEquals(13, AgeRatings.parse("PG-13"))
        assertEquals(17, AgeRatings.parse("R"))
        assertEquals(18, AgeRatings.parse("TV-MA"))
        assertEquals(7, AgeRatings.parse("TV-Y7"))
        assertEquals(16, AgeRatings.parse("FSK 16"))
        assertNull(AgeRatings.parse(""))
        assertNull(AgeRatings.parse("NR"))
    }

    @Test
    fun pickPrefersTurkeyThenUs() {
        assertEquals(15, AgeRatings.pick(mapOf("US" to "R", "TR" to "15+")))
        assertEquals(13, AgeRatings.pick(mapOf("DE" to "16", "US" to "PG-13")))
        assertEquals(16, AgeRatings.pick(mapOf("DE" to "16", "US" to "")))
        assertNull(AgeRatings.pick(emptyMap()))
    }

    @Test
    fun blockedOnlyAboveLimitAndWhenKnown() {
        assertFalse(AgeRatings.isBlocked(18, 0))      // sınır kapalı
        assertTrue(AgeRatings.isBlocked(18, 13))
        assertFalse(AgeRatings.isBlocked(13, 13))
        assertFalse(AgeRatings.isBlocked(null, 13))   // bilinmiyorsa engellemez
    }

    @Test
    fun gateShowsOnlyForTooOldTitles_andLimitSelectorSaves() {
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent { MyApplicationTheme { AgeLimitSelector() } }
        compose.onNodeWithTag("age_limit_13").performClick()
        compose.waitForIdle()
        assertEquals(13, AgeRatings.limit.value)
        assertEquals(13, app.getSharedPreferences("age_ratings", 0).getInt("__limit", 0))
    }

    @Test
    fun gate_blocksAbove_allowsBelow() {
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        vm.setParentalLock(true)
        AgeRatings.setLimit(app, 13)
        var age by androidx.compose.runtime.mutableStateOf<Int?>(18)
        var backs = 0
        compose.setContent { MyApplicationTheme { AgeLockGate(vm, age, onBack = { backs++ }) } }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("age_lock_gate").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("age_lock_back").performClick()
        assertEquals(1, backs)
        age = 7
        compose.waitForIdle()
        compose.onAllNodesWithTag("age_lock_gate").assertCountEquals(0)
    }

    @Test
    fun newEpisodeRule_todayOrYesterday_once() {
        assertTrue(NewEpisodeAlerts.shouldNotify("2026-11-13", false, today = "2026-11-13", yesterday = "2026-11-12"))
        assertTrue(NewEpisodeAlerts.shouldNotify("2026-11-12", false, today = "2026-11-13", yesterday = "2026-11-12"))
        assertFalse(NewEpisodeAlerts.shouldNotify("2026-11-13", true, today = "2026-11-13", yesterday = "2026-11-12"))
        assertFalse(NewEpisodeAlerts.shouldNotify("2026-11-01", false, today = "2026-11-13", yesterday = "2026-11-12"))
        assertFalse(NewEpisodeAlerts.shouldNotify(null, false))
    }
}
