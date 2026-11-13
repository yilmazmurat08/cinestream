package com.example.util

import android.content.Context
import com.example.data.model.IPTVItem
import java.util.Locale

/** Bir dizinin jenerik aralığı (saniye). */
data class IntroMark(val startSec: Long, val endSec: Long)

/**
 * "Jeneriği geç": kullanıcı bir dizinin jeneriğini bir kez işaretler (başı ve sonu), sonraki bölümlerde o aralıkta
 * "Jeneriği geç" düğmesi çıkar. İşaret cihazda, dizi adına göre saklanır; liste yenilense de kalır.
 */
object IntroSkip {
    private const val PREFS = "intro_marks"
    const val MIN_LENGTH_SEC = 5L
    const val MAX_LENGTH_SEC = 300L
    /** Bölümlerin açılışı birkaç saniye kayabilir: düğme işaretli başlangıçtan biraz önce çıkar. */
    const val EARLY_SEC = 10L
    /** Jeneriğin son saniyelerinde düğme gösterilmez (geçmeye değmez). */
    const val LATE_SEC = 3L

    fun showKey(item: IPTVItem): String? {
        if (item.type != "SERIES") return null
        val title = com.example.data.model.SeriesParser.episodeInfoOf(item)?.showTitle
            ?: item.cleanedName.ifBlank { item.name }
        return title.trim().lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }

    fun get(context: Context, key: String): IntroMark? {
        val raw = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) }.getOrNull() ?: return null
        val parts = raw.split(',')
        val start = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val end = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return IntroMark(start, end).takeIf { isValid(it.startSec, it.endSec) }
    }

    fun save(context: Context, key: String, mark: IntroMark) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, "${mark.startSec},${mark.endSec}").apply()
    }

    fun clear(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    // İpuçları (bir kez): bayrağın ne işe yaradığı ve bir dizide bir kez gösterilen "işaretle" önerisi.
    fun hintShown(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("__hint_shown", false)
    fun markHintShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("__hint_shown", true).apply()
    }
    fun suggested(context: Context, key: String): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("__suggested|$key", false)
    fun markSuggested(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("__suggested|$key", true).apply()
    }

    /** "Jeneriği atlamak ister misin?" önerisi: işaretsiz dizinin 2. ve sonraki bölümlerinde, ilk 5 dakikada bir kez. */
    fun shouldSuggest(season: Int?, episode: Int?, positionSec: Long): Boolean =
        ((episode ?: 0) >= 2 || (season ?: 0) >= 2) && positionSec in 15L..300L

    fun isValid(startSec: Long, endSec: Long): Boolean =
        startSec >= 0 && endSec - startSec in MIN_LENGTH_SEC..MAX_LENGTH_SEC

    /** Bu konumda "Jeneriği geç" düğmesi görünsün mü? */
    fun shouldOffer(positionSec: Long, mark: IntroMark?): Boolean {
        if (mark == null) return false
        return positionSec >= (mark.startSec - EARLY_SEC).coerceAtLeast(0) && positionSec < mark.endSec - LATE_SEC
    }
}
