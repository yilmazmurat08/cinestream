package com.example.ui.tv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.ViewMode
import com.example.ui.IPTVViewModel
import com.example.ui.screens.CineStreamProUpgradeCard
import com.example.ui.screens.DataAndMemoryCard
import com.example.ui.screens.EditProfileDialog
import com.example.ui.screens.GeminiApiKeyCard
import com.example.ui.screens.IPTVPlaylistsCard
import com.example.ui.screens.LanguageSettingsCard
import com.example.ui.screens.ManualEpgCard
import com.example.ui.screens.PlayerSettingsCard
import com.example.ui.screens.SecurityCard
import com.example.ui.screens.SubtitlesCard
import com.example.ui.screens.ThemeSettingsCard
import com.example.ui.screens.ViewModeSettingsCard
import com.example.ui.theme.AppTheme
import com.example.util.LocaleHelper
import kotlinx.coroutines.delay

private enum class TvSetting { PROFILE, PRO, PLAYLISTS, EPG, LANGUAGE, VIEW_MODE, THEME, PLAYER, DATA, SUBTITLES, SECURITY, GEMINI, LEGAL }

/**
 * TV Ayarlar: solda dikey liste (ikon, başlık, sağda mevcut değer), sağda seçili ayarın cam paneli. Paneldeki
 * ayar kartları telefondakilerin aynısıdır (aynı ViewModel işlemleri), yani hiçbir ayar eksik kalmaz. Odak bir
 * satırda kısa süre durunca panel o ayarı gösterir; OK veya Sağ panele girer; Sol / Geri listeye döner.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSettingsScreen(viewModel: IPTVViewModel, modifier: Modifier = Modifier) {
    // TV ekranı her zaman koyu cam tasarımlıdır. Sağ paneldeki telefon kartları Material renklerini kullandığından
    // açık temada koyu zemin üstünde koyu yazı çıkıyordu; panel her zaman koyu (siyah) renk şemasıyla çizilir.
    com.example.ui.theme.MyApplicationTheme(appTheme = AppTheme.PURE_BLACK, dynamicColor = false) {
        TvSettingsContent(viewModel, modifier)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSettingsContent(viewModel: IPTVViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // LocalContext dil ayarlı bir bağlamdır; Activity, Compose görünümünün bağlamından bulunur.
    val view = androidx.compose.ui.platform.LocalView.current

    val appTheme by viewModel.appTheme.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val currentLanguage by viewModel.appLanguage.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
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
    val userProfile by viewModel.userProfile.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()

    val openCategoryLocks = com.example.ui.screens.rememberCategoryLocksOpener(viewModel)

    var focused by rememberSaveable { mutableStateOf(TvSetting.PLAYLISTS) }
    var shown by rememberSaveable { mutableStateOf(TvSetting.PLAYLISTS) }
    var inPanel by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var isBufferDropdownExpanded by remember { mutableStateOf(false) }
    var pinInputValue by remember { mutableStateOf("") }
    var apiKeyInputValue by remember { mutableStateOf("") }
    var isClearingCache by remember { mutableStateOf(false) }
    val rowFocus = remember { TvSetting.values().associateWith { FocusRequester() } }

    LaunchedEffect(parentalPin) { if (pinInputValue.isEmpty()) pinInputValue = parentalPin }
    LaunchedEffect(geminiApiKey) { if (apiKeyInputValue.isEmpty()) apiKeyInputValue = geminiApiKey }
    LaunchedEffect(Unit) { requestFocusWhenReady(rowFocus.getValue(focused)) }
    // Odak satırda kısa süre durunca panel o ayarı gösterir (hızlı gezinmede her kart çizilmez).
    LaunchedEffect(focused) {
        delay(250)
        shown = focused
    }
    // Panel içindeyken Geri listeye döner (ikinci Geri ana sayfaya).
    BackHandler(enabled = inPanel) { rowFocus.getValue(shown).requestFocus() }

    val profileName = userProfile?.displayName ?: userName ?: ""
    val on = stringResource(R.string.tv_on)
    val off = stringResource(R.string.tv_off)

    fun valueOf(setting: TvSetting): String? = when (setting) {
        TvSetting.PROFILE -> profileName.ifBlank { userProfile?.email ?: userEmail }
        TvSetting.PRO -> if (isProUser) "PRO ✓" else null
        TvSetting.PLAYLISTS -> playlists.size.toString()
        TvSetting.EPG -> if (manualEpgUrl.isNotBlank()) context.getString(R.string.tv_manual) else context.getString(R.string.tv_automatic)
        TvSetting.LANGUAGE -> if (currentLanguage == "en") "English" else "Türkçe"
        TvSetting.VIEW_MODE -> context.getString(if (viewMode == ViewMode.PHONE) R.string.mode_phone else R.string.mode_tv)
        TvSetting.THEME -> context.getString(
            when (appTheme) {
                AppTheme.SYSTEM -> R.string.theme_system
                AppTheme.PURE_BLACK -> R.string.theme_dark
                AppTheme.SYSTEM_LIGHT -> R.string.theme_light
            }
        )
        TvSetting.PLAYER -> selectedBuffer
        TvSetting.DATA -> if (autoRefreshList) on else off
        TvSetting.SUBTITLES -> "$subtitleSize · $subtitleColor"
        TvSetting.SECURITY -> if (parentalLockEnabled) on else off
        TvSetting.GEMINI -> if (geminiApiKey.isNotBlank()) context.getString(R.string.tv_saved_value) else null
        TvSetting.LEGAL -> null
    }

    Row(
        modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .padding(start = 40.dp, top = 28.dp, end = 32.dp)
            .testTag("tv_settings_screen")
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
            modifier = Modifier.width(380.dp).fillMaxHeight()
        ) {
            item {
                Text(stringResource(R.string.settings_title), color = TvTheme.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            }
            items(TvSetting.values().toList()) { setting ->
                val (icon, title) = settingLabel(setting)
                TvGlassButton(
                    onClick = {
                        if (setting == TvSetting.PRO) {
                            viewModel.openPaywall()
                        } else if (setting == TvSetting.PROFILE) {
                            showEditProfile = true
                        } else {
                            shown = setting
                            focusManager.moveFocus(FocusDirection.Right)
                        }
                    },
                    onFocused = { focused = setting },
                    shape = TvTheme.RowShape,
                    focusRequester = rowFocus.getValue(setting),
                    focusScale = 1.03f,
                    glassColor = if (setting == shown) TvTheme.Accent.copy(alpha = 0.25f) else TvTheme.Glass.copy(alpha = 0.55f),
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("tv_setting_${setting.name}")
                ) { isFocused ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(icon, null, tint = if (isFocused) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(title, color = TvTheme.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        valueOf(setting)?.let {
                            Text(it, color = TvTheme.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp).width(130.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        // Sağ panel: telefondaki ayar kartının aynısı
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(bottom = 24.dp)
                .tvGlass(TvTheme.CardShape, TvTheme.GlassDark)
                .onFocusChanged { inPanel = it.hasFocus }
                .focusProperties {
                    exit = { direction -> if (direction == FocusDirection.Left) rowFocus.getValue(shown) else FocusRequester.Default }
                }
                .focusGroup()
                .testTag("tv_settings_panel")
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                when (shown) {
                    TvSetting.PROFILE, TvSetting.PRO -> CineStreamProUpgradeCard(isProUser = isProUser, onOpenPaywall = { viewModel.openPaywall() })
                    TvSetting.PLAYLISTS -> IPTVPlaylistsCard(
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
                    TvSetting.EPG -> ManualEpgCard(
                        currentEpgUrl = manualEpgUrl,
                        onSaveEpgUrl = { url ->
                            Toast.makeText(context, context.getString(R.string.epg_url_saved_toast), Toast.LENGTH_SHORT).show()
                            viewModel.setManualEpgUrl(url) { success ->
                                if (!success) Toast.makeText(context, context.getString(R.string.epg_url_failed_toast), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    TvSetting.LANGUAGE -> LanguageSettingsCard(
                        currentLanguage = currentLanguage,
                        onLanguageSelect = { newLang ->
                            if (newLang != currentLanguage) {
                                viewModel.setAppLanguage(newLang)
                                LocaleHelper.setLocale(context, newLang)
                                (view.context.findActivityOrNull() ?: context.findActivityOrNull())?.recreate()
                            }
                        }
                    )
                    TvSetting.VIEW_MODE -> ViewModeSettingsCard(currentMode = viewMode, onModeSelect = { viewModel.setViewMode(it) })
                    TvSetting.THEME -> ThemeSettingsCard(currentTheme = appTheme, onThemeSelect = { viewModel.setAppTheme(it) })
                    TvSetting.PLAYER -> PlayerSettingsCard(
                        hwAccEnabled = hwAccEnabled,
                        onHwAccChange = { viewModel.setHardwareAcceleration(it) },
                        selectedBuffer = selectedBuffer,
                        isDropdownExpanded = isBufferDropdownExpanded,
                        onDropdownToggle = { isBufferDropdownExpanded = it },
                        bufferOptions = listOf("Düşük", "Normal", "Yüksek"),
                        onBufferSelect = { viewModel.setBufferSize(it) }
                    )
                    TvSetting.DATA -> DataAndMemoryCard(
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
                    TvSetting.SUBTITLES -> SubtitlesCard(
                        subtitleSize = subtitleSize,
                        onSizeChange = { viewModel.setSubtitleSize(it) },
                        selectedColor = subtitleColor,
                        colorOptions = listOf("Beyaz", "Sarı", "Yeşil", "Mavi"),
                        colorMap = mapOf("Beyaz" to Color.White, "Sarı" to Color(0xFFFFEB3B), "Yeşil" to Color(0xFF4CAF50), "Mavi" to Color(0xFF2196F3)),
                        onColorSelect = { viewModel.setSubtitleColor(it) }
                    )
                    TvSetting.SECURITY -> SecurityCard(
                        onOpenCategoryLocks = openCategoryLocks,
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
                    TvSetting.GEMINI -> GeminiApiKeyCard(
                        apiKey = apiKeyInputValue,
                        isValidating = isValidatingGeminiKey,
                        onApiKeyChange = { apiKeyInputValue = it },
                        onValidateClick = {
                            viewModel.validateAndSaveGeminiApiKey(apiKeyInputValue) { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    TvSetting.LEGAL -> com.example.ui.legal.LegalSettingsCard(onOpen = { viewModel.openLegal(it) })
                }
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            currentName = profileName.ifBlank { "Kullanıcı" },
            currentEmail = userProfile?.email ?: userEmail ?: "",
            currentPhotoUrl = userProfile?.photoUrl ?: userPhotoUrl,
            onDismiss = { showEditProfile = false },
            onSave = { newName, newEmail, newPhoto -> viewModel.updateUserProfile(newName, newEmail, newPhoto) },
            onLogout = { viewModel.logout() }
        )
    }
}

@Composable
private fun settingLabel(setting: TvSetting): Pair<ImageVector, String> = when (setting) {
    TvSetting.PROFILE -> Icons.Outlined.Person to stringResource(R.string.tv_profile)
    TvSetting.PRO -> Icons.Outlined.WorkspacePremium to "PRO"
    TvSetting.PLAYLISTS -> Icons.Outlined.ListAlt to stringResource(R.string.playlists_title)
    TvSetting.EPG -> Icons.Outlined.ViewList to stringResource(R.string.settings_manual_epg_title)
    TvSetting.LANGUAGE -> Icons.Outlined.Language to stringResource(R.string.app_language)
    TvSetting.VIEW_MODE -> Icons.Outlined.Tv to stringResource(R.string.settings_view_mode)
    TvSetting.THEME -> Icons.Outlined.DarkMode to stringResource(R.string.app_theme)
    TvSetting.PLAYER -> Icons.Outlined.PlayCircle to stringResource(R.string.settings_player)
    TvSetting.DATA -> Icons.Outlined.Storage to stringResource(R.string.settings_data_storage)
    TvSetting.SUBTITLES -> Icons.Outlined.ClosedCaption to stringResource(R.string.settings_subtitles)
    TvSetting.SECURITY -> Icons.Outlined.Lock to stringResource(R.string.settings_security)
    TvSetting.GEMINI -> Icons.Outlined.AutoAwesome to stringResource(R.string.gemini_ai_settings)
    TvSetting.LEGAL -> Icons.Outlined.Gavel to stringResource(R.string.legal_title)
}

private fun android.content.Context.findActivityOrNull(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
