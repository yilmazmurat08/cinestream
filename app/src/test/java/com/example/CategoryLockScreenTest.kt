package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.IPTVViewModel.LockableCategory
import com.example.ui.screens.ParentalCategoriesContent
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Kilitli kategoriler" ekranı: sekmeler, sayılar, "yalnızca kilitliler", arama ve anahtar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class CategoryLockScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun cat(type: String, name: String, count: Int, locked: Boolean, manual: Boolean = false) =
        LockableCategory(type, name, count, locked, manual)

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun tabs_filter_search_andToggle_work() {
        var lists by mutableStateOf(
            mapOf(
                "LIVE" to listOf(cat("LIVE", "Derbi Özetleri", 6, true, manual = true), cat("LIVE", "Spor", 3, false)),
                "MOVIE" to listOf(cat("MOVIE", "Aksiyon", 4, false), cat("MOVIE", "XXX Yetişkin", 2, true)),
                "SERIES" to emptyList()
            )
        )
        val toggled = mutableListOf<Triple<String, String, Boolean>>()
        var resetClicks = 0
        compose.setContent {
            MyApplicationTheme(dynamicColor = false) {
                ParentalCategoriesContent(
                    lists = lists, anyManual = true, onBack = {},
                    onToggle = { c, locked -> toggled += Triple(c.type, c.name, locked) },
                    onResetAll = { resetClicks++ }
                )
            }
        }
        compose.waitForIdle()

        // Sekme başlıklarında kilitli sayısı yazar; elle ayarlanan satır işaretlidir.
        compose.onNodeWithText("Canlı TV · 1 kilitli").assertExists()
        compose.onNodeWithText("Filmler · 1 kilitli").assertExists()
        compose.onNodeWithText("Diziler · 0 kilitli").assertExists()
        compose.onNodeWithText("6 içerik · elle ayarlandı").assertExists()
        compose.onNodeWithText("3 içerik").assertExists()

        // Anahtar: kilitli olmayanı kilitlemek ister.
        compose.onNodeWithTag("category_lock_switch_LIVE_Spor").performClick()
        assertEquals(listOf(Triple("LIVE", "Spor", true)), toggled)

        // Yalnızca kilitliler
        compose.onNodeWithTag("category_locks_only_locked").performClick()
        compose.waitForIdle()
        assertEquals(0, count("category_lock_row_LIVE_Spor"))
        assertTrue(count("category_lock_row_LIVE_Derbi Özetleri") > 0)

        // Filmler sekmesi + arama
        compose.onNodeWithTag("category_locks_tab_MOVIE").performClick()
        compose.waitForIdle()
        assertTrue("yetişkin kategori kilitli görünür", count("category_lock_row_MOVIE_XXX Yetişkin") > 0)
        assertEquals("Aksiyon kilitli değil, süzülür", 0, count("category_lock_row_MOVIE_Aksiyon"))
        compose.onNodeWithTag("category_locks_only_locked").performClick()
        compose.onNodeWithTag("category_locks_search").performTextInput("aksi")
        compose.waitForIdle()
        assertEquals(0, count("category_lock_row_MOVIE_XXX Yetişkin"))
        assertTrue(count("category_lock_row_MOVIE_Aksiyon") > 0)

        // Boş sekme
        compose.onNodeWithTag("category_locks_tab_SERIES").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bu listede kategori yok.").assertExists()

        compose.onNodeWithTag("category_locks_reset").performClick()
        assertEquals(1, resetClicks)
    }
}
