package com.example.util

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Kullanıcının değiştiremeyeceği saat (günlük ücretsiz izleme süresi için). Telefonun saati ileri/geri alınabilir;
 * bu yüzden saat bir kez internetten (HTTPS, Google ya da Cloudflare) alınır ve cihaz açıldığından beri geçen
 * süreyle (elapsedRealtime: kullanıcı değiştiremez) ilerletilir. Aynı açılış (boot) içinde uygulama yeniden
 * başlasa da kayıtlı çapa kullanılır. İnternet saati alınamazsa telefonun saati kullanılır.
 */
object TrustedClock {
    private const val PREFS = "trusted_clock"

    internal data class Anchor(val trustedMs: Long, val elapsedMs: Long, val bootCount: Int)

    @Volatile private var anchor: Anchor? = null
    private val mutex = Mutex()

    // Testlerde değiştirilebilir.
    internal var elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }
    internal var wallClock: () -> Long = { System.currentTimeMillis() }
    internal var networkTime: suspend () -> Long? = { fetchNetworkTime() }

    /** Güvenilir şimdiki zaman (ms); internet saati henüz alınmadıysa telefonun saati. */
    fun now(context: Context? = null): Long {
        val a = validAnchor(context) ?: return wallClock()
        return a.trustedMs + (elapsedRealtime() - a.elapsedMs)
    }

    /** İnternet saati bu açılışta alındı mı? */
    fun isSynced(context: Context? = null): Boolean = validAnchor(context) != null

    /** İnternet saati yoksa alır (en fazla [timeoutMs] bekler). Başarılıysa true. */
    suspend fun ensureSynced(context: Context, timeoutMs: Long = 3_000): Boolean {
        if (isSynced(context)) return true
        return withTimeoutOrNull(timeoutMs) {
            mutex.withLock {
                if (isSynced(context)) return@withLock true
                val elapsedBefore = elapsedRealtime()
                val net = runCatching { networkTime() }.getOrNull() ?: return@withLock false
                // İstek süresinin yarısı kadar ileri: saatin yaklaşık olarak alındığı an.
                val elapsedAfter = elapsedRealtime()
                val a = Anchor(net, (elapsedBefore + elapsedAfter) / 2, bootCount(context))
                anchor = a
                save(context, a)
                true
            }
        } ?: false
    }

    /** Testler için: uygulamanın yeniden başlaması (bellekteki saat silinir, kayıtlı olan kalır). */
    internal fun forgetInMemoryForTest() {
        anchor = null
    }

    /** Testler için: kayıtlı saati unutur. */
    internal fun resetForTest(context: Context? = null) {
        anchor = null
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit { clear() }
        elapsedRealtime = { SystemClock.elapsedRealtime() }
        wallClock = { System.currentTimeMillis() }
        networkTime = { fetchNetworkTime() }
    }

    private fun validAnchor(context: Context?): Anchor? {
        anchor?.let { a -> if (elapsedRealtime() >= a.elapsedMs) return a }
        // Uygulama yeniden başladı: aynı açılışta kaydedilen saat kullanılabilir.
        if (context == null) return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("trusted")) return null
        val saved = Anchor(p.getLong("trusted", 0L), p.getLong("elapsed", 0L), p.getInt("boot", -1))
        val boot = bootCount(context)
        if (saved.bootCount < 0 || saved.bootCount != boot || elapsedRealtime() < saved.elapsedMs) return null
        anchor = saved
        return saved
    }

    private fun save(context: Context, a: Anchor) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong("trusted", a.trustedMs).putLong("elapsed", a.elapsedMs).putInt("boot", a.bootCount)
            }
        }
    }

    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /** HTTPS sunucularının saati: önce Google (Date başlığı), olmazsa Cloudflare (ts=). */
    private suspend fun fetchNetworkTime(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("https://www.google.com/generate_204").head().build()).execute().use { r ->
                r.headers.getDate("Date")?.time
            }
        }.getOrNull() ?: runCatching {
            client.newCall(Request.Builder().url("https://www.cloudflare.com/cdn-cgi/trace").get().build()).execute().use { r ->
                r.body?.string()?.lineSequence()?.firstOrNull { it.startsWith("ts=") }
                    ?.removePrefix("ts=")?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            }
        }.getOrNull()
    }
}
