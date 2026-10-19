package com.example.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * Ayarlar'daki "Çökme Günlükleri (Tanılama)" bölümü normal kullanıcıdan gizlidir. Ayarlar'ın altındaki sürüm
 * satırına [TAPS] kez art arda dokununca açılır ve bu cihazda açık kalır (geliştirici ve test kullanıcıları için).
 * Çökme sonrası açılan hata ekranı bundan etkilenmez, her zaman görünür.
 */
object DiagnosticsUnlock {
    const val TAPS = 7
    private const val PREFS = "diagnostics"
    private const val KEY = "unlocked"

    /** Ekranın dinlediği durum; [load] ile cihazdaki kayıttan doldurulur. */
    val unlocked = mutableStateOf(false)

    fun load(context: Context) {
        unlocked.value = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
        }.getOrDefault(false)
    }

    fun unlock(context: Context) {
        unlocked.value = true
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) } }
    }
}
