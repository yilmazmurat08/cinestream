package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R

/**
 * TV poster kartı: 2:3 poster, altında en fazla 2 satır ad. Odaklanınca mor parlayan çerçeveyle %8 büyür.
 * [inLibrary] true ise "Kütüphanende var" etiketi; [dimmed] ise soluk görünür (kişi sayfasında kütüphanede olmayanlar).
 * Poster yoksa adın baş harfi gösterilir (uydurma görsel yok).
 */
@Composable
fun TvPosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    inLibrary: Boolean = false,
    dimmed: Boolean = false,
    subtitle: String? = null,
    tag: String? = null
) {
    Column(modifier = modifier.alpha(if (dimmed) 0.45f else 1f)) {
        TvGlassButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            focusRequester = focusRequester,
            onFocused = onFocused,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        ) {
            var failed by remember(posterUrl) { mutableStateOf(posterUrl.isNullOrBlank()) }
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
                    Icon(Icons.Outlined.Movie, null, tint = TvTheme.Accent.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                    Text(title, color = TvTheme.TextSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            } else {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (inLibrary) {
                Text(
                    stringResource(R.string.tv_in_library),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(TvTheme.PillShape)
                        .background(TvTheme.Accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = TvTheme.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
        if (subtitle != null) {
            Text(subtitle, color = TvTheme.TextMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Kutu genişliğini dolduran yuvarlak baş harf (fotoğrafı olmayan kişi için). */
@Composable
fun TvInitials(name: String, modifier: Modifier = Modifier) {
    val initials = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.background(TvTheme.Accent.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Text(initials.ifEmpty { "?" }, color = TvTheme.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
