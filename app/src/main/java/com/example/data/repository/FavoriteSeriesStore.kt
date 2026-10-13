package com.example.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Xtream dizi kataloğundaki favori diziler (dizi kimliğiyle).
 *
 * Katalog dizileri ve bölümleri `iptv_items` tablosunda satır olarak bulunmaz; favori bayrağı oraya yazılınca ya hiçbir
 * şey olmuyordu (bölüm kimliği) ya da aynı numaralı başka bir kanal/film favorilere ekleniyordu (dizi kimliği). Bu yüzden
 * dizi favorileri ayrı tutulur; dizi kimliği sağlayıcıda sabit olduğundan liste yenilemesinde kaybolmaz.
 */
class FavoriteSeriesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<Set<Int>> = _ids.asStateFlow()

    private fun load(): Set<Int> =
        try {
            prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

    /** Favoriye ekler ya da çıkarır; yeni durumu döndürür. */
    @Synchronized
    fun toggle(seriesId: Int): Boolean {
        val now = _ids.value.toMutableSet()
        val added = now.add(seriesId)
        if (!added) now.remove(seriesId)
        _ids.value = now
        prefs.edit().putStringSet(KEY, now.map { it.toString() }.toSet()).apply()
        return added
    }

    private companion object {
        const val PREFS = "favorite_series"
        const val KEY = "series_ids"
    }
}
