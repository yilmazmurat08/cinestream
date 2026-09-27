package com.example

import com.example.util.DiagnosticLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tanılama dosyalarına ve çökme kayıtlarına anahtar/jeton/şifre yazılmadığını denetler. */
class DiagnosticLogTest {

    @Test
    fun redactsQueryCredentials() {
        val out = DiagnosticLog.redact(
            "API URL: http://h:8080/player_api.php?username=ali&password=gizli123&action=get_vod_info&vod_id=5 " +
                "https://api.themoviedb.org/3/movie/1?api_key=0123456789abcdef0123456789abcdef&language=tr-TR"
        )
        assertFalse(out, out.contains("gizli123"))
        assertFalse(out, out.contains("0123456789abcdef0123456789abcdef"))
        assertFalse(out, out.contains("username=ali"))
        assertTrue("Anahtar olmayan parametreler korunmalı", out.contains("action=get_vod_info") && out.contains("language=tr-TR"))
    }

    @Test
    fun redactsBearerJwtAndGoogleKeys() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4eHgifQ.c2lnbmF0dXJlLXZhbHVl"
        val out = DiagnosticLog.redact("Authorization: Bearer $jwt\njeton: $jwt\ngemini: AIzaSyA1234567890abcdefghijklmnopqrstu")
        assertFalse(out, out.contains(jwt))
        assertFalse(out, out.contains("AIzaSyA1234567890"))
    }

    @Test
    fun redactsXtreamStreamPathCredentials() {
        val out = DiagnosticLog.redact("streamUrl: http://sunucu.tv:8080/movie/kullanici/sifre/12345.mkv")
        assertEquals("streamUrl: http://sunucu.tv:8080/movie/***/***/12345.mkv", out)
    }

    @Test
    fun plainTextIsUnchanged() {
        val text = "Toplam dizi: 120\nKategori: Aksiyon"
        assertEquals(text, DiagnosticLog.redact(text))
    }
}
