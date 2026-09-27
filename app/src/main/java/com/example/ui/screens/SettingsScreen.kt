package com.example.ui.screens

import android.app.Activity
import com.example.util.findActivity
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.IPTVViewModel
import com.example.data.model.Playlist
import com.example.ui.theme.CineOrange
import com.example.ui.theme.LocalAppTheme
import com.example.ui.theme.AppTheme
import com.example.util.LocaleHelper
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

import com.example.ui.theme.rememberAppAdaptiveLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val context = LocalContext.current
    val currentTheme = LocalAppTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Settings States collected from ViewModel
    val appTheme by viewModel.appTheme.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val currentLanguage by viewModel.appLanguage.collectAsState()
    val currentOrientation by viewModel.screenOrientation.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()
    val hwAccEnabled by viewModel.hardwareAcceleration.collectAsState()
    val selectedBuffer by viewModel.bufferSize.collectAsState()
    val reduceCellularQuality by viewModel.reduceCellularQuality.collectAsState()
    val autoRefreshList by viewModel.autoRefreshList.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()
    val parentalLockEnabled by viewModel.parentalLock.collectAsState()
    val parentalPin by viewModel.parentalPin.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val isValidatingGeminiKey by viewModel.isValidatingGeminiKey.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val manualEpgUrl by viewModel.manualEpgUrl.collectAsState()

    // User Profile State
    val userProfile by viewModel.userProfile.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()
    var showEditProfileDialog by remember { mutableStateOf(false) }

    var isBufferDropdownExpanded by remember { mutableStateOf(false) }
    var pinInputValue by remember { mutableStateOf("") }
    var apiKeyInputValue by remember { mutableStateOf("") }
    var isClearingCache by remember { mutableStateOf(false) }
    var isManualSyncing by remember { mutableStateOf(false) }

    val bufferOptions = listOf("Düşük", "Normal", "Yüksek")
    val subtitleColorOptions = listOf("Beyaz", "Sarı", "Yeşil", "Mavi")

    // Subtitle Color Helper mapping to actual Colors for visual preview
    val colorPreviewMap = mapOf(
        "Beyaz" to Color.White,
        "Sarı" to Color(0xFFFFEB3B),
        "Yeşil" to Color(0xFF4CAF50),
        "Mavi" to Color(0xFF2196F3)
    )

    LaunchedEffect(parentalPin) {
        if (pinInputValue.isEmpty()) {
            pinInputValue = parentalPin
        }
    }

    LaunchedEffect(geminiApiKey) {
        if (apiKeyInputValue.isEmpty()) {
            apiKeyInputValue = geminiApiKey
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen_container")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = adaptiveLayout.screenPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Text(
                text = stringResource(id = R.string.settings_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.testTag("settings_screen_title")
            )
            
            Text(
                text = stringResource(id = R.string.settings_subtitle),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // PROFIL ÜST ALANI (Google Auth Kullanıcı Profil Kartı)
            AccountHeaderComponent(
                displayName = userProfile?.displayName ?: userName ?: "Kullanıcı",
                email = userProfile?.email ?: userEmail ?: "",
                photoUrl = userProfile?.photoUrl ?: userPhotoUrl,
                onEditClick = { showEditProfileDialog = true },
                modifier = Modifier.padding(bottom = 20.dp)
            )

            if (isLandscape) {
                // Side-by-side grid layout for Landscape
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CineStreamProUpgradeCard(
                            isProUser = isProUser,
                            onOpenPaywall = { viewModel.openPaywall() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 5. IPTV LİSTE YÖNETİMİ CARD
                        IPTVPlaylistsCard(
                            playlists = playlists,
                            isLoading = isLoading,
                            onAddM3UPlaylist = { name, url ->
                                viewModel.loadM3UPlaylistFromUrl(name, url)
                                Toast.makeText(context, context.getString(R.string.settings_toast_adding_playlist), Toast.LENGTH_SHORT).show()
                            },
                            onAddXtreamPlaylist = { name, serverUrl, username, password ->
                                viewModel.loadXtreamPlaylist(name, serverUrl, username, password)
                                Toast.makeText(context, context.getString(R.string.settings_toast_adding_xtream), Toast.LENGTH_SHORT).show()
                            },
                            onDeletePlaylist = { id ->
                                viewModel.deletePlaylist(id)
                                Toast.makeText(context, context.getString(R.string.settings_toast_deleted), Toast.LENGTH_SHORT).show()
                            },
                            onRefreshPlaylist = { playlist ->
                                viewModel.refreshSinglePlaylist(playlist) {
                                    Toast.makeText(context, context.getString(R.string.settings_toast_refreshed), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEditPlaylist = { playlist, newName, newUrl, isXtream, username, password ->
                                viewModel.updatePlaylistDetails(playlist, newName, newUrl, isXtream, username, password)
                                Toast.makeText(context, context.getString(R.string.settings_toast_updated), Toast.LENGTH_SHORT).show()
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // MANUEL EPG CARD
                        ManualEpgCard(
                            currentEpgUrl = manualEpgUrl,
                            onSaveEpgUrl = { url ->
                                Toast.makeText(context, context.getString(R.string.epg_url_saved_toast), Toast.LENGTH_SHORT).show()
                                viewModel.setManualEpgUrl(url) { success ->
                                    if (!success) {
                                        Toast.makeText(context, context.getString(R.string.epg_url_failed_toast), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 0. DİL SEÇİMİ CARD
                        LanguageSettingsCard(
                            currentLanguage = currentLanguage,
                            onLanguageSelect = { newLang ->
                                if (newLang != currentLanguage) {
                                    viewModel.setAppLanguage(newLang)
                                    LocaleHelper.setLocale(context, newLang)
                                    context.findActivity()?.recreate()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // UYGULAMA TEMASI CARD
                        ThemeSettingsCard(
                            currentTheme = appTheme,
                            onThemeSelect = { viewModel.setAppTheme(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // EKRAN YÖNLENDİRMESİ CARD
                        ScreenOrientationCard(
                            currentOrientation = currentOrientation,
                            onOrientationSelect = { viewModel.setScreenOrientation(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 1. OYNATICI AYARLARI CARD
                        PlayerSettingsCard(
                            hwAccEnabled = hwAccEnabled,
                            onHwAccChange = { viewModel.setHardwareAcceleration(it) },
                            selectedBuffer = selectedBuffer,
                            isDropdownExpanded = isBufferDropdownExpanded,
                            onDropdownToggle = { isBufferDropdownExpanded = it },
                            bufferOptions = bufferOptions,
                            onBufferSelect = { viewModel.setBufferSize(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. VERİ VE HAFIZA CARD
                        DataAndMemoryCard(
                            reduceCellularQuality = reduceCellularQuality,
                            onReduceCellularChange = { viewModel.setReduceCellularQuality(it) },
                            autoRefreshList = autoRefreshList,
                            onAutoRefreshChange = { viewModel.setAutoRefreshList(it) },
                            isClearingCache = isClearingCache,
                            onClearCache = {
                                isClearingCache = true
                                viewModel.clearAppCache {
                                    isClearingCache = false
                                    Toast.makeText(context, context.getString(R.string.settings_toast_cache_cleared), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        // 3. ALTYAZI ÖZELLEŞTİRME CARD
                        SubtitlesCard(
                            subtitleSize = subtitleSize,
                            onSizeChange = { viewModel.setSubtitleSize(it) },
                            selectedColor = subtitleColor,
                            colorOptions = subtitleColorOptions,
                            colorMap = colorPreviewMap,
                            onColorSelect = { viewModel.setSubtitleColor(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. GÜVENLİK CARD
                        SecurityCard(
                            parentalLockEnabled = parentalLockEnabled,
                            onLockChange = { viewModel.setParentalLock(it) },
                            pinValue = pinInputValue,
                            onPinChange = {
                                if (it.length <= 4) {
                                    pinInputValue = it
                                    viewModel.setParentalPin(it)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GeminiApiKeyCard(
                            apiKey = apiKeyInputValue,
                            isValidating = isValidatingGeminiKey,
                            onApiKeyChange = {
                                apiKeyInputValue = it
                            },
                            onValidateClick = {
                                viewModel.validateAndSaveGeminiApiKey(apiKeyInputValue) { _, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            } else {
                // Single-column portrait layout
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CineStreamProUpgradeCard(
                        isProUser = isProUser,
                        onOpenPaywall = { viewModel.openPaywall() }
                    )

                    // OYNATMA LİSTELERİ CARD
                    IPTVPlaylistsCard(
                        playlists = playlists,
                        isLoading = isLoading,
                        onAddM3UPlaylist = { name, url ->
                            viewModel.loadM3UPlaylistFromUrl(name, url)
                            Toast.makeText(context, context.getString(R.string.settings_toast_adding_playlist), Toast.LENGTH_SHORT).show()
                        },
                        onAddXtreamPlaylist = { name, serverUrl, username, password ->
                            viewModel.loadXtreamPlaylist(name, serverUrl, username, password)
                            Toast.makeText(context, context.getString(R.string.settings_toast_adding_xtream), Toast.LENGTH_SHORT).show()
                        },
                        onDeletePlaylist = { id ->
                            viewModel.deletePlaylist(id)
                            Toast.makeText(context, context.getString(R.string.settings_toast_deleted), Toast.LENGTH_SHORT).show()
                        },
                        onRefreshPlaylist = { playlist ->
                            viewModel.refreshSinglePlaylist(playlist) {
                                Toast.makeText(context, context.getString(R.string.settings_toast_refreshed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEditPlaylist = { playlist, newName, newUrl, isXtream, username, password ->
                            viewModel.updatePlaylistDetails(playlist, newName, newUrl, isXtream, username, password)
                            Toast.makeText(context, context.getString(R.string.settings_toast_updated), Toast.LENGTH_SHORT).show()
                        }
                    )

                    // MANUEL EPG CARD
                    ManualEpgCard(
                        currentEpgUrl = manualEpgUrl,
                        onSaveEpgUrl = { url ->
                            Toast.makeText(context, context.getString(R.string.epg_url_saved_toast), Toast.LENGTH_SHORT).show()
                            viewModel.setManualEpgUrl(url) { success ->
                                if (!success) {
                                    Toast.makeText(context, context.getString(R.string.epg_url_failed_toast), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    LanguageSettingsCard(
                        currentLanguage = currentLanguage,
                        onLanguageSelect = { newLang ->
                            if (newLang != currentLanguage) {
                                viewModel.setAppLanguage(newLang)
                                LocaleHelper.setLocale(context, newLang)
                                context.findActivity()?.recreate()
                            }
                        }
                    )

                    ThemeSettingsCard(
                        currentTheme = appTheme,
                        onThemeSelect = { viewModel.setAppTheme(it) }
                    )

                    ScreenOrientationCard(
                        currentOrientation = currentOrientation,
                        onOrientationSelect = { viewModel.setScreenOrientation(it) }
                    )

                    PlayerSettingsCard(
                        hwAccEnabled = hwAccEnabled,
                        onHwAccChange = { viewModel.setHardwareAcceleration(it) },
                        selectedBuffer = selectedBuffer,
                        isDropdownExpanded = isBufferDropdownExpanded,
                        onDropdownToggle = { isBufferDropdownExpanded = it },
                        bufferOptions = bufferOptions,
                        onBufferSelect = { viewModel.setBufferSize(it) }
                    )

                    DataAndMemoryCard(
                        reduceCellularQuality = reduceCellularQuality,
                        onReduceCellularChange = { viewModel.setReduceCellularQuality(it) },
                        autoRefreshList = autoRefreshList,
                        onAutoRefreshChange = { viewModel.setAutoRefreshList(it) },
                        isClearingCache = isClearingCache,
                        onClearCache = {
                            isClearingCache = true
                            viewModel.clearAppCache {
                                isClearingCache = false
                                Toast.makeText(context, context.getString(R.string.settings_toast_cache_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    SubtitlesCard(
                        subtitleSize = subtitleSize,
                        onSizeChange = { viewModel.setSubtitleSize(it) },
                        selectedColor = subtitleColor,
                        colorOptions = subtitleColorOptions,
                        colorMap = colorPreviewMap,
                        onColorSelect = { viewModel.setSubtitleColor(it) }
                    )

                    SecurityCard(
                        parentalLockEnabled = parentalLockEnabled,
                        onLockChange = { viewModel.setParentalLock(it) },
                        pinValue = pinInputValue,
                        onPinChange = {
                            if (it.length <= 4) {
                                pinInputValue = it
                                viewModel.setParentalPin(it)
                            }
                        }
                    )

                    GeminiApiKeyCard(
                        apiKey = apiKeyInputValue,
                        isValidating = isValidatingGeminiKey,
                        onApiKeyChange = {
                            apiKeyInputValue = it
                        },
                        onValidateClick = {
                            viewModel.validateAndSaveGeminiApiKey(apiKeyInputValue) { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(120.dp))
        }

        if (showEditProfileDialog) {
            EditProfileDialog(
                currentName = userProfile?.displayName ?: userName ?: "Kullanıcı",
                currentEmail = userProfile?.email ?: userEmail ?: "",
                currentPhotoUrl = userProfile?.photoUrl ?: userPhotoUrl,
                onDismiss = { showEditProfileDialog = false },
                onSave = { newName, newEmail, newPhoto ->
                    viewModel.updateUserProfile(newName, newEmail, newPhoto)
                },
                onLogout = {
                    viewModel.logout()
                }
            )
        }
    }
}

// ---------------------- Sub-Composables for Settings Cards ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsCard(
    hwAccEnabled: Boolean,
    onHwAccChange: (Boolean) -> Unit,
    selectedBuffer: String,
    isDropdownExpanded: Boolean,
    onDropdownToggle: (Boolean) -> Unit,
    bufferOptions: List<String>,
    onBufferSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("player_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_player),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Hardware Acceleration Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_hw_accel),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_hw_accel_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = hwAccEnabled,
                    onCheckedChange = onHwAccChange,
                    modifier = Modifier.testTag("hardware_acc_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CineOrange
                    )
                )
            }

            // Custom bulletproof divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(vertical = 12.dp)
            )

            // Buffer Size Dropdown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_buffer),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_buffer_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Box {
                    Button(
                        onClick = { onDropdownToggle(true) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("buffer_size_dropdown_button")
                    ) {
                        Text(text = settingsOptionLabel(selectedBuffer), color = MaterialTheme.colorScheme.onSurface)
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.settings_select_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { onDropdownToggle(false) }
                    ) {
                        bufferOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = settingsOptionLabel(option)) },
                                onClick = {
                                    onBufferSelect(option)
                                    onDropdownToggle(false)
                                },
                                modifier = Modifier.testTag("buffer_option_$option")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataAndMemoryCard(
    reduceCellularQuality: Boolean,
    onReduceCellularChange: (Boolean) -> Unit,
    autoRefreshList: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    isClearingCache: Boolean,
    onClearCache: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_and_memory_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_data_storage),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Reduce Quality on Cellular
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_cellular_quality),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_cellular_quality_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = reduceCellularQuality,
                    onCheckedChange = onReduceCellularChange,
                    modifier = Modifier.testTag("reduce_cellular_quality_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CineOrange
                    )
                )
            }

            // Custom bulletproof divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(vertical = 12.dp)
            )

            // Auto Refresh List
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_auto_refresh),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_auto_refresh_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = autoRefreshList,
                    onCheckedChange = onAutoRefreshChange,
                    modifier = Modifier.testTag("auto_refresh_list_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CineOrange
                    )
                )
            }

            // Custom bulletproof divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(vertical = 12.dp)
            )

            // Clear Cache Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_cache),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_cache_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("clear_cache_button"),
                    enabled = !isClearingCache
                ) {
                    if (isClearingCache) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(text = stringResource(R.string.settings_clear), color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            var showCrashLogsDialog by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { showCrashLogsDialog = true }
                    .testTag("view_crash_logs_row")
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_crash_logs_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_crash_logs_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showCrashLogsDialog) {
                CrashLogsDialog(onDismiss = { showCrashLogsDialog = false })
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_about),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tmdb_logo),
                    contentDescription = "TMDB",
                    modifier = Modifier.height(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_tmdb_notice),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun CrashLogsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var logFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var selectedLogContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        logFiles = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = java.io.File(context.filesDir, "crash_logs")
                dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF16122C),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLogContent != null) stringResource(R.string.settings_crash_detail) else stringResource(R.string.settings_crash_logs),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        if (selectedLogContent != null) selectedLogContent = null else onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.settings_close_desc), tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp), color = CineOrange)
                    }
                    selectedLogContent != null -> {
                        Column {
                            androidx.compose.foundation.rememberScrollState().let { scrollState ->
                                Text(
                                    text = selectedLogContent ?: "",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 340.dp)
                                        .verticalScroll(scrollState)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(selectedLogContent ?: ""))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_copy), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    logFiles.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.settings_no_crash_logs),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(logFiles, key = { it.name }) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable {
                                            selectedLogContent = try {
                                                file.readText()
                                            } catch (e: Exception) {
                                                context.getString(R.string.settings_read_error, e.message ?: "")
                                            }
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = file.name,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitlesCard(
    subtitleSize: Int,
    onSizeChange: (Int) -> Unit,
    selectedColor: String,
    colorOptions: List<String>,
    colorMap: Map<String, Color>,
    onColorSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subtitles_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_subtitles),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Subtitle Size Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_subtitle_size),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${subtitleSize}sp",
                        fontWeight = FontWeight.Bold,
                        color = CineOrange,
                        fontSize = 14.sp
                    )
                }
                
                Slider(
                    value = subtitleSize.toFloat(),
                    onValueChange = { onSizeChange(it.toInt()) },
                    valueRange = 12f..28f,
                    steps = 15,
                    modifier = Modifier.testTag("subtitle_size_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = CineOrange,
                        thumbColor = CineOrange
                    )
                )
            }

            // Custom bulletproof divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(vertical = 12.dp)
            )

            // Subtitle Color Choices
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = stringResource(R.string.settings_subtitle_color),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colorOptions.forEach { colorName ->
                        val isSelected = selectedColor == colorName
                        val actualColor = colorMap[colorName] ?: Color.White
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) CineOrange.copy(alpha = 0.15f) 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CineOrange else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onColorSelect(colorName) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("color_option_$colorName")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(actualColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = settingsOptionLabel(colorName),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityCard(
    parentalLockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    pinValue: String,
    onPinChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_security),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Parental Lock Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_pin_protection),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_pin_protection_desc),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = parentalLockEnabled,
                    onCheckedChange = onLockChange,
                    modifier = Modifier.testTag("parental_lock_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CineOrange
                    )
                )
            }

            // Expanding 4-digit PIN Entry block
            AnimatedVisibility(
                visible = parentalLockEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_pin_code),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_pin_code_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        // PIN Input text field
                        OutlinedTextField(
                            value = pinValue,
                            onValueChange = {
                                if (it.all { char -> char.isDigit() }) {
                                    onPinChange(it)
                                }
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            maxLines = 1,
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .width(90.dp)
                                .height(52.dp)
                                .testTag("parental_pin_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CineOrange,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                focusedLabelColor = CineOrange
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IPTVPlaylistsCard(
    playlists: List<Playlist>,
    isLoading: Boolean,
    onAddM3UPlaylist: (String, String) -> Unit,
    onAddXtreamPlaylist: (String, String, String, String) -> Unit,
    onDeletePlaylist: (Int) -> Unit,
    onRefreshPlaylist: (Playlist) -> Unit,
    onEditPlaylist: (Playlist, String, String, Boolean, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.playlists_title).uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("iptv_playlists_card"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1824),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_playlists),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    playlists.forEachIndexed { index, playlist ->
                        PlaylistItemRow(
                            playlist = playlist,
                            isSelected = index == 0,
                            onRefresh = {
                                onRefreshPlaylist(playlist)
                            },
                            onEdit = { p -> editingPlaylist = p },
                            onDelete = { id -> onDeletePlaylist(id) }
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // 'Oynatma Listesi İçe Aktar' Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag("import_playlist_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.settings_add_desc),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(id = R.string.import_playlist),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            onDismiss = { showAddDialog = false },
            onAddM3U = { name, url ->
                onAddM3UPlaylist(name, url)
                showAddDialog = false
            },
            onAddXtream = { name, serverUrl, username, password ->
                onAddXtreamPlaylist(name, serverUrl, username, password)
                showAddDialog = false
            }
        )
    }

    editingPlaylist?.let { playlist ->
        EditPlaylistDialog(
            playlist = playlist,
            onDismiss = { editingPlaylist = null },
            onSave = { p, newName, newUrl, isXtream, username, password ->
                onEditPlaylist(p, newName, newUrl, isXtream, username, password)
                editingPlaylist = null
            }
        )
    }
}

@Composable
fun PlaylistItemRow(
    playlist: Playlist,
    isSelected: Boolean,
    onRefresh: () -> Unit,
    onEdit: (Playlist) -> Unit,
    onDelete: (Int) -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Sol: Seçili durum ikonu
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.settings_selected_desc),
                tint = if (isSelected) Color.White else Color.Transparent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            // Orta: Liste Adı & Tür Rozeti
            Column {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (playlist.isXtream || !playlist.xtreamUsername.isNullOrBlank()) {
                    Text(
                        text = "Xtream Codes",
                        color = CineOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Sağ: Tıklanabilir Üç Nokta ikonu & Pop-up Menu
        Box {
            IconButton(
                onClick = { isMenuExpanded = true },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("playlist_menu_${playlist.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.settings_menu_desc),
                    tint = Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Pop-up Menü (İkonlar SAĞ tarafta)
            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
                modifier = Modifier
                    .background(Color(0xFF282436))
                    .width(180.dp)
            ) {
                // 1. Listeyi Düzenle
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(id = R.string.edit_playlist), color = Color.White, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        isMenuExpanded = false
                        onEdit(playlist)
                    }
                )

                // 2. Listeyi Yenile
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(id = R.string.refresh_playlist), color = Color.White, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        isMenuExpanded = false
                        onRefresh()
                    }
                )

                // 3. Listeyi Sil
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(id = R.string.delete_playlist), color = Color.Red, fontSize = 14.sp)
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        isMenuExpanded = false
                        onDelete(playlist.id)
                    }
                )
            }
        }
    }
}

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAddM3U: (String, String) -> Unit,
    onAddXtream: (String, String, String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = M3U, 1 = Xtream

    // M3U fields
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }

    // Xtream fields
    var serverUrlInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1A29),
        title = {
            Text(text = stringResource(id = R.string.import_playlist), color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    // İçerik küçük/yatay ekranda veya büyük yazı boyutunda sığmazsa kaydırılabilsin.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Android TV: liste bilgilerini telefondan QR ile gönder
                val phoneEntryContext = androidx.compose.ui.platform.LocalContext.current
                if (remember { com.example.ui.tv.TvDevice.isTv(phoneEntryContext) }) {
                    var showPhoneEntry by remember { mutableStateOf(false) }
                    com.example.ui.tv.PhoneEntryButton(
                        mode = com.example.ui.tv.PhoneEntryMode.PLAYLIST,
                        onClick = { showPhoneEntry = true }
                    )
                    if (showPhoneEntry) {
                        com.example.ui.tv.PhoneEntryDialog(
                            mode = com.example.ui.tv.PhoneEntryMode.PLAYLIST,
                            onDismiss = { showPhoneEntry = false },
                            onReceived = { data ->
                                val m3u = data.m3uUrl.orEmpty().trim()
                                val host = data.xtreamHost.orEmpty().trim()
                                when {
                                    host.isNotEmpty() -> {
                                        serverUrlInput = host
                                        usernameInput = data.xtreamUser.orEmpty().trim()
                                        passwordInput = data.xtreamPass.orEmpty().trim()
                                        if (nameInput.isBlank()) nameInput = com.example.ui.tv.PhoneEntryData.defaultName(host)
                                        selectedTab = 1
                                        null
                                    }
                                    m3u.isNotEmpty() -> {
                                        urlInput = m3u
                                        if (nameInput.isBlank()) nameInput = com.example.ui.tv.PhoneEntryData.defaultName(m3u)
                                        selectedTab = 0
                                        null
                                    }
                                    else -> com.example.ui.tv.PhoneEntryError.EMPTY_PLAYLIST
                                }
                            }
                        )
                    }
                }

                // Tab Selection (M3U Link vs Xtream Codes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF282436), shape = RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) CineOrange else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.m3u_link),
                            color = Color.White,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) CineOrange else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.xtream_codes),
                            color = Color.White,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }

                if (selectedTab == 0) {
                    // M3U Link Form
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(id = R.string.playlist_name), color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(id = R.string.playlist_url), color = Color.Gray) },
                        placeholder = { Text("http://example.com/playlist.m3u", color = Color.Gray.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Xtream Codes Form
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(stringResource(id = R.string.playlist_name), color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text(stringResource(id = R.string.server_url), color = Color.Gray) },
                        placeholder = { Text("http://server.com:8080", color = Color.Gray.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(stringResource(id = R.string.username), color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(id = R.string.password), color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(id = R.string.show_password),
                                    tint = Color.Gray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        if (nameInput.isNotBlank() && urlInput.isNotBlank()) {
                            onAddM3U(nameInput.trim(), urlInput.trim())
                        }
                    } else {
                        if (nameInput.isNotBlank() && serverUrlInput.isNotBlank() && usernameInput.isNotBlank() && passwordInput.isNotBlank()) {
                            onAddXtream(nameInput.trim(), serverUrlInput.trim(), usernameInput.trim(), passwordInput.trim())
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineOrange)
            ) {
                Text(stringResource(id = R.string.add), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel), color = Color.Gray)
            }
        }
    )
}

@Composable
fun EditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (playlist: Playlist, newName: String, newUrl: String, isXtream: Boolean, username: String?, password: String?) -> Unit
) {
    val isXtream = playlist.isXtream || !playlist.xtreamUsername.isNullOrBlank()

    var nameInput by remember { mutableStateOf(playlist.name) }
    var urlInput by remember { mutableStateOf(playlist.url) }
    var usernameInput by remember { mutableStateOf(playlist.xtreamUsername ?: "") }
    var passwordInput by remember { mutableStateOf(playlist.xtreamPassword ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1A29),
        title = {
            Text(
                text = stringResource(id = if (isXtream) R.string.edit_xtream_playlist else R.string.edit_m3u_playlist),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    // İçerik küçük/yatay ekranda veya büyük yazı boyutunda sığmazsa kaydırılabilsin.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(id = R.string.playlist_name), color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isXtream) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(id = R.string.playlist_url), color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(id = R.string.server_url), color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(stringResource(id = R.string.username), color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(id = R.string.password), color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(id = R.string.show_password),
                                    tint = Color.Gray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CineOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank() && urlInput.isNotBlank()) {
                        onSave(
                            playlist,
                            nameInput.trim(),
                            urlInput.trim(),
                            isXtream,
                            if (isXtream) usernameInput.trim() else null,
                            if (isXtream) passwordInput.trim() else null
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineOrange)
            ) {
                Text(stringResource(id = R.string.save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel), color = Color.Gray)
            }
        }
    )
}



@Composable
fun LanguageSettingsCard(
    currentLanguage: String,
    onLanguageSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("language_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.app_language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(id = R.string.app_language_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LanguageOptionItem(
                    label = stringResource(id = R.string.language_tr),
                    flagEmoji = "🇹🇷",
                    isSelected = currentLanguage == "tr",
                    onClick = { onLanguageSelect("tr") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("language_option_tr")
                )

                LanguageOptionItem(
                    label = stringResource(id = R.string.language_en),
                    flagEmoji = "🇬🇧",
                    isSelected = currentLanguage == "en",
                    onClick = { onLanguageSelect("en") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("language_option_en")
                )
            }
        }
    }
}

@Composable
fun LanguageOptionItem(
    label: String,
    flagEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CineOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        ),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Text(text = flagEmoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CineStreamProUpgradeCard(
    isProUser: Boolean,
    onOpenPaywall: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B113B)
        ),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                listOf(CineOrange, Color(0xFFFF007A), Color(0xFF9D00FF))
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pro_upgrade_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(CineOrange, Color(0xFFFF007A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "PRO",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CineStream PRO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isProUser) "Sınırsız PRO Erişim Aktif" else "Abonelik ve Ayrıcalıklar",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Surface(
                    color = if (isProUser) Color(0xFF00E676).copy(alpha = 0.2f) else CineOrange.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isProUser) Color(0xFF00E676) else CineOrange)
                ) {
                    Text(
                        text = if (isProUser) "PRO AKTİF ✅" else "ÜCRETSİZ SÜRÜM",
                        color = if (isProUser) Color(0xFF00E676) else CineOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProPointItem("Sınırsız Canlı TV, Film ve Dizi İzleme")
                ProPointItem("AI Destekli Bölüm Özetleri & Trend Vitrini")
                ProPointItem("Sınırsız Radyo & Reklamsız Deneyim")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenPaywall,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Unspecified),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.horizontalGradient(listOf(CineOrange, Color(0xFFFF007A))),
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("open_paywall_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isProUser) "Abonelik Detayları & Paketler" else "CineStream PRO'ya Yükselt",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProPointItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00E676),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun ScreenOrientationCard(
    currentOrientation: String,
    onOrientationSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_orientation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.screen_orientation),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(id = R.string.screen_orientation_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrientationOptionItem(
                    label = stringResource(id = R.string.orientation_auto),
                    iconEmoji = "📱",
                    isSelected = currentOrientation == "AUTO" || currentOrientation == "UNSPECIFIED",
                    onClick = { onOrientationSelect("AUTO") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("orientation_option_auto")
                )

                OrientationOptionItem(
                    label = stringResource(id = R.string.orientation_portrait),
                    iconEmoji = "📲",
                    isSelected = currentOrientation == "PORTRAIT",
                    onClick = { onOrientationSelect("PORTRAIT") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("orientation_option_portrait")
                )

                OrientationOptionItem(
                    label = stringResource(id = R.string.orientation_landscape),
                    iconEmoji = "📺",
                    isSelected = currentOrientation == "LANDSCAPE",
                    onClick = { onOrientationSelect("LANDSCAPE") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("orientation_option_landscape")
                )
            }
        }
    }
}

@Composable
fun OrientationOptionItem(
    label: String,
    iconEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CineOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Text(text = iconEmoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}



/**
 * Account / Profile Header Component
 * Renders user profile picture, display name, email address, and an edit button.
 */
@Composable
fun AccountHeaderComponent(
    displayName: String?,
    email: String?,
    photoUrl: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("account_header_component"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF16131E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Profile Photo (90.dp, clip CircleShape, border, ContentScale.Crop)
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .border(
                        border = BorderStroke(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF9C27B0), Color(0xFF3F51B5), CineOrange)
                            )
                        ),
                        shape = CircleShape
                    )
                    .background(Color(0xFF231C2E)),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.settings_profile_photo_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.settings_default_profile_desc),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Kullanıcı Ad-Soyadı
            Text(
                text = if (!displayName.isNullOrBlank()) displayName else stringResource(R.string.settings_default_user),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            // E-posta Adresi
            if (!email.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = email,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 'Düzenle' Butonu (koyu gri Color(0xFF2C2C2E), rounded)
            Surface(
                onClick = onEditClick,
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF2C2C2E),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.testTag("edit_profile_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.settings_edit_desc),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_edit),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    currentPhotoUrl: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var nameInput by remember { mutableStateOf(currentName) }
    var emailInput by remember { mutableStateOf(currentEmail) }
    var photoInput by remember { mutableStateOf(currentPhotoUrl ?: "") }
    var isCopyingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopyingPhoto = true
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val fileName = "profile_photo_${System.currentTimeMillis()}.jpg"
                        val destFile = java.io.File(context.filesDir, fileName)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        Uri.fromFile(destFile).toString()
                    } catch (e: Exception) {
                        null
                    }
                }
                isCopyingPhoto = false
                if (savedPath != null) {
                    photoInput = savedPath
                } else {
                    Toast.makeText(context, context.getString(R.string.settings_toast_photo_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1A29),
        title = {
            Text(
                text = stringResource(R.string.settings_edit_profile),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    // İçerik küçük/yatay ekranda veya büyük yazı boyutunda sığmazsa kaydırılabilsin.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.settings_full_name), color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(stringResource(R.string.settings_email), color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = photoInput,
                    onValueChange = { photoInput = it },
                    label = { Text(stringResource(R.string.settings_photo_url), color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CineOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isCopyingPhoto,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCopyingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CineOrange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_loading), color = CineOrange)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_pick_photo), color = CineOrange, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679).copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color(0xFFCF6679)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = Color(0xFFCF6679),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sign_out), color = Color(0xFFCF6679), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(nameInput, emailInput, photoInput.ifBlank { null })
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.settings_save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = Color.Gray)
            }
        }
    )
}

@Composable
fun ThemeSettingsCard(
    currentTheme: AppTheme,
    onThemeSelect: (AppTheme) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_settings_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.app_theme),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(id = R.string.app_theme_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOptionItem(
                    label = stringResource(id = R.string.theme_system),
                    iconVector = Icons.Default.BrightnessAuto,
                    isSelected = currentTheme == AppTheme.SYSTEM,
                    onClick = { onThemeSelect(AppTheme.SYSTEM) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_option_system")
                )

                ThemeOptionItem(
                    label = stringResource(id = R.string.theme_dark),
                    iconVector = Icons.Default.DarkMode,
                    isSelected = currentTheme == AppTheme.PURE_BLACK,
                    onClick = { onThemeSelect(AppTheme.PURE_BLACK) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_option_dark")
                )

                ThemeOptionItem(
                    label = stringResource(id = R.string.theme_light),
                    iconVector = Icons.Default.LightMode,
                    isSelected = currentTheme == AppTheme.SYSTEM_LIGHT,
                    onClick = { onThemeSelect(AppTheme.SYSTEM_LIGHT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_option_light")
                )
            }
        }
    }
}

@Composable
fun ThemeOptionItem(
    label: String,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CineOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) CineOrange else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun GeminiApiKeyCard(
    apiKey: String,
    isValidating: Boolean,
    onApiKeyChange: (String) -> Unit,
    onValidateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gemini_api_key_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.gemini_ai_settings),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(id = R.string.gemini_ai_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIzaSy...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CineOrange,
                    focusedLabelColor = CineOrange,
                    cursorColor = CineOrange
                )
            )

            // Android TV: anahtarı telefondan QR ile gönder
            val phoneEntryContext = androidx.compose.ui.platform.LocalContext.current
            if (remember { com.example.ui.tv.TvDevice.isTv(phoneEntryContext) }) {
                var showPhoneEntry by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(10.dp))
                com.example.ui.tv.PhoneEntryButton(
                    mode = com.example.ui.tv.PhoneEntryMode.GEMINI_KEY,
                    onClick = { showPhoneEntry = true }
                )
                if (showPhoneEntry) {
                    com.example.ui.tv.PhoneEntryDialog(
                        mode = com.example.ui.tv.PhoneEntryMode.GEMINI_KEY,
                        onDismiss = { showPhoneEntry = false },
                        onReceived = { data ->
                            val key = data.geminiKey.orEmpty().trim()
                            if (!com.example.ui.tv.PhoneEntryData.looksLikeApiKey(key)) {
                                com.example.ui.tv.PhoneEntryError.INVALID_KEY
                            } else {
                                onApiKeyChange(key)
                                onValidateClick()
                                null
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onValidateClick,
                enabled = !isValidating && apiKey.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CineOrange,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_validate_button")
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_verifying), fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_verify_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ManualEpgCard(
    currentEpgUrl: String,
    onSaveEpgUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var epgUrlInput by remember(currentEpgUrl) { mutableStateOf(currentEpgUrl) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.settings_manual_epg_title).uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_epg_card"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1B1824),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_manual_epg_description),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                OutlinedTextField(
                    value = epgUrlInput,
                    onValueChange = { epgUrlInput = it },
                    label = { Text(stringResource(id = R.string.login_epg_label)) },
                    placeholder = { Text(stringResource(id = R.string.epg_url_placeholder), color = Color.White.copy(alpha = 0.3f)) },
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
                        .testTag("settings_manual_epg_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onSaveEpgUrl(epgUrlInput.trim())
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CineOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_save_epg_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.save),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/** Tampon/altyazı rengi seçenekleri Türkçe anahtar olarak kaydedilir; ekranda seçili dildeki karşılığı gösterilir. */
@Composable
private fun settingsOptionLabel(key: String): String = when (key) {
    "Düşük" -> stringResource(R.string.settings_buffer_low)
    "Normal" -> stringResource(R.string.settings_buffer_normal)
    "Yüksek" -> stringResource(R.string.settings_buffer_high)
    "Beyaz" -> stringResource(R.string.settings_color_white)
    "Sarı" -> stringResource(R.string.settings_color_yellow)
    "Yeşil" -> stringResource(R.string.settings_color_green)
    "Mavi" -> stringResource(R.string.settings_color_blue)
    else -> key
}

