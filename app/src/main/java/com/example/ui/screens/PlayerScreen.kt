@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.example.ui.screens

import com.example.ui.theme.rememberAppAdaptiveLayout

import androidx.lifecycle.viewmodel.compose.viewModel

import android.app.Activity
import android.content.Context
import com.example.util.findActivity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.isActive
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.platform.testTag
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import android.util.TypedValue
import coil.compose.AsyncImage
import com.example.data.model.IPTVItem
import com.example.player.ExoPlayerConfigurator
import com.example.ui.IPTVViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlin.math.max
import kotlin.math.min

enum class AspectRatioMode(val labelRes: Int) {
    FIT(R.string.player_aspect_fit),
    FILL(R.string.player_aspect_fill),
    ZOOM(R.string.player_aspect_zoom),
    SIXTEEN_NINE(R.string.player_aspect_16_9)
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    item: IPTVItem,
    siblingItems: List<IPTVItem>,
    onBack: () -> Unit,
    onPlayItem: (IPTVItem) -> Unit,
    onProgressUpdate: (IPTVItem, progress: Long, total: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialProgressSeconds: Long = 0L,
    iptvViewModel: IPTVViewModel? = null
) {
    // Oynatıcı ExoPlayer'dır (önceki VLC yolu hiç açılmıyordu ve kaldırıldı).
    LegacyExoPlayerScreen(
        item = item,
        siblingItems = siblingItems,
        onBack = onBack,
        onPlayItem = onPlayItem,
        onProgressUpdate = onProgressUpdate,
        modifier = modifier,
        initialProgressSeconds = initialProgressSeconds,
        iptvViewModel = iptvViewModel
    )
}

@OptIn(UnstableApi::class)
@Composable
fun LegacyExoPlayerScreen(
    item: IPTVItem,
    siblingItems: List<IPTVItem>,
    onBack: () -> Unit,
    onPlayItem: (IPTVItem) -> Unit,
    onProgressUpdate: (IPTVItem, progress: Long, total: Long) -> Unit,
    modifier: Modifier = Modifier,
    initialProgressSeconds: Long = 0L,
    iptvViewModel: IPTVViewModel? = null
) {
    val context = LocalContext.current
    val layout = rememberAppAdaptiveLayout()
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember(context) { context.findActivity() }

    // Subtitle Customization
    val subtitleSize by (iptvViewModel?.subtitleSize?.collectAsState() ?: remember { mutableStateOf(16) })
    val subtitleColor by (iptvViewModel?.subtitleColor?.collectAsState() ?: remember { mutableStateOf("Beyaz") })

    // ExoPlayer creation & lifecycle management
    var fallbackCount by remember(item) { mutableStateOf(0) }
    val player = remember(item) {
        // Stop any background radio playback when starting a video
        try {
            com.example.player.RadioPlayerManager.stopRadio()
        } catch (e: Exception) {
            // Ignore
        }

        val hwAcc = iptvViewModel?.hardwareAcceleration?.value ?: true
        val bufSize = iptvViewModel?.bufferSize?.value ?: "Normal"
        val redCell = iptvViewModel?.reduceCellularQuality?.value ?: false

        ExoPlayerConfigurator.buildConfiguredPlayer(
            context = context,
            hardwareAcceleration = hwAcc,
            bufferSize = bufSize,
            reduceCellularQuality = redCell
        ).apply {
            val rawUrl = item.streamUrl.trim()
            val mediaItem = ExoPlayerConfigurator.buildMediaItemForUrl(rawUrl)
            setMediaItem(mediaItem)
            prepare()
            volume = 1f
            playWhenReady = true
        }
    }

    // Hardware levels
    var brightnessLevel by remember { mutableStateOf(0.5f) }
    var volumeLevel by remember { mutableStateOf(0.5f) }
    var showBrightnessHUD by remember { mutableStateOf(false) }
    var showVolumeHUD by remember { mutableStateOf(false) }

    // UI state controllers
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(100L) }

    // Aspect Ratio & Layout Resize
    var currentAspectRatioMode by remember { mutableStateOf(AspectRatioMode.FILL) }
    var showAspectRatioBadge by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    var isLandscapeMode by remember { mutableStateOf(true) }

    // Screen Lock
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockButtonBriefly by remember { mutableStateOf(false) }

