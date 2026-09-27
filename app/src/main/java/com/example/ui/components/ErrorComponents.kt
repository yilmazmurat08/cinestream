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
import androidx.compose.ui.res.stringResource
import com.example.R

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
                            text = error.localizedTitle(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = error.localizedMessage(),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_ok), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Hata bandı başlığı, uygulamada seçilen dilde (TR/EN). */
@Composable
private fun AppError.localizedTitle(): String = when (this) {
    is AppError.NoInternetConnection -> stringResource(R.string.error_title_no_internet)
    is AppError.Timeout -> stringResource(R.string.error_title_timeout)
    is AppError.Unauthorized -> stringResource(R.string.error_title_unauthorized)
    is AppError.ServerError -> stringResource(R.string.error_title_server, code)
    is AppError.ParsingError -> stringResource(R.string.error_title_parsing)
    is AppError.NotFound -> stringResource(R.string.error_title_not_found)
    is AppError.OutOfMemoryOrCorruptedData -> stringResource(R.string.error_title_memory)
    is AppError.Unknown -> stringResource(R.string.error_title_unknown)
}

/**
 * Hata bandı mesajı, seçilen dilde. ErrorHandlingManager'ın bilinen (varsayılan) Türkçe mesajları
 * kaynaklardaki TR/EN karşılığıyla gösterilir; önceden çevrilmiş ya da sunucudan gelen özel mesajlar
 * olduğu gibi kalır.
 */
@Composable
private fun AppError.localizedMessage(): String {
    val message = userFriendlyMessage
    val serverCode = (this as? AppError.ServerError)?.code ?: 0
    val known: Map<String, @Composable () -> String> = mapOf(
        AppError.NoInternetConnection().userFriendlyMessage to { stringResource(R.string.error_no_internet) },
        AppError.Timeout().userFriendlyMessage to { stringResource(R.string.error_timeout) },
        AppError.ServerError(serverCode).userFriendlyMessage to { stringResource(R.string.error_server, serverCode) },
        "Sunucu geçici olarak hizmet veremiyor (Kod: $serverCode)." to { stringResource(R.string.error_server_unavailable, serverCode) },
        AppError.ParsingError().userFriendlyMessage to { stringResource(R.string.error_parsing) },
        AppError.Unauthorized().userFriendlyMessage to { stringResource(R.string.error_unauthorized) },
        AppError.NotFound().userFriendlyMessage to { stringResource(R.string.error_not_found) },
        AppError.OutOfMemoryOrCorruptedData().userFriendlyMessage to { stringResource(R.string.error_memory_or_data) },
        "Cihaz belleği doldu. Önbellek optimize edilerek güvenle sıfırlandı." to { stringResource(R.string.error_memory_full) },
        "Veritabanı erişiminde hata oluştu. Veriler güvenle onarılıyor." to { stringResource(R.string.error_database) },
        "Veriler bozulmuş veya eksik. Lütfen listeyi yenileyin." to { stringResource(R.string.error_data_corrupt) },
        AppError.Unknown().userFriendlyMessage to { stringResource(R.string.error_unknown) },
        "Beklenmeyen bir hata oluştu." to { stringResource(R.string.error_unknown) }
    )
    known[message]?.let { return it() }
    val connectionPrefix = "Bağlantı hatası: "
    if (message.startsWith(connectionPrefix)) {
        val detail = message.removePrefix(connectionPrefix).let {
            if (it == "Sunucu erişilemiyor") stringResource(R.string.error_server_unreachable) else it
        }
        return stringResource(R.string.error_connection, detail)
    }
    return message
}
