package com.example.util

import android.app.Activity
import android.content.Context

/**
 * PRO abonelik ürünleri ve satın alma giriş noktası.
 *
 * Ödeme altyapısı henüz kurulmadı (Google Play Billing ile kurulacak). O zamana kadar satın alma ve
 * geri yükleme "servis kullanılabilir değil" hatası döndürür; PRO ekranı ve ürün adları değişmez.
 */
object SubscriptionManager {
    const val PRODUCT_MONTHLY = "cinestream_monthly"
    const val PRODUCT_YEARLY = "cinestream_yearly"
    const val PRODUCT_LIFETIME = "cinestream_lifetime"

    @Suppress("UNUSED_PARAMETER")
    fun purchasePackage(
        activity: Activity,
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onError("Satın alma servisi şu anda kullanılabilir değil.")
    }

    @Suppress("UNUSED_PARAMETER")
    fun restorePurchases(
        context: Context,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        onError("Geri yükleme servisi şu anda kullanılabilir değil.")
    }
}
