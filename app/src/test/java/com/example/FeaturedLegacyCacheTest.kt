package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FeaturedMovieRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Öne Çıkan film artık kaydedilmiyor; eski sürümün 12 saatlik kaydı silinmeli (yer kaplamasın). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeaturedLegacyCacheTest {

    @Test
    fun legacyFeaturedPrefs_areDeleted() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("featured_movie_prefs", Context.MODE_PRIVATE)
            .edit().putString("featured_title", "Eski Film").commit()
        val file = File(context.applicationInfo.dataDir, "shared_prefs/featured_movie_prefs.xml")
        assertTrue(file.exists())

        FeaturedMovieRepository.clearLegacyCache(context)

        assertFalse(file.exists())
    }
}
