package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.util.findActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.IPTVItem
import com.example.ui.PlayerViewModel
import com.example.ui.theme.rememberAppAdaptiveLayout
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    item: IPTVItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialProgressSeconds: Long = 0L,
    onProgressUpdate: ((IPTVItem, Long, Long) -> Unit)? = null,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val layout = rememberAppAdaptiveLayout()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val isBuffering by playerViewModel.isBuffering.collectAsState()
    val errorState by playerViewModel.errorState.collectAsState()

    var showControls by remember { mutableStateOf(true) }

    var orientationMode by remember { mutableStateOf(0) } // 0: SENSOR_LANDSCAPE, 1: PORTRAIT, 2: SENSOR (AUTO)

    // Handle Activity Lifecycle (Pause on background, resume on foreground)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    playerViewModel.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val curSec = currentPosition / 1000L
            val durSec = duration / 1000L
            if (durSec > 0 && curSec > 0) {
                onProgressUpdate?.invoke(item, curSec, durSec)
            }
            playerViewModel.stop()
        }
    }

    // Keep screen on during playback & default to auto sensor landscape + durum çubuğunu gizle (immersive mod)
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    // Auto hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Initialize VLC player with stream URL
    LaunchedEffect(item.streamUrl) {
        playerViewModel.initAndPlay(item.streamUrl, initialProgressSeconds * 1000L)
    }

    var lastSavedPosSec by remember { mutableStateOf(0L) }
    // Report progress periodically (every 30s or on substantial seek)
    LaunchedEffect(currentPosition, duration) {
        val curSec = currentPosition / 1000L
        val durSec = duration / 1000L
        if (durSec > 0 && curSec > 0) {
            if (kotlin.math.abs(curSec - lastSavedPosSec) >= 30L) {
                lastSavedPosSec = curSec
                onProgressUpdate?.invoke(item, curSec, durSec)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // VLC Player View
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    keepScreenOn = true
                    try {
                        playerViewModel.mediaPlayer?.let { mp ->
                            if (!mp.vlcVout.areViewsAttached()) {
                                mp.attachViews(this, null, false, true)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoPlayerScreen", "Error attaching views in factory", e)
                        playerViewModel.switchToFallback()
                    }
                }
            },
            update = { layout ->
                try {
                    playerViewModel.mediaPlayer?.let { mp ->
                        if (!mp.vlcVout.areViewsAttached()) {
                            mp.attachViews(layout, null, false, true)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerScreen", "Error updating attached views", e)
                    playerViewModel.switchToFallback()
                }
            },
            onRelease = {
                try {
                    playerViewModel.mediaPlayer?.detachViews()
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerScreen", "Error detaching views", e)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Indicator
        if (isBuffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }

        // Error Banner
        errorState?.let { err ->
            Surface(
                color = Color.Red.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(err, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { playerViewModel.initAndPlay(item.streamUrl) }) {
                            Text("Tekrar Deneyin")
                        }
                        OutlinedButton(
                            onClick = { playerViewModel.switchToFallback() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Yedek Oynatıcı")
                        }
                    }
                }
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = layout.playerHorizontalPadding, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(layout.playerSecondaryControlSize)
                            .testTag("vlc_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.player_back_desc),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = (layout.titleFontSize.value - 2f).coerceIn(14f, 20f).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            orientationMode = (orientationMode + 1) % 3
                            activity?.requestedOrientation = when (orientationMode) {
                                0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            }
                        },
                        modifier = Modifier
                            .size(layout.playerSecondaryControlSize)
                            .testTag("vlc_rotate_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = stringResource(R.string.player_rotate_screen_desc),
                            tint = Color.White
                        )
                    }
                }

                // Bottom Unified Controls (Controls + Seekbar aligned together, preventing overlap)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            horizontal = layout.playerHorizontalPadding,
                            vertical = layout.playerBottomPadding
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Playback Controls Row (Rewind - Play/Pause - Forward)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (duration > 0) {
                            IconButton(
                                onClick = { playerViewModel.seekTo((currentPosition - 10000L).coerceAtLeast(0L)) },
                                modifier = Modifier.size(layout.playerControlSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = stringResource(R.string.player_rewind_10_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size((layout.playerControlSize.value * 0.55f).dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(layout.playPauseSize)
                                .background(Color.White.copy(alpha = 0.2f), shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                tint = Color.White,
                                modifier = Modifier.size((layout.playPauseSize.value * 0.55f).dp)
                            )
                        }

                        if (duration > 0) {
                            IconButton(
                                onClick = { playerViewModel.seekTo((currentPosition + 10000L).coerceAtMost(duration)) },
                                modifier = Modifier.size(layout.playerControlSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = stringResource(R.string.player_forward_10_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size((layout.playerControlSize.value * 0.55f).dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Seekbar / Live Indicator
                    if (duration > 0) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { playerViewModel.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text(formatTime(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    } else {
                        // Live TV indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Red, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CANLI YAYIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hr = totalSec / 3600
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
