package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AiRecommendationHistory
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel

/** Kayıtlı ya da uygulamaya gömülü kullanılabilir bir Gemini anahtarı var mı (Gemini davranışı değişmez). */
internal fun hasUsableGeminiKey(saved: String): Boolean {
    if (saved.isNotBlank()) return true
    val builtIn = BuildConfig.GEMINI_API_KEY
    return builtIn.isNotEmpty() && builtIn != "MY_GEMINI_API_KEY" && builtIn != "placeholder"
}

/**
 * TV asistanı: telefondaki ✨ asistanın (aynı sohbet, aynı Gemini tahmini, aynı kütüphane eşleştirmesi) büyük yazılı,
 * cam tarzı TV ekranı. Sahneden film bulmanın ana yolu sesli giriştir ("Sahnesini anlatayım, filmi bul").
 * Bulunan yapım kütüphanede varsa kart olarak gösterilir, odak karta gelir, OK detayı açar; yoksa bu açıkça
 * söylenir ve oynatma seçeneği gösterilmez. Telefondaki "Geçmiş" sekmesi burada da vardır.
 */
@Composable
fun TvAssistantScreen(
    viewModel: IPTVViewModel,
    onOpenItem: (IPTVItem) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val history by viewModel.aiRecommendationHistory.collectAsState()
    val savedKey by viewModel.geminiApiKey.collectAsState()
    val hasKey = hasUsableGeminiKey(savedKey)
    var input by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }
    val foundCardFocus = remember { FocusRequester() }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty() && !isLoading) {
            viewModel.sendChatMessage(clean)
            if (text == input) input = ""
            showHistory = false
        }
    }

    // Uygulama içi sesli giriş (sistemin ses arama ekranı açılmaz); yoksa ekran klavyesi.
    val voice = rememberTvVoiceInput(
        onResult = { send(it) },
        onUnavailable = { runCatching { inputFocus.requestFocus() } }
    )
    val canSpeak = voice.available

    /** Sesle sor; cihazda ses tanıma yoksa ekran klavyesiyle yazma alanına geçer. */
    fun listen() {
        if (voice.listening) voice.stop() else voice.start()
    }
    androidx.activity.compose.BackHandler(enabled = voice.listening) { voice.stop() }

    val lastMessage = messages.lastOrNull()
    LaunchedEffect(messages.size, isLoading) {
        val count = messages.size + if (isLoading) 1 else 0
        if (count > 0 && !showHistory) listState.animateScrollToItem(count - 1)
        // Bulunan yapım kartına odak otomatik gelir (OK ile detay açılır).
        if (!isLoading && lastMessage?.role == "ai" && (lastMessage.matchedItems.isNotEmpty() || lastMessage.matchedItem != null)) {
            requestFocusWhenReady(foundCardFocus)
        }
    }
    LaunchedEffect(hasKey) { requestFocusWhenReady(firstFocus) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(TvTheme.Background)
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .testTag("tv_assistant_screen"),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Sol: başlık, sahneden bulma (ses), hazır sorular, geçmiş, temizle
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(330.dp)
                .tvGlass()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = TvTheme.Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.chat_title), color = TvTheme.TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasKey) {
                TvSideAction(Icons.Filled.Mic, stringResource(R.string.tv_ai_scene_find), "tv_ai_scene", firstFocus, height = 60, highlight = true) {
                    listen()
                }
                Text(stringResource(R.string.tv_ai_presets), color = TvTheme.TextMuted, fontSize = 15.sp)
                listOf(R.string.tv_ai_q1, R.string.tv_ai_q2, R.string.tv_ai_q3, R.string.tv_ai_q4).forEachIndexed { i, res ->
                    val text = stringResource(res)
                    TvSideAction(null, text, "tv_ai_preset_$i") { send(text) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvSideAction(
                        Icons.Filled.History,
                        stringResource(if (showHistory) R.string.ai_history_tab_chat else R.string.ai_history_tab_history),
                        "tv_ai_history_toggle",
                        modifier = Modifier.weight(1f)
                    ) { showHistory = !showHistory }
                    if (messages.isNotEmpty() && !showHistory) {
                        TvSideAction(Icons.Filled.DeleteOutline, stringResource(R.string.chat_clear), "tv_ai_clear",
                            modifier = Modifier.weight(1f)) { viewModel.clearChat() }
                    }
                }
            } else {
                Text(stringResource(R.string.tv_ai_no_key), color = TvTheme.TextSecondary, fontSize = 17.sp, lineHeight = 24.sp)
                var showPhoneEntry by remember { mutableStateOf(false) }
                TvSideAction(Icons.Filled.Key, stringResource(R.string.tv_ai_key_phone), "tv_ai_key_phone", firstFocus) { showPhoneEntry = true }
                TvSideAction(Icons.Filled.Settings, stringResource(R.string.tv_ai_key_settings), "tv_ai_key_settings", onClick = onOpenSettings)
                if (showPhoneEntry) {
                    PhoneEntryDialog(
                        mode = PhoneEntryMode.GEMINI_KEY,
                        onDismiss = { showPhoneEntry = false },
                        onReceived = { data ->
                            val key = data.geminiKey.orEmpty().trim()
                            if (!PhoneEntryData.looksLikeApiKey(key)) {
                                PhoneEntryError.INVALID_KEY
                            } else {
                                viewModel.setGeminiApiKey(key)
                                null
                            }
                        }
                    )
                }
            }
        }

        // Sağ: mesajlar (veya geçmiş) + yazma alanı
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    showHistory -> TvAiHistory(
                        history = history,
                        onOpenItem = onOpenItem,
                        onReAsk = { send(it.query) },
                        onDelete = { viewModel.deleteAiHistoryItem(it.id) },
                        onClearAll = { viewModel.clearAllAiHistory() }
                    )
                    messages.isEmpty() && !isLoading -> Column(
                        Modifier.align(Alignment.Center).widthIn(max = 560.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = TvTheme.Accent.copy(alpha = 0.7f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.chat_empty_title), color = TvTheme.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.chat_empty_subtitle), color = TvTheme.TextSecondary, fontSize = 18.sp, lineHeight = 26.sp)
                    }
                    else -> LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        modifier = Modifier.fillMaxSize().testTag("tv_ai_messages")
                    ) {
                        itemsIndexed(messages) { index, message ->
                            TvChatBubble(
                                message = message,
                                onOpenItem = onOpenItem,
                                cardFocus = if (index == messages.lastIndex) foundCardFocus else null
                            )
                        }
                        if (isLoading) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.tvGlass(TvTheme.RowShape).padding(16.dp)) {
                                    CircularProgressIndicator(color = TvTheme.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.chat_thinking), color = TvTheme.TextSecondary, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (hasKey) {
                // Dinlerken konuşulan metin anlık görünür (Geri / mikrofon düğmesi durdurur).
                TvListeningCard(voice, Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvTextInput(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = stringResource(R.string.chat_input_placeholder),
                        onSubmit = { send(input) },
                        focusRequester = inputFocus,
                        modifier = Modifier.weight(1f)
                    )
                    if (canSpeak) {
                        TvRoundButton(Icons.Filled.Mic, stringResource(R.string.tv_ai_voice), "tv_ai_voice") { listen() }
                    }
                    TvRoundButton(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send), "tv_ai_send") { send(input) }
                }
            }
        }
    }
}

