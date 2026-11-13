package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R

private val Accent = Color(0xFFD946EF)

/** Kumandanın rakam tuşu (yoksa -1). */
internal fun goToMinuteDigit(key: Key): Int = when (key) {
    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3; Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7; Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9; else -> -1
}

/** Yazılan dakikaya rakam ekler: en çok 3 hane, baştaki sıfırlar atılır. */
internal fun goToMinuteAppend(typed: String, digit: Int): String =
    if (typed.length >= 3) typed else (typed + digit).trimStart('0').ifEmpty { "0" }

/** Girilen dakika geçerli mi: 0..yapımın süresi (dakika). */
internal fun goToMinuteValid(minutes: Int?, durationSec: Long): Boolean =
    minutes != null && minutes >= 0 && minutes * 60L < durationSec.coerceAtLeast(1L)

/**
 * "Dakikaya git": rakamla dakika yazılır (ör. 65), yapım o dakikaya gider. Ekrandaki tuş takımı dokunarak ya da
 * kumandayla (yön tuşları + OK) kullanılır; rakamlı kumandada rakam tuşları da çalışır (Mi TV Stick'te rakam yok).
 */
@Composable
fun GoToMinuteDialog(durationSec: Long, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val minutes = typed.toIntOrNull()
    val totalMin = (durationSec / 60).toInt()
    val valid = goToMinuteValid(minutes, durationSec)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    fun type(d: Int) { typed = goToMinuteAppend(typed, d) }
    fun go() { if (valid) { onGo(minutes!!); onDismiss() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF15101F))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = goToMinuteDigit(e.key)
                    when {
                        digit >= 0 -> { type(digit); true }
                        e.key == Key.Backspace || e.key == Key.Delete -> { typed = typed.dropLast(1); true }
                        else -> false
                    }
                }
                .padding(20.dp)
                .testTag("go_to_minute_dialog")
        ) {
            Text(stringResource(R.string.player_go_to_minute), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.player_go_to_minute_total, totalMin),
                color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    typed.ifEmpty { "–" }, color = if (typed.isEmpty() || valid) Color.White else Color(0xFFFF8A80),
                    fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("go_to_minute_value")
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.player_go_to_minute_unit), color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            if (typed.isNotEmpty() && !valid) {
                Text(stringResource(R.string.player_go_to_minute_too_far, totalMin), color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEachIndexed { c, label ->
                        val isGo = label == "OK"
                        PadKey(
                            label = if (isGo) stringResource(R.string.player_go_to_minute_go) else label,
                            highlighted = isGo && valid,
                            enabled = !isGo || valid,
                            modifier = if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            tag = "go_key_${if (isGo) "go" else if (label == "⌫") "back" else label}"
                        ) {
                            when (label) {
                                "⌫" -> typed = typed.dropLast(1)
                                "OK" -> go()
                                else -> type(label.toInt())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, highlighted: Boolean, enabled: Boolean, modifier: Modifier, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(80.dp)
            .height(54.dp)
            .clip(shape)
            .background(
                when {
                    highlighted -> Accent
                    focused -> Color.White.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.12f), shape)
            .clickable(interactionSource = interaction, indication = null) { if (enabled) onClick() }
            .testTag(tag)
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.35f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
