package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CineBlack
import com.example.ui.theme.CineRed
import com.example.ui.theme.CineSurface
import com.example.ui.theme.CineSurfaceVariant

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties

@Composable
fun PlaylistAddDialog(
    onDismiss: () -> Unit,
    onM3UAdded: (name: String, content: String) -> Unit,
    onM3UUrlAdded: (name: String, url: String) -> Unit
) {
    val layout = com.example.ui.theme.rememberAppAdaptiveLayout()
    var activeTab by remember { mutableStateOf(0) } // 0: URL Link, 1: Raw Text, 2: Xtream
    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var m3uContent by remember { mutableStateOf("") }
    
    // Xtream states
    var serverHost by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CineSurface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = layout.dialogMaxWidth)
                .heightIn(max = if (layout.isLandscape) 420.dp else 580.dp)
                .padding(vertical = 12.dp)
                .testTag("playlist_add_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "İçerik Ekle",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Tab Selector (Apple style pill segment)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CineSurfaceVariant)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 0) CineRed else Color.Transparent)
                            .clickable { activeTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M3U8 Linki",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 1) CineRed else Color.Transparent)
                            .clickable { activeTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M3U Metin",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 2) CineRed else Color.Transparent)
                            .clickable { activeTab = 2 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Xtream API",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Common name field
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Oynatma Listesi Adı") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineRed,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = CineRed,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (activeTab) {
                    0 -> {
                        // M3U8 Link / URL Field
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = { playlistUrl = it },
                            label = { Text("M3U/M3U8 Liste Linki (URL)") },
                            placeholder = { Text("https://iptv-org.github.io/iptv/countries/tr.m3u") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = CineRed,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("m3u_url_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    1 -> {
                        // M3U Content paste
                        OutlinedTextField(
                            value = m3uContent,
                            onValueChange = { m3uContent = it },
                            label = { Text("M3U Çalma Listesi İçeriği") },
                            placeholder = { Text("#EXTM3U\n#EXTINF:-1 tvg-logo=\"logo.png\" group-title=\"Spor\",Kanal Adı\nhttp://stream_url") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = CineRed,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("m3u_content_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    2 -> {
                        // Xtream Inputs
                        OutlinedTextField(
                            value = serverHost,
                            onValueChange = { serverHost = it },
                            label = { Text("Sunucu Adresi (Host)") },
                            placeholder = { Text("http://example.com:8080") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = CineRed,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("xtream_host_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Kullanıcı") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CineRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = CineRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("xtream_username_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Şifre") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CineRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = CineRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("xtream_password_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Vazgeç", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val name = playlistName.ifEmpty {
                                when (activeTab) {
                                    0 -> "M3U8 Liste Linki"
                                    1 -> "M3U Çalma Listesi"
                                    else -> "Xtream Listesi"
                                }
                            }
                            when (activeTab) {
                                0 -> {
                                    onM3UUrlAdded(name, playlistUrl.trim())
                                }
                                1 -> {
                                    onM3UAdded(name, m3uContent)
                                }
                                2 -> {
                                    val content = "#EXTM3U\n#EXTINF:-1 tvg-logo=\"\" group-title=\"Xtream Canlı\",$name Canlı\n$serverHost/live/$username/$password/1.ts\n#EXTINF:-1 tvg-logo=\"\" group-title=\"Xtream Film\",$name Sinema\n$serverHost/movie/$username/$password/1.mp4"
                                    onM3UAdded(name, content)
                                }
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("playlist_save_button")
                    ) {
                        Text("Yükle", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
