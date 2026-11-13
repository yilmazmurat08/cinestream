package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.IPTVViewModel
import com.example.util.AgeRatings

/** Yapımın yaş sınırı (TMDB; önbellekli). Bilinmiyorsa ya da yüklenirken null. */
@Composable
fun rememberAgeRating(viewModel: IPTVViewModel?, title: String, isSeries: Boolean): Int? {
    val context = LocalContext.current
    val age by produceState<Int?>(null, title, isSeries) {
        if (viewModel == null || title.isBlank()) return@produceState
        val tmdbId = runCatching { viewModel.tvTmdbDetails(title, if (isSeries) "SERIES" else "MOVIE")?.tmdbId }.getOrNull()
        if (tmdbId != null && tmdbId > 0) value = AgeRatings.fetch(context, isSeries, tmdbId)
    }
    return age
}

/** Yaş rozeti: "13+"; genel izleyici "Genel". */
@Composable
fun AgeBadge(age: Int, modifier: Modifier = Modifier) {
    val color = when {
        age >= 18 -> Color(0xFFDC2626)
        age >= 13 -> Color(0xFFF59E0B)
        else -> Color(0xFF16A34A)
    }
    Text(
        text = if (age == 0) stringResource(R.string.age_general) else "$age+",
        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .testTag("age_badge")
    )
}

/**
 * Ebeveyn kilidi açık, yaş sınırı seçili ve yapım bu yaşın üstündeyse detayın üstünde kilit: PIN ile açılır
 * (15 dakikalık güvenli oturum), "Geri" detayı kapatır. TV'de odak "PIN ile aç"ta.
 */
@Composable
fun AgeLockGate(viewModel: IPTVViewModel?, age: Int?, tvMode: Boolean = false, onBack: () -> Unit) {
    if (viewModel == null) return
    val limit by AgeRatings.limit.collectAsState()
    val lockOn by viewModel.parentalLock.collectAsState()
    val safe by viewModel.isSafeSessionActive.collectAsState()
    if (!lockOn || safe || !AgeRatings.isBlocked(age, limit)) return
    var askPin by remember { mutableStateOf(false) }
    val unlockFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (tvMode) runCatching { com.example.ui.tv.requestFocusWhenReady(unlockFocus) } }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF20B0814))
                .testTag("age_lock_gate"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 380.dp).padding(24.dp)
            ) {
                Box(
                    Modifier.size(72.dp).background(Color(0x33D946EF), RoundedCornerShape(36.dp))
                        .border(1.dp, Color(0x88D946EF), RoundedCornerShape(36.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD946EF), modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.age_lock_title, age ?: 0), color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.age_lock_body, limit), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.testTag("age_lock_back")) {
                        Text(stringResource(R.string.age_lock_back), color = Color.White)
                    }
                    Button(
                        onClick = { askPin = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD946EF)),
                        modifier = Modifier.focusRequester(unlockFocus).testTag("age_lock_unlock")
                    ) { Text(stringResource(R.string.age_lock_unlock), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (askPin) {
        ParentalPinDialog(viewModel = viewModel, onDismiss = { askPin = false }, onSuccess = { askPin = false })
    }
}
