package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CineOrange
import com.example.ui.theme.CineRed
import com.example.util.AppError

/**
 * Maps AppError instances to suitable visual icons.
 */
fun AppError.getIcon(): ImageVector {
    return when (this) {
        is AppError.NoInternetConnection -> Icons.Default.SignalWifiOff
        is AppError.Timeout -> Icons.Default.CloudOff
        is AppError.Unauthorized -> Icons.Default.Lock
        is AppError.ServerError -> Icons.Default.ErrorOutline
        is AppError.ParsingError -> Icons.Default.Warning
        else -> Icons.Default.ErrorOutline
    }
}

/**
 * Top animated error banner for displaying network layer errors gracefully.
 */
@Composable
fun ErrorNotificationBanner(
    error: AppError?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = error != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (error != null) {
            Surface(
                color = CineRed,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = error.getIcon(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (error) {
                                is AppError.NoInternetConnection -> "Ağ Bağlantısı Kesildi"
                                is AppError.Timeout -> "Zaman Aşımı"
                                is AppError.Unauthorized -> "Yetkisiz Erişim"
                                is AppError.ServerError -> "Sunucu Hatası (${error.code})"
                                is AppError.ParsingError -> "Veri İşleme Hatası"
                                is AppError.NotFound -> "İçerik Bulunamadı"
                                is AppError.OutOfMemoryOrCorruptedData -> "Bellek & Önbellek Optimize Edildi"
                                is AppError.Unknown -> "Sistem Hatası"
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = error.userFriendlyMessage,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Tamam", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
