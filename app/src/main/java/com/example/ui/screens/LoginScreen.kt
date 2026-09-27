package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CineBlack
import com.example.ui.theme.CineRed
import com.example.ui.theme.CineOrange
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.IPTVViewModel
import com.example.ui.theme.rememberAppAdaptiveLayout

@Composable
fun LoginScreen(
    savedApiKey: String = "",
    viewModel: IPTVViewModel? = null,
    onSaveApiKey: (String) -> Unit = {},
    onLoginSuccess: (email: String, name: String) -> Unit
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { com.example.data.repository.UserPreferences(context) }

    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var playlistUrlInput by remember { mutableStateOf("") }
    var epgUrlInput by remember { mutableStateOf("") }
    
    var xcodeHost by remember { mutableStateOf("") }
    var xcodeUser by remember { mutableStateOf("") }
    var xcodePass by remember { mutableStateOf("") }
    var showXcodePass by remember { mutableStateOf(false) }

    var isApiKeyVisible by remember { mutableStateOf(false) }
    var showXtreamSection by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var syncLogText by remember { mutableStateOf(context.getString(R.string.login_preparing_library)) }
    var showApiHelpSheet by remember { mutableStateOf(false) }

    // Android TV: Gemini anahtarı ve liste bilgileri telefondan QR ile gönderilebilir
    val isTvDevice = remember { com.example.ui.tv.TvDevice.isTv(context) }
    var phoneEntryMode by remember { mutableStateOf<com.example.ui.tv.PhoneEntryMode?>(null) }
    phoneEntryMode?.let { mode ->
        com.example.ui.tv.PhoneEntryDialog(
            mode = mode,
            onDismiss = { phoneEntryMode = null },
            onReceived = { data ->
                if (mode == com.example.ui.tv.PhoneEntryMode.GEMINI_KEY) {
                    val key = data.geminiKey.orEmpty().trim()
                    if (!com.example.ui.tv.PhoneEntryData.looksLikeApiKey(key)) {
                        com.example.ui.tv.PhoneEntryError.INVALID_KEY
                    } else {
                        apiKeyInput = key
                        onSaveApiKey(key)
                        null
                    }
                } else {
                    val m3u = data.m3uUrl.orEmpty().trim()
                    val epg = data.epgUrl.orEmpty().trim()
                    val host = data.xtreamHost.orEmpty().trim()
                    if (m3u.isNotEmpty()) playlistUrlInput = m3u
                    if (epg.isNotEmpty()) epgUrlInput = epg
                    if (host.isNotEmpty()) {
                        xcodeHost = host
                        xcodeUser = data.xtreamUser.orEmpty().trim()
                        xcodePass = data.xtreamPass.orEmpty().trim()
                        showXtreamSection = true
                    }
                    if (m3u.isEmpty() && host.isEmpty()) com.example.ui.tv.PhoneEntryError.EMPTY_PLAYLIST else null
                }
            }
        )
    }

    // Pre-fill existing Gemini key if provided via viewModel
    val savedGeminiKey by viewModel?.geminiApiKey?.collectAsState() ?: remember { mutableStateOf("") }
    val savedManualEpg by viewModel?.manualEpgUrl?.collectAsState() ?: remember { mutableStateOf("") }
    LaunchedEffect(savedGeminiKey) {
        if (savedGeminiKey.isNotBlank() && apiKeyInput.isBlank()) {
            apiKeyInput = savedGeminiKey
        }
    }
    LaunchedEffect(savedManualEpg) {
        if (savedManualEpg.isNotBlank() && epgUrlInput.isBlank()) {
            epgUrlInput = savedManualEpg
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CineBlack)
            .testTag("login_screen")
    ) {
        // Decorative background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CineOrange.copy(alpha = 0.15f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(adaptiveLayout.screenPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Center Branding & Logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // App Logo
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            width = 1.dp,
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(2.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_logo_1782414357652),
                        contentDescription = stringResource(R.string.login_logo_description),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp)),
                        alpha = 0.85f,
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "CineStream",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "IPTV",
                    color = CineOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.login_tagline),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- BÖLÜM 1: YAPAY ZEKA (GEMINI) AYARLARI ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.login_section_ai_title),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            onSaveApiKey(it)
                        },
                        label = { Text("Gemini API Key") },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.login_api_key_placeholder),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isApiKeyVisible) stringResource(R.string.login_hide_key) else stringResource(R.string.login_show_key),
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CineOrange,
                            focusedLabelColor = CineOrange,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_gemini_api_key_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isTvDevice) {
                        com.example.ui.tv.PhoneEntryButton(
                            mode = com.example.ui.tv.PhoneEntryMode.GEMINI_KEY,
                            onClick = { phoneEntryMode = com.example.ui.tv.PhoneEntryMode.GEMINI_KEY }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    TextButton(
                        onClick = { showApiHelpSheet = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = CineOrange),
                        modifier = Modifier.testTag("gemini_help_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.login_how_to_get_key),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- BÖLÜM 2: MEDYA / LISTE AYARLARI ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.login_section_media_title),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isTvDevice) {
                        com.example.ui.tv.PhoneEntryButton(
                            mode = com.example.ui.tv.PhoneEntryMode.PLAYLIST,
                            onClick = { phoneEntryMode = com.example.ui.tv.PhoneEntryMode.PLAYLIST }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = playlistUrlInput,
                        onValueChange = { playlistUrlInput = it },
                        label = { Text(stringResource(R.string.login_playlist_label)) },
                        placeholder = { Text("http://example.com/playlist.m3u", color = Color.White.copy(alpha = 0.3f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CineOrange,
                            focusedLabelColor = CineOrange,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_playlist_url_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = epgUrlInput,
                        onValueChange = { epgUrlInput = it },
                        label = { Text(stringResource(R.string.login_epg_label)) },
                        placeholder = { Text(stringResource(R.string.epg_url_placeholder), color = Color.White.copy(alpha = 0.3f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CineOrange,
                            focusedLabelColor = CineOrange,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_epg_url_input")
                    )

                    TextButton(
                        onClick = { showXtreamSection = !showXtreamSection },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (showXtreamSection) Icons.Default.ExpandLess else Icons.Default.SettingsInputAntenna,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showXtreamSection) stringResource(R.string.login_hide_xtream) else stringResource(R.string.login_show_xtream),
                                fontSize = 12.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showXtreamSection,
                        enter = fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = fadeOut() + androidx.compose.animation.shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = xcodeHost,
                                onValueChange = { xcodeHost = it },
                                label = { Text(stringResource(R.string.login_server_address)) },
                                placeholder = { Text("http://server.com:8080", color = Color.White.copy(alpha = 0.3f)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CineOrange,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = CineOrange,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = xcodeUser,
                                    onValueChange = { xcodeUser = it },
                                    label = { Text(stringResource(R.string.login_username_label)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = CineOrange,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedLabelColor = CineOrange,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = xcodePass,
                                    onValueChange = { xcodePass = it },
                                    label = { Text(stringResource(R.string.login_password_label)) },
                                    singleLine = true,
                                    visualTransformation = if (showXcodePass) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showXcodePass = !showXcodePass }) {
                                            Icon(
                                                imageVector = if (showXcodePass) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = CineOrange,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedLabelColor = CineOrange,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- BOTTOM PRIMARY ACTION: GOOGLE LOGIN & SAVE ---
            if (isLoading) {
                val importProgress by viewModel?.importProgress?.collectAsState()
                    ?: remember { mutableStateOf(emptyMap<String, Int>()) }
                val importFinished by viewModel?.importFinishedSuccessfully?.collectAsState()
                    ?: remember { mutableStateOf(false) }
                val typeLabels = remember {
                    mapOf(
                        "LIVE" to "📡 Canlı Yayın",
                        "MOVIE" to "🎬 Filmler",
                        "SERIES" to "📺 Diziler",
                        "RADIO" to "📻 Radyo"
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    if (importFinished) {
                        Text(
                            text = "✨ Harika! Kütüphanen hazır.",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ana sayfaya yönlendiriliyorsun...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (importProgress.values.any { it > 0 }) {
                        Text(
                            text = syncLogText,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        importProgress.filter { it.value > 0 }.forEach { (type, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = CineOrange,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${typeLabels[type] ?: type}: $count içerik indirildi",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    } else {
                        CircularProgressIndicator(
                            color = CineOrange,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = syncLogText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        syncLogText = context.getString(R.string.login_saving_settings)
                        coroutineScope.launch {
                            val cleanApiKey = apiKeyInput.trim()
                            val cleanPlaylist = playlistUrlInput.trim()
                            val cleanEpg = epgUrlInput.trim()

                            if (cleanApiKey.isNotBlank()) {
                                onSaveApiKey(cleanApiKey)
                                viewModel?.setGeminiApiKey(cleanApiKey)
                            }

                            userPreferences.saveUserSettings(cleanApiKey, cleanPlaylist, cleanEpg)

                            if (cleanEpg.isNotBlank()) {
                                viewModel?.setManualEpgUrl(cleanEpg) { success ->
                                    if (!success) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.epg_url_failed_toast),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }

                            syncLogText = context.getString(R.string.login_preparing_media)

                            if (viewModel != null && (cleanPlaylist.isNotBlank() || (xcodeHost.isNotBlank() && xcodeUser.isNotBlank()))) {
                                viewModel.setupPlaylists(
                                    m3uUrl = cleanPlaylist,
                                    xcodeHost = xcodeHost.trim(),
                                    xcodeUser = xcodeUser.trim(),
                                    xcodePass = xcodePass.trim(),
                                    onFinished = {
                                        val setupError = viewModel.playlistSetupError.value
                                        if (!setupError.isNullOrBlank()) {
                                            isLoading = false
                                            syncLogText = setupError
                                            android.widget.Toast.makeText(
                                                context,
                                                setupError,
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            viewModel.completeSetup()
                                            onLoginSuccess("kullanici@cinestream.local", "CineStream Kullanıcısı")
                                        }
                                    }
                                )
                            } else {
                                viewModel?.completeSetup()
                                onLoginSuccess("kullanici@cinestream.local", "CineStream Kullanıcısı")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_login_button"),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.login_cta_button),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.login_terms_notice),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Gemini API Help Sheet Overlay
        AnimatedVisibility(
            visible = showApiHelpSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            GeminiApiHelpSheet(
                onDismiss = { showApiHelpSheet = false }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GeminiApiHelpSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
            .testTag("gemini_help_overlay")
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF130B24) // Deep luxurious dark purple background
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(enabled = false) {} // Prevent click-through from dismissing
                .testTag("gemini_help_sheet_card")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // Close button top right
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .testTag("gemini_help_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Main content column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 100.dp), // Leaves space for fixed button at bottom
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Apple style drag handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(R.string.login_help_sheet_title),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.login_help_sheet_subtitle),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )

                    // Pager for step-by-step tutorial cards
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("gemini_help_pager")
                    ) { page ->
                        when (page) {
                            0 -> HelpStepCard(
                                title = stringResource(R.string.login_step1_title),
                                description = stringResource(R.string.login_step1_desc),
                                visual = { StepOneVisual() }
                            )
                            1 -> HelpStepCard(
                                title = stringResource(R.string.login_step2_title),
                                description = stringResource(R.string.login_step2_desc),
                                visual = { StepTwoVisual() }
                            )
                            2 -> HelpStepCard(
                                title = stringResource(R.string.login_step3_title),
                                description = stringResource(R.string.login_step3_desc),
                                visual = { StepThreeVisual() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Indicator Dots (Apple Style)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        repeat(3) { index ->
                            val isSelected = pagerState.currentPage == index
                            val width = if (isSelected) 18.dp else 8.dp
                            val color = if (isSelected) CineOrange else Color.White.copy(alpha = 0.25f)
                            Box(
                                modifier = Modifier
                                    .size(width = width, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            )
                        }
                    }
                }

                // FIXED BOTTOM ACCESS BUTTON AREA
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF130B24).copy(alpha = 0.95f),
                                    Color(0xFF130B24)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    val AppleAccentBlue = Color(0xFF007AFF)
                    Button(
                        onClick = {
                            uriHandler.openUri("https://aistudio.google.com")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleAccentBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("open_ai_studio_button"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.login_open_ai_studio_button),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpStepCard(
    title: String,
    description: String,
    visual: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Visual mockup container at top of step card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
                contentAlignment = Alignment.Center
            ) {
                visual()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step text at bottom of step card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.9f)
            ) {
                Text(
                    text = title,
                    color = Color(0xFF130B24),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = description,
                    color = Color(0xFF5E4F75),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StepOneVisual() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFFF3F0F8), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Browser Mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD6CDE6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Three browser dots (Red, Yellow, Green)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFFF5F56), CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFBD2E), CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF27C93F), CircleShape))
                }
                
                Spacer(modifier = Modifier.width(10.dp))
                
                // URL Address bar
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFFF1EEF5), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color(0xFF8E80A9),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "aistudio.google.com",
                        color = Color(0xFF53486B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // "Google ile Giriş Yap" layout with Arrow pointing down
            Row(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF4285F4).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "G",
                    color = Color(0xFF4285F4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Google ile Giriş Yap",
                    color = Color(0xFF5F6368),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = Color(0xFF8E24AA),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun StepTwoVisual() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFFF3F0F8), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFD6CDE6), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            // Header simulated
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Get API Key button on left (glowing blue)
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1A73E8), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF00E676), CircleShape) // glowing green/blue dot
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Get API Key",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Mock avatar
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color(0xFFE0E0E0), CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sub-menu popup pointing down from "Get API Key"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F3F4), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF1A73E8).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = "➕ Create API key in new project",
                    color = Color(0xFF1A73E8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.login_create_key_caption),
                    color = Color.Gray,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
fun StepThreeVisual() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFFF3F0F8), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Simulated key string
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD6CDE6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AIzaSyD_xY78B...k9P0",
                        color = Color(0xFF333333),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Copy button (Apple style)
                Row(
                    modifier = Modifier
                        .background(Color(0xFFF0EBF5), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF8E24AA).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Copy",
                        color = Color(0xFF8E24AA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = stringResource(R.string.login_copied),
                color = Color(0xFF00B16A),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0F0F14)
@Composable
fun LoginScreenPreview() {
    LoginScreen(
        savedApiKey = "",
        onLoginSuccess = { _, _ -> }
    )
}


