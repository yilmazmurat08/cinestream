package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import com.example.data.repository.WatchUsage
import com.example.ui.components.PaywallContent
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ProOffer
import com.example.util.SubscriptionManager
import com.example.util.SubscriptionManager.OwnedPurchase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CineStream PRO (Google Play Faturalandırma):
 * - PRO yalnızca tamamlanmış bir CineStream satın alımıyla açılır (bekleyen / başka ürün PRO açmaz).
 * - Paywall fiyatları Google Play'den gelen metinle gösterir; fiyat yoksa satın alma düğmesi kapalıdır.
 * - Ücretsiz izleme süresi günlüktür (yeni günde 0'dan başlar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class PaywallTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val offers = mapOf(
        SubscriptionManager.PRODUCT_MONTHLY to ProOffer(SubscriptionManager.PRODUCT_MONTHLY, "₺80,00", 80_000_000, true),
        SubscriptionManager.PRODUCT_YEARLY to ProOffer(SubscriptionManager.PRODUCT_YEARLY, "₺800,00", 800_000_000, true),
        SubscriptionManager.PRODUCT_LIFETIME to ProOffer(SubscriptionManager.PRODUCT_LIFETIME, "₺1.500,00", 1_500_000_000, false)
    )

    @Test
    fun proOnlyFromCompletedCineStreamPurchase() {
        assertFalse(SubscriptionManager.isProFromPurchases(emptyList()))
        assertFalse(SubscriptionManager.isProFromPurchases(listOf(OwnedPurchase(listOf(SubscriptionManager.PRODUCT_YEARLY), completed = false))))
        assertFalse(SubscriptionManager.isProFromPurchases(listOf(OwnedPurchase(listOf("baska_urun"), completed = true))))
        assertTrue(SubscriptionManager.isProFromPurchases(listOf(OwnedPurchase(listOf(SubscriptionManager.PRODUCT_MONTHLY), completed = true))))
        assertTrue(SubscriptionManager.isProFromPurchases(listOf(OwnedPurchase(listOf(SubscriptionManager.PRODUCT_LIFETIME), completed = true))))
    }

    @Test
    fun yearlySavings_fromPlayPrices() {
        assertEquals(17, SubscriptionManager.yearlySavingsPercent(80_000_000, 800_000_000))
        assertNull(SubscriptionManager.yearlySavingsPercent(0, 800_000_000))
        assertNull(SubscriptionManager.yearlySavingsPercent(80_000_000, 1_000_000_000))
    }

    @Test
    fun paywall_showsPlayPrices_selectsPlan_andBuysSelectedProduct() {
        var bought: String? = null
        compose.setContent {
            MyApplicationTheme {
                PaywallContent(
                    reasonMessage = null, isPro = false, offers = offers, busy = false,
                    onDismiss = {}, onBuy = { bought = it }, onRestore = {}, onManage = {}, onOpenLegal = {}
                )
            }
        }
        compose.onNodeWithTag("package_price_${SubscriptionManager.PRODUCT_YEARLY}", useUnmergedTree = true).assertTextContains("₺800,00")
        compose.onNodeWithTag("package_price_${SubscriptionManager.PRODUCT_LIFETIME}", useUnmergedTree = true).assertTextContains("₺1.500,00")
        compose.onNode(hasText("%17 tasarruf"), useUnmergedTree = true).assertExists()
        // Varsayılan: yıllık
        compose.onNodeWithTag("paywall_purchase_button").performScrollTo().assertTextContains("₺800,00", substring = true)

        compose.onNodeWithTag("package_card_${SubscriptionManager.PRODUCT_MONTHLY}").performScrollTo().performClick()
        compose.onNodeWithTag("paywall_purchase_button").performScrollTo().assertTextContains("₺80,00", substring = true).performClick()
        assertEquals(SubscriptionManager.PRODUCT_MONTHLY, bought)

        // Abonelik yenileme bilgisi görünür; gerçek olmayan vaatler yoktur.
        compose.onNodeWithTag("paywall_auto_renew").assertExists()
        compose.onNode(hasText("Reklamsız", substring = true)).assertDoesNotExist()
        compose.onNode(hasText("4K", substring = true)).assertDoesNotExist()
    }

    @Test
    fun paywall_withoutPlayPrices_disablesPurchase() {
        var bought: String? = null
        compose.setContent {
            MyApplicationTheme {
                PaywallContent(
                    reasonMessage = null, isPro = false, offers = emptyMap(), busy = false,
                    onDismiss = {}, onBuy = { bought = it }, onRestore = {}, onManage = {}, onOpenLegal = {}
                )
            }
        }
        compose.onNodeWithTag("paywall_prices_loading").assertExists()
        compose.onNodeWithTag("paywall_purchase_button").performScrollTo().assertIsNotEnabled()
        assertNull(bought)
    }

    @Test
    fun paywall_forProUser_showsManageInsteadOfPlans() {
        var managed = false
        compose.setContent {
            MyApplicationTheme {
                PaywallContent(
                    reasonMessage = null, isPro = true, offers = offers, busy = false,
                    onDismiss = {}, onBuy = {}, onRestore = {}, onManage = { managed = true }, onOpenLegal = {}
                )
            }
        }
        compose.onNodeWithTag("paywall_purchase_button").assertDoesNotExist()
        compose.onNodeWithTag("paywall_manage").assertIsEnabled().performClick()
        assertTrue(managed)
    }

    @Test
    fun freeWatchTime_isDaily() {
        val today = WatchUsage.today()
        assertEquals(0L, WatchUsage("2000-01-01", 5_000).secondsOn(today))
        assertEquals(1_200L, WatchUsage(today, 1_200).secondsOn(today))

        val repo = SettingsRepository(app)
        runBlocking {
            repo.addWatchSeconds(30)
            repo.addWatchSeconds(30)
            val usage = repo.watchUsageFlow.first()
            assertEquals(today, usage.day)
            assertTrue("bugünkü süre birikir: ${usage.seconds}", usage.secondsOn(today) >= 60)
        }
    }
}
