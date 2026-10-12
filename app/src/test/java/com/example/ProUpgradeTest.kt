package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.components.PaywallContent
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ProOffer
import com.example.util.SubscriptionManager
import com.example.util.SubscriptionManager.OwnedPurchase
import com.example.util.SubscriptionManager.ProPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRO paket yükseltme: PRO olan kullanıcı daha kapsamlı bir pakete geçebilir.
 * - Aylık → yıllık: eski abonelik yenisiyle DEĞİŞTİRİLİR (iki abonelik birden ücretlenmez).
 * - Aylık/yıllık → ömür boyu: tek seferlik ayrı satın alım; ardından aboneliğin iptal edilmesi istenir.
 * - Ömür boyu: alınacak paket kalmaz; abonelik hâlâ yenileniyorsa iptal etmesi hatırlatılır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class ProUpgradeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val m = SubscriptionManager.PRODUCT_MONTHLY
    private val y = SubscriptionManager.PRODUCT_YEARLY
    private val l = SubscriptionManager.PRODUCT_LIFETIME

    private val offers = mapOf(
        m to ProOffer(m, "₺95,99", 95_990_000, true),
        y to ProOffer(y, "₺959,99", 959_990_000, true),
        l to ProOffer(l, "₺1.799,99", 1_799_990_000, false)
    )

    private fun owned(id: String, renewing: Boolean = id != l, token: String = "tok_$id") =
        OwnedPurchase(listOf(id), completed = true, autoRenewing = renewing, token = token)

    @Test
    fun ownedPlan_isTheMostCompleteFinishedPurchase() {
        assertEquals(ProPlan.NONE, SubscriptionManager.ownedStateFrom(emptyList()).plan)
        assertEquals(ProPlan.MONTHLY, SubscriptionManager.ownedStateFrom(listOf(owned(m))).plan)
        assertEquals(ProPlan.YEARLY, SubscriptionManager.ownedStateFrom(listOf(owned(m), owned(y))).plan)
        assertEquals(ProPlan.LIFETIME, SubscriptionManager.ownedStateFrom(listOf(owned(m), owned(l))).plan)
        // Onay bekleyen satın alım paket sayılmaz.
        assertEquals(ProPlan.NONE, SubscriptionManager.ownedStateFrom(listOf(OwnedPurchase(listOf(y), completed = false, autoRenewing = true, token = "t"))).plan)
        // Ömür boyu alındı ama aylık hâlâ yenileniyor: iptal edilmesi gereken abonelik.
        assertEquals(listOf(m), SubscriptionManager.ownedStateFrom(listOf(owned(m), owned(l))).renewingSubscriptions)
        assertEquals(emptyList<String>(), SubscriptionManager.ownedStateFrom(listOf(owned(m, renewing = false), owned(l))).renewingSubscriptions)
    }

    @Test
    fun upgradeTargets_perPlan() {
        assertEquals(listOf(y, l), SubscriptionManager.upgradeTargets(ProPlan.MONTHLY))
        assertEquals(listOf(l), SubscriptionManager.upgradeTargets(ProPlan.YEARLY))
        assertEquals(emptyList<String>(), SubscriptionManager.upgradeTargets(ProPlan.LIFETIME))
    }

    @Test
    fun switchingSubscription_replacesTheOldOne_lifetimeIsASeparatePurchase() {
        val monthly = listOf(owned(m, token = "monthly_token"))
        assertEquals(SubscriptionManager.SubscriptionReplacement("monthly_token", m), SubscriptionManager.subscriptionToReplace(y, monthly))
        assertNull(SubscriptionManager.subscriptionToReplace(l, monthly))
        assertNull(SubscriptionManager.subscriptionToReplace(y, emptyList()))
        assertNull(SubscriptionManager.subscriptionToReplace(y, listOf(owned(y))))
        // Onay bekleyen abonelik değiştirilmez.
        assertNull(SubscriptionManager.subscriptionToReplace(y, listOf(OwnedPurchase(listOf(m), completed = false, autoRenewing = true, token = "p"))))
    }

    @Test
    fun afterPurchase_replacedSubscriptionIsRemoved_lifetimeKeepsTheOpenSubscription() {
        val monthly = owned(m, token = "tok_m")
        // Aylık → yıllık: eski aylık listeden çıkar ("Aboneliği yönet" artık olmayan aylığı açmasın).
        val afterSwitch = SubscriptionManager.mergeAfterPurchase(listOf(monthly), listOf(owned(y, token = "tok_y")), y to "tok_m")
        assertEquals(listOf("tok_y"), afterSwitch.map { it.token })
        assertEquals(SubscriptionManager.OwnedState(ProPlan.YEARLY, listOf(y)), SubscriptionManager.ownedStateFrom(afterSwitch))
        // Aylık → ömür boyu: aylık hâlâ yenileniyor; iptal uyarısı için listede kalır.
        val afterLifetime = SubscriptionManager.mergeAfterPurchase(listOf(monthly), listOf(owned(l, token = "tok_l")), null)
        assertEquals(SubscriptionManager.OwnedState(ProPlan.LIFETIME, listOf(m)), SubscriptionManager.ownedStateFrom(afterLifetime))
        // Değişim tamamlanmadan başka bir satın alım gelirse eski abonelik silinmez.
        val unrelated = SubscriptionManager.mergeAfterPurchase(listOf(monthly), listOf(owned(l, token = "tok_l")), y to "tok_m")
        assertEquals(listOf("tok_m", "tok_l"), unrelated.map { it.token })
    }

    private fun show(plan: ProPlan, renewing: List<String>, onBuy: (String) -> Unit = {}, onCancel: (String) -> Unit = {}) {
        compose.setContent {
            MyApplicationTheme {
                PaywallContent(
                    reasonMessage = null, isPro = true, offers = offers, busy = false,
                    onDismiss = {}, onBuy = onBuy, onRestore = {}, onManage = {}, onOpenLegal = {},
                    ownedPlan = plan, renewingSubscriptions = renewing, onCancelSubscription = onCancel
                )
            }
        }
    }

    @Test
    fun monthlyUser_canUpgradeToYearlyOrLifetime() {
        var bought: String? = null
        show(ProPlan.MONTHLY, listOf(m), onBuy = { bought = it })
        compose.onNodeWithTag("paywall_current_plan").assertTextContains("Aylık", substring = true)
        compose.onNodeWithTag("package_card_$m").assertDoesNotExist()
        compose.onNodeWithTag("package_card_$y").assertExists()
        compose.onNodeWithTag("package_card_$l").assertExists()

        compose.onNodeWithTag("paywall_upgrade_button").performScrollTo().assertTextContains("₺959,99", substring = true).performClick()
        assertEquals(y, bought)

        compose.onNodeWithTag("package_card_$l").performScrollTo().performClick()
        // Ömür boyuna geçerken aboneliğin ayrıca iptal edilmesi gerektiği önceden söylenir.
        compose.onNodeWithTag("paywall_upgrade_note").performScrollTo().assertTextContains("aylık aboneliğin kendiliğinden kapanmaz", substring = true)
        compose.onNodeWithTag("paywall_upgrade_button").performScrollTo().assertTextContains("₺1.799,99", substring = true).performClick()
        assertEquals(l, bought)
        compose.onNodeWithTag("paywall_manage").performScrollTo()
    }

    @Test
    fun yearlyUser_canOnlyUpgradeToLifetime() {
        show(ProPlan.YEARLY, listOf(y))
        compose.onNodeWithTag("paywall_current_plan").assertTextContains("Yıllık", substring = true)
        compose.onNodeWithTag("package_card_$m").assertDoesNotExist()
        compose.onNodeWithTag("package_card_$y").assertDoesNotExist()
        compose.onNodeWithTag("package_card_$l").assertExists()
    }

    @Test
    fun lifetimeUser_withRenewingSubscription_isAskedToCancelIt() {
        var cancelled: String? = null
        show(ProPlan.LIFETIME, listOf(m), onCancel = { cancelled = it })
        compose.onNodeWithTag("paywall_upgrade_button").assertDoesNotExist()
        // İptal edilecek olan ömür boyu değil, eski aylık abonelik; metin bunu açıkça söyler.
        compose.onNodeWithTag("paywall_cancel_warning").assertTextContains("hiç yenilenmez", substring = true)
        compose.onNodeWithTag("paywall_cancel_warning").assertTextContains("eski aylık aboneliğin", substring = true)
        compose.onNodeWithTag("paywall_cancel_subscription").performScrollTo().assertTextContains("Aylık aboneliği iptal et").performClick()
        assertEquals(m, cancelled)
    }

    @Test
    fun lifetimeOnly_hasNothingToBuyOrCancel() {
        show(ProPlan.LIFETIME, emptyList())
        compose.onNodeWithTag("paywall_current_plan").assertTextContains("Ömür Boyu", substring = true)
        compose.onNodeWithTag("paywall_upgrade_button").assertDoesNotExist()
        compose.onNodeWithTag("paywall_cancel_subscription").assertDoesNotExist()
        compose.onNodeWithTag("paywall_manage").assertDoesNotExist()
        compose.onNodeWithTag("package_card_$l").assertDoesNotExist()
    }
}
