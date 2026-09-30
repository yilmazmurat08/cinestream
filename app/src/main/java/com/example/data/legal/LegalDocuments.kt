package com.example.data.legal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Uygulama içinde gösterilen yasal metinler. */
enum class LegalDoc(val assetName: String) {
    PRIVACY("privacy"),
    TERMS("terms")
}

data class LegalSection(
    val heading: String,
    val paragraphs: List<String>,
    val bullets: List<String>,
    val after: List<String>
)

data class LegalDocument(
    val title: String,
    val updated: String,
    val intro: String,
    val highlight: String,
    val sections: List<LegalSection>
)

/**
 * Metinler assets/legal/ altındaki JSON dosyalarından okunur (TR ve EN). Geliştirici adı, e-posta ve güncelleme
 * tarihi tek yerde, assets/legal/info.json dosyasındadır; metinlerdeki {DEVELOPER}, {EMAIL}, {UPDATED} yerine
 * konur. Aynı dosyalar depo kökündeki web (HTML) sürümlerinin de kaynağıdır.
 */
object LegalDocuments {

    fun load(context: Context, doc: LegalDoc, language: String): LegalDocument? = runCatching {
        val lang = if (language == "en") "en" else "tr"
        val info = JSONObject(readAsset(context, "legal/info.json"))
        val raw = JSONObject(readAsset(context, "legal/${doc.assetName}_$lang.json"))
        val tokens = mapOf(
            "{DEVELOPER}" to info.optString("developer"),
            "{EMAIL}" to info.optString("email"),
            "{UPDATED}" to info.optString("updated_$lang")
        )
        fun fill(text: String) = tokens.entries.fold(text) { acc, (k, v) -> acc.replace(k, v) }
        fun list(array: JSONArray?) = (0 until (array?.length() ?: 0)).map { fill(array!!.getString(it)) }
        val sections = raw.getJSONArray("sections").let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                LegalSection(fill(s.getString("h")), list(s.optJSONArray("p")), list(s.optJSONArray("list")), list(s.optJSONArray("after")))
            }
        }
        LegalDocument(
            title = fill(raw.getString("title")),
            updated = fill(raw.getString("updated")),
            intro = fill(raw.getString("intro")),
            highlight = fill(raw.getString("highlight")),
            sections = sections
        )
    }.getOrNull()

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
