package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVGroup
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Ebeveyn kilidi: kullanıcı kategorileri elle kilitler/açar. Elle ayar yoksa yetişkin kategorileri otomatik kilitli
 * kalır (eski davranış); elle ayar her zaman otomatik tespitten önce gelir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryLockFeatureTest {

    // Her test kendi ayar dosyasını kullanır (ortak DataStore testler arasında karışmasın).
    @get:Rule
    val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: IPTVViewModel
    private lateinit var settings: SettingsRepository
    private val playlist = 91

    private fun item(id: Int, type: String, category: String, name: String = "Öğe $id") = IPTVItem(
        id = id, playlistId = playlist, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://10.255.255.1/$id", category = category, type = type
    )

    private fun group(type: String, name: String) =
        IPTVGroup(id = name, name = name, type = type, items = listOf(item(1, type, name)))

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
        assertTrue("beklenen durum oluşmadı", condition())
    }

    @Before
    fun setUp() {
        val db = AppDatabase.getDatabase(app)
        runBlocking {
            db.iptvDao().insertItems(
                (1..6).map { item(91000 + it, "LIVE", "Derbi Özetleri") } +
                    (1..3).map { item(91010 + it, "LIVE", "Spor") } +
                    (1..4).map { item(91020 + it, "MOVIE", "Aksiyon") } +
                    (1..2).map { item(91030 + it, "MOVIE", "XXX Yetişkin") }
            )
        }
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            tmp.newFile("ayarlar.preferences_pb").also { it.delete() }
        }
        settings = SettingsRepository(app, store)
        vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db), settings)
        vm.setParentalLock(true)
    }

    @After
    fun tearDown() {
        runBlocking { AppDatabase.getDatabase(app).iptvDao().deleteItemsByPlaylist(playlist) }
    }

    @Test
    fun withoutManualSettings_onlyAdultCategoriesAreLocked() {
        assertTrue(vm.isCategoryLocked("MOVIE", "XXX Yetişkin"))
        assertFalse(vm.isCategoryLocked("MOVIE", "Aksiyon"))
        assertFalse(vm.isAdultContent(group("MOVIE", "Aksiyon")))
        assertTrue(vm.isAdultContent(group("MOVIE", "XXX Yetişkin")))
    }

    @Test
    fun manuallyLockedCategory_isLocked_hiddenFromShelves_andStyleStaysNormal() {
        vm.setCategoryLocked("MOVIE", "Aksiyon", true)
        waitUntil { vm.isCategoryLocked("MOVIE", "Aksiyon") }

        assertTrue("klasör kilitli", vm.isAdultContent(group("MOVIE", "Aksiyon")))
        assertTrue("vitrinden/kanal geçişinden süzülür", vm.isAdultContent(item(1, "MOVIE", "Aksiyon")))
        assertFalse("klasör kartı 'yetişkin' görünümüne geçmez", vm.isAdultDetected(group("MOVIE", "Aksiyon")))
        assertFalse("aynı adlı canlı kategori etkilenmez", vm.isCategoryLocked("LIVE", "Aksiyon"))

        // Ebeveyn kilidi kapalıysa kullanıcı kilitleri uygulanmaz (yetişkin otomatik tespiti eskisi gibi kalır).
        vm.setParentalLock(false)
        waitUntil { !vm.parentalLock.value || !vm.isAdultContent(group("MOVIE", "Aksiyon")) }
        assertFalse(vm.isAdultContent(group("MOVIE", "Aksiyon")))
    }

    @Test
    fun manuallyUnlockedAdultCategory_isOpen_untilReset() {
        vm.setCategoryLocked("MOVIE", "XXX Yetişkin", false)
        waitUntil { !vm.isCategoryLocked("MOVIE", "XXX Yetişkin") }
        assertFalse(vm.isAdultContent(group("MOVIE", "XXX Yetişkin")))
        assertTrue("görünüm yine yetişkin", vm.isAdultDetected(group("MOVIE", "XXX Yetişkin")))

        vm.resetCategoryLock("MOVIE", "XXX Yetişkin")
        waitUntil { vm.isCategoryLocked("MOVIE", "XXX Yetişkin") }
        assertTrue(vm.isAdultContent(group("MOVIE", "XXX Yetişkin")))
    }

    @Test
    fun lockableCategories_listCountsLockedAndManualState() {
        vm.setCategoryLocked("LIVE", "Spor", true)
        waitUntil { vm.isCategoryLocked("LIVE", "Spor") }

        val live = runBlocking { vm.lockableCategories("LIVE") }
        val spor = live.first { it.name == "Spor" }
        assertEquals(3, spor.count)
        assertTrue(spor.locked && spor.manual)
        val derbi = live.first { it.name == "Derbi Özetleri" }
        assertEquals(6, derbi.count)
        assertFalse(derbi.locked || derbi.manual)

        val movies = runBlocking { vm.lockableCategories("MOVIE") }
        val adult = movies.first { it.name == "XXX Yetişkin" }
        assertTrue("yetişkin otomatik kilitli, elle ayar yok", adult.locked && !adult.manual)
        assertEquals(movies.map { it.name.lowercase() }, movies.map { it.name.lowercase() }.sorted())
    }

    @Test
    fun manualSettings_surviveRestart() {
        vm.setCategoryLocked("MOVIE", "Aksiyon", true)
        waitUntil { vm.isCategoryLocked("MOVIE", "Aksiyon") }
        val db = AppDatabase.getDatabase(app)
        val restarted = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db), settings)
        waitUntil { restarted.isCategoryLocked("MOVIE", "Aksiyon") }
    }

    @Test
    fun tvCategories_reflectManualLocks() {
        vm.setCategoryLocked("MOVIE", "Aksiyon", true)
        waitUntil { vm.isCategoryLocked("MOVIE", "Aksiyon") }
        val categories = runBlocking { vm.tvCategories("MOVIE") }
        assertTrue(categories.first { it.name == "Aksiyon" }.isAdult)
        assertTrue(categories.first { it.name == "XXX Yetişkin" }.isAdult)
    }

}
