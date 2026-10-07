package com.example

import android.app.Application
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.components.CastSection
import com.example.ui.components.TrailerWebViewClient
import com.example.ui.components.castNamesForDisplay
import com.example.ui.screens.EditProfileDialog
import com.example.ui.screens.multiScreenCategories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * 1.0.6 yayın öncesi çökme taraması: bulunan her çökme riskinin regresyon testi.
 * Her test, düzeltmeden önceki kodda çöküyor ya da yanlış sonuç veriyordu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashAuditTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun item(id: Int, category: String, type: String = "LIVE") = IPTVItem(
        id = id, playlistId = 1, name = "Öğe $id", cleanedName = "Öğe $id", logoUrl = null,
        streamUrl = "http://h/live/u/p/$id.ts", category = category, type = type
    )

    /** Detay ekranı: aynı oyuncu adı iki kez gelince liste anahtarı tekrarlanıp ekran çöküyordu. */
    @Test
    fun detailCast_duplicateNames_doNotCrash() {
        compose.setContent {
            CastSection(
                title = "",
                rawCast = "Ada Yıldız, Ada Yıldız, Bora Kaya",
                director = "Deniz Arslan, Deniz Arslan",
                viewModel = null,
                onPersonClick = { _, _ -> }
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("Bora Kaya").assertExists()
        assertEquals(
            listOf("Ada Yıldız", "Bora Kaya"),
            castNamesForDisplay("Ada Yıldız, Ada Yıldız, Bora Kaya, Bilinmiyor, ")
        )
    }

    /** Çoklu ekran: sağlayıcıda "Tümü" adlı kategori olunca anahtar iki kez oluşuyordu. */
    @Test
    fun multiScreenCategories_haveNoDuplicates() {
        val categories = multiScreenCategories(listOf(item(1, "Tümü"), item(2, "Haber"), item(3, "Haber"), item(4, "")))
        assertEquals(listOf("Tümü", "Haber"), categories)
        assertEquals(categories.size, categories.toSet().size)
    }

    /** Ayarlar > Profil > Fotoğraf: cihazda seçici yoksa (Android TV) uygulama kapanıyordu. */
    @Test
    fun photoPicker_missingOnDevice_showsMessageInsteadOfCrash() {
        // Robolectric, gerçek cihaz gibi, açılabilecek bir uygulama yoksa ActivityNotFoundException fırlatsın.
        shadowOf(app).checkActivities(true)
        compose.setContent {
            EditProfileDialog(
                currentName = "Murat", currentEmail = "", currentPhotoUrl = null,
                onDismiss = {}, onSave = { _, _, _ -> }, onLogout = {}
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("edit_profile_pick_photo").performClick()
        compose.waitForIdle()
        assertEquals(app.getString(R.string.settings_toast_photo_picker_unavailable), ShadowToast.getTextOfLatestToast())
    }

    /** Fragman: WebView'in iç işlemi kapanınca olay karşılanmazsa Android tüm uygulamayı kapatır. */
    @Test
    fun trailerWebView_rendererGone_isHandled() {
        val parent = FrameLayout(app)
        val web = WebView(app)
        parent.addView(web)
        // Varsayılan istemci olayı karşılamaz (false): Android bu durumda uygulamayı kapatır.
        assertFalse(WebViewClient().onRenderProcessGone(web, null))

        var reported = false
        val handled = TrailerWebViewClient { reported = true }.onRenderProcessGone(web, null)
        assertTrue("olay karşılanmalı", handled)
        assertTrue("fragman hata olarak bildirilmeli", reported)
        assertNull("ölü WebView görünümden kaldırılmalı", web.parent)
    }

    /** TV ekranlarının veritabanı okumaları: hata uygulamayı kapatmaz; iptal ise iptal olarak kalır. */
    @Test
    fun tvRead_returnsFallbackOnError_butKeepsCancellation() {
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        runBlocking {
            assertEquals(emptyList<Int>(), vm.tvRead("test", emptyList<Int>()) { throw IllegalStateException("database is full") })
            assertNull(vm.tvRead<IPTVItem?>("test", null) { throw android.database.sqlite.SQLiteFullException("disk full") })
            assertEquals(listOf(1), vm.tvRead("test", emptyList()) { listOf(1) })
            try {
                vm.tvRead("test", 0) { throw CancellationException("iptal") }
                fail("iptal yutulmamalı")
            } catch (_: CancellationException) {
            }
        }
    }

    /** Xtream: aynı bölüm iki sezonda listelenince oynatıcı paneli ve TV detay aynı anahtarı iki kez görüp çöküyordu. */
    @Test
    fun seriesInfo_duplicateEpisodeIdsAcrossSeasons_areListedOnce() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().contains("get_series_info")) {
                    MockResponse().setBody(
                        """{"info":{},"episodes":{
                            "0":[{"id":"11","title":"Özel","episode_num":"1","container_extension":"mkv"}],
                            "1":[{"id":"11","title":"Bölüm 1","episode_num":"1","container_extension":"mkv"},
                                 {"id":"12","title":"Bölüm 2","episode_num":"2","container_extension":"mkv"}]}}"""
                    )
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
        server.start()
        try {
            val host = server.url("/").toString().trimEnd('/')
            val credentials = IPTVItem(
                id = 1, playlistId = 1, name = "x", cleanedName = "x", logoUrl = null,
                streamUrl = "$host/movie/u/p/1.mkv", category = "", type = "MOVIE"
            )
            val show = runBlocking {
                com.example.data.api.MetadataEnricher.fetchSeriesInfoLive(
                    context = app, seriesId = 900, showTitle = "Dizi", showCover = null, showRating = 0.0,
                    showSummary = "", showCast = "", showDirector = "", anyItemForCredentials = credentials,
                    showCategory = "Amazon"
                )
            }
            assertNotNull(show)
            val ids = show!!.seasons.flatMap { s -> s.episodes.map { it.item.id } }
            assertEquals("her bölüm bir kez", ids.size, ids.toSet().size)
            assertEquals(listOf(-11, -12), ids)
            assertEquals(listOf(1), show.seasons.map { it.seasonNumber })
        } finally {
            server.shutdown()
        }
    }
}
