package com.example.ui.components
import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.epg.EPGProvider
import com.example.data.model.ChannelEPG
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*

/**
 * Vertical Electronic Program Guide (EPG) Component.
 * Lists scheduled TV channels vertically along with their full daily program schedule timelines.
 */
@Composable
fun VerticalEPGComponent(
    liveChannels: List<IPTVItem>,
    onPlayChannel: (IPTVItem) -> Unit,
    onSelectChannelDetails: (IPTVItem) -> Unit,
    realEpgPrograms: Map<String, List<EPGProgram>> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var selectedTimeSlot by remember { mutableStateOf("Şimdi Canlı") } // "Şimdi Canlı", "Sabah", "Öğle", "Akşam", "Gece", "Tüm Gün"

    var selectedProgramForModal by remember { mutableStateOf<Pair<IPTVItem, EPGProgram>?>(null) }

    // Live Clock state
    var currentTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (isActive) {
            currentTimeStr = sdf.format(Date())
            delay(1000L)
        }
    }

    // Categories list extracted from live channels
    val availableCategories = remember(liveChannels) {
        val baseGenres = listOf("Tümü", "Spor", "Belgesel", "Eğlence", "Sinema", "Dizi", "Haber", "Çocuk", "Müzik", "Ulusal")
        val dynamicCats = liveChannels.map { it.category.trim() }.filter { it.isNotBlank() && !baseGenres.contains(it) }.distinct()
        baseGenres + dynamicCats
    }

    // Filter channels first (ultra-fast string checks) before generating schedule objects
    val filteredChannels = remember(liveChannels, selectedCategory, searchQuery) {
        liveChannels.filter { channel ->
            val matchesCategory = when (selectedCategory) {
                "Tümü" -> true
                "Spor" -> channel.cleanedName.contains("spor", ignoreCase = true) ||
                        channel.cleanedName.contains("sport", ignoreCase = true) ||
                        channel.cleanedName.contains("futbol", ignoreCase = true) ||
                        channel.cleanedName.contains("bein", ignoreCase = true) ||
                        channel.cleanedName.contains("s sport", ignoreCase = true) ||
                        channel.cleanedName.contains("tivibu spor", ignoreCase = true) ||
                        channel.cleanedName.contains("eurosport", ignoreCase = true) ||
                        channel.category.contains("spor", ignoreCase = true) ||
                        channel.category.contains("sport", ignoreCase = true)
                "Belgesel" -> channel.cleanedName.contains("belgesel", ignoreCase = true) ||
                        channel.cleanedName.contains("doc", ignoreCase = true) ||
                        channel.cleanedName.contains("nat geo", ignoreCase = true) ||
                        channel.cleanedName.contains("discovery", ignoreCase = true) ||
                        channel.cleanedName.contains("history", ignoreCase = true) ||
                        channel.cleanedName.contains("animal planet", ignoreCase = true) ||
                        channel.category.contains("belgesel", ignoreCase = true) ||
                        channel.category.contains("doc", ignoreCase = true)
                "Eğlence" -> channel.cleanedName.contains("eğlence", ignoreCase = true) ||
                        channel.cleanedName.contains("eglence", ignoreCase = true) ||
                        channel.cleanedName.contains("show", ignoreCase = true) ||
                        channel.cleanedName.contains("tv8", ignoreCase = true) ||
                        channel.cleanedName.contains("tlc", ignoreCase = true) ||
                        channel.cleanedName.contains("dmax", ignoreCase = true) ||
                        channel.cleanedName.contains("entertainment", ignoreCase = true) ||
                        channel.category.contains("eğlence", ignoreCase = true) ||
                        channel.category.contains("eglence", ignoreCase = true) ||
                        channel.category.contains("entertainment", ignoreCase = true) ||
                        channel.category.contains("show", ignoreCase = true)
                "Sinema" -> channel.cleanedName.contains("sinema", ignoreCase = true) ||
                        channel.cleanedName.contains("film", ignoreCase = true) ||
                        channel.cleanedName.contains("movie", ignoreCase = true) ||
                        channel.cleanedName.contains("cinema", ignoreCase = true) ||
                        channel.category.contains("sinema", ignoreCase = true) ||
                        channel.category.contains("film", ignoreCase = true)
                "Dizi" -> channel.cleanedName.contains("dizi", ignoreCase = true) ||
                        channel.cleanedName.contains("series", ignoreCase = true) ||
                        channel.category.contains("dizi", ignoreCase = true)
                "Haber" -> channel.cleanedName.contains("haber", ignoreCase = true) ||
                        channel.cleanedName.contains("news", ignoreCase = true) ||
                        channel.cleanedName.contains("ntv", ignoreCase = true) ||
                        channel.cleanedName.contains("cnn", ignoreCase = true) ||
                        channel.category.contains("haber", ignoreCase = true) ||
                        channel.category.contains("news", ignoreCase = true)
                "Çocuk" -> channel.cleanedName.contains("çocuk", ignoreCase = true) ||
                        channel.cleanedName.contains("cocuk", ignoreCase = true) ||
                        channel.cleanedName.contains("kids", ignoreCase = true) ||
                        channel.cleanedName.contains("cartoon", ignoreCase = true) ||
                        channel.cleanedName.contains("disney", ignoreCase = true) ||
                        channel.cleanedName.contains("minika", ignoreCase = true) ||
                        channel.category.contains("çocuk", ignoreCase = true) ||
                        channel.category.contains("cocuk", ignoreCase = true) ||
                        channel.category.contains("kids", ignoreCase = true)
                "Müzik" -> channel.cleanedName.contains("müzik", ignoreCase = true) ||
                        channel.cleanedName.contains("muzik", ignoreCase = true) ||
                        channel.cleanedName.contains("music", ignoreCase = true) ||
                        channel.cleanedName.contains("kral", ignoreCase = true) ||
                        channel.cleanedName.contains("power", ignoreCase = true) ||
                        channel.category.contains("müzik", ignoreCase = true) ||
                        channel.category.contains("music", ignoreCase = true)
                "Ulusal" -> channel.cleanedName.contains("ulusal", ignoreCase = true) ||
                        channel.cleanedName.contains("yerli", ignoreCase = true) ||
                        channel.cleanedName.contains("genel", ignoreCase = true) ||
                        channel.cleanedName.contains("trt", ignoreCase = true) ||
                        channel.category.contains("ulusal", ignoreCase = true) ||
                        channel.category.contains("yerli", ignoreCase = true) ||
                        channel.category.contains("genel", ignoreCase = true)
                else -> channel.category.equals(selectedCategory, ignoreCase = true) ||
                        channel.category.contains(selectedCategory, ignoreCase = true)
            }

            val matchesSearch = searchQuery.isBlank() ||
                    channel.cleanedName.contains(searchQuery, ignoreCase = true) ||
                    channel.name.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }.take(150) // Safe display limit to keep UI super smooth and RAM free
    }

    // Generate EPG data only for the filtered visible channels with memory caching
    val filteredEPGList = remember(filteredChannels, selectedTimeSlot, realEpgPrograms) {
        filteredChannels.map { channel ->
            EPGProvider.generateEPGForSingleChannel(channel, realEpgPrograms)
        }
    }

    // Program Modal Dialog
    selectedProgramForModal?.let { (channel, program) ->
        EPGProgramDetailDialog(
            channel = channel,
            program = program,
            onDismiss = { selectedProgramForModal = null },
            onPlayChannel = {
                selectedProgramForModal = null
                onPlayChannel(channel)
            },
            onSetReminder = {
                Toast.makeText(context, context.getString(R.string.epg_reminder_set, program.title), Toast.LENGTH_SHORT).show()
                selectedProgramForModal = null
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepPurpleBg)
            .testTag("vertical_epg_container")
    ) {
        item(key = "epg_header_and_filters") {
        // Top Header Banner Card with Live Clock
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MidPurpleBg.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, WhiteText.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .clip(RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(LiveGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.epg_title),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Clock pill in normal padding flow
                    if (currentTimeStr.isNotEmpty()) {
                        Surface(
                            color = LiveGold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, LiveGold.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = LiveGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = currentTimeStr,
                                    color = LiveGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(stringResource(R.string.epg_search_hint), color = WhiteText.copy(alpha = 0.4f), fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CineOrange)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Temizle", tint = WhiteText.copy(alpha = 0.7f))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MidPurpleBg.copy(alpha = 0.6f),
                unfocusedContainerColor = MidPurpleBg.copy(alpha = 0.4f),
                focusedBorderColor = CineOrange,
                unfocusedBorderColor = WhiteText.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("epg_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Yatay Kategori Bandı (Horizontal Category Filter Bar)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("epg_category_bar")
        ) {
            items(availableCategories, key = { it }) { cat ->
                val isSelected = selectedCategory == cat
                val chipIcon = remember(cat) {
                    when (cat) {
                        "Tümü" -> Icons.Default.GridView
                        "Spor" -> Icons.Default.SportsSoccer
                        "Belgesel" -> Icons.Default.Science
                        "Eğlence" -> Icons.Default.Celebration
                        "Sinema" -> Icons.Default.Movie
                        "Dizi" -> Icons.Default.Tv
                        "Haber" -> Icons.Default.Newspaper
                        "Çocuk" -> Icons.Default.ChildCare
                        "Müzik" -> Icons.Default.MusicNote
                        "Ulusal" -> Icons.Default.LiveTv
                        else -> CategoryIconMatcher.matchIcon(cat, "LIVE")
                    }
                }

                val bgColor by animateColorAsState(
                    if (isSelected) CineOrange else MidPurpleBg.copy(alpha = 0.6f),
                    label = "catBg"
                )
                val contentColor = if (isSelected) Color.Black else Color.White
                val borderColor = if (isSelected) CineOrange else WhiteText.copy(alpha = 0.15f)

                Surface(
                    onClick = { selectedCategory = cat },
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.testTag("epg_category_$cat")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = chipIcon,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else CineOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = epgDisplayLabel(cat),
                            color = contentColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Time Filter Row
        val timeSlots = listOf("Şimdi Canlı", "Sabah (06-12)", "Öğle (12-18)", "Akşam (18-00)", "Gece (00-06)", "Tüm Gün")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(timeSlots, key = { it }, contentType = { "time_slot" }) { slot ->
                val isSelected = selectedTimeSlot == slot
                val bgColor by animateColorAsState(if (isSelected) CineOrange else MidPurpleBg.copy(alpha = 0.5f), label = "slotBg")
                val textColor = if (isSelected) Color.Black else Color.White

                Surface(
                    onClick = { selectedTimeSlot = slot },
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, if (isSelected) CineOrange else WhiteText.copy(alpha = 0.15f)),
                    modifier = Modifier.testTag("epg_timeslot_$slot")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (slot == "Şimdi Canlı") {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.Black else LiveGold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = epgDisplayLabel(slot),
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Vertical Channel Schedules Stream
        } // "epg_header_and_filters" item kapanışı

        if (filteredEPGList.isEmpty()) {
            item(key = "epg_empty_state") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.TvOff,
                        contentDescription = null,
                        tint = CineOrange.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.epg_no_results),
                        color = MutedPurpleText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            }
        } else {
            itemsIndexed(filteredEPGList, key = { index, channelEPG -> "${channelEPG.channel.id}_$index" }) { _, channelEPG ->
                ChannelEPGVerticalItem(
                    channelEPG = channelEPG,
                    selectedTimeSlot = selectedTimeSlot,
                    onPlayChannel = { onPlayChannel(channelEPG.channel) },
                    onSelectChannel = { onSelectChannelDetails(channelEPG.channel) },
                    onProgramClick = { program ->
                        selectedProgramForModal = Pair(channelEPG.channel, program)
                    }
                )
            }
        }
    }
}

/**
 * Vertical Schedule Card for a Single Channel in EPG.
 */
@Composable
private fun ChannelEPGVerticalItem(
    channelEPG: ChannelEPG,
    selectedTimeSlot: String,
    onPlayChannel: () -> Unit,
    onSelectChannel: () -> Unit,
    onProgramClick: (EPGProgram) -> Unit
) {
    val channel = channelEPG.channel
    val currentLiveProg = channelEPG.currentProgram

    // Filter programs based on selected time slot
    val displayPrograms = remember(channelEPG.programs, selectedTimeSlot) {
        when (selectedTimeSlot) {
            "Şimdi Canlı" -> channelEPG.programs.filter { it.isLiveNow || (currentLiveProg != null && it == currentLiveProg) }
            "Sabah (06-12)" -> channelEPG.programs.filter {
                val hour = extractHour(it.startTimeFormatted)
                hour in 6..11
            }
            "Öğle (12-18)" -> channelEPG.programs.filter {
                val hour = extractHour(it.startTimeFormatted)
                hour in 12..17
            }
            "Akşam (18-00)" -> channelEPG.programs.filter {
                val hour = extractHour(it.startTimeFormatted)
                hour in 18..23
            }
            "Gece (00-06)" -> channelEPG.programs.filter {
                val hour = extractHour(it.startTimeFormatted)
                hour in 0..5
            }
            else -> channelEPG.programs
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidPurpleBg.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, CineBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Channel Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepPurpleBg.copy(alpha = 0.6f))
                    .clickable { onSelectChannel() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel Logo Box
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MidPurpleBg)
                        .border(1.dp, CineOrange.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SafeAsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.cleanedName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.cleanedName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CineOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = channel.category.ifBlank { stringResource(R.string.epg_live_tv) },
                                color = CineOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (currentLiveProg != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            // Pulsing CANLI badge
                            PulsingLiveBadge()
                        }
                    }
                }

                // Quick Play Channel Button
                IconButton(
                    onClick = onPlayChannel,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CineOrange)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.epg_watch_channel_desc),
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vertical Timeline of Scheduled Shows for this channel
            if (displayPrograms.isEmpty()) {
                Text(
                    text = stringResource(R.string.epg_no_programs_in_slot),
                    color = MutedPurpleText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    displayPrograms.forEach { program ->
                        EPGProgramRowCard(
                            program = program,
                            onClick = { onProgramClick(program) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single Row Card representing a Program in the Channel's EPG Vertical Schedule.
 */
@Composable
private fun EPGProgramRowCard(
    program: EPGProgram,
    onClick: () -> Unit
) {
    val isLive = program.isLiveNow
    val cardBg = if (isLive) LiveGold.copy(alpha = 0.12f) else DeepPurpleBg.copy(alpha = 0.35f)
    val borderStroke = if (isLive) BorderStroke(1.dp, LiveGold.copy(alpha = 0.6f)) else BorderStroke(1.dp, WhiteText.copy(alpha = 0.06f))

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("epg_program_${program.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start & End Time Pill
                Surface(
                    color = if (isLive) LiveGold else MidPurpleBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${program.startTimeFormatted} - ${program.endTimeFormatted}",
                        color = if (isLive) Color.Black else PalePurpleText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Show Title
                Text(
                    text = program.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isLive) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YAYINDA",
                        color = LiveGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Description snippet if available
            if (program.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = program.description,
                    color = WhiteText.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            // Active Progress Bar for Live Shows
            if (isLive) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.epg_live_progress),
                            color = LiveGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(program.progressPercent * 100).toInt()}%",
                            color = LiveGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { program.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = LiveGold,
                        trackColor = LiveGold.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

/**
 * Animated Pulsing CANLI Badge for EPG items currently live.
 */
@Composable
private fun PulsingLiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        color = LiveGold.copy(alpha = alpha * 0.25f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LiveGold.copy(alpha = alpha))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LiveGold)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "CANLI YAYIN",
                color = LiveGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Detail Modal Dialog when user clicks any scheduled EPG program.
 */
@Composable
private fun EPGProgramDetailDialog(
    channel: IPTVItem,
    program: EPGProgram,
    onDismiss: () -> Unit,
    onPlayChannel: () -> Unit,
    onSetReminder: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MidPurpleBg),
            border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    // İçerik küçük/yatay ekranda veya büyük yazı boyutunda sığmazsa kaydırılabilsin.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = channel.cleanedName,
                            color = CineOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = WhiteText.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Program Title
                Text(
                    text = program.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time & Category Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = LiveGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, LiveGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${program.startTimeFormatted} - ${program.endTimeFormatted}",
                            color = LiveGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = CineOrange.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = program.category,
                            color = CineOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description
                Text(
                    text = program.description.ifBlank { stringResource(R.string.epg_no_description) },
                    color = WhiteText.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPlayChannel,
                        colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.epg_watch_channel), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onSetReminder,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LiveGold.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveGold),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = LiveGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.epg_remind), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun extractHour(timeStr: String): Int {
    return try {
        timeStr.split(":")[0].toInt()
    } catch (e: Exception) {
        12
    }
}

/** Filtre anahtarları Türkçe kalır (mantık bunlara bağlı); ekranda seçili dildeki karşılığı gösterilir. */
@Composable
private fun epgDisplayLabel(key: String): String = when (key) {
    "Tümü" -> stringResource(R.string.common_all)
    "Spor" -> stringResource(R.string.epg_genre_sports)
    "Belgesel" -> stringResource(R.string.epg_genre_documentary)
    "Eğlence" -> stringResource(R.string.epg_genre_entertainment)
    "Sinema" -> stringResource(R.string.epg_genre_cinema)
    "Dizi" -> stringResource(R.string.epg_genre_series)
    "Haber" -> stringResource(R.string.epg_genre_news)
    "Çocuk" -> stringResource(R.string.epg_genre_kids)
    "Müzik" -> stringResource(R.string.epg_genre_music)
    "Ulusal" -> stringResource(R.string.epg_genre_national)
    "Şimdi Canlı" -> stringResource(R.string.epg_slot_live_now)
    "Sabah (06-12)" -> stringResource(R.string.epg_slot_morning)
    "Öğle (12-18)" -> stringResource(R.string.epg_slot_afternoon)
    "Akşam (18-00)" -> stringResource(R.string.epg_slot_evening)
    "Gece (00-06)" -> stringResource(R.string.epg_slot_night)
    "Tüm Gün" -> stringResource(R.string.epg_slot_all_day)
    else -> key
}
