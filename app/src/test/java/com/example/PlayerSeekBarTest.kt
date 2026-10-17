package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.IPTVItem
import com.example.ui.screens.PlayerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Süresi öğrenilemeyen yayında (sunucu dosya uzunluğunu bildirmiyor) ilerleme çubuğu eskiden tamamen dolu, kalan
 * süre "-00:00" görünüyordu. Artık çubuk boş ve kilitli, kalan süre "--:--".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
class PlayerSeekBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unknownDuration_showsAnEmptyLockedBar() {
        val item = IPTVItem(id = 88001, playlistId = 1, name = "Bölüm", cleanedName = "Bölüm", logoUrl = null,
            streamUrl = "http://10.255.255.1/series/u/p/88001.mkv", category = "Dizi", type = "SERIES")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerScreen(item = item, siblingItems = emptyList(), onBack = {}, onPlayItem = {}, onProgressUpdate = { _, _, _ -> })
        }
        for (i in 0 until 50) {
            if (compose.onAllNodes(hasTestTag("player_seek_bar")).fetchSemanticsNodes().isNotEmpty()) break
            compose.mainClock.advanceTimeBy(100)
        }
        compose.onNodeWithTag("player_seek_bar").assertIsNotEnabled()
        compose.onNodeWithText("--:--").assertExists()
    }
}
