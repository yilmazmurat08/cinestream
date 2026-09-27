package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AiRecommendationHistory
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel
import com.example.ui.IPTVViewModel.ChatMessage
import com.example.ui.components.SafeAsyncImage
import com.example.ui.theme.CineOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieFinderChatScreen(
    viewModel: IPTVViewModel,
    onBack: () -> Unit,
    onOpenItem: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isChatLoading.collectAsState()
    val historyList by viewModel.aiRecommendationHistory.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Sohbet, 1: Geçmiş
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(messages.size, isLoading) {
        val target = (messages.size - 1 + if (isLoading) 1 else 0).coerceAtLeast(0)
        if (messages.isNotEmpty() || isLoading) {
            listState.animateScrollToItem(target)
        }
    }

    fun send(textToSend: String = inputText) {
        val cleanText = textToSend.trim()
        if (cleanText.isNotBlank() && !isLoading) {
            viewModel.sendChatMessage(cleanText)
            if (textToSend == inputText) {
                inputText = ""
            }
            focusManager.clearFocus()
            selectedTab = 0
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.ai_history_clear_all),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ai_history_clear_confirm),
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllAiHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(stringResource(R.string.ai_history_delete_item), color = CineOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(android.R.string.cancel), color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1A29),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF0F0F14),
        topBar = {
            Column(modifier = Modifier.background(Color(0xFF17121F))) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CineOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.chat_title),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.player_back_desc), tint = Color.White)
                        }
                    },
                    actions = {
                        if (selectedTab == 0 && messages.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearChat() }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.chat_clear), tint = Color.White.copy(alpha = 0.7f))
                            }
                        } else if (selectedTab == 1 && historyList.isNotEmpty()) {
                            IconButton(onClick = { showClearHistoryDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.ai_history_clear_all), tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17121F))
                )

                // Segmented Tab Selector (Sohbet / Geçmiş)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: Sohbet
                    Surface(
                        onClick = { selectedTab = 0 },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selectedTab == 0) CineOrange else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (selectedTab == 0) Color.Black else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ai_history_tab_chat),
                                color = if (selectedTab == 0) Color.Black else Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // Tab 1: Geçmiş
                    Surface(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selectedTab == 1) CineOrange else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (selectedTab == 1) Color.Black else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val countBadge = if (historyList.isNotEmpty()) " (${historyList.size})" else ""
                            Text(
                                text = stringResource(R.string.ai_history_tab_history) + countBadge,
                                color = if (selectedTab == 1) Color.Black else Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        bottomBar = {
            if (selectedTab == 0) {
                Surface(color = Color(0xFF17121F), modifier = Modifier.navigationBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(stringResource(R.string.chat_input_placeholder), color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedBorderColor = CineOrange,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = CineOrange
                            ),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { send() },
                            enabled = inputText.isNotBlank() && !isLoading,
                            modifier = Modifier
                                .size(48.dp)
                                .background(if (inputText.isNotBlank() && !isLoading) CineOrange else Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = stringResource(R.string.chat_send), tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            // TAB 0: SOHBET EKRANI
            if (messages.isEmpty() && !isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CineOrange.copy(alpha = 0.6f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.chat_empty_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.chat_empty_subtitle),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // Son yapılan öneriler hızlı başlatıcı
                    if (historyList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(28.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = CineOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ai_history_recent_suggestions),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            historyList.take(3).forEach { histItem ->
                                Surface(
                                    onClick = { send(histItem.query) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay,
                                            contentDescription = null,
                                            tint = CineOrange,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = histItem.query,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(messages, key = { index, message -> "${index}_${message.role}_${message.text.hashCode()}" }) { _, message ->
                        ChatBubble(message = message, onOpenItem = onOpenItem)
                    }
                    if (isLoading) {
                        item { ChatTypingIndicator() }
                    }
                }
            }
        } else {
            // TAB 1: GEÇMİŞ EKRANI
            if (historyList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = CineOrange.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ai_history_empty_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ai_history_empty_desc),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(historyList, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            dateFormat = dateFormat,
                            onReAsk = {
                                send(item.query)
                            },
                            onDelete = {
                                viewModel.deleteAiHistoryItem(item.id)
                            },
                            onOpenItem = onOpenItem
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: AiRecommendationHistory,
    dateFormat: SimpleDateFormat,
    onReAsk: () -> Unit,
    onDelete: () -> Unit,
    onOpenItem: (IPTVItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1626)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Tarih ve Silme İkonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    val dateStr = remember(item.timestamp) {
                        try {
                            dateFormat.format(Date(item.timestamp))
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    Text(
                        text = dateStr,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.ai_history_delete_item),
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Kullanıcı Sorgusu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CineOrange.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.QuestionAnswer,
                    contentDescription = null,
                    tint = CineOrange,
                    modifier = Modifier.size(15.dp).padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.query,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // AI Yanıtı
            Text(
                text = item.reply,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Eşleşen Kütüphane İçeriği (Varsa)
            if (!item.matchedItemName.isNullOrBlank() || item.detectedTitle.isNotBlank()) {
                val titleToDisplay = item.matchedItemName ?: item.detectedTitle
                val itemId = item.matchedItemId ?: 0
                val browsableItem = remember(item) {
                    IPTVItem(
                        id = itemId,
                        playlistId = 0,
                        name = titleToDisplay,
                        cleanedName = titleToDisplay,
                        logoUrl = item.matchedItemLogoUrl,
                        streamUrl = "",
                        category = "",
                        type = item.matchedItemType ?: "MOVIE"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, CineOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onOpenItem(browsableItem) }
                        .padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SafeAsyncImage(
                        model = item.matchedItemLogoUrl,
                        contentDescription = titleToDisplay,
                        modifier = Modifier
                            .size(width = 40.dp, height = 56.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_found_in_library),
                            color = CineOrange,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = titleToDisplay,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.matchedItemType.isNullOrBlank()) {
                            Text(
                                text = if (item.matchedItemType == "SERIES") "Dizi" else "Film",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Surface(
                        color = CineOrange.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onOpenItem(browsableItem) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.ai_history_watch_now),
                                tint = CineOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.ai_history_watch_now),
                                color = CineOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alt Eylem: Tekrar Sor Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReAsk,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CineOrange),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.ai_history_reask),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onOpenItem: (IPTVItem) -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        )
                    )
                    .background(if (isUser) CineOrange else Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (isUser) Color.Black else Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }

            message.matchedItem?.let { item ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, CineOrange.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { onOpenItem(item) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SafeAsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.cleanedName,
                        modifier = Modifier
                            .size(width = 46.dp, height = 64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_found_in_library),
                            color = CineOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.cleanedName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = stringResource(R.string.player_play_desc),
                        tint = CineOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTypingIndicator() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CineOrange, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = stringResource(R.string.chat_thinking), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}
