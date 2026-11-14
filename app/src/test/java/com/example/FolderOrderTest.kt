package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.EditableFolder
import com.example.ui.screens.FolderEditContent
import com.example.ui.screens.orderForEditing
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CategoryLocks
import com.example.util.FolderOrder
import com.example.util.FolderPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Klasörleri düzenle (kullanıcı geri bildirimi, 14 Kasım 2026): öne al ve gizle; telefon ve TV listelerine uygulanır. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr-w392dp-h850dp-port-xxhdpi")
class FolderOrderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val folders = listOf("DE | Sport", "TR | Ulusal", "UK | News", "TR | Spor", "Adult")

    @Test
    fun emptyPrefsKeepsOrder() {
        assertEquals(folders, FolderOrder.apply(folders, "LIVE", FolderPrefs()) { it })
    }

    @Test
    fun pinnedGoFirstInPinOrderAndHiddenDisappear() {
        var p = FolderPrefs()
        p = FolderOrder.withPinned(p, CategoryLocks.key("LIVE", "TR | Spor"), true)
        p = FolderOrder.withPinned(p, CategoryLocks.key("LIVE", "TR | Ulusal"), true)
        p = FolderOrder.withHidden(p, CategoryLocks.key("LIVE", "Adult"), true)
        val out = FolderOrder.apply(folders, "LIVE", p) { it }
        assertEquals(listOf("TR | Spor", "TR | Ulusal", "DE | Sport", "UK | News"), out)
        // Başka türün düzeni bu türü etkilemez.
        assertEquals(folders, FolderOrder.apply(folders, "MOVIE", p) { it })
        // Büyük/küçük harf ve boşluk farkı aynı klasördür.
        assertEquals("tr | spor ", FolderOrder.apply(listOf("x", "tr | spor "), "LIVE", p) { it }.first())
    }

    @Test
    fun pinAndHideExcludeEachOther() {
        val k = CategoryLocks.key("MOVIE", "Aksiyon")
        var p = FolderOrder.withPinned(FolderPrefs(), k, true)
        p = FolderOrder.withHidden(p, k, true)
        assertFalse(k in p.pinned); assertTrue(k in p.hidden)
        p = FolderOrder.withPinned(p, k, true)
        assertTrue(k in p.pinned); assertFalse(k in p.hidden)
        p = FolderOrder.withPinned(p, k, false)
        assertTrue(p.isEmpty)
    }

    @Test
    fun savedAndReloaded() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        FolderOrder.reset(ctx)
        FolderOrder.setPinned(ctx, "SERIES", "Netflix", true)
        FolderOrder.setHidden(ctx, "LIVE", "Adult", true)
        FolderOrder.init(ctx)
        assertTrue(FolderOrder.isPinned(FolderOrder.prefs.value, "SERIES", "Netflix"))
        assertTrue(FolderOrder.isHidden(FolderOrder.prefs.value, "LIVE", "Adult"))
        FolderOrder.reset(ctx)
        FolderOrder.init(ctx)
        assertTrue(FolderOrder.prefs.value.isEmpty)
    }

    @Test
    fun editScreenPinAndHide() {
        var prefs by mutableStateOf(FolderPrefs())
        val lists = mapOf("LIVE" to folders.map { EditableFolder("LIVE", it, 10) })
        compose.setContent {
            MyApplicationTheme {
                FolderEditContent(
                    lists = lists, prefs = prefs, onBack = {},
                    onPin = { f, on -> prefs = FolderOrder.withPinned(prefs, CategoryLocks.key(f.type, f.name), on) },
                    onHide = { f, on -> prefs = FolderOrder.withHidden(prefs, CategoryLocks.key(f.type, f.name), on) },
                    onReset = { prefs = FolderPrefs() }
                )
            }
        }
        compose.onNodeWithTag("folder_pin_LIVE_TR | Spor").performClick()
        compose.onNodeWithTag("folder_hide_LIVE_Adult").performClick()
        compose.waitForIdle()
        assertTrue(FolderOrder.isPinned(prefs, "LIVE", "TR | Spor"))
        assertTrue(FolderOrder.isHidden(prefs, "LIVE", "Adult"))
        compose.onNodeWithTag("folder_edit_reset").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(prefs.isEmpty)
        // Ekran açılınca öne alınanlar üstte listelenir.
        val p2 = FolderOrder.withPinned(FolderPrefs(), CategoryLocks.key("LIVE", "UK | News"), true)
        assertEquals("UK | News", orderForEditing(lists.getValue("LIVE"), p2).first().name)
    }
}
