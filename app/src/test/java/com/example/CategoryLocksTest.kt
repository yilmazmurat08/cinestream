package com.example

import com.example.util.CategoryLocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLocksTest {

    @Test
    fun key_ignoresCaseAndSpaces_andNormalizesLiveTypes() {
        assertEquals("MOVIE|aksiyon", CategoryLocks.key("movie", "  Aksiyon "))
        assertEquals(CategoryLocks.key("LIVE", "Spor"), CategoryLocks.key("LIVE_TV", "SPOR"))
        assertEquals(CategoryLocks.key("LIVE", "Spor"), CategoryLocks.key("RADIO", "spor"))
        // Aynı ad farklı türde ayrı kayıttır (Canlı "Çocuk" ile Film "Çocuk" birbirini etkilemez).
        assertFalse(CategoryLocks.key("LIVE", "Çocuk") == CategoryLocks.key("MOVIE", "Çocuk"))
    }

    @Test
    fun encodeDecode_roundTrips_andSkipsBrokenEntries() {
        val k1 = CategoryLocks.key("LIVE", "Derbi Özetleri")
        val k2 = CategoryLocks.key("MOVIE", "Aksiyon | Macera") // ayırıcı karakter kategori adında olabilir
        val decoded = CategoryLocks.decode(
            setOf(CategoryLocks.encode(k1, true), CategoryLocks.encode(k2, false), "", "x", "X|MOVIE|a", "L|")
        )
        assertEquals(mapOf(k1 to true, k2 to false), decoded)
    }

    @Test
    fun manualSetting_winsOverAutoDetection_both_ways() {
        val overrides = mapOf(
            CategoryLocks.key("MOVIE", "Aksiyon") to true,
            CategoryLocks.key("MOVIE", "Yetişkin") to false
        )
        assertTrue("elle kilitlendi", CategoryLocks.isLocked(overrides, "MOVIE", "Aksiyon", detected = false))
        assertFalse("yetişkin ama elle açıldı", CategoryLocks.isLocked(overrides, "MOVIE", "Yetişkin", detected = true))
        assertTrue("ayar yok: otomatik tespit", CategoryLocks.isLocked(overrides, "MOVIE", "XXX", detected = true))
        assertFalse("ayar yok, yetişkin değil", CategoryLocks.isLocked(overrides, "MOVIE", "Komedi", detected = false))
        assertFalse("başka türdeki aynı ad etkilenmez", CategoryLocks.isLocked(overrides, "LIVE", "Aksiyon", detected = false))
    }
}
