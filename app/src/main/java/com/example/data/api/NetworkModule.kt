package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * NetworkModule centralizes network dependencies:
 * - Default OkHttpClient utilizes system default trust store for secure API calls (TMDB / Gemini)
 * - LoggingInterceptor is enabled ONLY in Debug builds
 * - provideUnsafeOkHttpClient() is provided strictly for user-provided IPTV stream / M3U links
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

    val unsafeIptvOkHttpClient: OkHttpClient by lazy {
        provideUnsafeOkHttpClient()
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
     * Secure OkHttpClient using system trust store. Used for official API calls (TMDB, Gemini, RevenueCat).
     */
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "CineStreamIPTV/1.0 (Android)")
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
     * Shared Unsafe OkHttpClient for IPTV stream / M3U playlist fetching where server SSL certificates
     * might be self-signed, expired, or custom.
     * NEVER use this for TMDB or Gemini API calls!
     */
    fun provideUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier(HostnameVerifier { _, _ -> true })
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
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder().build()
        }
    }

    /**
     * Bypasses SSL certificate verification for standard java.net.HttpURLConnection / HttpsURLConnection
     * used exclusively for user-provided IPTV stream links or M3U playlist downloads.
     * NEVER use this for TMDB or Gemini API calls!
     */
    fun configureUnsafeSslForConnection(connection: java.net.URLConnection) {
        if (connection is javax.net.ssl.HttpsURLConnection) {
            try {
                val unsafeClient = unsafeIptvOkHttpClient
                connection.sslSocketFactory = unsafeClient.sslSocketFactory
                connection.hostnameVerifier = unsafeClient.hostnameVerifier
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply unsafe SSL configuration to connection", e)
            }
        }
    }
}