@Composable
private fun TvRoundButton(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    TvGlassButton(onClick = onClick, shape = TvTheme.PillShape, modifier = Modifier.size(56.dp).testTag(tag)) { f ->
        Icon(icon, description, tint = if (f) TvTheme.FocusGlow else TvTheme.TextPrimary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TvSideAction(
    icon: ImageVector?,
    label: String,
    tag: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Int = 52,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.RowShape,
        focusRequester = focusRequester,
        focusScale = 1.04f,
        glassColor = if (highlight) TvTheme.Accent.copy(alpha = 0.35f) else TvTheme.Glass,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.height(height.dp).testTag(tag)
    ) { f ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            if (icon != null) {
                Icon(icon, null, tint = if (f) TvTheme.FocusGlow else if (highlight) Color.White else TvTheme.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = TvTheme.TextPrimary, fontSize = 16.sp, fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
    }
}

/** Yazma alanı: odaklanınca mor çerçeve; OK ile TV'nin ekran klavyesi açılır, Gönder ile soru gider. */
@Composable
private fun TvTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TextStyle(color = TvTheme.TextPrimary, fontSize = 20.sp),
        cursorBrush = SolidColor(TvTheme.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        modifier = modifier
            .height(56.dp)
            .clip(TvTheme.PillShape)
            .background(TvTheme.Glass)
            .background(if (focused) TvTheme.FocusTint else Color.Transparent)
            .border(if (focused) 2.dp else 1.dp, if (focused) SolidColor(TvTheme.FocusGlow) else TvTheme.GlassBorder, TvTheme.PillShape)
            .focusRequester(focusRequester)
            .testTag("tv_ai_input"),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = TvTheme.TextMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        }
    )
}

/** Mesajlar kumandayla yukarı-aşağı gezilebilsin diye odaklanabilir (tıklanmaz) cam balon. */
@Composable
private fun TvFocusableText(text: String, background: Color, shape: RoundedCornerShape) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, if (focused) SolidColor(TvTheme.FocusGlow) else TvTheme.GlassBorder, shape)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(text, color = Color.White, fontSize = 19.sp, lineHeight = 27.sp)
    }
}

@Composable
private fun TvChatBubble(message: IPTVViewModel.ChatMessage, onOpenItem: (IPTVItem) -> Unit, cardFocus: FocusRequester?) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 640.dp)) {
            TvFocusableText(
                text = message.text,
                background = if (isUser) TvTheme.Accent.copy(alpha = 0.85f) else TvTheme.Glass,
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp, bottomEnd = if (isUser) 6.dp else 20.dp
                )
            )
            val found = message.matchedItems.ifEmpty { listOfNotNull(message.matchedItem) }
            if (found.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                // Birden fazla olası yapım kütüphanede varsa kartlar yan yana; odak ilkinde.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    found.forEachIndexed { index, item ->
                        TvLibraryCard(
                            title = item.cleanedName.ifBlank { item.name },
                            year = item.releaseDate.trim().take(4).takeIf { it.length == 4 && it.all(Char::isDigit) },
                            posterUrl = item.logoUrl,
                            focusRequester = if (index == 0) cardFocus else null,
                            tag = "tv_ai_item_${item.id}",
                            onClick = { onOpenItem(item) },
                            compact = found.size > 1
                        )
                    }
                }
            } else if (!isUser && message.detectedTitle.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                TvNotInLibrary(message.detectedTitle)
            }
        }
    }
}

