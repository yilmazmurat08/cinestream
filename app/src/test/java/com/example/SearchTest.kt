package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import com.example.data.model.XtreamSeriesCatalogEntity
import com.example.data.repository.IPTVRepository
import com.example.ui.IPTVViewModel
import com.example.util.SearchRanking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Arama: Xtream dizileri (ayrı katalog tablosunda) bulunur; aranan yapım en üstte gelir, yalnızca klasör adı
 * eşleşen alakasız yapımlar araya girmez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    // Xtream adresleri: katalog yalnızca Xtream kaynağı varken tutulur.
    private fun item(id: Int, name: String, type: String, category: String) = IPTVItem(id = id, playlistId = 1, name = name,
        cleanedName = name, logoUrl = null, streamUrl = "http://10.255.255.1/${type.lowercase()}/u/p/$id.ts", category = category, type = type)

    private fun series(id: Int, name: String) = XtreamSeriesCatalogEntity(seriesId = id, name = name,
        canonicalKey = name.lowercase(), coverUrl = "", categoryId = "Apple TV")

    private fun search(vm: IPTVViewModel, query: String): List<IPTVItem> {
        vm.updateSearchQuery(query)
        val end = System.currentTimeMillis() + 8_000
        var last = emptyList<IPTVItem>()
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
            val now = vm.searchResults.value
            if (now.isNotEmpty() && now == last) break
            last = now
        }
        return vm.searchResults.value
    }

    @Test
    fun xtreamSeries_areFound_andComeFirst() {
        runBlocking {
            db.iptvDao().insertItems(listOf(
                // Kanal numarası 7001, katalogdaki dizi numarasıyla aynı (listede çakışmamalı).
                item(7001, "HD: Gala Brea Kanal", "LIVE", "Ulusal"),
                item(7002, "Escalabrea Night", "MOVIE", "Aksiyon"),
                item(7003, "Başka Film", "MOVIE", "La Brea Koleksiyonu")
            ))
            db.iptvDao().insertXtreamSeriesCatalog(listOf(series(7001, "La Brea"), series(7010, "Breaking Bad")))
        }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        scope.launch { vm.searchResults.collect {} }

        val results = search(vm, "la brea ")
        assertEquals("La Brea", results.first().name)
        assertEquals("SERIES", results.first().type)
        assertTrue("yalnızca klasörü eşleşen film çıkmaz", results.none { it.id == 7003 && it.type == "MOVIE" })
        assertEquals("aynı numaralı kanal ve dizi birlikte listelenir", 2, results.count { it.id == 7001 })

        assertEquals(listOf("Breaking Bad"), search(vm, "breaking").map { it.name })
    }

    private fun named(id: Int, name: String, category: String = "Film") = item(id, name, "MOVIE", category)

    @Test
    fun ranking_putsTheSearchedTitleFirst() {
        val items = listOf(
            named(1, "Escape Room"), named(2, "TR: Room (2015)"), named(3, "The Room"), named(4, "Bathroom Stories"),
            named(5, "Room"), named(6, "Gizli", category = "Room Service")
        )
        assertEquals(listOf(5, 2, 3, 1, 4), SearchRanking.rank("room", items).map { it.id })
        // Ad eşleşmesi yoksa klasör eşleşmesi gösterilir (ör. "Aksiyon" araması).
        assertEquals(listOf(6), SearchRanking.rank("service", items).map { it.id })
        // Türkçe harfler ve büyük harf fark etmez.
        assertEquals(listOf(7), SearchRanking.rank("kizilcik serbeti", listOf(named(7, "KIZILCIK ŞERBETİ"))).map { it.id })
    }
}
