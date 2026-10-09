package com.example.util

import java.util.Locale

/**
 * Kullanıcının kategori kilidi tercihleri. Elle ayar varsa o geçerlidir; yoksa yetişkin kategorisi otomatik
 * tespit edilir (eski davranış). Tercihler "TİP|kategori" anahtarıyla saklanır ve cihazdan hiçbir yere gitmez.
 */
object CategoryLocks {

    /** "LIVE", "MOVIE", "SERIES"; "LIVE_TV" ve "RADIO" canlı yayın sayılır. */
    fun normalizeType(type: String): String {
        val t = type.trim().uppercase(Locale.ROOT)
        return if (t.startsWith("LIVE") || t == "RADIO") "LIVE" else t
    }

    fun key(type: String, category: String): String =
        "${normalizeType(type)}|${category.trim().lowercase(Locale.ROOT)}"

    /** Saklama biçimi: "L|anahtar" (kilitli) ya da "U|anahtar" (kilidi elle açılmış). */
    fun encode(key: String, locked: Boolean): String = (if (locked) "L|" else "U|") + key

    fun decode(entries: Set<String>): Map<String, Boolean> {
        val out = HashMap<String, Boolean>(entries.size)
        for (e in entries) {
            if (e.length < 3 || e[1] != '|') continue
            when (e[0]) {
                'L' -> out[e.substring(2)] = true
                'U' -> out[e.substring(2)] = false
            }
        }
        return out
    }

    /** Elle ayar varsa o, yoksa [detected] (yetişkin otomatik tespiti). */
    fun isLocked(overrides: Map<String, Boolean>, type: String, category: String, detected: Boolean): Boolean =
        overrides[key(type, category)] ?: detected
}
