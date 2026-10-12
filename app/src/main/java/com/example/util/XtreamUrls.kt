package com.example.util

/** Xtream adresleri için yardımcılar. */
object XtreamUrls {
    /**
     * Kullanıcı adı/şifreyi sorgu değeri olarak güvenli yapar. Yalnızca adresi bozan karakterler kodlanır:
     * `&` (parametreyi böler), `#` (adresin kalanını keser), `+` (sunucu boşluk sayar). Boşluk ve `%` gibi
     * karakterleri ağ kütüphanesi (OkHttp) zaten doğru kodlar; zaten kodlanmış değerler (ör. `%40`) değişmez.
     */
    fun queryValue(value: String): String {
        if (value.none { it == '&' || it == '#' || it == '+' }) return value
        val out = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '&' -> out.append("%26")
                '#' -> out.append("%23")
                '+' -> out.append("%2B")
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
