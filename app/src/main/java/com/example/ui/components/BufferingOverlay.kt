package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AccentNeonPurple
import kotlinx.coroutines.delay

/** Bu süre boyunca yükleniyorsa kullanıcıya bağlantının yavaş olduğu söylenir. */
const val SLOW_BUFFERING_HINT_MS = 4_000L

/**
 * Oynatıcı veri beklerken (yükleniyor / donma) ekranda dönen daire gösterir; uzun sürerse "bağlantı yavaş" notu eklenir.
 * Yoksa kullanıcı görüntünün durduğunu görüp programın duraklatıldığını sanıyordu.
 */
@Composable
fun BufferingOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_BUFFERING_HINT_MS)
        slow = true
    }
    Box(modifier = modifier.testTag("buffering_overlay"), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            CircularProgressIndicator(color = AccentNeonPurple, strokeWidth = 4.dp, modifier = Modifier.size(44.dp))
            Text(
                text = stringResource(if (slow) R.string.player_buffering_slow else R.string.player_buffering),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(if (slow) "buffering_slow_text" else "buffering_text")
            )
        }
    }
}
