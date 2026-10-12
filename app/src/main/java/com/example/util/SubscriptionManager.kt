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
    /** Süren abonelik değişimi: hedef ürün ve yerine geçilen eski aboneliğin jetonu (başarıda yerel listeden çıkarılır). */
    private var pendingReplacement: Pair<String, String>? = null
    private var onProChanged: ((Boolean) -> Unit)? = null

    /** Play'den en son okunan satın alımlar (paket yükseltmede eski aboneliğin jetonu buradan alınır). */
    private var ownedPurchases: List<OwnedPurchase> = emptyList()

    private val _owned = MutableStateFlow(OwnedState())
    /** Kullanıcının sahip olduğu paket ve hâlâ yenilenen abonelikleri (paywall'da yükseltme seçenekleri için). */
    val owned: StateFlow<OwnedState> = _owned.asStateFlow()

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
                if (!failed) {
                    val owned = collected.map { it.toOwned() }
                    ownedPurchases = owned
                    _owned.value = ownedStateFrom(owned)
                    onProChanged?.invoke(isProFromPurchases(owned))
                }
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
        val replacement = subscriptionToReplace(productId, ownedPurchases)
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (d.productType == BillingClient.ProductType.SUBS) {
            val offer = d.subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: d.subscriptionOfferDetails?.firstOrNull()
            if (offer == null) {
                onResult(PurchaseOutcome.Unavailable)
                return
            }
            builder.setOfferToken(offer.offerToken)
        }
        // Aylık → yıllık geçişte eski abonelik yenisiyle DEĞİŞTİRİLİR; yoksa iki abonelik birden ücretlenirdi.
        // CHARGE_FULL_PRICE: yeni paketin fiyatı hemen alınır, eski paketin kalan değeri zamana çevrilip yeni pakete
        // eklenir (Google'ın "kısa dönemden uzun döneme geçiş" için örnek gösterdiği mod; ürünler arası her geçişte geçerli).
        if (replacement != null) {
            builder.setSubscriptionProductReplacementParams(
                BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.newBuilder()
                    .setOldProductId(replacement.oldProductId)
                    .setReplacementMode(BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE)
                    .build()
            )
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(builder.build()))
        if (replacement != null) {
            // Eski aboneliğin jetonu burada verilir. Aynı mod eski Play Store sürümleri için de eklenir; yeni sürümler
            // ürün düzeyindeki ayarı kullanır (iki enum'un sayısal değerleri farklıdır, her biri kendi sabitiyle).
            @Suppress("DEPRECATION")
            val update = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(replacement.oldToken)
                .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE)
                .build()
            flow.setSubscriptionUpdateParams(update)
        }
        pendingPurchaseCallback = onResult
        pendingReplacement = replacement?.let { productId to it.oldToken }
        val result = runCatching { c.launchBillingFlow(activity, flow.build()) }.getOrNull()
        if (result == null || result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseCallback = null
            pendingReplacement = null
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
            val replaced = pendingReplacement
            pendingReplacement = null
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val ours = purchases.orEmpty().filter { p -> p.products.any { it in ALL_PRODUCTS } }
                    val completed = ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    completed.filter { !it.isAcknowledged }.forEach { acknowledge(it) }
                    if (completed.isNotEmpty()) {
                        // Yeni paket hemen yansısın (Play'den kesin liste birazdan queryOwned ile gelir).
                        ownedPurchases = mergeAfterPurchase(ownedPurchases, completed.map { it.toOwned() }, replaced)
                        _owned.value = ownedStateFrom(ownedPurchases)
                    }
                    when {
                        completed.isNotEmpty() -> {
                            onProChanged?.invoke(true)
                            callback?.invoke(PurchaseOutcome.Success)
                            // Yeni paket ve (ömür boyuna geçildiyse) hâlâ yenilenen abonelik Play'den yeniden okunur.
                            queryOwned()
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
    data class OwnedPurchase(
        val products: List<String>,
        val completed: Boolean,
        val autoRenewing: Boolean = false,
        val token: String = ""
    )

    private fun Purchase.toOwned() =
        OwnedPurchase(products, purchaseState == Purchase.PurchaseState.PURCHASED, isAutoRenewing, purchaseToken)

    /** Sahip olunan PRO paketi; birden fazlaysa en kapsamlısı (ömür boyu > yıllık > aylık). */
    enum class ProPlan { NONE, MONTHLY, YEARLY, LIFETIME }

    /** Paket ve hâlâ yenilenen abonelikler (ömür boyu alındıysa bunların iptal edilmesi gerekir). */
    data class OwnedState(val plan: ProPlan = ProPlan.NONE, val renewingSubscriptions: List<String> = emptyList())

    fun ownedStateFrom(purchases: List<OwnedPurchase>): OwnedState {
        val done = purchases.filter { it.completed }
        val products = done.flatMap { it.products }.toSet()
        val plan = when {
            PRODUCT_LIFETIME in products -> ProPlan.LIFETIME
            PRODUCT_YEARLY in products -> ProPlan.YEARLY
            PRODUCT_MONTHLY in products -> ProPlan.MONTHLY
            else -> ProPlan.NONE
        }
        val renewing = done.filter { it.autoRenewing }.flatMap { it.products }.filter { it in SUBSCRIPTIONS }.distinct()
        return OwnedState(plan, renewing)
    }

    /** Bu paketten geçilebilecek daha kapsamlı paketler (düşürme sunulmaz: yıllıktan aylığa geçilmez). */
    fun upgradeTargets(plan: ProPlan): List<String> = when (plan) {
        ProPlan.NONE -> ALL_PRODUCTS
        ProPlan.MONTHLY -> listOf(PRODUCT_YEARLY, PRODUCT_LIFETIME)
        ProPlan.YEARLY -> listOf(PRODUCT_LIFETIME)
        ProPlan.LIFETIME -> emptyList()
    }

    /** Abonelik değişiminde yerine geçilecek mevcut abonelik: satın alma jetonu ve ürün kimliği. */
    data class SubscriptionReplacement(val oldToken: String, val oldProductId: String)

    /**
     * Başka bir aboneliğe geçilirken değiştirilecek mevcut abonelik. Ömür boyu tek seferlik bir üründür, aboneliğin
     * yerine geçemez: o zaman null (ayrı satın alım; abonelik kullanıcı tarafından iptal edilir).
     */
    fun subscriptionToReplace(targetProductId: String, purchases: List<OwnedPurchase>): SubscriptionReplacement? {
        if (targetProductId !in SUBSCRIPTIONS) return null
        val current = purchases.firstOrNull { p ->
            p.completed && p.token.isNotEmpty() && targetProductId !in p.products && p.products.any { it in SUBSCRIPTIONS }
        } ?: return null
        return SubscriptionReplacement(current.token, current.products.first { it in SUBSCRIPTIONS })
    }

    /**
     * Satın alma sonrası yerel liste: yeni satın alımlar eklenir. Abonelik değiştirildiyse ([replaced]: hedef ürün →
     * eski jeton) ve hedef ürün gerçekten alındıysa eski abonelik çıkarılır; Play'den kesin liste ayrıca okunur.
     */
    fun mergeAfterPurchase(current: List<OwnedPurchase>, fresh: List<OwnedPurchase>, replaced: Pair<String, String>?): List<OwnedPurchase> {
        val replacedToken = replaced?.takeIf { (target, _) -> fresh.any { target in it.products } }?.second
        return current.filter { old -> old.token != replacedToken && fresh.none { it.token == old.token } } + fresh
    }

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
