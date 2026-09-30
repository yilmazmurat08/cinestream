package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Paywall'da gösterilen bir paket: fiyat metni doğrudan Google Play'den gelir (kullanıcıya gösterilen = alınan). */
data class ProOffer(
    val productId: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val isSubscription: Boolean
)

/** Satın alma sonucu (arayüz metinleri çağıran tarafta TR/EN kaynaklardan seçilir). */
sealed class PurchaseOutcome {
    object Success : PurchaseOutcome()
    object Pending : PurchaseOutcome()
    object Cancelled : PurchaseOutcome()
    object AlreadyOwned : PurchaseOutcome()
    object Unavailable : PurchaseOutcome()
    data class Failed(val code: Int) : PurchaseOutcome()
}

/**
 * CineStream PRO: Google Play Faturalandırma.
 *
 * Ürünler (Play Console'da aynı kimliklerle oluşturulur):
 * - [PRODUCT_MONTHLY], [PRODUCT_YEARLY]: abonelik (her birinde tek temel plan)
 * - [PRODUCT_LIFETIME]: tek seferlik ürün
 *
 * PRO durumu yalnızca Google Play'deki geçerli satın alımlardan belirlenir ([isPro]); uygulama içi bir düğme
 * ödeme olmadan PRO açamaz. Play'e ulaşılamazsa (ağ yok, Play Store yok) son bilinen durum korunur, böylece
 * ödeme yapmış kullanıcı çevrimdışıyken PRO'sunu kaybetmez. Abonelik iptal/süre dolumu bir sonraki başarılı
 * sorguda PRO'yu kapatır.
 */
object SubscriptionManager {
    const val PRODUCT_MONTHLY = "cinestream_monthly"
    const val PRODUCT_YEARLY = "cinestream_yearly"
    const val PRODUCT_LIFETIME = "cinestream_lifetime"

