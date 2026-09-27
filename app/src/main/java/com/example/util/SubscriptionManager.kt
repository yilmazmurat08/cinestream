package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.repository.SettingsRepository
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SubscriptionManager {
    private const val TAG = "SubscriptionManager"
    const val ENTITLEMENT_PRO = "pro_access"

    // RevenueCat package identifiers
    const val PRODUCT_MONTHLY = "cinestream_monthly"
    const val PRODUCT_YEARLY = "cinestream_yearly"
    const val PRODUCT_LIFETIME = "cinestream_lifetime"

    var isInitialized = false
        private set

    fun initRevenueCat(context: Context, apiKey: String = "REVENUECAT_API_KEY") {
        if (isInitialized) return

        if (apiKey.isBlank() || apiKey == "REVENUECAT_API_KEY" || apiKey == "placeholder" ||
            (!apiKey.startsWith("goog_") && !apiKey.startsWith("amzn_"))) {
            Log.i(TAG, "RevenueCat API key is placeholder or invalid. Running in local subscription mode.")
            return
        }

        try {
            Purchases.configure(
                PurchasesConfiguration.Builder(context.applicationContext, apiKey).build()
            )
            isInitialized = true
            Log.d(TAG, "RevenueCat initialized successfully.")

            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
                checkCustomerProAccess(customerInfo, context)
            }

            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    checkCustomerProAccess(customerInfo, context)
                }

                override fun onError(error: PurchasesError) {
                    Log.w(TAG, "RevenueCat customer info notice: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "RevenueCat initialization unavailable (${e.message}). Falling back to local subscription mode.")
            isInitialized = false
        }
    }

    private fun checkCustomerProAccess(customerInfo: CustomerInfo, context: Context) {
        val hasProAccess = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
        if (hasProAccess) {
            com.example.IPTVApplication.applicationScope.launch {
                SettingsRepository(context).setProUser(true)
            }
        }
    }

    fun purchasePackage(
        activity: Activity,
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isInitialized) {
            try {
                Purchases.sharedInstance.getOfferingsWith(
                    onError = { error ->
                        Log.w(TAG, "getOfferings failed: ${error.message}")
                        onError(error.message)
                    },
                    onSuccess = { offerings ->
                        val packageToPurchase = offerings.current?.availablePackages?.find {
                            it.product.id == productId || it.identifier == productId
                        }
                        if (packageToPurchase != null) {
                            Purchases.sharedInstance.purchaseWith(
                                purchaseParams = com.revenuecat.purchases.PurchaseParams.Builder(activity, packageToPurchase).build(),
                                onError = { error, userCancelled ->
                                    if (!userCancelled) {
                                        onError(error.message)
                                    }
                                },
                                onSuccess = { _, customerInfo ->
                                    val isPro = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
                                    if (isPro) {
                                        onSuccess()
                                    } else {
                                        onError("Satın alma işlemi tamamlandı fakat PRO erişimi aktifleşmedi.")
                                    }
                                }
                            )
                        } else {
                            onError("Seçilen abonelik paketi sunucuda bulunamadı.")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Purchase exception: ${e.message}")
                onError(e.message ?: "Satın alma sırasında bir hata oluştu.")
            }
        } else {
            onError("Satın alma servisi şu anda kullanılabilir değil.")
        }
    }

    fun restorePurchases(
        context: Context,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isInitialized) {
            try {
                Purchases.sharedInstance.restorePurchasesWith(
                    onError = { error ->
                        Log.w(TAG, "Restore error: ${error.message}")
                        onError(error.message)
                    },
                    onSuccess = { customerInfo ->
                        val isPro = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
                        onSuccess(isPro)
                    }
                )
            } catch (e: Exception) {
                onError(e.message ?: "Geri yükleme başarısız.")
            }
        } else {
            onError("Geri yükleme servisi şu anda kullanılabilir değil.")
        }
    }
}
