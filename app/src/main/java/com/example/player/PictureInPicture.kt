package com.example.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.example.ui.tv.TvDevice
import com.example.util.findActivity

/**
 * Resim içinde resim (PiP): oynatıcıda video oynarken kullanıcı ana ekran tuşuna basarsa video küçük
 * pencerede oynamaya devam eder.
 *
 * - Android 12+ : `setAutoEnterEnabled` ile sistem kendisi geçer (akıcı animasyon).
 * - Android 8–11: kullanıcı uygulamadan ayrılırken (`onUserLeaveHint`) PiP'e geçilir.
 * - Android TV'de ve PiP desteklemeyen cihazlarda kapalıdır.
 *
 * @param canEnter şu an PiP'e geçilebilir mi (ör. video oynuyor ve hata yok)
 * @param onDismissedInBackground PiP penceresi kapatıldığında (uygulama arka planda kalırken) çağrılır;
 *        oynatıcı burada durdurulmalıdır.
 * @return uygulama şu an PiP penceresinde mi
 */
@Composable
fun rememberPictureInPicture(
    player: Player,
    canEnter: Boolean,
    onDismissedInBackground: () -> Unit
): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val supported = remember(activity) { activity != null && isPictureInPictureSupported(activity) }
    var inPip by remember(activity) {
        mutableStateOf(activity?.isInPictureInPictureMode == true)
    }
    val latestOnDismissed by rememberUpdatedState(onDismissedInBackground)

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            // Pencere "X" ile kapatıldıysa etkinlik görünür değildir: oynatmayı durdur.
            if (!info.isInPictureInPictureMode && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                latestOnDismissed()
            }
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    if (!supported || activity == null) return inPip

    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val latestCanEnter by rememberUpdatedState(canEnter)
    val latestVideoSize by rememberUpdatedState(videoSize)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SideEffect {
            runCatching {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(pipAspectRatio(videoSize))
                        .setAutoEnterEnabled(canEnter)
                        .setSeamlessResizeEnabled(true)
                        .build()
                )
            }
        }
        DisposableEffect(activity) {
            onDispose {
                // Oynatıcıdan çıkınca diğer ekranlarda PiP'e geçilmesin.
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                    )
                }
            }
        }
    } else {
        DisposableEffect(activity) {
            val listener = Runnable {
                if (latestCanEnter) {
                    runCatching {
                        activity.enterPictureInPictureMode(
                            PictureInPictureParams.Builder()
                                .setAspectRatio(pipAspectRatio(latestVideoSize))
                                .build()
                        )
                    }
                }
            }
            activity.addOnUserLeaveHintListener(listener)
            onDispose { activity.removeOnUserLeaveHintListener(listener) }
        }
    }
    return inPip
}

private fun isPictureInPictureSupported(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
        !TvDevice.isTv(context)

/** Android PiP oranı 1:2,39 ile 2,39:1 arasında olmalıdır; bilinmiyorsa 16:9. */
internal fun pipAspectRatio(size: VideoSize): Rational {
    val width = (size.width * size.pixelWidthHeightRatio).toInt()
    val height = size.height
    if (width <= 0 || height <= 0) return Rational(16, 9)
    val ratio = width.toFloat() / height
    return when {
        ratio > 2.39f -> Rational(239, 100)
        ratio < 1f / 2.39f -> Rational(100, 239)
        else -> Rational(width, height)
    }
}
