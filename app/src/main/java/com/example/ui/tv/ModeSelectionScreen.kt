package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.ViewMode
import com.example.ui.theme.CinematicBackgroundGradient

/**
 * İlk açılışta (açılış animasyonundan sonra, giriş ekranından önce) görünüm modu seçimi: "Telefon" ve "TV".
 * Dokunmayla ve kumandayla seçilebilir; cihaz türü otomatik algılanır ve o seçenek hazır odaklı gelir.
 */
@Composable
fun ModeSelectionScreen(onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val detectedTv = remember(context) { TvDevice.isTv(context) }
    val phoneFocus = remember { FocusRequester() }
    val tvFocus = remember { FocusRequester() }

    LaunchedEffect(detectedTv) {
        requestFocusWhenReady(if (detectedTv) tvFocus else phoneFocus)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .background(CinematicBackgroundGradient)
            .testTag("mode_selection_screen"),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val cardSize = (minOf(maxWidth * 0.34f, maxHeight * 0.52f)).coerceIn(140.dp, 300.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.mode_select_title),
                    color = TvTheme.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.mode_select_subtitle),
                    color = TvTheme.TextSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(40.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeCard(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = stringResource(R.string.mode_phone),
                        hint = stringResource(R.string.mode_phone_hint),
                        size = cardSize,
                        focusRequester = phoneFocus,
                        tag = "mode_card_phone",
                        onClick = { onSelect(ViewMode.PHONE) }
                    )
                    ModeCard(
                        icon = Icons.Outlined.Tv,
                        title = stringResource(R.string.mode_tv),
                        hint = stringResource(R.string.mode_tv_hint),
                        size = cardSize,
                        focusRequester = tvFocus,
                        tag = "mode_card_tv",
                        onClick = { onSelect(ViewMode.TV) }
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.mode_select_footer),
                    color = TvTheme.TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    hint: String,
    size: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester,
    tag: String,
    onClick: () -> Unit
) {
    TvGlassButton(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = Modifier
            .size(width = size, height = size * 1.05f)
            .testTag(tag)
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) TvTheme.FocusGlow else TvTheme.Accent,
                modifier = Modifier.size(size * 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(title, color = TvTheme.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(hint, color = TvTheme.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.width(1.dp))
        }
    }
}
