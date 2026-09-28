package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.tv.TvAppState
import com.example.ui.tv.TvBrowseScreen
import com.example.ui.tv.TvDetailScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV Filmler ekranı ve detay: kategori listesinden Sağ ile ızgaraya, ilk sütundan Sol ile seçili kategoriye dönülür;
 * poster OK ile detay açılır. Detayda ilk odak "İzle"; oyuncu satırında OK kişi sayfasını açar, Geri ile dönünce
 * odak aynı kişidedir.
 */
/** TV Diziler: kaydedilmiş kategori listenin aşağısında olsa bile kaydırılıp görünür ve odaklı gelir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvSeriesBrowseTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun waitForTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test
    fun series_categoriesAndShowsAppear() {
        val db = AppDatabase.getDatabase(app)
        runBlocking {
            db.iptvDao().insertItems((1..3).flatMap { s ->
                listOf("Tv Dizi A", "Tv Dizi B").mapIndexed { k, show ->
                    IPTVItem(id = 9300 + k * 10 + s, playlistId = 1, name = "$show S01E0$s", cleanedName = "$show S01E0$s", logoUrl = null,
                        streamUrl = "http://s/$k$s.mp4", category = "TvTest Diziler", type = "SERIES")
                }
            } + (1..30).map { n ->
                // Seçili kategorinin ekran dışında kalması için başa sıralanan kategoriler
                IPTVItem(id = 9400 + n, playlistId = 1, name = "Aa Dizi $n S01E01", cleanedName = "Aa Dizi $n S01E01", logoUrl = null,
                    streamUrl = "http://s/aa$n.mp4", category = "Aa Kategori %02d".format(n), type = "SERIES")
            })
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        // Kaydedilmiş kategori listenin aşağısında olsa bile görünür ve odaklı gelir (veritabanında başka
        // testlerin kategorileri de olabilir).
        val state = TvAppState("SERIES", "SERIES", null, "TvTest Diziler", null)
        compose.setContent { TvBrowseScreen(viewModel = vm, type = "SERIES", state = state) }
        waitForTag("tv_category_TvTest Diziler")
        waitForTag("tv_poster_show_Tv Dizi A")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_category_TvTest Diziler").assertIsFocused() }.isSuccess }
    }
}
