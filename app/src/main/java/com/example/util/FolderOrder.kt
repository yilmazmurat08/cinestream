package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kullanıcının klasör düzeni: öne alınanlar (sabitleme sırasıyla) ve gizlenenler. Anahtar: [CategoryLocks.key]. */
data class FolderPrefs(val pinned: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = pinned.isEmpty() && hidden.isEmpty()
}

/**
 * "Klasörleri düzenle" (kullanıcı geri bildirimi, 14 Kasım 2026; ücretsiz): Canlı TV, film ve dizi klasörlerinden
 * istenenler en üste alınır, istenmeyenler gizlenir. Düzen cihazda saklanır, liste yenilense de kalır; sağlayıcının
 * kendi sırası ve içerik değişmez (gizlenen klasör aramada ve diğer yerlerde yine bulunur).
 */
object FolderOrder {
    private const val PREFS = "folder_prefs"
    private const val KEY_PINNED = "pinned"
    private const val KEY_HIDDEN = "hidden"

    private val _prefs = MutableStateFlow(FolderPrefs())
    val prefs: StateFlow<FolderPrefs> = _prefs.asStateFlow()

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        _prefs.value = runCatching {
            val p = sp(context)
            FolderPrefs(
                pinned = p.getString(KEY_PINNED, null).orEmpty().split('\n').filter { it.isNotBlank() },
                hidden = p.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
            )
        }.getOrDefault(FolderPrefs())
    }

    private fun save(context: Context, value: FolderPrefs) {
        _prefs.value = value
        runCatching {
            sp(context).edit()
                .putString(KEY_PINNED, value.pinned.joinToString("\n"))
                .putStringSet(KEY_HIDDEN, value.hidden)
                .apply()
        }
    }

    fun setPinned(context: Context, type: String, category: String, pinned: Boolean) {
        save(context, withPinned(_prefs.value, CategoryLocks.key(type, category), pinned))
    }

    fun setHidden(context: Context, type: String, category: String, hidden: Boolean) {
        save(context, withHidden(_prefs.value, CategoryLocks.key(type, category), hidden))
    }

    fun reset(context: Context) = save(context, FolderPrefs())

    /** Öne alınan klasör gizlenemez, gizlenen öne alınamaz (biri açılınca diğeri kalkar). */
    internal fun withPinned(p: FolderPrefs, key: String, pinned: Boolean): FolderPrefs =
        if (pinned) FolderPrefs((p.pinned - key) + key, p.hidden - key) else p.copy(pinned = p.pinned - key)

    internal fun withHidden(p: FolderPrefs, key: String, hidden: Boolean): FolderPrefs =
        if (hidden) FolderPrefs(p.pinned - key, p.hidden + key) else p.copy(hidden = p.hidden - key)

    fun isPinned(p: FolderPrefs, type: String, category: String) = CategoryLocks.key(type, category) in p.pinned
    fun isHidden(p: FolderPrefs, type: String, category: String) = CategoryLocks.key(type, category) in p.hidden

    /**
     * Klasör listesine düzeni uygular: gizlenenler çıkar, öne alınanlar sabitleme sırasıyla en üste gelir, kalanlar
     * mevcut sırasını korur. Düzen boşsa liste aynen döner.
     */
    fun <T> apply(list: List<T>, type: String, prefs: FolderPrefs, name: (T) -> String): List<T> {
        if (prefs.isEmpty || list.isEmpty()) return list
        val visible = list.filter { CategoryLocks.key(type, name(it)) !in prefs.hidden }
        if (prefs.pinned.isEmpty()) return visible
        val order = prefs.pinned.withIndex().associate { (i, k) -> k to i }
        val (top, rest) = visible.partition { CategoryLocks.key(type, name(it)) in order }
        return top.sortedBy { order[CategoryLocks.key(type, name(it))] } + rest
    }
}
