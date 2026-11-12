package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.CoverOverrideStore
import com.example.data.repository.CoverSuggestion
import com.example.data.repository.CoverSuggestions
import com.example.data.repository.TmdbCoverImage
import com.example.data.repository.TmdbCoverSearchItem
import com.example.ui.components.CoverPickerDialog
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Kapak Görselini Değiştir": seçim cihazda saklanır, eski adres her yerde yeni kapağa çevrilir, "Varsayılana dön"
 * eski kapağı geri getirir. Liste ve TMDB eşleştirmesi değişmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class CoverOverrideTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val key = CoverOverrideStore.keyFor(false, "Gece Treni")

    @Before
    @After
    fun clean() {
        CoverOverrideStore.init(app)
        CoverOverrideStore.overrides.value.keys.toList().forEach { CoverOverrideStore.clear(app, it) }
    }

    @Test
    fun choiceIsStoredAndOldAddressIsRedirected() {
        CoverOverrideStore.set(app, key, "http://liste/yanlis.jpg", "https://image.tmdb.org/t/p/w780/dogru.jpg")
        assertEquals("https://image.tmdb.org/t/p/w780/dogru.jpg", CoverOverrideStore.coverFor(key))
        assertEquals("https://image.tmdb.org/t/p/w780/dogru.jpg", CoverOverrideStore.replacementFor("http://liste/yanlis.jpg"))
        assertNull(CoverOverrideStore.replacementFor("http://liste/baska.jpg"))
        // Cihazda saklandı
        val raw = app.getSharedPreferences("cover_overrides", 0).getString("data", "")!!
        assertTrue(raw.contains("dogru.jpg"))
    }

    @Test
    fun secondChangeKeepsFirstOriginalAndResetRestores() {
        CoverOverrideStore.set(app, key, "http://liste/yanlis.jpg", "https://x/a.jpg")
        // İkinci değişiklikte ekran artık bizim kapağımızı "eski" diye verir; ilk adres korunmalı.
        CoverOverrideStore.set(app, key, "https://x/a.jpg", "https://x/b.jpg")
        assertEquals("https://x/b.jpg", CoverOverrideStore.replacementFor("http://liste/yanlis.jpg"))
        CoverOverrideStore.clear(app, key)
        assertNull(CoverOverrideStore.coverFor(key))
        assertNull(CoverOverrideStore.replacementFor("http://liste/yanlis.jpg"))
    }

    @Test
    fun keyIgnoresCaseAndSeparatesMoviesFromSeries() {
        assertEquals(CoverOverrideStore.keyFor(false, " GECE Treni "), CoverOverrideStore.keyFor(false, "gece treni"))
        assertTrue(CoverOverrideStore.keyFor(true, "Gece Treni") != CoverOverrideStore.keyFor(false, "Gece Treni"))
    }

    @Test
    fun suggestionsListTitlesFirstThenExtraPostersWithoutDuplicates() {
        val found = listOf(
            TmdbCoverSearchItem(1, title = "Gece Treni", posterPath = "/a.jpg", releaseDate = "2019-05-01"),
            TmdbCoverSearchItem(2, title = "Gece Treni 2", posterPath = null),
            TmdbCoverSearchItem(3, title = "Tren", posterPath = "/c.jpg")
        )
        val list = CoverSuggestions.build(found, listOf(TmdbCoverImage("/a.jpg"), TmdbCoverImage("/d.jpg"), TmdbCoverImage(null)))
        assertEquals(listOf("/a.jpg", "/c.jpg", "/d.jpg"), list.map { it.coverUrl.substringAfter("w780") })
        assertEquals("Gece Treni (2019)", list[0].label)
        assertEquals("Tren", list[1].label)
        assertEquals("Gece Treni (2019)", list[2].label)
        assertTrue(list[0].thumbUrl.contains("w342"))
    }

    @Test
    fun dialogPickSavesCoverAndResetButtonAppears() {
        val options = listOf(
            CoverSuggestion("https://t/1.jpg", "https://c/1.jpg", "Gece Treni (2019)"),
            CoverSuggestion("https://t/2.jpg", "https://c/2.jpg", "Tren")
        )
        var open = true
        compose.setContent {
            MyApplicationTheme {
                if (open) CoverPickerDialog("Gece Treni", isSeries = false, originalUrl = "http://liste/yanlis.jpg", onDismiss = { open = false }, loadSuggestions = { options })
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("cover_reset").assertCountEquals(0)
        compose.onNodeWithTag("cover_gallery").assertExists()
        compose.onNodeWithTag("cover_option_1").performClick()
        compose.waitForIdle()
        assertEquals("https://c/2.jpg", CoverOverrideStore.coverFor(key))
        assertEquals("https://c/2.jpg", CoverOverrideStore.replacementFor("http://liste/yanlis.jpg"))
    }

    @Test
    fun dialogShowsMessageWhenNoSuggestionAndResetClears() {
        CoverOverrideStore.set(app, key, "http://liste/yanlis.jpg", "https://c/9.jpg")
        compose.setContent {
            MyApplicationTheme {
                CoverPickerDialog("Gece Treni", isSeries = false, originalUrl = "http://liste/yanlis.jpg", onDismiss = {}, loadSuggestions = { emptyList() })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("cover_picker_empty").assertExists()
        compose.onNodeWithTag("cover_reset").performClick()
        compose.waitForIdle()
        assertNull(CoverOverrideStore.coverFor(key))
    }
}
