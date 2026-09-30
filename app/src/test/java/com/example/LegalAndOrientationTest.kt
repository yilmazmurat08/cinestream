package com.example

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.legal.LegalDoc
import com.example.data.legal.LegalDocuments
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.ui.legal.LegalScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Yasal metinler (Hizmet Şartları / Gizlilik Politikası) ve ekran yönü:
 * - Dört belge (TR/EN) yüklenir, yer tutucuların hepsi doldurulur, iki dilde bölüm sayısı aynıdır.
 * - Giriş ekranının altındaki bağlantılar ilgili belgeyi açar; Geri ekranı kapatır.
 * - Telefonda uygulama yalnızca dikeydir (ayar kaldırıldı).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegalAndOrientationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun allDocumentsLoad_withTokensFilled_andMatchingSectionsInBothLanguages() {
        for (doc in LegalDoc.values()) {
            val tr = LegalDocuments.load(app, doc, "tr")
            val en = LegalDocuments.load(app, doc, "en")
            assertNotNull("$doc tr", tr)
            assertNotNull("$doc en", en)
            assertEquals("$doc bölüm sayısı", tr!!.sections.size, en!!.sections.size)
            for (d in listOf(tr, en)) {
                val all = (listOf(d.title, d.updated, d.intro, d.highlight) +
                    d.sections.flatMap { listOf(it.heading) + it.paragraphs + it.bullets + it.after }).joinToString("\n")
                assertFalse("doldurulmamış yer tutucu: $doc", Regex("""\{[A-Z]+\}""").containsMatchIn(all))
                assertTrue("iletişim e-postası: $doc", all.contains("yilmazmurat08@gmail.com"))
            }
        }
        // Play / YouTube API Hizmetleri gereği: şartlarda YouTube Hizmet Şartları, gizlilikte Google Gizlilik Politikası bağlantısı.
        val terms = LegalDocuments.load(app, LegalDoc.TERMS, "tr")!!.sections.flatMap { it.paragraphs }.joinToString()
        assertTrue(terms.contains("https://www.youtube.com/t/terms"))
        val privacy = LegalDocuments.load(app, LegalDoc.PRIVACY, "en")!!.sections.flatMap { it.bullets }.joinToString()
        assertTrue(privacy.contains("https://policies.google.com/privacy"))
    }

    @Test
    fun loginLinks_openDocuments_andBackCloses() {
        val db = AppDatabase.getDatabase(app)
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        compose.setContent {
            MyApplicationTheme {
                val doc by vm.legalDoc.collectAsState()
                LoginScreen(viewModel = vm, onLoginSuccess = { _, _ -> })
                doc?.let { LegalScreen(doc = it, language = "tr", onClose = { vm.closeLegal() }) }
            }
        }
        compose.onNodeWithTag("legal_link_privacy").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(LegalDoc.PRIVACY, vm.legalDoc.value)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("legal_section_0")).fetchSemanticsNodes().isNotEmpty() }

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertNull(vm.legalDoc.value)

        compose.onNodeWithTag("legal_link_terms").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(LegalDoc.TERMS, vm.legalDoc.value)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("legal_screen_terms")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun phone_appIsLockedToPortrait() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        repeat(20) { org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, controller.get().requestedOrientation)
        controller.pause().stop().destroy()
    }
}
