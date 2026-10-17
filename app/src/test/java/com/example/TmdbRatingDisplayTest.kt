package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.IPTVItem
import com.example.ui.screens.MediaCard
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocalTmdbRatings
import com.example.util.TmdbRatings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Kartlarda yalnızca TMDB puanı görünür; sağlayıcının puanı (ör. sahte 9.6) gösterilmez. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TmdbRatingDisplayTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun movie(id: Int, providerRating: Double) = IPTVItem(id = id, playlistId = 1, name = "Hamlet $id",
        cleanedName = "Hamlet $id", logoUrl = null, streamUrl = "http://10.255.255.1/m/$id.mp4", category = "Film",
        type = "MOVIE", rating = providerRating)

    @Test
    fun card_showsTmdbRating_andHidesProviderRating() {
        val known = movie(1, providerRating = 9.6)
        val unknown = movie(2, providerRating = 9.6)
        compose.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalTmdbRatings provides mapOf(TmdbRatings.key(known) to 7.3)) {
                    androidx.compose.foundation.layout.Row {
                        MediaCard(item = known, onClick = {}, onLongClick = {})
                        MediaCard(item = unknown, onClick = {}, onLongClick = {})
                    }
                }
            }
        }
        compose.onNodeWithText("★ 7.3").assertExists()
        compose.onNodeWithText("★ 9.6").assertDoesNotExist()
    }
}
