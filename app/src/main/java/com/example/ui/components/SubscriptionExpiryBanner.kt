package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.XtreamAccount
import com.example.util.XtreamAccountInfo
import kotlinx.coroutines.delay

/** Uyarı ekranda bu kadar kalır (TV'de dokunmadan kapanması için). */
private const val BANNER_VISIBLE_MS = 12_000L

/**
 * Abonelik bitiş uyarısı: Xtream hesabının süresi doldu ya da 7 gün içinde doluyorsa ekranın üstünde kısa bir kart.
 * Günde en fazla bir kez gösterilir; 12 sn sonra ya da Kapat ile gizlenir (telefon ve TV).
 */
@Composable
fun SubscriptionExpiryBanner(account: XtreamAccountInfo?, modifier: Modifier = Modifier, now: Long = System.currentTimeMillis()) {
    val context = LocalContext.current
    val today = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now)) }
    val warn = XtreamAccount.shouldWarn(account, now)
    var visible by remember(account?.expiresAtMillis) { mutableStateOf(false) }
    LaunchedEffect(warn, account?.expiresAtMillis) {
        if (!warn || XtreamAccount.dismissedToday(context, today)) return@LaunchedEffect
        visible = true
        XtreamAccount.dismissToday(context, today)
        delay(BANNER_VISIBLE_MS)
        visible = false
    }
    val days = XtreamAccount.daysLeft(account, now)
    val date = account?.expiresAtMillis?.let {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it))
    }.orEmpty()
    AnimatedVisibility(
        visible = visible && days != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color(0xF21A1026),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFD4A853).copy(alpha = 0.7f)),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 520.dp)
                .testTag("subscription_expiry_banner")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFD4A853), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            days == null -> ""
                            days < 0 -> stringResource(R.string.sub_expired, date)
                            days == 0 -> stringResource(R.string.sub_expiry_today)
                            else -> stringResource(R.string.sub_expiry_days, days, date)
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.sub_expiry_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                IconButton(onClick = { visible = false }, modifier = Modifier.testTag("subscription_expiry_close")) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.sub_expiry_close), tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
