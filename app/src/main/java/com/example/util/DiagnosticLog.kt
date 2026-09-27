package com.example.util

import android.content.Context
import com.example.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Geliştirme sırasında kullanılan tanılama dosyaları (xtream_cover_diag, person_tmdb_diag vb.).
 *
 * Yalnızca debug derlemede crash_logs/ klasörüne yazılır; release derlemede hiçbir şey yazılmaz.
 * Gerçek çökme kayıtları (crash_*.txt) CrashRecoveryManager tarafından her derlemede yazılmaya devam eder.
 * Anahtar, jeton ve şifreler dosyaya yazılmadan önce maskelenir.
 */
object DiagnosticLog {
    private const val DIR = "crash_logs"

    val enabled: Boolean get() = BuildConfig.DEBUG

    /** [name]_yyyyMMdd_HHmmss.txt dosyasına yazar. [timestamped] false ise [name].txt üzerine yazar. */
    fun write(context: Context, name: String, content: String, timestamped: Boolean = true) {
        if (!enabled) return
        try {
            val dir = File(context.filesDir, DIR)
            if (!dir.exists()) dir.mkdirs()
            val fileName = if (timestamped) {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                "${name}_$ts.txt"
            } else {
                "$name.txt"
            }
            File(dir, fileName).writeText(redact(content))
        } catch (_: Exception) {
        }
    }

    private val secretPatterns = listOf(
        // ?api_key=... / &password=... / &username=... gibi sorgu parametreleri
        Regex("(?i)((?:api_key|apikey|key|token|access_token|password|pass|username|user)=)[^&\\s'\"]+") to "$1***",
        // Authorization: Bearer eyJ...
        Regex("(?i)(Bearer\\s+)[A-Za-z0-9._\\-]+") to "$1***",
        // JWT biçimindeki jetonlar
        Regex("eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+") to "eyJ***",
        // Google API anahtarları (Gemini)
        Regex("AIza[0-9A-Za-z_\\-]{20,}") to "AIza***",
        // Xtream yayın yolları: /live|movie|series/kullanıcı/şifre/id
        Regex("(?i)(/(?:live|movie|series)/)[^/\\s]+/[^/\\s]+/") to "$1***/***/"
    )

    fun redact(text: String): String =
        secretPatterns.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
}
