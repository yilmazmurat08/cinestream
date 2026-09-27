package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.example.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val xRatio: Float,
    val yRatio: Float,
    val size: Float,
    val speed: Float,
    val color: Color
)

@Composable
fun IntroSplashScreen(
    appName: String = "CineStream",
    tagline: String = "Sınırsız Eğlence, İzlemenin Daha Akıllı Hali",
    onIntroFinished: () -> Unit
) {
    val purpleNeon = Color(0xFFDF55F7)
    val purpleDeep = Color(0xFF8B2FC9)
    val bgColor = Color(0xFF131319)
    val centerBgColor = Color(0xFF1C1A26)
    val greyText = Color(0xFFB4A9CE)

    val context = LocalContext.current
    val isInspectionMode = LocalInspectionMode.current

    // Ölçek: tasarım 400dp kısa kenara göre yapılmış. Küçük telefon (320dp), yatay telefon ve
    // TV (960x540dp) için logo/çizgi boyutları orantılı küçülür/büyür; görünüm aynı kalır.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shortSideDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
    val uiScale = (shortSideDp / 400f).coerceIn(0.7f, 1.35f)
    // "CineStream" başlığı tek satırdır; dar ekranda ve büyük yazı boyutunda taşmasın.
    val titleMaxWidthDp = (configuration.screenWidthDp - 48).coerceAtLeast(120)
    val titleSp = minOf(32f * uiScale, titleMaxWidthDp / (appName.length.coerceAtLeast(1) * 0.72f * density.fontScale))
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Audio Playback & Safe Lifecycle Management
    DisposableEffect(isInspectionMode) {
        if (!isInspectionMode) {
            try {
                val player = MediaPlayer.create(context, R.raw.intro_sound)?.apply {
                    setVolume(1.0f, 1.0f)
                    start()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                Log.w("IntroSplashScreen", "Failed to start intro sound: ${e.message}")
            }
        }
        onDispose {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                } catch (e: Exception) {
                    // Safe cleanup
                }
            }
            mediaPlayer = null
        }
    }

    // Animation drivers
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_and_particles")
    val pulseBrightness by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_brightness"
    )

    val beamSweep1 by animateFloatAsState(
        targetValue = if (transitionState.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, delayMillis = 150, easing = EaseOutQuad),
        label = "beam_1"
    )
    val beamSweep2 by animateFloatAsState(
        targetValue = if (transitionState.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 1300, delayMillis = 400, easing = EaseOutQuad),
        label = "beam_2"
    )

    val irisScale by animateFloatAsState(
        targetValue = if (transitionState.targetState) 14f else 1f,
        animationSpec = tween(durationMillis = 1500, delayMillis = 100, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        label = "iris_scale"
    )
    val irisAlpha by animateFloatAsState(
        targetValue = if (transitionState.targetState) 0f else 0.85f,
        animationSpec = tween(durationMillis = 1500, delayMillis = 100, easing = EaseOut),
        label = "iris_alpha"
    )

    val logoScale by animateFloatAsState(
        targetValue = if (transitionState.targetState) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logo_scale"
    )
    val logoRotation by animateFloatAsState(
        targetValue = if (transitionState.targetState) 0f else -25f,
        animationSpec = tween(durationMillis = 800, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "logo_rotation"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (transitionState.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 200),
        label = "logo_alpha"
    )

    val underlineWidthFraction by animateFloatAsState(
        targetValue = if (transitionState.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 2000, easing = FastOutSlowInEasing),
        label = "underline_anim"
    )

    val taglineAlpha by animateFloatAsState(
        targetValue = if (transitionState.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 1700, easing = FastOutSlowInEasing),
        label = "tagline_alpha"
    )

    // Stage fade out at the end of intro
    var stageFadingOut by remember { mutableStateOf(false) }
    val stageAlpha by animateFloatAsState(
        targetValue = if (stageFadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "stage_fade_out"
    )

    // Floating particles
    val particles = remember {
        List(22) {
            Particle(
                xRatio = Random.nextFloat(),
                yRatio = Random.nextFloat(),
                size = Random.nextFloat() * 3.5f + 2f,
                speed = Random.nextFloat() * 0.8f + 0.4f,
                color = if (it % 2 == 0) purpleNeon else purpleDeep
            )
        }
    }
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_time"
    )

    // Automatic transition sequence with volume fade-out
    LaunchedEffect(Unit) {
        delay(3400)
        stageFadingOut = true
        // 400ms fade-out volume reduction
        val steps = 8
        val stepMs = 50L
        for (i in steps downTo 0) {
            val vol = i / steps.toFloat()
            try {
                mediaPlayer?.setVolume(vol, vol)
            } catch (e: Exception) {
                // Ignore volume update exception during disposal
            }
            delay(stepMs)
        }
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {}
        onIntroFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(stageAlpha)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(centerBgColor, bgColor),
                    center = Offset.Unspecified,
                    radius = 1200f
                )
            )
            // TV: kumandanın OK tuşuyla da atlanabilsin (tam ekran alan, odak çerçevesi çizilmez).
            .then(com.example.ui.tv.tvInitialFocus())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip: immediately stop music and finish intro
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                } catch (e: Exception) {}
                onIntroFinished()
            }
            .testTag("cinestream_intro_stage"),
        contentAlignment = Alignment.Center
    ) {
        // Light Beams & Iris Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)

            // Expanding Iris Circles
            if (irisAlpha > 0.01f) {
                drawCircle(
                    color = purpleNeon.copy(alpha = (irisAlpha * 0.45f).coerceIn(0f, 1f)),
                    radius = 24.dp.toPx() * irisScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = purpleDeep.copy(alpha = (irisAlpha * 0.35f).coerceIn(0f, 1f)),
                    radius = 16.dp.toPx() * (irisScale * 0.8f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = purpleNeon.copy(alpha = (irisAlpha * 0.25f).coerceIn(0f, 1f)),
                    radius = 32.dp.toPx() * (irisScale * 1.2f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Sweeping Beam 1 (-20 deg)
            if (beamSweep1 > 0f) {
                val beamLength = size.maxDimension * 1.5f * beamSweep1
                val beamAlpha = (1f - beamSweep1 * 0.7f).coerceIn(0f, 1f)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, purpleNeon.copy(alpha = 0.5f * beamAlpha), Color.Transparent),
                        start = Offset(center.x - beamLength * 0.5f, center.y + beamLength * 0.18f),
                        end = Offset(center.x + beamLength * 0.5f, center.y - beamLength * 0.18f)
                    ),
                    start = Offset(center.x - beamLength * 0.5f, center.y + beamLength * 0.18f),
                    end = Offset(center.x + beamLength * 0.5f, center.y - beamLength * 0.18f),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Sweeping Beam 2 (12 deg)
            if (beamSweep2 > 0f) {
                val beamLength2 = size.maxDimension * 1.5f * beamSweep2
                val beamAlpha2 = (1f - beamSweep2 * 0.7f).coerceIn(0f, 1f)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, purpleDeep.copy(alpha = 0.5f * beamAlpha2), Color.Transparent),
                        start = Offset(center.x - beamLength2 * 0.5f, center.y - beamLength2 * 0.1f),
                        end = Offset(center.x + beamLength2 * 0.5f, center.y + beamLength2 * 0.1f)
                    ),
                    start = Offset(center.x - beamLength2 * 0.5f, center.y - beamLength2 * 0.1f),
                    end = Offset(center.x + beamLength2 * 0.5f, center.y + beamLength2 * 0.1f),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            // Floating Rising Particles
            particles.forEach { p ->
                val currentYRatio = (p.yRatio - particleTime * p.speed + 1f) % 1f
                val pY = size.height * 0.75f - (currentYRatio * size.height * 0.55f)
                val pX = size.width * p.xRatio
                val pAlpha = sin(currentYRatio * Math.PI.toFloat()).coerceIn(0f, 0.9f)
                drawCircle(
                    color = p.color.copy(alpha = pAlpha),
                    radius = p.size.dp.toPx(),
                    center = Offset(pX, pY)
                )
            }
        }

        // Center Content Column: Logo, Animated Title, Tagline, Underline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Animated Logo Box with Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(logoScale)
                    .rotate(logoRotation)
                    .alpha(logoAlpha)
            ) {
                // Background Soft Neon Glow behind logo
                Box(
                    modifier = Modifier
                        .size(140.dp * uiScale)
                        .scale(pulseBrightness)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(purpleNeon.copy(alpha = 0.35f), Color.Transparent),
                                radius = 160f * uiScale
                            )
                        )
                )

                Image(
                    painter = painterResource(id = R.drawable.img_app_logo_1782414357652),
                    contentDescription = "CineStream Logo",
                    modifier = Modifier
                        .size(100.dp * uiScale)
                        .clip(RoundedCornerShape(24.dp * uiScale))
                        .scale(pulseBrightness),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cascading Letters App Name: "CineStream"
            val titleText = appName
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                titleText.forEachIndexed { index, char ->
                    var letterVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(650L + index * 45L)
                        letterVisible = true
                    }
                    val letterAlpha by animateFloatAsState(
                        targetValue = if (letterVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
                        label = "char_alpha_$index"
                    )
                    val letterOffsetY by animateDpAsState(
                        targetValue = if (letterVisible) 0.dp else 14.dp,
                        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
                        label = "char_offset_$index"
                    )

                    Text(
                        text = char.toString(),
                        color = Color.White,
                        fontSize = titleSp.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp,
                        modifier = Modifier
                            .offset(y = letterOffsetY)
                            .alpha(letterAlpha)
                    )
                }
            }

            // Tagline
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tagline,
                color = greyText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.2.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.alpha(taglineAlpha)
            )

            // Animated Gradient Underline (0 to 140 dp)
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(140.dp * uiScale * underlineWidthFraction)
                    .height(2.5.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(purpleDeep, purpleNeon)
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IntroSplashScreenPreview() {
    IntroSplashScreen(
        appName = "CineStream",
        tagline = "Sınırsız Eğlence, İzlemenin Daha Akıllı Hali",
        onIntroFinished = {}
    )
}

