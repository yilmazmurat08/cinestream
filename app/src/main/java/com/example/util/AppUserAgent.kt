package com.example.util

import android.os.Build
import com.example.BuildConfig

/**
 * Uygulamanın ağ isteklerinde kullandığı tek kimlik. Başka uygulamaların (TiviMate, IPTV Smarters, VLC vb.)
 * adını taşıyan User-Agent'lar kullanılmaz; yalnızca kendi adımız ve genel bir tarayıcı kimliği vardır.
 * Liste dosyasında yayın için özel bir User-Agent belirtilmişse oynatıcı onu kullanır.
 */
object AppUserAgent {
    /** Örn. "CineStream/1.0.1 (Linux; Android 14) AndroidXMedia3/1.4.1" (Media3'ün standart biçimi). */
    val app: String =
        "CineStream/${BuildConfig.VERSION_NAME} (Linux; Android ${Build.VERSION.RELEASE}) AndroidXMedia3/1.4.1"

    /** Uygulama kimliğini kabul etmeyen web tabanlı liste sunucuları için genel tarayıcı kimliği. */
    const val BROWSER: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
}
