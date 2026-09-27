package com.example

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.example.data.model.IPTVItem
import com.example.ui.components.MediaCard
import com.example.ui.screens.DetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvFocusMemory
import com.example.ui.tv.tvModalFocus
import com.example.ui.tv.tvRestorableFocus
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android TV kumandası: ana sayfa kartları arasında yön tuşlarıyla gezinme, detay panelinde odağın
 * panel içinde kalması, Geri tuşunun paneli kapatması ve kapanınca odağın karta dönmesi.
 * Uygulamanın gerçek bileşenleri (MediaCard, DetailScreen, TvSupport) TV modunda çizilir.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television")
class TvRemoteFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val items = (1..6).map { i ->
        IPTVItem(
            id = i, playlistId = 1, name = "Film $i", cleanedName = "Film $i", logoUrl = null,
            streamUrl = "http://example.com/movie/$i.mp4", category = "Film", type = "MOVIE"
        )
    }

    @Before
    fun forceTv() {
        TvDevice.overrideForTest = true
    }

    @After
    fun reset() {
        TvDevice.overrideForTest = null
    }

    private fun focusedInside(tag: String) =
        rule.onNode(isFocused() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun press(key: Key, times: Int = 1) {
        repeat(times) {
            rule.onRoot().performKeyInput { pressKey(key) }
            rule.waitForIdle()
        }
    }

    @Test
    fun dpadMovesFocusAcrossHomeRowCards() {
        rule.setContent {
            MyApplicationTheme {
                LazyRow {
                    items(items, key = { it.id }) { item ->
                        Box(Modifier.width(140.dp).testTag("card_${item.id}")) {
                            MediaCard(item = item, onClick = {})
                        }
                    }
                }
            }
        }
        rule.onNode(hasAnyAncestor(hasTestTag("card_1")) and androidx.compose.ui.test.hasClickAction(), useUnmergedTree = true)
            .requestFocus()
        focusedInside("card_1").assertExists()

        press(Key.DirectionRight)
        focusedInside("card_2").assertExists()

        press(Key.DirectionRight, times = 2)
        focusedInside("card_4").assertExists()

        press(Key.DirectionLeft)
        focusedInside("card_3").assertExists()
    }

    @Test
    fun detailPanelKeepsFocusInside_backClosesIt_andFocusReturnsToCard() {
        rule.setContent {
            MyApplicationTheme {
                var selected by remember { mutableStateOf<IPTVItem?>(null) }
                // MainActivity'deki davranışın aynısı: panel açılırken odak kaydedilir, kapanınca geri yüklenir.
                var saved by remember { mutableStateOf<java.lang.ref.WeakReference<FocusRequester>?>(null) }
                LaunchedEffect(selected != null) {
                    if (selected != null) {
                        saved = TvFocusMemory.snapshot()
                    } else if (saved != null) {
                        kotlinx.coroutines.delay(350)
                        TvFocusMemory.restore(saved)
                        saved = null
                    }
                }
                BackHandler(enabled = selected != null) { selected = null }

                Box(Modifier.fillMaxSize()) {
                    LazyRow(Modifier.testTag("home_row")) {
                        items(items, key = { it.id }) { item ->
                            Box(Modifier.width(140.dp).testTag("card_${item.id}")) {
                                MediaCard(item = item, onClick = { selected = item })
                            }
                        }
                    }
                    if (selected != null) {
                        Box(modifier = tvModalFocus(selected?.id).testTag("detail_panel")) {
                            DetailScreen(
                                item = selected,
                                onDismiss = { selected = null },
                                onPlay = {},
                                onToggleFavorite = {}
                            )
                        }
                    }
                }
            }
        }

        rule.onNode(hasAnyAncestor(hasTestTag("card_3")) and androidx.compose.ui.test.hasClickAction(), useUnmergedTree = true)
            .requestFocus()
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNode(hasTestTag("detail_panel")).assertExists()
        focusedInside("detail_panel").assertExists()

        // Kumandayla her yöne defalarca basılsa da odak arkadaki ana sayfaya kaçmamalı.
        press(Key.DirectionUp, times = 6)
        press(Key.DirectionLeft, times = 6)
        press(Key.DirectionDown, times = 30)
        press(Key.DirectionRight, times = 6)
        focusedInside("detail_panel").assertExists()
        focusedInside("home_row").assertDoesNotExist()

        // Geri tuşu paneli kapatır.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNode(hasTestTag("detail_panel")).assertDoesNotExist()

        // Odak paneli açan karta döner (kapanma animasyonu için bekleme sonrası).
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        focusedInside("card_3").assertExists()
    }

    @Test
    fun modalFocusStaysInsideSimplePanel() {
        rule.setContent {
            MyApplicationTheme {
                Row {
                    Button(onClick = {}, modifier = Modifier.testTag("behind")) { Text("Arka") }
                    Column(modifier = tvModalFocus("panel").testTag("panel")) {
                        Button(onClick = {}, modifier = Modifier.testTag("a")) { Text("A") }
                        Button(onClick = {}, modifier = Modifier.testTag("b")) { Text("B") }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        focusedInside("panel").assertExists()
        press(Key.DirectionLeft, times = 3)
        press(Key.DirectionUp, times = 3)
        focusedInside("panel").assertExists()
        rule.onNode(hasTestTag("behind") and isFocused()).assertDoesNotExist()
    }

    @Test
    fun phoneModeLeavesFocusUntouched() {
        TvDevice.overrideForTest = false
        rule.setContent {
            MyApplicationTheme {
                Column {
                    Box(Modifier.size(10.dp).focusable().testTag("outside"))
                    Column(modifier = tvModalFocus("panel").then(tvRestorableFocus()).testTag("panel")) {
                        Button(onClick = {}) { Text("A") }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        // Telefonda panel odağı kendiliğinden almaz (dokunmatik kullanımda görünür vurgu oluşmaz).
        focusedInside("panel").assertDoesNotExist()
    }
}
