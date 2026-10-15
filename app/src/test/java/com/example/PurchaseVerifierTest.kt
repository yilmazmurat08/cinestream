package com.example

import android.util.Base64
import com.android.billingclient.api.Purchase
import com.example.util.PurchaseVerifier
import com.example.util.SubscriptionManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * Satın alma imzası: yalnızca Google'ın (lisans anahtarına karşılık gelen özel anahtarla) imzaladığı satın alımlar
 * PRO açar. Sahte satın alma araçlarının uydurduğu ya da değiştirdiği satın alımlar reddedilir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PurchaseVerifierTest {
    private val google: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val attacker: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val licenseKeyBase64 = Base64.encodeToString(google.public.encoded, Base64.NO_WRAP)

    private val json = """{"orderId":"GPA.1234","packageName":"com.cinestream.iptv","productId":"cinestream_lifetime",""" +
        """"purchaseTime":1760000000000,"purchaseState":0,"purchaseToken":"tok","acknowledged":true}"""

    private fun sign(data: String, pair: KeyPair): String {
        val s = Signature.getInstance("SHA1withRSA")
        s.initSign(pair.private)
        s.update(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(s.sign(), Base64.NO_WRAP)
    }

    private val key get() = PurchaseVerifier.publicKeyOrNull(licenseKeyBase64)!!

    @Test
    fun genuinePurchase_isAccepted() {
        assertTrue(PurchaseVerifier.isSignedByGoogle(key, json, sign(json, google)))
        assertTrue(SubscriptionManager.isGenuinePurchase(key, Purchase(json, sign(json, google))))
    }

    @Test
    fun fakePurchase_signedByAnotherKey_isRejected() {
        assertFalse(SubscriptionManager.isGenuinePurchase(key, Purchase(json, sign(json, attacker))))
    }

    @Test
    fun alteredPurchase_isRejected() {
        val signature = sign(json, google)
        val altered = json.replace("cinestream_lifetime", "cinestream_yearly")
        assertFalse(PurchaseVerifier.isSignedByGoogle(key, altered, signature))
    }

    @Test
    fun missingOrGarbageSignature_isRejected() {
        assertFalse(SubscriptionManager.isGenuinePurchase(key, Purchase(json, "")))
        assertFalse(SubscriptionManager.isGenuinePurchase(key, Purchase(json, "bm90LWEtc2lnbmF0dXJl")))
    }

    @Test
    fun keyParsing_toleratesSpaces_andRejectsGarbage() {
        assertNotNull(PurchaseVerifier.publicKeyOrNull(licenseKeyBase64.chunked(64).joinToString("\n")))
        assertNull(PurchaseVerifier.publicKeyOrNull(""))
        assertNull(PurchaseVerifier.publicKeyOrNull("bozuk"))
    }

    @Test
    fun withoutAKey_testBuildsAcceptPurchases() {
        // Test sürümlerinde anahtar verilmez: imza denetimi yapılmaz (mağaza paketi anahtarsız üretilemez).
        assertTrue(SubscriptionManager.isGenuinePurchase(null, Purchase(json, "")))
    }
}
