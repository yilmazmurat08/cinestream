package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object ExoPlayerConfigurator {

    /**
     * Creates an OkHttpClient that bypasses SSL certificate errors (TrustAllCerts)
     * and handles redirects and custom timeouts for high-reliability IPTV streams.
     */
    private fun createUnsafeOkHttpClient(): OkHttpClient {
        return try {
            com.example.data.api.NetworkModule.provideUnsafeOkHttpClient().newBuilder()
                .addInterceptor { chain ->
                    var request = chain.request()
                    val requestBuilder = request.newBuilder()
                    if (request.header("User-Agent").isNullOrEmpty()) {
                        requestBuilder.header("User-Agent", "VLC/3.0.18 (Linux; Android 11)")
                    }
                    if (request.header("Accept").isNullOrEmpty()) {
                        requestBuilder.header("Accept", "*/*")
                    }
                    request = requestBuilder.build()

                    var response = chain.proceed(request)
                    var followCount = 0
                    while ((response.isRedirect || response.code in 301..308) && followCount < 5) {
                        followCount++
                        val location = response.header("Location") ?: break
                        response.close()
                        val newUrl = response.request.url.resolve(location) ?: break
                        val newRequest = request.newBuilder()
                            .url(newUrl)
                            .build()
                        response = chain.proceed(newRequest)
                    }
                    response
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                    if (original.header("User-Agent").isNullOrEmpty()) {
                        requestBuilder.header("User-Agent", "IPTVSmartersPlayer")
                    }
                    chain.proceed(requestBuilder.build())
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * Checks if the device is currently connected to cellular (mobile) network.
     */
    fun isConnectedToCellular(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    /**
     * Creates and returns an attribution context for API 30+ to satisfy AppOps auditing.
     */
    fun getAttributionContext(context: Context): Context {
        return context
    }

    /**
     * Builds and configures an optimized ExoPlayer instance based on the user's settings.
     * Includes decoder fallback, anti-stutter back-buffer retention, and cross-protocol http source.
     *
     * @param context Application/Activity context
     * @param hardwareAcceleration If false, only software decoders are used.
     * @param bufferSize "Düşük" (Low), "Normal", "Yüksek" (High) values.
     * @param reduceCellularQuality If true, limits resolution to 480p on Cellular network.
     */
    fun buildConfiguredPlayer(
        context: Context,
        hardwareAcceleration: Boolean,
        bufferSize: String,
        reduceCellularQuality: Boolean
    ): ExoPlayer {
        val wrappedContext = getAttributionContext(context.applicationContext)

        // 1. Configure Renderers (Hardware Acceleration & MediaCodec Fallback for missing codecs)
        val renderersFactory = DefaultRenderersFactory(wrappedContext).apply {
            setEnableDecoderFallback(true) // Crucial for missing HW decoders or desync
            setAllowedVideoJoiningTimeMs(5000L)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            
            if (!hardwareAcceleration) {
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val defaultDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    if (mimeType.startsWith("audio/")) {
                        defaultDecoders
                    } else {
                        val swDecoders = defaultDecoders.filter { decoder ->
                            val name = decoder.name.lowercase()
                            name.contains("google") || 
                            name.contains("sw") || 
                            name.contains("software") || 
                            !decoder.hardwareAccelerated
                        }
                        if (swDecoders.isNotEmpty()) swDecoders else defaultDecoders
                    }
                }
            } else {
                setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            }
        }

        // 2. Configure Load Control (Buffer Sizes & Anti-Stutter Back-Buffer)
        val (minBuffer, maxBuffer, bufferForPlayback, bufferForPlaybackAfterRebuffer) = when (bufferSize) {
            "Düşük" -> {
                Quadruple(10_000, 30_000, 1_500, 3_000)
            }
            "Yüksek" -> {
                Quadruple(30_000, 90_000, 4_000, 8_000)
            }
            else -> { // "Normal"
                Quadruple(15_000, 50_000, 2_500, 5_000)
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                bufferForPlayback,
                bufferForPlaybackAfterRebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setBackBuffer(30_000, true) // Retain 30s back buffer to prevent re-buffering micro-stutters
            .build()

        // 3. Unsafe SSL OkHttp Data Source (Handles self-signed certificates, redirects, custom VLC headers & 30s timeouts)
        val okHttpClient = createUnsafeOkHttpClient()
        val defaultRequestProperties = mapOf(
            "Accept" to "*/*",
            "Accept-Encoding" to "identity"
        )

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("VLC/3.0.18 (Linux; Android 11)")
            .setDefaultRequestProperties(defaultRequestProperties)

        val dataSourceFactory = DefaultDataSource.Factory(wrappedContext, httpDataSourceFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )
            .setTsExtractorTimestampSearchBytes(1500 * 188)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // 4. Configure Track Selector (Quality & Adaptive Smoothness)
        val trackSelector = DefaultTrackSelector(wrappedContext)
        val trackParams = trackSelector.buildUponParameters()
            .setForceLowestBitrate(false)
            .setAllowVideoNonSeamlessAdaptiveness(true)
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setAllowAudioMixedMimeTypeAdaptiveness(true)

        if (reduceCellularQuality && isConnectedToCellular(wrappedContext)) {
            trackParams
                .setMaxVideoSize(854, 480)
                .setMaxVideoBitrate(1_200_000)
        } else {
            trackParams.clearVideoSizeConstraints()
        }
        trackSelector.setParameters(trackParams)

        // 5. Configure modern Audio Attributes for movie/media playback with automatic audio focus
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // 6. Build custom ExoPlayer instance
        return ExoPlayer.Builder(wrappedContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /**
     * Sanitizes raw stream URLs by percent-encoding spaces, special characters, and pipe symbols.
     */
    fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.replace("\uFEFF", "").trim().replace("\r", "").replace("\n", "")
        if (trimmed.isEmpty()) return ""
        return trimmed
            .replace(" ", "%20")
            .replace("|", "%7C")
            .replace("{", "%7B")
            .replace("}", "%7D")
            .replace("^", "%5E")
            .replace("`", "%60")
            .replace("<", "%3C")
            .replace(">", "%3E")
            .replace("\\", "%5C")
    }

    /**
     * Constructs a MediaItem with the optimal MimeType based on the stream URL parameters and extensions.
     */
    fun buildMediaItemForUrl(rawUrl: String): MediaItem {
        val sanitized = sanitizeUrl(rawUrl)
        if (sanitized.isEmpty()) return MediaItem.fromUri(android.net.Uri.EMPTY)
        val mediaUri = android.net.Uri.parse(sanitized)
        val urlLower = sanitized.lowercase()

        return when {
            urlLower.endsWith(".m3u8") || urlLower.contains(".m3u8?") || urlLower.contains("output=hls") || urlLower.contains("output=m3u8") -> {
                MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            }
            urlLower.endsWith(".ts") || urlLower.contains(".ts?") || urlLower.contains("output=ts") || urlLower.contains("/live/") -> {
                MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMimeType(MimeTypes.VIDEO_MP2T)
                    .build()
            }
            urlLower.endsWith(".mp4") || urlLower.contains(".mp4?") || urlLower.contains("/movie/") || urlLower.contains("/vod/") -> {
                MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMimeType(MimeTypes.VIDEO_MP4)
                    .build()
            }
            urlLower.endsWith(".mkv") || urlLower.contains(".mkv?") || urlLower.contains("/series/") -> {
                MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMimeType(MimeTypes.VIDEO_MATROSKA)
                    .build()
            }
            else -> {
                MediaItem.fromUri(mediaUri)
            }
        }
    }
}

/**
 * Simple container helper for buffer options mapping.
 */
data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
