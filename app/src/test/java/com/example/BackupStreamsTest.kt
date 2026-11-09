package com.example

import com.example.data.model.IPTVItem
import com.example.util.BackupStreams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kesintisiz izleme: aynı kanalın yedek sürümlerini bulma ve sıralama. */
class BackupStreamsTest {
    private var nextId = 1
    private fun live(name: String, category: String = "Ulusal", url: String = "http://h/live/u/p/${nextId}.ts") =
        IPTVItem(id = nextId++, playlistId = 1, name = name, cleanedName = name, logoUrl = null, streamUrl = url, category = category, type = "LIVE")

    @Test fun baseName_dropsQualityCountryAndBackupTags() {
        assertEquals("trt 1", BackupStreams.baseName("TRT 1 HD"))
        assertEquals("trt 1", BackupStreams.baseName("TR: TRT 1 FHD"))
        assertEquals("trt 1", BackupStreams.baseName("TR | TRT 1 (Yedek)"))
        assertEquals("trt 1", BackupStreams.baseName("TRT 1 4K HEVC"))
        assertEquals("trt 1", BackupStreams.baseName("TRT 1 Yedek 2"))
        assertEquals("show tv", BackupStreams.baseName("SHOW TV [1080p]"))
        assertEquals("kanal d", BackupStreams.baseName("KANAL D HD"))
    }

    @Test fun channelNumbersStayDifferent() {
        assertTrue(BackupStreams.baseName("beIN Sports 1 HD") != BackupStreams.baseName("beIN Sports 2 HD"))
    }

    @Test fun candidates_sameChannelOnly_sameFolderFirst_thenQuality() {
        val failed = live("TRT 1 HD")
        val sd = live("TRT 1 SD")
        val fhd = live("TRT 1 FHD")
        val otherFolderUhd = live("TRT 1 4K", category = "4K Kanallar")
        val backup = live("TRT 1 HD Yedek")
        val other = live("TRT 2 HD")
        val sameUrl = live("TRT 1", url = failed.streamUrl)
        val pool = listOf(failed, sd, fhd, otherFolderUhd, backup, other, sameUrl)
        val result = BackupStreams.candidates(failed, pool, tried = emptySet())
        assertEquals(listOf(fhd, sd, backup, otherFolderUhd).map { it.name }, result.map { it.name })
    }

    @Test fun candidates_skipTriedUrls() {
        val failed = live("TRT 1 HD")
        val fhd = live("TRT 1 FHD")
        val sd = live("TRT 1 SD")
        val result = BackupStreams.candidates(failed, listOf(fhd, sd), tried = setOf(fhd.streamUrl))
        assertEquals(listOf(sd), result)
    }
}
