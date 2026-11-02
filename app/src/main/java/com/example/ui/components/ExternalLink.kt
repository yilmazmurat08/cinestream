package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.R
import com.example.ui.tv.TvDevice

/**
 * Web adresini açar. Android TV'de tarayıcı açılmaz (Android TV kalite yönergesi TV-WB: "uygulama web
 * tarayıcısı başlatmamalı"); bunun yerine adres uzun bir mesajla gösterilir, kullanıcı telefonda açar.
 * Telefonda davranış değişmedi.
 */
object ExternalLink {

    /** Adresi açar; TV'de yalnızca adresi gösterir. Açıldıysa true. */
    fun open(context: Context, url: String): Boolean {
        if (TvDevice.isTv(context)) {
            val shown = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            Toast.makeText(context, context.getString(R.string.tv_open_link_on_phone, shown), Toast.LENGTH_LONG).show()
            return false
        }
        return launch(context, url)
    }

    /**
     * Google Play abonelik sayfası. TV'de tarayıcı yerine abonelik yönetiminin yeri anlatılır
     * (Google Play → Ödemeler ve abonelikler).
     */
    fun openPlaySubscriptions(context: Context, url: String): Boolean {
        if (TvDevice.isTv(context)) {
            Toast.makeText(context, context.getString(R.string.tv_manage_subscription_hint), Toast.LENGTH_LONG).show()
            return false
        }
        return launch(context, url)
    }

    private fun launch(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
