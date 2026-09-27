package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Person
import com.example.ui.theme.isDark
import androidx.compose.foundation.shape.CircleShape
import com.example.data.api.GeminiAiService
import com.example.data.api.PersonDetails
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.IPTVViewModel
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.IPTVItem
import com.example.data.api.MetadataEnricher
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrokenWhite
import com.example.ui.theme.MatteGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InfoCapsule(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color(0xFFA855F7)
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color(0xFFA855F7).copy(alpha = 0.1f))
            .border(
                BorderStroke(1.2.dp, Color(0xFFA855F7).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            color = BrokenWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun DescriptionCard(
    text: String,
    title: String = "",
    itemId: Int = 0,
    viewModel: com.example.ui.IPTVViewModel? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val displayText = text.trim().ifBlank {
        "Bu içerik için özet bilgisi bulunmuyor."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(
                BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(16.dp)
    ) {
        Text(
            text = displayText,
            color = MatteGray,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        if (displayText.length > 120) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Daha Az Göster" else "Devamını Oku...",
                    color = Color(0xFFA855F7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GeminiSpoilerFreeSummaryCard(
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var aiSummary by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(top = 10.dp)
    ) {
        if (aiSummary == null && errorMessage == null && !isLoading) {
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val summary = MetadataEnricher.getSpoilerFreeSummary(context, title)
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                if (summary == "AI Özeti şu an oluşturulamadı") {
                                    errorMessage = summary
                                } else {
                                    aiSummary = summary
                                    isExpanded = true
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                errorMessage = "AI Özeti şu an oluşturulamadı"
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("gemini_summary_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF6D00), Color(0xFFFF3D00))
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_ai_spoiler_free_button),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(BorderStroke(0.8.dp, Color(0xFFFF6D00).copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFF6D00),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.detail_ai_generating),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (aiSummary != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF6D00).copy(alpha = 0.18f),
                                Color(0xFF1E102F).copy(alpha = 0.5f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Color(0xFFFF6D00).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = Color(0xFFFF6D00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.detail_ai_spoiler_free_badge),
                            color = Color(0xFFFF6D00),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isExpanded) "Daralt" else "Genişlet",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = aiSummary.orEmpty(),
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Red.copy(alpha = 0.1f))
                    .border(BorderStroke(0.8.dp, Color.Red.copy(alpha = 0.3f)), RoundedCornerShape(14.dp))
                .clickable {
                    errorMessage = null
                }
                .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.detail_error_desc),
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = stringResource(R.string.detail_retry),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SimilarMoviesSection(
    item: IPTVItem,
    viewModel: IPTVViewModel?,
    onSelectItem: (IPTVItem) -> Unit,
    modifier: Modifier = Modifier,
    currentThemeIsDark: Boolean = true
) {
    var similarItems by remember(item.id, item.cleanedName) { mutableStateOf<List<IPTVItem>>(emptyList()) }
    var isLoading by remember(item.id, item.cleanedName) { mutableStateOf(true) }

    LaunchedEffect(item.id, item.cleanedName) {
        isLoading = true
        if (viewModel != null) {
            viewModel.getSimilarItemsFlow(item).collect { list ->
                similarItems = list
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    if (!isLoading && similarItems.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFF6D00),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI Önerileri",
                color = if (currentThemeIsDark) Color.White else Color(0xFF1E1E24),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isLoading) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(4, key = { "similar_skeleton_$it" }, contentType = { "skeleton" }) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(165.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.White.copy(alpha = 0.02f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFFFF6D00),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(similarItems, key = { index, recItem -> "${recItem.id}_$index" }) { _, recItem ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentThemeIsDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6)
                        ),
                        border = BorderStroke(
                            0.8.dp,
                            if (currentThemeIsDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .width(115.dp)
                            .clickable { onSelectItem(recItem) }
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                if (!recItem.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = recItem.logoUrl,
                                        contentDescription = recItem.cleanedName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color(0xFF2C2D35), Color(0xFF14151B))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = recItem.cleanedName.take(1).uppercase(),
                                            color = Color(0xFFFF6D00),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Rating Badge
                                if (recItem.rating > 0.0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", recItem.rating),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = recItem.cleanedName,
                                color = if (currentThemeIsDark) Color.White.copy(alpha = 0.9f) else Color(0xFF1E1E24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CastSection(
    title: String,
    rawCast: String,
    director: String,
    viewModel: IPTVViewModel?,
    item: IPTVItem? = null,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentCast by remember(rawCast) { mutableStateOf(rawCast) }
    var currentDirector by remember(director) { mutableStateOf(director) }
    var isFetchingAi by remember { mutableStateOf(false) }

    // Detect missing/empty/placeholder cast or director
    val isCastMissing = currentCast.isBlank() || 
            currentCast.equals("Belirtilmemiş", ignoreCase = true) || 
            currentCast.equals("Bilinmiyor", ignoreCase = true) || 
            currentCast.equals("Popüler Oyuncular", ignoreCase = true)

    val isDirectorMissing = currentDirector.isBlank() || 
            currentDirector.equals("Belirtilmemiş", ignoreCase = true) || 
            currentDirector.equals("Bilinmiyor", ignoreCase = true)

    // Trigger AI / local metadata if cast/director is missing
    LaunchedEffect(title, isCastMissing, isDirectorMissing) {
        if ((isCastMissing || isDirectorMissing) && title.isNotBlank() && !isFetchingAi) {
            isFetchingAi = true
            try {
                if (viewModel != null && item != null) {
                    viewModel.fetchCastAndDirectorInfo(title, item) { info ->
                        if (info.cast.isNotEmpty()) {
                            currentCast = info.cast.joinToString(", ")
                        }
                        if (info.director.isNotBlank()) {
                            currentDirector = info.director
                        }
                        isFetchingAi = false
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        try {
                            val info = GeminiAiService.fetchCastAndDirectorInfo(context, title)
                            withContext(Dispatchers.Main) {
                                if (info.cast.isNotEmpty()) {
                                    currentCast = info.cast.joinToString(", ")
                                }
                                if (info.director.isNotBlank()) {
                                    currentDirector = info.director
                                }
                            }
                        } catch (_: Throwable) {
                        } finally {
                            withContext(Dispatchers.Main) {
                                isFetchingAi = false
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                isFetchingAi = false
            }
        }
    }

    val displayCastList = remember(currentCast) {
        currentCast.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Popüler Oyuncular", ignoreCase = true) && !it.equals("Belirtilmemiş", ignoreCase = true) && !it.equals("Bilinmiyor", ignoreCase = true) }
    }

    val currentTheme = com.example.ui.theme.LocalAppTheme.current

    Column(modifier = modifier.fillMaxWidth()) {
        // CAST MEMBERS ROW
        if (displayCastList.isNotEmpty() || isFetchingAi) {
            Text(
                text = stringResource(R.string.detail_cast),
                color = if (currentTheme.isDark) Color.White else Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isFetchingAi && displayCastList.isEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(5, key = { "cast_skeleton_$it" }, contentType = { "skeleton" }) {
                        ShimmerCircularActorItem()
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(displayCastList, key = { it }) { actorName ->
                        ActorItemCard(
                            actorName = actorName,
                            viewModel = viewModel,
                            onClick = { onPersonClick(actorName, "Oyuncu") }
                        )
                    }
                }
            }
        }

        // DIRECTOR ROW
        if (currentDirector.isNotBlank() && !currentDirector.equals("Bilinmiyor", ignoreCase = true) && !currentDirector.equals("Belirtilmemiş", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.detail_creator_director),
                color = if (currentTheme.isDark) Color.White else Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Birden fazla yaratıcı/yönetmen varsa ("Paul Currie, Xander Collier, ...") her biri ayrı kart olur:
            // her birinin kendi fotoğrafı ve dokununca kendi "diğer yapımları" açılır.
            val directorNames = remember(currentDirector) {
                currentDirector.split(",", ";", "/", "&")
                    .map { it.trim() }
                    .filter {
                        it.isNotEmpty() &&
                            !it.equals("Bilinmiyor", ignoreCase = true) &&
                            !it.equals("Belirtilmemiş", ignoreCase = true)
                    }
                    .distinct()
            }
            if (directorNames.size <= 1) {
                val singleName = directorNames.firstOrNull() ?: currentDirector
                DirectorCard(
                    directorName = singleName,
                    viewModel = viewModel,
                    onClick = { onPersonClick(singleName, "Yönetmen") }
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(directorNames, key = { it }) { name ->
                        ActorItemCard(
                            actorName = name,
                            viewModel = viewModel,
                            role = "Yönetmen",
                            onClick = { onPersonClick(name, "Yönetmen") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActorItemCard(
    actorName: String,
    viewModel: IPTVViewModel?,
    role: String = "Oyuncu",
    onClick: () -> Unit
) {
    val currentTheme = com.example.ui.theme.LocalAppTheme.current
    val cachedPersonState = remember(actorName) { mutableStateOf<PersonDetails?>(null) }
    var isLoading by remember(actorName) { mutableStateOf(true) }

    LaunchedEffect(actorName) {
        viewModel?.getPersonDetailsFlow(actorName, role)?.collect { cached ->
            cachedPersonState.value = cached
            if (cached != null) isLoading = false
        }
    }

    var tmdbPhotoUrl by remember(actorName) { mutableStateOf<String?>(null) }
    LaunchedEffect(actorName) {
        tmdbPhotoUrl = viewModel?.lookupPersonPhoto(actorName, role)
    }
    val actorImgUrl = tmdbPhotoUrl ?: cachedPersonState.value?.imageUrl

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.25f),
                            Color(0xFFD946EF).copy(alpha = 0.12f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFD946EF), Color(0xFF8B5CF6))
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!actorImgUrl.isNullOrBlank()) {
                AsyncImage(
                    model = actorImgUrl,
                    contentDescription = actorName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                ShimmerCircularActorItemInner()
            } else {
                Text(
                    text = actorName.take(1).uppercase(),
                    color = Color(0xFFD946EF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actorName,
            color = if (currentTheme.isDark) Color.White else Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DirectorCard(
    directorName: String,
    viewModel: IPTVViewModel?,
    onClick: () -> Unit
) {
    val currentTheme = com.example.ui.theme.LocalAppTheme.current
    val cachedDirectorState = remember(directorName) { mutableStateOf<PersonDetails?>(null) }
    var isLoading by remember(directorName) { mutableStateOf(true) }

    LaunchedEffect(directorName) {
        viewModel?.getPersonDetailsFlow(directorName, "Yönetmen")?.collect { cached ->
            cachedDirectorState.value = cached
            if (cached != null) isLoading = false
        }
    }

    var tmdbPhotoUrl by remember(directorName) { mutableStateOf<String?>(null) }
    LaunchedEffect(directorName) {
        tmdbPhotoUrl = viewModel?.lookupPersonPhoto(directorName, "Yönetmen")
    }
    val directorImgUrl = tmdbPhotoUrl ?: cachedDirectorState.value?.imageUrl

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (currentTheme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFD946EF).copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.3f),
                            Color(0xFFD946EF).copy(alpha = 0.15f)
                        )
                    )
                )
                .border(1.2.dp, Color(0xFFD946EF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!directorImgUrl.isNullOrBlank()) {
                AsyncImage(
                    model = directorImgUrl,
                    contentDescription = directorName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (isLoading) {
                ShimmerCircularActorItemInner()
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.detail_director_desc),
                    tint = Color(0xFFD946EF),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = directorName,
                color = if (currentTheme.isDark) Color.White else Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.detail_director_creator),
                color = Color(0xFFD946EF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ShimmerCircularActorItem() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
        ) {
            ShimmerCircularActorItemInner()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
        )
    }
}

@Composable
fun ShimmerCircularActorItemInner() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD946EF).copy(alpha = alpha),
                        Color(0xFF8B5CF6).copy(alpha = alpha * 0.5f)
                    )
                )
            )
    )
}

