package com.example.ui.legal

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.legal.LegalDoc
import com.example.ui.theme.CineOrange

/** Giriş ekranının altındaki iki bağlantı: Hizmet Şartları ve Gizlilik Politikası (dokunma ve kumanda ile açılır). */
@Composable
fun LegalLinks(onOpen: (LegalDoc) -> Unit, modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.75f)) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onOpen(LegalDoc.TERMS) }, modifier = Modifier.testTag("legal_link_terms")) {
            Text(stringResource(R.string.legal_terms), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        }
        Text("·", color = color, fontSize = 13.sp)
        TextButton(onClick = { onOpen(LegalDoc.PRIVACY) }, modifier = Modifier.testTag("legal_link_privacy")) {
            Text(stringResource(R.string.legal_privacy), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
        }
    }
}

/** Ayarlar > Yasal kartı (telefon ve TV ayar panelinde aynı kart). */
@Composable
fun LegalSettingsCard(onOpen: (LegalDoc) -> Unit, modifier: Modifier = Modifier, showTmdbNotice: Boolean = true) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().testTag("legal_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = CineOrange, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.legal_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(R.string.legal_desc), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp)
            )
            LegalRow(Icons.Outlined.Description, stringResource(R.string.legal_terms), "legal_open_terms") { onOpen(LegalDoc.TERMS) }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            LegalRow(Icons.Outlined.PrivacyTip, stringResource(R.string.legal_privacy), "legal_open_privacy") { onOpen(LegalDoc.PRIVACY) }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            LegalRow(Icons.Outlined.SmartDisplay, stringResource(R.string.legal_youtube_terms), "legal_open_youtube_terms") {
                openUrl(context, YOUTUBE_TERMS_URL)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            LegalRow(Icons.Outlined.DeleteForever, stringResource(R.string.legal_delete_all_data), "legal_delete_all_data") {
                confirmDelete = true
            }
            Text(
                stringResource(R.string.app_disclaimer), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 10.dp).testTag("settings_disclaimer")
            )
            if (showTmdbNotice) {
                Text(
                    stringResource(R.string.settings_tmdb_notice), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                stringResource(R.string.legal_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp).testTag("settings_app_version")
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.legal_delete_all_title)) },
            text = { Text(stringResource(R.string.legal_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Tüm uygulama verilerini (listeler, ayarlar, anahtar, geçmiş) siler; sistem uygulamayı kapatır.
                    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.clearApplicationUserData()
                }) { Text(stringResource(R.string.legal_delete_all_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private const val YOUTUBE_TERMS_URL = "https://www.youtube.com/t/terms"

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Tarayıcı yoksa (bazı TV'ler) sessizce geçilir.
    }
}

@Composable
private fun LegalRow(icon: ImageVector, title: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(10.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
