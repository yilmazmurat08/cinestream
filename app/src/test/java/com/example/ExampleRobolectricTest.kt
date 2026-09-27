package com.example

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CineStream IPTV", appName)
  }

  @Test
  fun `test main activity launch`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }

  @Test
  fun testMetadataEnricherOffline() {
    val item = com.example.data.model.IPTVItem(
        playlistId = 1,
        name = "Dune: Part Two Movie Test",
        cleanedName = "Dune: Part Two",
        logoUrl = "logo",
        streamUrl = "http://stream/dune2",
        category = "Movies",
        type = "MOVIE",
        summary = "Harika bir sinema deneyimi sunan..."
    )
    val enriched = com.example.data.api.MetadataEnricher.generateLocalMovieMetadata(item)
    assertNotNull(enriched)
    org.junit.Assert.assertTrue(enriched.summary.isNotEmpty())
  }
}
