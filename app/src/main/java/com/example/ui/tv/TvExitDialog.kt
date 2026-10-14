package com.example.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.ContinueWatching
import com.example.data.model.SeriesParser

/**
 * TV ana sayfasında Geri'ye basınca açılan çıkış onay kartı. Yanlışlıkla çıkışı önler: ilk odak "Kal"
 * düğmesindedir, kart açıkken Geri kartı kapatır. Her açılışta farklı bir tatlı söz gösterilir; yarım kalan
 * bir içerik varsa "Kaldığın yerden devam et" düğmesi de çıkar.
 */
@Composable
fun TvExitDialog(
    continueItem: ContinueWatching?,
    onStay: () -> Unit,
    onContinue: (ContinueWatching) -> Unit,
    onExit: () -> Unit
) {
    val messages = stringArrayResource(R.array.tv_exit_messages)
    val messageIndex = rememberSaveable { messages.indices.random() }
    val stayFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusWhenReady(stayFocus) }

    Dialog(onDismissRequest = onStay) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .tvGlass(color = TvTheme.Background.copy(alpha = 0.96f))
                .padding(horizontal = 32.dp, vertical = 28.dp)
                .testTag("tv_exit_dialog")
        ) {
            Text("🍿", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tv_exit_title),
                color = TvTheme.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                messages[messageIndex.coerceIn(messages.indices)],
                color = TvTheme.TextSecondary,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("tv_exit_message")
            )

            if (continueItem != null) {
                Spacer(Modifier.height(22.dp))
                val episode = remember(continueItem.itemName) { SeriesParser.parseEpisodeInfo(continueItem.itemName) }
                TvGlassButton(
                    onClick = { onContinue(continueItem) },
                    shape = TvTheme.RowShape,
                    focusScale = 1.03f,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tv_exit_continue")
                ) { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = if (f) TvTheme.FocusGlow else TvTheme.Accent,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.tv_exit_continue), color = TvTheme.TextMuted, fontSize = 13.sp)
                            Text(
                                episode?.showTitle?.takeIf { it.isNotBlank() } ?: continueItem.itemName,
                                color = TvTheme.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                TvGlassButton(
                    onClick = onStay,
                    shape = TvTheme.PillShape,
                    focusRequester = stayFocus,
                    glassColor = TvTheme.Accent.copy(alpha = 0.85f),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("tv_exit_stay")
                ) {
                    Text(stringResource(R.string.tv_exit_stay), color = TvTheme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                TvGlassButton(
                    onClick = onExit,
                    shape = TvTheme.PillShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("tv_exit_leave")
                ) {
                    Text(stringResource(R.string.tv_exit_leave), color = TvTheme.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
