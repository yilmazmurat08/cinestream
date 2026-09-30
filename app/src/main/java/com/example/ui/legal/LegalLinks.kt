package com.example.ui.legal

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
fun LegalSettingsCard(onOpen: (LegalDoc) -> Unit, modifier: Modifier = Modifier) {
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
        }
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
