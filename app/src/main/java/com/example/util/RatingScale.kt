package com.example.util

/**
 * Sağlayıcı puanlarını 0–10 ölçeğine getirir. Xtream iki alan gönderir: `rating` (10 üzerinden) ve
 * `rating_5based` (5 üzerinden). Güvenilir olan `rating_5based` × 2'dir; `rating` bazı panellerde boş ya da
 * anlamsızdır (10 ve üstü). Bilinmeyen ya da anlamsız puan 0 olur (ekranda gösterilmez, "IMDb 8+"e girmez).
 */
object RatingScale {

    /** Xtream `rating` ve `rating_5based` alanlarından 10 üzerinden puan. */
    fun fromXtream(rating: String?, rating5Based: String?): Double {
        val five = parse(rating5Based)
        if (five > 0.0 && five <= 5.0) return normalize(five * 2.0)
        return normalize(parse(rating))
    }

    /** 0–10 arası geçerli puan; 10 ve üstü (yer tutucu ya da hatalı değer), negatif ya da sayı olmayan → 0. */
    fun normalize(value: Double): Double {
        if (value.isNaN() || value <= 0.0 || value >= 10.0) return 0.0
        return Math.round(value * 10.0) / 10.0
    }

    private fun parse(raw: String?): Double =
        raw?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() && it != "null" }?.toDoubleOrNull() ?: 0.0
}