/** Kütüphanede bulunan yapım: poster, ad, yıl, "Kütüphanende var"; OK detayı açar. */
@Composable
private fun TvLibraryCard(
    title: String,
    year: String?,
    posterUrl: String?,
    focusRequester: FocusRequester?,
    tag: String,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    TvGlassButton(
        onClick = onClick,
        shape = TvTheme.RowShape,
        focusRequester = focusRequester,
        focusScale = 1.04f,
        contentAlignment = Alignment.CenterStart,
        modifier = (if (compact) Modifier.width(300.dp) else Modifier.widthIn(min = 340.dp)).testTag(tag)
    ) { f ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(8.dp)).background(TvTheme.GlassDark)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.widthIn(max = 360.dp)) {
                Text(
                    stringResource(R.string.tv_in_library),
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(TvTheme.PillShape).background(TvTheme.Accent).padding(horizontal = 10.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(title, color = TvTheme.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (year != null) Text(year, color = TvTheme.TextSecondary, fontSize = 16.sp)
            }
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Filled.PlayArrow, stringResource(R.string.tv_open_details), tint = if (f) TvTheme.FocusGlow else TvTheme.Accent, modifier = Modifier.size(32.dp))
        }
    }
}

/** Tahmin edilen yapım kütüphanede yok: açıkça söylenir, oynatma/açma seçeneği yoktur. */
@Composable
private fun TvNotInLibrary(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.tvGlass(TvTheme.RowShape, TvTheme.GlassDark).padding(horizontal = 16.dp, vertical = 12.dp).testTag("tv_ai_not_in_library")
    ) {
        Icon(Icons.Outlined.Info, null, tint = TvTheme.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.tv_not_in_library, title), color = TvTheme.TextSecondary, fontSize = 17.sp)
    }
}

/** Telefondaki "Geçmiş" sekmesinin TV karşılığı: aç (kütüphanede varsa), tekrar sor, sil, tümünü temizle. */
@Composable
private fun TvAiHistory(
    history: List<AiRecommendationHistory>,
    onOpenItem: (IPTVItem) -> Unit,
    onReAsk: (AiRecommendationHistory) -> Unit,
    onDelete: (AiRecommendationHistory) -> Unit,
    onClearAll: () -> Unit
) {
    if (history.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.History, null, tint = TvTheme.Accent.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.ai_history_empty_title), color = TvTheme.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_history_empty_desc), color = TvTheme.TextSecondary, fontSize = 17.sp)
        }
        return
    }
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize().testTag("tv_ai_history")
    ) {
        item {
            TvSideAction(
                Icons.Filled.DeleteOutline,
                stringResource(if (confirmClear) R.string.tv_confirm_press_again else R.string.ai_history_clear_all),
                "tv_ai_history_clear",
                modifier = Modifier.widthIn(min = 260.dp)
            ) {
                if (confirmClear) {
                    onClearAll()
                    confirmClear = false
                } else {
                    confirmClear = true
                }
            }
        }
        items(history, key = { it.id }) { entry ->
            Column(Modifier.fillMaxWidth().tvGlass(TvTheme.RowShape).padding(16.dp)) {
                Text(entry.query, color = TvTheme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(entry.reply, color = TvTheme.TextSecondary, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                Spacer(Modifier.height(12.dp))
                val matchedId = entry.matchedItemId
                if (matchedId != null && !entry.matchedItemName.isNullOrBlank()) {
                    TvLibraryCard(
                        title = entry.matchedItemName,
                        year = null,
                        posterUrl = entry.matchedItemLogoUrl,
                        focusRequester = null,
                        tag = "tv_ai_history_item_${entry.id}",
                        onClick = {
                            onOpenItem(
                                IPTVItem(
                                    id = matchedId, playlistId = 1, name = entry.matchedItemName, cleanedName = entry.matchedItemName,
                                    logoUrl = entry.matchedItemLogoUrl, streamUrl = "", category = "", type = entry.matchedItemType ?: "MOVIE"
                                )
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (entry.detectedTitle.isNotBlank()) {
                    TvNotInLibrary(entry.detectedTitle)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvSideAction(Icons.Filled.Replay, stringResource(R.string.tv_ai_ask_again), "tv_ai_reask_${entry.id}",
                        modifier = Modifier.width(220.dp)) { onReAsk(entry) }
                    TvSideAction(Icons.Filled.DeleteOutline, stringResource(R.string.tv_delete), "tv_ai_delete_${entry.id}",
                        modifier = Modifier.width(160.dp)) { onDelete(entry) }
                }
            }
        }
    }
}
