package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.IPTVItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Aşırı büyük alanlı satırlar (ör. base64 gömülü logo) kırpılmalı; aksi hâlde liste okuması
 * "Couldn't read row ... from CursorWindow" hatasıyla çöker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OversizedRowTrimTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun oversizedFields_areTrimmed_normalRowsUntouched() = runBlocking {
        val dao = db.iptvDao()
        val hugeLogo = "data:image/png;base64," + "A".repeat(3_000_000)
        val normal = IPTVItem(id = 1, playlistId = 1, name = "Normal Film", cleanedName = "Normal Film",
            logoUrl = "https://image.tmdb.org/t/p/w500/a.jpg", streamUrl = "http://x/1.mp4", category = "Aile", type = "MOVIE",
            summary = "Kısa özet")
        val huge = normal.copy(id = 2, name = "Dev Satır", logoUrl = hugeLogo, summary = "B".repeat(50_000))
        dao.insertItems(listOf(normal, huge))

        dao.trimOversizedItemFields()

        val items = dao.getItemsByTypeFlow("MOVIE").first().associateBy { it.id }
        assertEquals(normal, items[1])
        assertNull(items[2]!!.logoUrl)
        assertEquals(8000, items[2]!!.summary.length)
        assertTrue(items[2]!!.name == "Dev Satır")
    }
}
