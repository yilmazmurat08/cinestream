package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB kimlik doğrulamasının TEK kaynağı.
 *
 * Sorun: Secrets'taki TMDB_API_KEY değerine anahtarın kendisi yerine TMDB'nin "anahtar iste"
 * sayfasının linki (https://www.themoviedb.org/settings/api/request) girilmişti. SettingsRepository
 * DataStore değerini doğruluyordu ama BuildConfig'e düşerken doğrulamıyordu; link her TMDB
 * isteğine api_key olarak gidiyor, hepsi HTTP 401 alıyordu. Oyuncu fotoğrafları, filmografi,
 * özetler... TMDB'ye dayanan her şey bu yüzden boştu.
 *
 * Çözüm (profesyonel istemcilerin yaptığı gibi, tek noktada):
 *  - Yalnızca gerçek formattaki değerler kabul edilir: v3 API anahtarı (32 hex karakter) ya da
 *    "API Okuma Erişim Jetonu" (eyJ… ile başlayan JWT). Link, "placeholder", boş değer elenir.
 *  - Jeton, TMDB'nin önerdiği şekilde Authorization: Bearer başlığıyla; v3 anahtarı api_key ile gider.
 *  - 401 alan anahtar bu oturum için "reddedildi" sayılır ve istek sıradaki anahtarla bir kez tekrarlanır.
 *  - Aday sırası: ayarlardan gelen → uygulamaya gömülü okuma jetonu → gömülü v3 anahtarı → BuildConfig.
 *    Gömülü jeton ve anahtar aynı TMDB hesabına ait (jetonun "aud" alanı = v3 anahtarı), yetkisi sadece
 *    okuma (api_read). Böylece uygulama Secrets'taki değerden bağımsız çalışır.
 */
object TmdbAuth {
    private const val TAG = "TmdbAuth"
    const val TMDB_HOST = "api.themoviedb.org"

    private val V3_KEY = Regex("^[a-fA-F0-9]{32}$")

    // TMDB "API Okuma Erişim Jetonu", TMDBRepository'deki gömülü anahtarla aynı XOR + Base64 yöntemiyle saklanır.
    private const val TOKEN_MASK = "cinestream_tmdb_2026"
    private const val EMBEDDED_TOKEN_ENCODED =
        "BhAkDREzEQwuBBU9OB4rbnxZeA9NDBcvGxAlNAgiNj1fKiYGB35YZ1AwFAIKOzYjCyMIJV8+CB5FfWZzUCY5PEItGDQWIzU9XT4hFkF5XwMKMwcsRTkmBlUgNRcXKiY0B35xAhomOjAAPRwrUDQ2PVstCAVafnZ/USQqDgo6JShSIhscBylQFVh9AGdXMwQsCi4xLBIkMToHBlEdXlNLf1U+Fy8bFzUJBw4yIgU+IRVWfHF8UTM2LwkVJVwUJDUbFQIzcUABYXcnUTg1RBAkIiIsKTc9SRU7cVdnXxMjJicbQTgHCh0qHyw+Jg1galU="

    private fun embeddedReadToken(): String = try {
        val decoded = android.util.Base64.decode(EMBEDDED_TOKEN_ENCODED, android.util.Base64.DEFAULT)
        val mask = TOKEN_MASK.toByteArray(Charsets.UTF_8)
        val bytes = ByteArray(decoded.size) { i ->
            (decoded[i].toInt() xor mask[i % mask.size].toInt()).toByte()
        }
        String(bytes, Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }

    private val rejectedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Son 401'in zamanı (ms). 0 ise hiç 401 alınmadı. Arayüz mesajı ve tanılama için. */
    @Volatile
    var lastUnauthorizedAt: Long = 0L
        private set

    fun isV3Key(value: String?): Boolean = value != null && V3_KEY.matches(value.trim())

    fun isReadAccessToken(value: String?): Boolean {
        val v = value?.trim() ?: return false
        return v.length > 100 && v.startsWith("eyJ") && v.count { it == '.' } == 2
    }

    fun isUsable(value: String?): Boolean = isV3Key(value) || isReadAccessToken(value)

    private fun candidates(preferred: String?): List<String> {
        val embedded = try {
            com.example.data.repository.TMDBRepository.resolveEmbeddedTmdbKey()
        } catch (e: Exception) {
            ""
        }
        val fromBuildConfig = try {
            BuildConfig.TMDB_API_KEY
        } catch (e: Exception) {
            ""
        }
        return listOf(preferred, embeddedReadToken(), embedded, fromBuildConfig)
            .mapNotNull { it?.trim() }
            .filter { isUsable(it) }
            .distinct()
    }

    /**
     * Kullanılabilir ve bu oturumda 401 almamış ilk değer. Hepsi reddedildiyse yine ilk geçerli
     * değer döner (anahtar sonradan düzelmiş olabilir). Hiç geçerli değer yoksa "" döner.
     */
    fun resolve(preferred: String? = null): String {
        val list = candidates(preferred)
        return list.firstOrNull { it !in rejectedKeys } ?: list.firstOrNull().orEmpty()
    }

    internal fun markRejected(key: String) {
        if (key.isNotEmpty()) rejectedKeys.add(key)
        lastUnauthorizedAt = System.currentTimeMillis()
    }

    internal fun isRejected(key: String): Boolean = key in rejectedKeys

    /** Tanılama dosyalarına anahtarın kendisini yazmamak için maskeler. */
    fun describe(value: String?): String {
        val v = value?.trim().orEmpty()
        return when {
            v.isEmpty() -> "(boş)"
            isV3Key(v) -> "v3 anahtarı (${v.take(4)}…${v.takeLast(4)})"
            isReadAccessToken(v) -> "okuma jetonu (eyJ…${v.takeLast(4)})"
            v.startsWith("http", ignoreCase = true) -> "GEÇERSİZ: anahtar değil, bir link girilmiş"
            else -> "GEÇERSİZ biçim (${v.length} karakter)"
        }
    }

    internal fun authorize(request: Request, key: String): Request {
        val url = request.url.newBuilder().removeAllQueryParameters("api_key")
        val builder = request.newBuilder()
        when {
            isReadAccessToken(key) -> builder.header("Authorization", "Bearer $key")
            key.isNotEmpty() -> {
                url.addQueryParameter("api_key", key)
                builder.removeHeader("Authorization")
            }
        }
        return builder.url(url.build()).build()
    }

    internal fun logWarning(message: String) {
        Log.w(TAG, message)
    }
}

/**
 * Sadece api.themoviedb.org isteklerine dokunur. Uygulamadaki bütün TMDB çağrıları
 * (MetadataEnricher, TMDBRepository, TmdbService) NetworkModule.okHttpClient üzerinden geçtiği
 * için tek tek çağrı yerlerini düzeltmeye gerek kalmaz.
 */
class TmdbAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != TmdbAuth.TMDB_HOST) return chain.proceed(original)

        val given = original.url.queryParameter("api_key")?.trim()
        val firstKey = if (given != null && TmdbAuth.isUsable(given) && !TmdbAuth.isRejected(given)) {
            given
        } else {
            TmdbAuth.resolve()
        }

        val response = chain.proceed(TmdbAuth.authorize(original, firstKey))
        if (response.code != 401) return response

        TmdbAuth.markRejected(firstKey)
        val nextKey = TmdbAuth.resolve()
        if (nextKey.isEmpty() || nextKey == firstKey) {
            TmdbAuth.logWarning("TMDB 401: denenecek başka geçerli anahtar yok (${TmdbAuth.describe(firstKey)})")
            return response
        }
        response.close()
        TmdbAuth.logWarning("TMDB 401: ${TmdbAuth.describe(firstKey)} reddedildi, ${TmdbAuth.describe(nextKey)} ile tekrar deneniyor")
        return chain.proceed(TmdbAuth.authorize(original, nextKey))
    }
}
