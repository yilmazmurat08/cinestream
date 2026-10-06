package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * NetworkModule centralizes network dependencies:
 * - Default OkHttpClient utilizes system default trust store for secure API calls (TMDB / Gemini)
 * - LoggingInterceptor is enabled ONLY in Debug builds
 * - provideIptvOkHttpClient() is used for user-provided IPTV stream / M3U / EPG links
 */
object NetworkModule {

    private const val TAG = "TMDB_HTTP"
    private const val BASE_URL = "https://api.themoviedb.org/3/"

    val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor { message ->
            // TmdbAuthInterceptor'dan sonra çalıştığı için istekte gerçek anahtar/jeton bulunur; maskele.
            Log.d(TAG, com.example.util.DiagnosticLog.redact(message))
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        provideOkHttpClient()
    }

    val iptvOkHttpClient: OkHttpClient by lazy {
        provideIptvOkHttpClient()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val tmdbApiService: MetadataEnricher.TMDBApiService by lazy {
        retrofit.create(MetadataEnricher.TMDBApiService::class.java)
    }

    val tmdbService: TmdbService by lazy {
        retrofit.create(TmdbService::class.java)
    }

    /**
     * Secure OkHttpClient using system trust store. Used for official API calls (TMDB, Gemini).
     */
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", com.example.util.AppUserAgent.app)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(TmdbAuthInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    /**
     * IPTV listeleri, EPG ve yayınlar için istemci. Sertifikalar Android'in standart yöntemiyle doğrulanır
     * (sistem + kullanıcının yüklediği sertifikalar, bkz. network_security_config.xml); Google Play'in
     * "güvensiz TrustManager / HostnameVerifier" kuralına uyar.
     *
     * Sertifikası bozuk (süresi dolmuş, kendinden imzalı, yanlış ada verilmiş) IPTV sunucuları için:
     * https isteği sertifika hatası verirse aynı adres bir kez http ile denenir. IPTV sunucularının çoğu
     * aynı içeriği http ile de sunar. NEVER use this for TMDB or Gemini API calls!
     */
    fun provideIptvOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = if (original.header("User-Agent") == null) {
                    original.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .addInterceptor(HttpsCertificateFallbackInterceptor())
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
}

/**
 * https isteği sertifika doğrulamasında başarısız olursa aynı isteği http ile bir kez tekrarlar.
 * Sadece kullanıcının girdiği IPTV adreslerinde kullanılır.
 */
class HttpsCertificateFallbackInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            if (!request.url.isHttps || !isCertificateError(e)) throw e
            val httpUrl = request.url.newBuilder().scheme("http").apply {
                // Varsayılan https portu (443) ise http'nin varsayılanına (80) geç; özel port korunur.
                if (request.url.port == 443) port(80)
            }.build()
            Log.w("IptvHttp", "Certificate error for ${request.url.host}, retrying over http")
            chain.proceed(request.newBuilder().url(httpUrl).build())
        }
    }

    companion object {
        fun isCertificateError(e: Throwable): Boolean {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is javax.net.ssl.SSLPeerUnverifiedException ||
                    cause is java.security.cert.CertificateException ||
                    cause is java.security.cert.CertPathValidatorException ||
                    (cause is javax.net.ssl.SSLHandshakeException && cause.message.orEmpty().contains("cert", ignoreCase = true))
                ) return true
                cause = cause.cause
            }
            return false
        }
    }
}
