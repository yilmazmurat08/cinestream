package com.example.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private fun isOkKey(key: Key) = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Kumanda OK tuşu: kısa basış [onClick], basılı tutma (tuş tekrarı) [onLongPress]. Uzun basıştan sonra tuş bırakılınca
 * [onClick] çalışmaz. [longFired] kartın kendi durumudur (remember ile).
 */
internal fun Modifier.tvOkLongPress(longFired: BooleanArray, onClick: () -> Unit, onLongPress: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        if (!isOkKey(event.key)) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                val native = event.nativeKeyEvent
                if (native.repeatCount == 0 && !native.isLongPress) {
                    longFired[0] = false
                } else if (!longFired[0]) {
                    longFired[0] = true
                    onLongPress()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (!longFired[0]) onClick()
                longFired[0] = false
                true
            }
            else -> false
        }
    }

/** Kart menüsündeki bir seçenek. [primary]: vurgulu (ilk odak bu seçenektedir). */
internal class TvMenuAction(val label: String, val tag: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * Kartta OK'e basılı tutunca açılan küçük menü: kartın adı ve alt alta seçenekler. Basılı tutulan OK tuşu bırakılana
 * kadar (tuş tekrarları ve bırakma) menüde yok sayılır; yanlışlıkla bir seçenek çalışmaz. İlk odak vurgulu seçenekte.
 */
@Composable
internal fun TvItemMenuDialog(
    title: String,
    actions: List<TvMenuAction>,
    onDismiss: () -> Unit,
    tag: String = "tv_item_menu"
) {
    val primaryFocus = remember { FocusRequester() }
    val armed = remember { BooleanArray(1) }
    LaunchedEffect(Unit) { requestFocusWhenReady(primaryFocus) }
    val firstPrimary = actions.indexOfFirst { it.primary }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .tvGlass(color = TvTheme.Background.copy(alpha = 0.96f))
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .onPreviewKeyEvent { event ->
                    if (!isOkKey(event.key) || armed[0]) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        armed[0] = true
                        false
                    } else {
                        true
                    }
                }
                .testTag(tag)
        ) {
            Text(
                title,
                color = TvTheme.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            actions.forEachIndexed { index, action ->
                TvGlassButton(
                    onClick = action.onClick,
                    shape = TvTheme.PillShape,
                    focusRequester = if (index == firstPrimary) primaryFocus else null,
                    glassColor = if (action.primary) TvTheme.Accent.copy(alpha = 0.85f) else TvTheme.Glass,
                    modifier = Modifier.width(300.dp).height(50.dp).testTag(action.tag)
                ) {
                    Text(
                        action.label,
                        color = if (action.primary) TvTheme.TextPrimary else TvTheme.TextSecondary,
                        fontSize = 17.sp,
                        fontWeight = if (action.primary) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