    private val SUBSCRIPTIONS = listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY)
    private val ONE_TIME = listOf(PRODUCT_LIFETIME)
    val ALL_PRODUCTS = SUBSCRIPTIONS + ONE_TIME

    private val main = Handler(Looper.getMainLooper())
    private var client: BillingClient? = null
    private val details = mutableMapOf<String, ProductDetails>()
    private var pendingPurchaseCallback: ((PurchaseOutcome) -> Unit)? = null
    private var onProChanged: ((Boolean) -> Unit)? = null

    private val _offers = MutableStateFlow<Map<String, ProOffer>>(emptyMap())
    /** Play'den okunan paketler; boşsa fiyatlar henüz yüklenmedi veya Play kullanılamıyor. */
    val offers: StateFlow<Map<String, ProOffer>> = _offers.asStateFlow()

    /**
     * Uygulama açılışında çağrılır. [onProStatus], Play'den kesin bir cevap alındığında PRO durumunu bildirir
     * (true/false); Play'e ulaşılamazsa çağrılmaz.
     */
    fun init(context: Context, onProStatus: (Boolean) -> Unit) {
        onProChanged = onProStatus
        if (client != null) {
            refresh()
            return
        }
        val listener = PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        client = runCatching {
            BillingClient.newBuilder(context.applicationContext)
                .setListener(listener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
        }.getOrNull()
        connectThen { refresh() }
    }

    /** Play bağlantısı hazırsa ürünleri ve satın alımları yeniden sorgular (uygulama öne gelince çağrılır). */
    fun refresh() {
        connectThen {
            queryProducts()
            queryOwned()
        }
    }

    private fun connectThen(action: () -> Unit) {
        val c = client ?: return
        if (c.isReady) {
            action()
            return
        }
        runCatching {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) main.post(action)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private fun queryProducts() {
        val c = client ?: return
        fun query(ids: List<String>, type: String) {
            val params = QueryProductDetailsParams.newBuilder().setProductList(
                ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() }
            ).build()
            runCatching {
                c.queryProductDetailsAsync(params) { result, list ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
                    main.post {
                        list.productDetailsList.forEach { details[it.productId] = it }
                        _offers.value = details.values.mapNotNull { toOffer(it) }.associateBy { it.productId }
                    }
                }
            }
        }
        query(SUBSCRIPTIONS, BillingClient.ProductType.SUBS)
        query(ONE_TIME, BillingClient.ProductType.INAPP)
    }

    private fun toOffer(d: ProductDetails): ProOffer? = if (d.productType == BillingClient.ProductType.SUBS) {
        // Temel planın son (sürekli) fiyat aşaması gösterilir.
        val phase = d.subscriptionOfferDetails?.firstOrNull { it.offerId == null }?.pricingPhases?.pricingPhaseList?.lastOrNull()
            ?: d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()
        phase?.let { ProOffer(d.productId, it.formattedPrice, it.priceAmountMicros, isSubscription = true) }
    } else {
        d.oneTimePurchaseOfferDetails?.let { ProOffer(d.productId, it.formattedPrice, it.priceAmountMicros, isSubscription = false) }
    }

    /** Geçerli satın alımları Play'den okur; sonuç kesinse PRO durumunu bildirir. */
    private fun queryOwned() {
        val c = client ?: return
        val collected = mutableListOf<Purchase>()
        var answered = 0
        var failed = false
        fun done() {
            answered++
            if (answered < 2) return
            main.post {
                collected.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach { acknowledge(it) }
                if (!failed) onProChanged?.invoke(isProFromPurchases(collected.map { OwnedPurchase(it.products, it.purchaseState == Purchase.PurchaseState.PURCHASED) }))
            }
        }
        for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            runCatching {
                c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, purchases ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) collected += purchases else failed = true
                    done()
                }
            }.onFailure {
                failed = true
                done()
            }
        }
    }

    /** Seçilen paket için Google Play satın alma ekranını açar. */
    fun purchase(activity: Activity, productId: String, onResult: (PurchaseOutcome) -> Unit) {
        val c = client
        val d = details[productId]
        if (c == null || !c.isReady || d == null) {
            onResult(PurchaseOutcome.Unavailable)
            refresh()
            return
        }
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (d.productType == BillingClient.ProductType.SUBS) {
            val offer = d.subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: d.subscriptionOfferDetails?.firstOrNull()
            if (offer == null) {
                onResult(PurchaseOutcome.Unavailable)
                return
            }
            builder.setOfferToken(offer.offerToken)
        }
        pendingPurchaseCallback = onResult
        val result = runCatching {
            c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build())).build())
        }.getOrNull()
        if (result == null || result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseCallback = null
            onResult(mapFailure(result?.responseCode ?: BillingClient.BillingResponseCode.ERROR))
        }
    }

    /** "Satın alımları geri yükle": Play'deki geçerli satın alımları yeniden okur. */
    fun restore(onResult: (Boolean?) -> Unit) {
        val c = client
        if (c == null) {
            onResult(null)
            return
        }
        val previous = onProChanged
        var delivered = false
        onProChanged = { isPro ->
            previous?.invoke(isPro)
            if (!delivered) {
                delivered = true
                onProChanged = previous
                onResult(isPro)
            }
        }
        connectThen { queryOwned() }
        // Play'e ulaşılamazsa kullanıcı sonsuza dek beklemesin.
        main.postDelayed({
            if (!delivered) {
                delivered = true
                onProChanged = previous
                onResult(null)
            }
        }, 10_000)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        main.post {
            val callback = pendingPurchaseCallback
            pendingPurchaseCallback = null
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val ours = purchases.orEmpty().filter { p -> p.products.any { it in ALL_PRODUCTS } }
                    val completed = ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    completed.filter { !it.isAcknowledged }.forEach { acknowledge(it) }
                    when {
                        completed.isNotEmpty() -> {
                            onProChanged?.invoke(true)
                            callback?.invoke(PurchaseOutcome.Success)
                        }
                        ours.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> callback?.invoke(PurchaseOutcome.Pending)
                        else -> callback?.invoke(PurchaseOutcome.Failed(result.responseCode))
                    }
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    queryOwned()
                    callback?.invoke(PurchaseOutcome.AlreadyOwned)
                }
                else -> callback?.invoke(mapFailure(result.responseCode))
            }
        }
    }

    private fun mapFailure(code: Int): PurchaseOutcome = when (code) {
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.AlreadyOwned
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> PurchaseOutcome.Unavailable
        else -> PurchaseOutcome.Failed(code)
    }

    /** Satın alım 3 gün içinde onaylanmazsa Google iade eder; bu yüzden her tamamlanan satın alım onaylanır. */
    private fun acknowledge(purchase: Purchase) {
        val c = client ?: return
        runCatching {
            c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
        }
    }

    /** Bir satın alımın ürünleri ve tamamlanıp tamamlanmadığı (test edilebilir, Play türlerinden bağımsız). */
    data class OwnedPurchase(val products: List<String>, val completed: Boolean)

    /** Tamamlanmış bir CineStream PRO satın alımı (abonelik veya ömür boyu) varsa PRO'dur. */
    fun isProFromPurchases(purchases: List<OwnedPurchase>): Boolean =
        purchases.any { p -> p.completed && p.products.any { it in ALL_PRODUCTS } }

    /** Yıllık paketin aylık pakete göre tasarruf yüzdesi (ör. 12×80 = 960 yerine 800 → %17). */
    fun yearlySavingsPercent(monthlyMicros: Long, yearlyMicros: Long): Int? {
        if (monthlyMicros <= 0 || yearlyMicros <= 0) return null
        val full = monthlyMicros * 12
        if (yearlyMicros >= full) return null
        return Math.round((full - yearlyMicros) * 100.0 / full).toInt()
    }
}
