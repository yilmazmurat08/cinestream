package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.ItemMetadataUpdate
import com.example.data.model.IPTVItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Takılma kök nedeni: her detay bilgisi yazımı tüm film listesinin yeniden okunmasına yol açıyordu.
 * Toplu yazma (uygulama arka plana geçince) listeyi yalnızca bir kez yeniden okutmalı.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadataBatchReloadTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun update(id: Int) = ItemMetadataUpdate(id, "Özet $id", "Oyuncu A, Oyuncu B", "Yönetmen", 7.5, "https://img/$id.jpg", null, "2020", "Dram")

    private fun countReloads(write: suspend () -> Unit): Int = runBlocking {
        val dao = db.iptvDao()
        val reloads = AtomicInteger(0)
        val job = launch(Dispatchers.IO) { dao.getItemsByTypeFlow("MOVIE").collect { reloads.incrementAndGet() } }
        while (reloads.get() == 0) delay(20)
        val before = reloads.get()
        write()
        delay(1_500)
        job.cancel()
        reloads.get() - before
    }

    @Test
    fun batchedMetadata_reloadsListOnce_insteadOfOncePerItem() {
        val dao = db.iptvDao()
        runBlocking {
            dao.insertItems((1..500).map {
                IPTVItem(id = it, playlistId = 1, name = "Film $it", cleanedName = "Film $it", logoUrl = null,
                    streamUrl = "http://x/$it.mp4", category = "Aile", type = "MOVIE")
            })
        }
        val separate = countReloads {
            (1..20).forEach { val u = update(it); dao.updateItemMetadata(u.itemId, u.summary, u.cast, u.director, u.rating, u.logoUrl, u.trailerUrl, u.releaseDate, u.genre); delay(50) }
        }
        val batched = countReloads { dao.updateItemsMetadata((21..40).map { update(it) }) }
        println("[PERF] Ayrı ayrı 20 yazma: $separate yeniden okuma, toplu 20 yazma: $batched yeniden okuma")
        assertEquals(1, batched)
        assertTrue("Ayrı yazmalar listeyi defalarca okutur: $separate", separate > batched)
        assertEquals("Özet 30", runBlocking { dao.getItemsByTypeFlow("MOVIE").first() }.first { it.id == 30 }.summary)
    }
}