    // Subtitles & Audio track selector
    var showTrackSelectorSheet by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }

    // Auto-hide Gesture HUD after 1.5 seconds
    LaunchedEffect(showBrightnessHUD) {
        if (showBrightnessHUD) {
            delay(1500)
            showBrightnessHUD = false
        }
    }

    LaunchedEffect(showVolumeHUD) {
        if (showVolumeHUD) {
            delay(1500)
            showVolumeHUD = false
        }
    }

    // Tray overlay
    var showTray by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Double tap feedback
    var showDoubleTapFeedback by remember { mutableStateOf<String?>(null) } // "geri" or "ileri"

    // Resume playback dialog state
    var showResumeDialog by remember { mutableStateOf(initialProgressSeconds > 0L) }
    var aiRecapText by remember { mutableStateOf<String?>(null) }
    var isAILoadingRecap by remember { mutableStateOf(false) }

    // PRO & Watch Limit
    val isProUser by (iptvViewModel?.isProUser?.collectAsState() ?: remember { mutableStateOf(false) })
    val totalWatchSeconds by (iptvViewModel?.totalWatchSeconds?.collectAsState() ?: remember { mutableStateOf(0L) })
    var hasRecordedSession by remember(item.id) { mutableStateOf(false) }

    // Check watch limit effect
    LaunchedEffect(totalWatchSeconds, isProUser) {
        if (!isProUser && totalWatchSeconds >= 3600L) {
            player.stop()
            iptvViewModel?.openPaywall("60 Dakikalık Ücretsiz İzleme Süreniz Doldu")
        }
    }

    // Sibling track navigation
    val currentIndex = remember(item, siblingItems) {
        siblingItems.indexOfFirst { it.id == item.id }
    }
    val nextItem = remember(currentIndex, siblingItems) {
        if (currentIndex != -1 && currentIndex < siblingItems.size - 1) {
            siblingItems[currentIndex + 1]
        } else {
            null
        }
    }

    // Oynatıcı dinleyicisi bir kez kurulur; kardeş liste sonradan (arka planda) dolsa bile güncel değeri görsün.
    val latestNextItem by rememberUpdatedState(nextItem)

    // Auto-hide controls timer Job
    var autoHideJob by remember { mutableStateOf<Job?>(null) }

    fun resetControlsTimer() {
        autoHideJob?.cancel()
        if (controlsVisible && !showTray && !isScreenLocked) {
            autoHideJob = scope.launch {
                delay(3000) // Hide controls after 3 seconds of inactivity
                controlsVisible = false
            }
        }
    }

    // Handle initial seek & release
    LaunchedEffect(player) {
        if (initialProgressSeconds > 0) {
            player.seekTo(initialProgressSeconds * 1000)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var releasedOnStop by remember(player) { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        com.example.player.PlaybackForegroundService.start(
            context,
            item.cleanedName.ifEmpty { item.name }
        )
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        try {
                            player.pause()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        try {
                            player.pause()
                            // Ekran kapandı / uygulama arka planda: kod çözücü ve tamponları serbest bırak,
                            // konum korunur. Geri dönüldüğünde (ON_START) yeniden hazırlanır.
                            player.stop()
                            releasedOnStop = true
                        } catch (e: Exception) {
                            // Ignore
                        }
                        com.example.player.PlaybackForegroundService.stop(context)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (releasedOnStop) {
                        releasedOnStop = false
                        try {
                            player.prepare()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        com.example.player.PlaybackForegroundService.start(
                            context,
                            item.cleanedName.ifEmpty { item.name }
                        )
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            autoHideJob?.cancel()
            autoHideJob = null
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!hasRecordedSession && currentPos >= 15) {
                hasRecordedSession = true
                iptvViewModel?.recordCompletedPlaybackSession()
            }
            try {
                player.stop()
                player.clearMediaItems()
                player.release()
            } catch (e: Exception) {
                // Ignore
            }
            com.example.player.PlaybackForegroundService.stop(context)
        }
    }

    // Keep screen awake during playback + durum çubuğunu gizle (immersive mod)
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Dynamic rotation handling based on user choice
    LaunchedEffect(isLandscapeMode) {
        activity?.requestedOrientation = if (isLandscapeMode) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Read hardware levels initially & start regular timeline updater
    LaunchedEffect(player) {
        // Read brightness
        val lp = activity?.window?.attributes
        brightnessLevel = lp?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f

        // Read volume
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val currVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        volumeLevel = if (maxVol > 0) currVol / maxVol else 0.5f

        // Poll position & tracks with batched DB persistence (30s interval or on pause/exit)
        var lastPersistedPos = 0L
        var pollCounter = 0
        var pendingWatchSeconds = 0L
        var lastWatchFlush = System.currentTimeMillis()

        try {
            while (isActive) {
                currentPos = player.currentPosition / 1000
                val dur = player.duration / 1000
                if (dur > 0) {
                    totalDuration = dur
                }
                isPlaying = player.isPlaying
                if (isPlaying && !isProUser) {
                    pendingWatchSeconds++
                }
                if (pendingWatchSeconds >= 30L || (pendingWatchSeconds > 0L && System.currentTimeMillis() - lastWatchFlush >= 30000L)) {
                    iptvViewModel?.addWatchSeconds(pendingWatchSeconds)
                    pendingWatchSeconds = 0L
                    lastWatchFlush = System.currentTimeMillis()
                }
                currentTracks = player.currentTracks

                // Save continue watching progress every 30 seconds or when seeking > 30s
                pollCounter++
                if (totalDuration > 0 && !showResumeDialog) {
                    val shouldSave = (pollCounter % 30 == 0) || (kotlin.math.abs(currentPos - lastPersistedPos) >= 30L)
                    if (shouldSave && currentPos > 0) {
                        lastPersistedPos = currentPos
                        onProgressUpdate(item, currentPos, totalDuration)
                    }
                }

                delay(1000L)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (pendingWatchSeconds > 0L) {
                iptvViewModel?.addWatchSeconds(pendingWatchSeconds)
                pendingWatchSeconds = 0L
            }
            throw ce
        } finally {
            if (pendingWatchSeconds > 0L) {
                iptvViewModel?.addWatchSeconds(pendingWatchSeconds)
            }
        }
    }

    // Yayın tüm denemelere rağmen açılamazsa hata penceresi gösterilir ("Tekrar dene" sayacı sıfırlar).
    var streamFailed by remember(player) { mutableStateOf(false) }
    var localRetryCount by remember(player) { mutableIntStateOf(0) }

    // Resim içinde resim: video oynarken ana ekran tuşuna basılınca küçük pencerede devam eder.
    val isInPip = com.example.player.rememberPictureInPicture(
        player = player,
        canEnter = isPlaying && !streamFailed,
        onDismissedInBackground = {
            // PiP penceresi kapatıldı: arka plana geçişteki gibi oynatmayı durdur (konum korunur).
            try {
                player.pause()
                player.stop()
                releasedOnStop = true
            } catch (e: Exception) {
                // Ignore
            }
            com.example.player.PlaybackForegroundService.stop(context)
        }
    )
    LaunchedEffect(isInPip) {
        if (isInPip) {
            controlsVisible = false
            showTray = false
            showTrackSelectorSheet = false
        }
    }

    // Track state listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                resetControlsTimer()
                if (!playing) {
                    if (totalDuration > 0 && currentPos > 0 && !showResumeDialog) {
                        onProgressUpdate(item, currentPos, totalDuration)
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = player.duration / 1000
                    if (dur > 0) {
                        totalDuration = dur
                    }
                    localRetryCount = 0 // Reset retry count when stream becomes READY
                } else if (state == Player.STATE_ENDED) {
                    if (!hasRecordedSession) {
                        hasRecordedSession = true
                        iptvViewModel?.recordCompletedPlaybackSession()
                    }
                    val next = latestNextItem
                    if (item.type == "SERIES" && next != null) {
                        onPlayItem(next)
                    }
                }
                resetControlsTimer()
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("PlayerScreen", "ExoPlayer playback error: ${error.message} (${error.errorCodeName})")
                val rawUrl = item.streamUrl.trim()
                val sanitizedUrl = ExoPlayerConfigurator.sanitizeUrl(rawUrl)
                val mediaUri = android.net.Uri.parse(sanitizedUrl)
                val errorMsg = error.message.orEmpty()
                val causeMsg = error.cause?.message.orEmpty()
                val isHttpError = errorMsg.contains("InvalidResponseCodeException", ignoreCase = true) ||
                                  causeMsg.contains("InvalidResponseCodeException", ignoreCase = true) ||
                                  errorMsg.contains("Response code:", ignoreCase = true) ||
                                  causeMsg.contains("Response code:", ignoreCase = true)
                val isExtM3uError = errorMsg.contains("EXTM3U", ignoreCase = true) || causeMsg.contains("EXTM3U", ignoreCase = true)
                val isUnrecognizedFormat = errorMsg.contains("UnrecognizedInputFormatException", ignoreCase = true) || causeMsg.contains("UnrecognizedInputFormatException", ignoreCase = true)

                val maxRetries = if (isHttpError) 2 else 4
                if (localRetryCount >= maxRetries) {
                    // Önceden burada ilgisiz bir test videosu (okyanus / test yayını) açılıyordu.
                    // Artık oynatma durdurulur ve kullanıcıya anlaşılır bir hata penceresi gösterilir.
                    android.util.Log.e("PlayerScreen", "Max retries ($maxRetries) reached for ${com.example.util.DiagnosticLog.redact(rawUrl)}")
                    try {
                        player.stop()
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Error stopping failed stream", e)
                    }
                    streamFailed = true
                    return
                }

                localRetryCount++
                val previousMime = player.currentMediaItem?.localConfiguration?.mimeType
                val urlLower = sanitizedUrl.lowercase()
                val isVodUrl = urlLower.contains("/movie/") || urlLower.contains("/series/") || item.type == "MOVIE" || item.type == "SERIES"

                val retryItem = when {
                    isHttpError && localRetryCount == 1 -> {
                        // Try switching between .ts and .m3u8 for Xtream streams on HTTP error
                        if (urlLower.endsWith(".ts")) {
                            val altUrl = sanitizedUrl.substringBeforeLast(".ts") + ".m3u8"
                            MediaItem.Builder()
                                .setUri(android.net.Uri.parse(altUrl))
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                        } else if (urlLower.endsWith(".m3u8")) {
                            val altUrl = sanitizedUrl.substringBeforeLast(".m3u8") + ".ts"
                            MediaItem.Builder()
                                .setUri(android.net.Uri.parse(altUrl))
                                .setMimeType(MimeTypes.VIDEO_MP2T)
                                .build()
                        } else {
                            MediaItem.fromUri(mediaUri)
                        }
                    }
                    isExtM3uError -> {
                        // HLS parser failed because raw video/ts bytes were returned -> fallback to TS extractor
                        MediaItem.Builder()
                            .setUri(mediaUri)
                            .setMimeType(MimeTypes.VIDEO_MP2T)
                            .build()
                    }
                    isUnrecognizedFormat && previousMime == MimeTypes.VIDEO_MP2T -> {
                        // TS extractor failed -> try HLS
                        MediaItem.Builder()
                            .setUri(mediaUri)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                    }
                    localRetryCount == 1 -> {
                        if (previousMime == MimeTypes.APPLICATION_M3U8) {
                            MediaItem.Builder()
                                .setUri(mediaUri)
                                .setMimeType(MimeTypes.VIDEO_MP2T)
                                .build()
                        } else if (previousMime == MimeTypes.VIDEO_MP2T) {
                            MediaItem.Builder()
                                .setUri(mediaUri)
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                        } else {
                            MediaItem.fromUri(mediaUri)
                        }
                    }
                    localRetryCount == 2 -> {
                        // Progressive auto-probing
                        MediaItem.fromUri(mediaUri)
                    }
                    localRetryCount == 3 -> {
                        // Fallback for Xtream VOD movies/series missing extension
                        if (isVodUrl && !urlLower.endsWith(".mp4") && !urlLower.endsWith(".mkv") && !urlLower.endsWith(".avi") && !urlLower.contains("?")) {
                            MediaItem.fromUri(android.net.Uri.parse("$sanitizedUrl.mp4"))
                        } else if (isVodUrl && urlLower.endsWith(".mp4")) {
                            MediaItem.fromUri(android.net.Uri.parse(sanitizedUrl.substringBeforeLast(".mp4") + ".mkv"))
                        } else {
                            MediaItem.fromUri(mediaUri)
                        }
                    }
                    else -> {
                        MediaItem.fromUri(mediaUri)
                    }
                }

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        player.setMediaItem(retryItem)
                        player.prepare()
                        player.playWhenReady = true
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Error during retry prepare", e)
                    }
                }, 500L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            try {
                if (totalDuration > 0 && currentPos > 0 && !showResumeDialog) {
                    onProgressUpdate(item, currentPos, totalDuration)
                }
            } catch (e: Exception) {
                // Ignore safe errors
            }
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                android.util.Log.e("PlayerScreen", "Error releasing player in onDispose", e)
            }
        }
    }

    // Restart controls timer on visibility toggles
    LaunchedEffect(controlsVisible, showTray, isScreenLocked) {
        resetControlsTimer()
    }

    // Adjust hardware brightness
    fun adjustBrightness(delta: Float) {
        val nextVal = min(1.0f, max(0.01f, brightnessLevel + delta))
        brightnessLevel = nextVal
        activity?.runOnUiThread {
            val lp = activity.window.attributes
            lp.screenBrightness = nextVal
            activity.window.attributes = lp
        }
        showBrightnessHUD = true
        showVolumeHUD = false
        resetControlsTimer()
    }

    // Adjust hardware volume
    fun adjustVolume(delta: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val nextVal = min(1.0f, max(0.0f, volumeLevel + delta))
        volumeLevel = nextVal
        val targetVolume = (nextVal * maxVol).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        showVolumeHUD = true
        showBrightnessHUD = false
        resetControlsTimer()
    }

    // Android TV kumandası: kontroller gizliyken OK = kontrolleri göster, sol/sağ = 10 sn geri/ileri,
    // yukarı/aşağı = kontrolleri göster; medya tuşları her zaman çalışır. Geri tuşu önce kontrolleri
    // gizler, ikinci basışta oynatıcıdan çıkar.
    val tvKeyFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            delay(150)
            try {
                tvKeyFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(300)
        try {
            tvKeyFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
    androidx.activity.compose.BackHandler(enabled = controlsVisible && !showTray && !isScreenLocked) {
        controlsVisible = false
    }
    // Kanal/bölüm listesi açıkken Geri önce listeyi kapatır (sonraki basış kontrolleri gizler / çıkar).
    androidx.activity.compose.BackHandler(enabled = showTray) {
        showTray = false
    }
    // Kilit açma düğmesi kaybolunca kumanda tuşları yeniden oynatıcıya gelsin.
    LaunchedEffect(showUnlockButtonBriefly) {
        if (!showUnlockButtonBriefly && isScreenLocked) {
            delay(150)
            try {
                tvKeyFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
    fun showUnlockHint() {
        showUnlockButtonBriefly = true
        scope.launch {
            delay(3000)
            showUnlockButtonBriefly = false
        }
    }
    // CH+/CH-: oynatıcının kanal listesindeki (canlı kanallar) sonraki/önceki kanal.
    fun switchChannel(step: Int) {
        val channels = iptvViewModel?.liveChannels?.value?.takeIf { it.isNotEmpty() } ?: return
        val index = channels.indexOfFirst { it.id == item.id }
        val target = if (index == -1) {
            channels.first()
        } else {
            channels[(index + step).mod(channels.size)]
        }
        if (target.id != item.id) onPlayItem(target)
    }
    fun tvSeekBy(deltaMs: Long) {
        if (!player.isCurrentMediaItemSeekable) return
        val duration = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (duration > 0) min(duration, target) else target)
        showDoubleTapFeedback = if (deltaMs < 0) "geri" else "ileri"
        scope.launch {
            delay(800)
            showDoubleTapFeedback = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DeepPurpleBg)
            .then(com.example.ui.tv.tvChannelKeys(enabled = item.type == "LIVE") { step -> switchChannel(step) })
            .then(
                com.example.ui.tv.tvPlayerKeys(
                    focusRequester = tvKeyFocusRequester,
                    // Kilitliyken de tuşlar burada yakalanır: kilit açma düğmesi gösterilir, sarma yapılmaz.
                    // Kilit açma düğmesi görünürken tuşlar ona gider (OK = kilidi aç).
                    isOverlayHidden = {
                        // Hata penceresi açıkken tuşlar pencerenin düğmelerine gider.
                        !showTray && !streamFailed &&
                            if (isScreenLocked) !showUnlockButtonBriefly else !controlsVisible
                    },
                    onShowControls = {
                        if (isScreenLocked) {
                            showUnlockHint()
                        } else {
                            controlsVisible = true
                            resetControlsTimer()
                        }
                    },
                    onSeek = { deltaMs -> if (isScreenLocked) showUnlockHint() else tvSeekBy(deltaMs) },
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                    },
                    onUserActivity = {
                        if (controlsVisible) resetControlsTimer()
                    }
                )
            )
            .testTag("player_screen")
    ) {
        val screenWidth = constraints.maxWidth
        val screenHeight = constraints.maxHeight

        // 1. Gesture and Touch Interceptor Panel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isScreenLocked) {
                    val touchSlop = viewConfiguration.touchSlop
                    val doubleTapTimeoutMs = 300L
                    var lastTapTime = 0L
                    var lastTapWasLeft = false

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val startPosition = down.position
                        var lastPosition = down.position
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                if (!isDragging) {
                                    val elapsed = System.currentTimeMillis() - downTime
                                    if (elapsed < 250L) {
                                        val tapX = change?.position?.x ?: startPosition.x
                                        val isLeft = tapX < (size.width / 2f)
                                        val now = System.currentTimeMillis()
                                        if (now - lastTapTime < doubleTapTimeoutMs && isLeft == lastTapWasLeft) {
                                            if (!isScreenLocked) {
                                                if (isLeft) {
                                                    val current = player.currentPosition
                                                    player.seekTo(max(0L, current - 10000L))
                                                    showDoubleTapFeedback = "geri"
                                                } else {
                                                    val current = player.currentPosition
                                                    player.seekTo(min(player.duration, current + 10000L))
                                                    showDoubleTapFeedback = "ileri"
                                                }
                                                scope.launch {
                                                    delay(800)
                                                    showDoubleTapFeedback = null
                                                }
                                                controlsVisible = true
                                            }
                                            lastTapTime = 0L
                                        } else {
                                            if (isScreenLocked) {
                                                showUnlockButtonBriefly = true
                                                scope.launch {
                                                    delay(3000)
                                                    showUnlockButtonBriefly = false
                                                }
                                            } else {
                                                controlsVisible = !controlsVisible
                                            }
                                            lastTapTime = now
                                            lastTapWasLeft = isLeft
                                        }
                                    }
                                }
                                break
                            }

                            val totalDx = change.position.x - startPosition.x
                            val totalDy = change.position.y - startPosition.y

                            if (!isDragging && (kotlin.math.abs(totalDx) > touchSlop || kotlin.math.abs(totalDy) > touchSlop)) {
                                isDragging = true
                                if (!isScreenLocked) {
                                    controlsVisible = true
                                }
                            }

                            if (isDragging && !isScreenLocked) {
                                change.consume()
                                val dragAmountY = change.position.y - lastPosition.y
                                val isLeftHalf = change.position.x < (size.width / 2f)
                                val deltaY = -dragAmountY / 500f
                                if (isLeftHalf) {
                                    adjustBrightness(deltaY)
                                } else {
                                    adjustVolume(deltaY)
                                }
                            }
                            lastPosition = change.position
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Embedded Media3 Video Frame
            AndroidView(
                factory = { ctx ->
                    (android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.player_view_texture, null) as PlayerView).apply {
                        useController = false
                        this.player = player
                        keepScreenOn = true
                        setBackgroundColor(android.graphics.Color.BLACK)

                        val subColorInt = when (subtitleColor) {
                            "Sarı" -> 0xFFFFEB3B.toInt()
                            "Yeşil" -> 0xFF4CAF50.toInt()
                            "Mavi" -> 0xFF2196F3.toInt()
                            else -> android.graphics.Color.WHITE
                        }
                        val captionStyle = CaptionStyleCompat(
                            subColorInt,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                        subtitleView?.apply {
                            setApplyEmbeddedFontSizes(false)
                            setApplyEmbeddedStyles(false)
                            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize.toFloat())
                            setStyle(captionStyle)
                        }
                    }
                },
                update = { playerView ->
                    if (playerView.player != player) {
                        playerView.player = player
                    }
                    playerView.resizeMode = when (currentAspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.SIXTEEN_NINE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }

                    val subColorInt = when (subtitleColor) {
                        "Sarı" -> 0xFFFFEB3B.toInt()
                        "Yeşil" -> 0xFF4CAF50.toInt()
                        "Mavi" -> 0xFF2196F3.toInt()
                        else -> android.graphics.Color.WHITE
                    }
                    val captionStyle = CaptionStyleCompat(
                        subColorInt,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        null
                    )
                    playerView.subtitleView?.apply {
                        setApplyEmbeddedFontSizes(false)
                        setApplyEmbeddedStyles(false)
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize.toFloat())
                        setStyle(captionStyle)
                    }
                },
                onRelease = { playerView ->
                    try {
                        playerView.player = null
                    } catch (e: Exception) {
                        // Safe detachment
                    }
                },
                modifier = if (currentAspectRatioMode == AspectRatioMode.SIXTEEN_NINE) {
                    Modifier
                        .aspectRatio(16f / 9f)
                        .align(Alignment.Center)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        // PiP penceresinde yalnızca video görünür; kontroller, paneller ve pencereler gizlenir.
        if (!isInPip) {
            // 2. Gesture HUD Indicator overlays (Left Edge: Brightness, Right Edge: Volume)
            AnimatedVisibility(
                visible = showBrightnessHUD,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp)
                    .zIndex(10f)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, ElectricBlue.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brightness5,
                            contentDescription = stringResource(R.string.player_brightness_desc),
                            tint = ElectricBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "%${(brightnessLevel * 100).toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showVolumeHUD,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp)
                    .zIndex(10f)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, ElectricBlue.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (volumeLevel == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.player_volume_desc),
                            tint = ElectricBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "%${(volumeLevel * 100).toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // 3. Double Tap Rewind/Forward Visual Overlay Anim
            if (showDoubleTapFeedback != null) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .align(if (showDoubleTapFeedback == "geri") Alignment.CenterStart else Alignment.CenterEnd)
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (showDoubleTapFeedback == "geri") {
                                        listOf(NeonPink.copy(alpha = 0.2f), Color.Transparent)
                                    } else {
                                        listOf(Color.Transparent, ElectricBlue.copy(alpha = 0.2f))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (showDoubleTapFeedback == "geri") Icons.Default.Replay10 else Icons.Default.Forward10,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (showDoubleTapFeedback == "geri") stringResource(R.string.player_seek_back_10) else stringResource(R.string.player_seek_forward_10),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 4. Aspect Ratio Badge notification overlay
            AnimatedVisibility(
                visible = showAspectRatioBadge != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-110).dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, ElectricBlue, RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = showAspectRatioBadge ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 5. Screen Lock Status Overlay
            if (isScreenLocked) {
                AnimatedVisibility(
                    visible = showUnlockButtonBriefly,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                ) {
                    Button(
                        onClick = {
                            isScreenLocked = false
                            controlsVisible = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(24.dp),
                        border = PaddingValues(0.dp).let {
                            androidx.compose.foundation.BorderStroke(1.dp, NeonPink)
                        },
                        // TV: düğme görününce odak ona geçer, OK ile kilit açılır.
                        modifier = Modifier.height(48.dp).then(com.example.ui.tv.tvInitialFocus())
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = stringResource(R.string.player_unlock_screen_desc),
                            tint = NeonPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.player_unlock_screen_text),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 6. Premium Gradient Black Overlay (for controls)
            AnimatedVisibility(
                visible = controlsVisible && !isScreenLocked,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0E071A).copy(alpha = 0.85f),
                                    Color.Transparent,
                                    Color(0xFF0E071A).copy(alpha = 0.9f)
                                )
                            )
                        )
                ) {
                    // 1. Üst Bar (Top Bar Layering)
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                horizontal = layout.playerHorizontalPadding,
                                vertical = if (layout.isLandscape) 8.dp else 12.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sol Üst: Geri Butonu + Film Adı & Türü
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(layout.playerSecondaryControlSize)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.player_go_back_desc),
                                tint = Color.White,
                                modifier = Modifier.size((layout.playerSecondaryControlSize.value * 0.5f).dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (item.type == "SERIES" && item.season != null && item.episode != null) {
                                    "${item.cleanedName} S${item.season}B${item.episode}"
                                } else {
                                    item.cleanedName
                                },
                                color = Color.White,
                                fontSize = (layout.titleFontSize.value - 3f).coerceIn(13f, 18f).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.category,
                                color = MutedText,
                                fontSize = (layout.bodyFontSize.value - 1f).coerceIn(10f, 13f).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. Alt Bölüm (Playback Kontrolleri + İlerleme Çubuğu + Alt Buton Grubu)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                horizontal = layout.playerHorizontalPadding,
                                vertical = layout.playerBottomPadding
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // A. Playback Kontrolleri (Geri 10sn - Oynat/Duraklat - İleri 10sn / Önceki-Sonraki Bölüm)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.type == "SERIES" && currentIndex > 0) {
                                val prevItem = siblingItems[currentIndex - 1]
                                IconButton(
                                    onClick = { onPlayItem(prevItem) },
                                    modifier = Modifier
                                        .size(layout.playerControlSize)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = stringResource(R.string.player_previous_episode_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size((layout.playerControlSize.value * 0.45f).dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val current = player.currentPosition
                                    player.seekTo(max(0, current - 10000))
                                    resetControlsTimer()
                                },
                                modifier = Modifier
                                    .size(layout.playerControlSize)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = stringResource(R.string.player_rewind_10_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size((layout.playerControlSize.value * 0.48f).dp)
                                )
                            }

                            // Play / Pause Glowing Button
                            Box(
                                modifier = Modifier
                                    .size(layout.playPauseSize)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            colors = listOf(NeonPink, ElectricBlue, NeonPink)
                                        )
                                    )
                                    .shadow(8.dp, shape = CircleShape, spotColor = NeonPink)
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable {
                                        if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                        resetControlsTimer()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.action_play),
                                    tint = Color.White,
                                    modifier = Modifier.size((layout.playPauseSize.value * 0.5f).dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val current = player.currentPosition
                                    player.seekTo(min(player.duration, current + 10000))
                                    resetControlsTimer()
                                },
                                modifier = Modifier
                                    .size(layout.playerControlSize)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = stringResource(R.string.player_forward_10_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size((layout.playerControlSize.value * 0.48f).dp)
                                )
                            }

                            if (item.type == "SERIES" && nextItem != null) {
                                IconButton(
                                    onClick = { onPlayItem(nextItem) },
                                    modifier = Modifier
                                        .size(if (isLandscape) 40.dp else 38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = stringResource(R.string.player_next_episode_desc),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

                        // B. İlerleme Çubuğu (Slider + Zaman Bilgileri)
                        if (item.type != "LIVE" && item.type != "RADIO") {
                            val maxVal = totalDuration.toFloat().coerceAtLeast(1f)
                            Slider(
                                value = currentPos.toFloat().coerceIn(0f, maxVal),
                                onValueChange = { targetPos ->
                                    currentPos = targetPos.toLong()
                                    player.seekTo((targetPos * 1000).toLong())
                                    resetControlsTimer()
                                },
                                valueRange = 0f..maxVal,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonPink,
                                    activeTrackColor = NeonPink,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentPos),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val remainingSec = (totalDuration - currentPos).coerceAtLeast(0L)
                                Text(
                                    text = "-${formatTime(remainingSec)}",
                                    color = MutedText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonPink)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.player_live_badge),
                                    color = NeonPink,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (layout.isLandscape) 6.dp else 10.dp))

                        // C. Alt Satır: Sadece İkon İçeren Dengeli Kontrol Butonları
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kilitle Butonu
                            Surface(
                                onClick = {
                                    isScreenLocked = true
                                    controlsVisible = false
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.6f)),
                                modifier = Modifier.size(layout.playerSecondaryControlSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = stringResource(R.string.player_lock_desc),
                                        tint = NeonPink,
                                        modifier = Modifier.size((layout.playerSecondaryControlSize.value * 0.48f).dp)
                                    )
                                }
                            }

                            // Ekran Formatı (Sığdır/Kırp) Butonu
                            Surface(
                                onClick = {
                                    val values = AspectRatioMode.values()
                                    val nextIndex = (currentAspectRatioMode.ordinal + 1) % values.size
                                    currentAspectRatioMode = values[nextIndex]
                                    val aspectBadge = context.getString(R.string.player_aspect_badge, context.getString(currentAspectRatioMode.labelRes))
                                    showAspectRatioBadge = aspectBadge
                                    scope.launch {
                                        delay(1500)
                                        if (showAspectRatioBadge == aspectBadge) {
                                            showAspectRatioBadge = null
                                        }
                                    }
                                    resetControlsTimer()
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
                                modifier = Modifier.size(layout.playerSecondaryControlSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = stringResource(R.string.player_aspect_ratio_desc),
                                        tint = ElectricBlue,
                                        modifier = Modifier.size((layout.playerSecondaryControlSize.value * 0.48f).dp)
                                    )
                                }
                            }

                            // Ses / Altyazı Butonu
                            Surface(
                                onClick = {
                                    showTrackSelectorSheet = true
                                    resetControlsTimer()
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
                                modifier = Modifier.size(layout.playerSecondaryControlSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = stringResource(R.string.player_audio_subtitle_desc),
                                        tint = ElectricBlue,
                                        modifier = Modifier.size((layout.playerSecondaryControlSize.value * 0.48f).dp)
                                    )
                                }
                            }

                            // Bölümler / Listeler Butonu
                            Surface(
                                onClick = {
                                    showTray = !showTray
                                    resetControlsTimer()
                                },
                                shape = CircleShape,
                                color = if (showTray) NeonPink.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (showTray) NeonPink else Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.size(layout.playerSecondaryControlSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = if (item.type == "SERIES") stringResource(R.string.player_episodes) else stringResource(R.string.player_lists),
                                        tint = Color.White,
                                        modifier = Modifier.size((layout.playerSecondaryControlSize.value * 0.48f).dp)
                                    )
                                }
                            }

                        }
                    }
                }
            }

            // 4. Bölümler ve Öneriler Paneli (SideDrawer in Landscape / BottomSheet in Portrait)
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            AnimatedVisibility(
                visible = showTray,
                enter = if (isLandscape) slideInHorizontally(initialOffsetX = { it }) else slideInVertically(initialOffsetY = { it }),
                exit = if (isLandscape) slideOutHorizontally(targetOffsetX = { it }) else slideOutVertically(targetOffsetY = { it }),
                modifier = if (isLandscape) Modifier.align(Alignment.CenterEnd) else Modifier.align(Alignment.BottomCenter)
            ) {
                if (isLandscape) {
                    // Landscape Right SideDrawer
                    Card(
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E071A).copy(alpha = 0.94f)),
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .border(
                                BorderStroke(1.dp, NeonPink.copy(alpha = 0.3f)),
                                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                            )
                            .testTag("player_side_drawer")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.type == "LIVE") stringResource(R.string.player_channel_list) else if (item.type == "SERIES") stringResource(R.string.player_episodes) else stringResource(R.string.player_recommended),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showTray = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (siblingItems.isNotEmpty()) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(siblingItems, key = { it.id }) { sib ->
                                        val isCurrent = sib.id == item.id
                                        Surface(
                                            onClick = {
                                                if (!isCurrent) {
                                                    showTray = false
                                                    onPlayItem(sib)
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isCurrent) NeonPink.copy(alpha = 0.2f) else Color(0xFF180D2C).copy(alpha = 0.8f),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isCurrent) NeonPink else Color.White.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!sib.logoUrl.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = sib.logoUrl,
                                                        contentDescription = sib.cleanedName,
                                                        modifier = Modifier
                                                            .size(48.dp, 32.dp)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sib.cleanedName,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isCurrent) {
                                                        Text(
                                                            text = stringResource(R.string.player_now_playing),
                                                            color = NeonPink,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = NeonPink,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_no_content_list),
                                        color = MutedText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Portrait Bottom Sheet Tray
                    Card(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E071A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .border(
                                BorderStroke(1.dp, NeonPink.copy(alpha = 0.2f)),
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                            .testTag("player_channel_tray")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.type == "LIVE") stringResource(R.string.player_channel_list_category, item.category) else if (item.type == "SERIES") stringResource(R.string.player_next_episode) else stringResource(R.string.player_recommended),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showTray = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (siblingItems.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(siblingItems, key = { it.id }) { sib ->
                                        val isCurrent = sib.id == item.id
                                        Box(
                                            modifier = Modifier
                                                .width(135.dp)
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF180D2C))
                                                .then(
                                                    if (isCurrent) {
                                                        Modifier.border(2.dp, NeonPink, RoundedCornerShape(12.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable {
                                                    if (!isCurrent) {
                                                        showTray = false
                                                        onPlayItem(sib)
                                                    }
                                                }
                                        ) {
                                            if (!sib.logoUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = sib.logoUrl,
                                                    contentDescription = sib.cleanedName,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = if (isCurrent) 0.35f else 0.55f))
                                            )

                                            if (isCurrent) {
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .background(NeonPink, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = stringResource(R.string.player_watching_now),
                                                        color = Color.White,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Text(
                                                text = sib.cleanedName,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_no_alt_content_list),
                                        color = MutedText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7.5. Kaldığın Yerden Devam Et Dialog Overlay
            if (showResumeDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F082B).copy(alpha = 0.95f))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(listOf(NeonPink.copy(alpha = 0.5f), ElectricBlue.copy(alpha = 0.5f))),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CineOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = CineOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.player_continue_watching_question),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val seriesInfo = remember(item) {
                            if (item.type == "SERIES") {
                                com.example.data.model.SeriesParser.parseEpisodeInfo(item.cleanedName)
                                    ?: com.example.data.model.SeriesParser.parseEpisodeInfo(item.name)
                            } else {
                                null
                            }
                        }

                        val detailText = remember(item, initialProgressSeconds, seriesInfo, context) {
                            val timeStr = formatTime(initialProgressSeconds)
                            if (seriesInfo != null) {
                                context.getString(R.string.player_resume_episode, seriesInfo.season, seriesInfo.episode, timeStr)
                            } else {
                                context.getString(R.string.player_resume_position, timeStr)
                            }
                        }

                        Text(
                            text = detailText,
                            color = SlateGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        if (seriesInfo != null && iptvViewModel != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (aiRecapText == null) {
                                OutlinedButton(
                                    onClick = {
                                        isAILoadingRecap = true
                                        aiRecapText = "Yapay zeka bülteni hazırlanıyor..."
                                        scope.launch {
                                            try {
                                                iptvViewModel.getSpoilerFreePreviousEpisodesSummary(
                                                    showTitle = seriesInfo.showTitle,
                                                    season = seriesInfo.season,
                                                    episode = seriesInfo.episode
                                                ).collect { result ->
                                                    aiRecapText = result
                                                    if (result != "Yapay zeka bülteni hazırlanıyor...") {
                                                        isAILoadingRecap = false
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                aiRecapText = "Özet hazırlanamadı. Lütfen tekrar deneyin."
                                                isAILoadingRecap = false
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = CineOrange
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CineOrange.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = CineOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.player_ai_summarize_spoiler_free),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    if (isAILoadingRecap) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            CircularProgressIndicator(
                                                color = CineOrange,
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = stringResource(R.string.player_ai_analyzing),
                                                color = SlateGray,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = CineOrange,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.player_ai_previous_summary_title),
                                                    color = CineOrange,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = aiRecapText ?: "",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showResumeDialog = false
                                    player.seekTo(0L)
                                    player.playWhenReady = true
                                    player.play()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.player_start_over),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    showResumeDialog = false
                                    player.playWhenReady = true
                                    player.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .shadow(8.dp, shape = RoundedCornerShape(14.dp), spotColor = NeonPink)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(NeonPink, CineOrange)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_continue),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7.6. Yayın açılamadı penceresi ("Kaldığın yerden devam" penceresiyle aynı görünüm)
            if (streamFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .pointerInput(Unit) { detectTapGestures { } }
                        .testTag("player_stream_failed"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF0F082B).copy(alpha = 0.95f))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(listOf(NeonPink.copy(alpha = 0.5f), ElectricBlue.copy(alpha = 0.5f))),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CineOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = CineOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = stringResource(R.string.player_stream_failed_title),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.player_stream_failed_message),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("player_stream_failed_back")
                            ) {
                                Text(
                                    text = stringResource(R.string.player_back_desc),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = {
                                    streamFailed = false
                                    localRetryCount = 0
                                    try {
                                        player.setMediaItem(ExoPlayerConfigurator.buildMediaItemForUrl(item.streamUrl.trim()))
                                        player.prepare()
                                        player.playWhenReady = true
                                    } catch (e: Exception) {
                                        android.util.Log.e("PlayerScreen", "Retry failed", e)
                                        streamFailed = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .shadow(8.dp, shape = RoundedCornerShape(14.dp), spotColor = NeonPink)
                                    // TV: pencere açılınca odak "Tekrar dene"de olsun.
                                    .then(com.example.ui.tv.tvInitialFocus())
                                    .testTag("player_stream_failed_retry")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(NeonPink, CineOrange)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.detail_retry),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 8. Custom Premium Track Selection BottomSheet
            if (showTrackSelectorSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTrackSelectorSheet = false },
                    containerColor = Color(0xFF0E071A),
                    scrimColor = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.player_audio_subtitle_selection),
                            color = BrokenWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val audioTracks = mutableListOf<Pair<Tracks.Group, Int>>()
                        val subtitleTracks = mutableListOf<Pair<Tracks.Group, Int>>()

                        currentTracks?.groups?.forEach { group ->
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    audioTracks.add(group to i)
                                }
                            } else if (group.type == C.TRACK_TYPE_TEXT) {
                                for (i in 0 until group.length) {
                                    subtitleTracks.add(group to i)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Left Column: Audio tracks
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.player_audio_language),
                                        color = ElectricBlue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (audioTracks.isNotEmpty()) {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        itemsIndexed(audioTracks, key = { i, _ -> "audio_$i" }) { _, (group, index) ->
                                            val format = group.getTrackFormat(index)
                                            val isSelected = group.isTrackSelected(index)
                                            val label = format.label ?: format.language ?: "Ses $index"

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) NeonPink.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                                                    .clickable {
                                                        player.trackSelectionParameters = player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, index))
                                                            .build()
                                                        showTrackSelectorSheet = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) NeonPink else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = stringResource(R.string.selected),
                                                        tint = NeonPink,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.player_no_audio_option),
                                        color = MutedText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            // Right Column: Subtitles
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = NeonPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.player_subtitle),
                                        color = NeonPink,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Option to turn subtitles off
                                val isSubtitlesDisabled = currentTracks?.groups?.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected } ?: true

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSubtitlesDisabled) NeonPink.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            showTrackSelectorSheet = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_subtitle_off),
                                        color = if (isSubtitlesDisabled) NeonPink else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSubtitlesDisabled) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSubtitlesDisabled) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = stringResource(R.string.player_off_desc),
                                            tint = NeonPink,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (subtitleTracks.isNotEmpty()) {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        itemsIndexed(subtitleTracks, key = { i, _ -> "sub_$i" }) { _, (group, index) ->
                                            val format = group.getTrackFormat(index)
                                            val isSelected = !isSubtitlesDisabled && group.isTrackSelected(index)
                                            val label = format.label ?: format.language ?: stringResource(R.string.player_subtitle_track, index)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) NeonPink.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                                                    .clickable {
                                                        player.trackSelectionParameters = player.trackSelectionParameters
                                                            .buildUpon()
                                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, index))
                                                            .build()
                                                        showTrackSelectorSheet = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) NeonPink else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = stringResource(R.string.selected),
                                                        tint = NeonPink,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.player_no_subtitle_option),
                                        color = MutedText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showResumeDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF130C1E).copy(alpha = 0.94f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .widthIn(max = 420.dp)
                            .padding(16.dp)
                            .border(
                                width = 1.2.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(CineOrange.copy(alpha = 0.4f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .shadow(24.dp, RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.player_play_desc),
                                tint = CineOrange,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.player_continue_watching_question),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val progressSec = initialProgressSeconds
                            val totalSec = iptvViewModel?.continueWatching?.value?.find { it.itemId == item.id }?.totalSeconds ?: 3600L
                            val percentage = if (totalSec > 0) ((progressSec.toFloat() / totalSec.toFloat()) * 100).toInt().coerceIn(1, 99) else 0
                            val progressStr = String.format("%02d:%02d", progressSec / 60, progressSec % 60)

                            Text(
                                text = stringResource(R.string.player_resume_progress, progressStr, percentage),
                                color = Color(0xFFE2E2E2).copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            LinearProgressIndicator(
                                progress = { percentage.toFloat() / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = CineOrange,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showResumeDialog = false
                                        player.seekTo(0)
                                        player.playWhenReady = true
                                        player.play()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_start_over),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        showResumeDialog = false
                                        player.seekTo(initialProgressSeconds * 1000)
                                        player.playWhenReady = true
                                        player.play()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CineOrange,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.player_continue),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
