package com.example.util

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Google Play satın alımının gerçekten Google tarafından imzalandığını denetler (Play Console → Gelir elde etme
 * ayarları → Lisanslama'daki herkese açık anahtarla, SHA1withRSA). Sahte satın alma araçlarının uydurduğu
 * satın alımlar bu imzayı taşımaz; PRO açmaz.
 */
object PurchaseVerifier {

    /** Base64 anahtarı açar; boşsa ya da bozuksa null (o zaman doğrulama yapılmaz, kimse PRO'suz kalmaz). */
    fun publicKeyOrNull(base64Key: String?): PublicKey? {
        val clean = base64Key?.trim()?.replace("\\s".toRegex(), "").orEmpty()
        if (clean.isEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        }.getOrNull()
    }

    /** Satın alma bilgisi ([signedData] = Purchase.originalJson) bu imzayla Google'dan mı geliyor? */
    fun isSignedByGoogle(key: PublicKey, signedData: String, signature: String): Boolean {
        if (signedData.isEmpty() || signature.isEmpty()) return false
        return runCatching {
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(key)
            sig.update(signedData.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(signature, Base64.DEFAULT))
        }.getOrDefault(false)
    }
}
