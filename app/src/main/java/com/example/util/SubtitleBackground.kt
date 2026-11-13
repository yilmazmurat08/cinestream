package com.example.util

/** Altyazı arka planı seçenekleri (Ayarlar'da saklanan anahtarlar). */
object SubtitleBackground {
    const val NONE = "NONE"
    const val SEMI = "SEMI"
    const val SOLID = "SOLID"
    val options = listOf(NONE, SEMI, SOLID)

    /** Oynatıcı ve önizleme için ARGB renk. Bilinmeyen anahtar saydam sayılır. */
    fun argb(key: String): Int = when (key) {
        SEMI -> 0x99000000.toInt()
        SOLID -> 0xFF000000.toInt()
        else -> 0x00000000
    }
}
