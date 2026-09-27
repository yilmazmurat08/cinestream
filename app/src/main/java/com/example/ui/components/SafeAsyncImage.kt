package com.example.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.CineOrange
import com.example.ui.theme.CinematicStart
import com.example.ui.theme.CinematicEnd

/**
 * Shimmer background brush for polished image loading feedback.
 */
@Composable
fun shimmerBrush(
    showShimmer: Boolean = true,
    targetValue: Float = 1000f
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color(0xFF242533),
            Color(0xFF3B3D54),
            Color(0xFF242533)
        )

        val transition = rememberInfiniteTransition(label = "shimmer_transition")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(1300)
            ),
            label = "shimmer_anim"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

/**
 * Cinematic, rich, dark-toned fallback view when loading fails or URL is empty.
 */
@Composable
fun CinemaPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CinematicStart, CinematicEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CineOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_cinema_placeholder),
                contentDescription = "Movie Placeholder Logo",
                modifier = Modifier
                    .size(24.dp)
                    .alpha(0.85f),
                colorFilter = ColorFilter.tint(CineOrange)
            )
        }
    }
}

/**
 * SafeAsyncImage relies on the application-wide ImageLoader (standard certificate validation) for loading images
 * without handshake exceptions. Fallbacks to Shimmer on loading and CinemaPlaceholder on error.
 */
@Composable
fun SafeAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showProgress: Boolean = false
) {
    val context = LocalContext.current
    
    // Check if the model is empty or null, if so direct fallback to error state
    val isUrlInvalid = when (model) {
        is String -> model.trim().isEmpty()
        null -> true
        else -> false
    }

    if (isUrlInvalid) {
        CinemaPlaceholder(modifier = modifier)
    } else {
        val effectiveModel = model

        val imageRequest = androidx.compose.runtime.remember(effectiveModel, contentScale) {
            ImageRequest.Builder(context)
                .data(effectiveModel)
                .crossfade(150)
                .allowHardware(true)
                .scale(if (contentScale == ContentScale.Crop) coil.size.Scale.FILL else coil.size.Scale.FIT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }

        val painter = rememberAsyncImagePainter(model = imageRequest)
        val painterState = painter.state

        Box(modifier = modifier) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
            when (painterState) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(shimmerBrush())
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    CinemaPlaceholder(modifier = Modifier.fillMaxSize())
                }
                else -> { }
            }
        }
    }
}
