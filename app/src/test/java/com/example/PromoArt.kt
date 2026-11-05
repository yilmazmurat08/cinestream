package com.example

import android.content.Context
import java.io.File

/**
 * Tanıtım ve mağaza ekranları için çizilmiş HAYALİ afiş ve kanal logoları (test kaynakları: `promo/afis`, `promo/kanal`).
 * Gerçek film, kanal ya da marka yoktur; görseller `play-store/gorseller/uret/afisleri-ciz.js` ile üretilir.
 * Görsel yoksa null döner; çağıran eski düz renkli görsele düşer.
 */
object PromoArt {

    fun slug(s: String): String = s.lowercase(java.util.Locale.forLanguageTag("tr"))
        .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** [kanal] true ise yatay kanal logosu, değilse dikey afiş. */
    fun uri(context: Context, name: String, kanal: Boolean): String? {
        val path = "promo/${if (kanal) "kanal" else "afis"}/${slug(name)}.jpg"
        val stream = PromoArt::class.java.classLoader?.getResourceAsStream(path) ?: return null
        val file = File(context.cacheDir, "promo_" + path.replace('/', '_'))
        stream.use { input -> if (!file.exists()) file.outputStream().use { input.copyTo(it) } }
        return file.toURI().toString()
    }
}
