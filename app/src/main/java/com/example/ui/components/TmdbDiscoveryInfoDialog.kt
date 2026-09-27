package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CineOrange
import com.example.ui.theme.BrokenWhite
import com.example.ui.theme.SlateGray

@Composable
fun TmdbDiscoveryInfoDialog(
    title: String,
    isSeries: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF160E1E)),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(CineOrange.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalMovies,
                        contentDescription = null,
                        tint = CineOrange,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    color = BrokenWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSeries)
                        "Bu dizi şu anda gündemde ve popüler, ancak kütüphanende henüz bulunmuyor. IPTV sağlayıcın eklediğinde burada, kendi listende görünecek."
                    else
                        "Bu film şu anda sinemalarda gösteriliyor, henüz kütüphanende değil. IPTV sağlayıcın eklediğinde burada, kendi listende görünecek.",
                    color = SlateGray,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Anladım", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
