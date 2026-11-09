package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EPGProgram
import com.example.reminder.ProgramReminder
import kotlinx.coroutines.launch

/** Kanal kartındaki onaylı tasarımın renkleri (bkz. docs: Kanal Listesi ve EPG taslağı). */
private val EpgAccent = Color(0xFFD946EF)
private val EpgAccentSoft = Color(0xFFE879F9)
private val EpgGold = Color(0xFFD4A853)
private val EpgLive = Color(0xFFF59E0B)
private val EpgPanel = Color(0xFF170D26)
private val EpgMuted = Color(0xFFA79FB5)

/** Kanal kartındaki "EPG ⌄" düğmesi. Yalnızca kanalın yayın akışı varsa gösterilir. */
@Composable
fun EpgToggleButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.epg_button_desc)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        color = if (open) EpgAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (open) EpgAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f)),
        modifier = modifier
            .height(32.dp)
            .semantics { contentDescription = description }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 6.dp)) {
            Text(
                "EPG",
                color = if (open) Color(0xFFF5D0FB) else Color(0xFFE9E4F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (open) Color(0xFFF5D0FB) else Color(0xFFE9E4F0),
                modifier = Modifier.size(16.dp).rotate(if (open) 180f else 0f)
            )
        }
    }
}

/** Kanal kartındaki yuvarlak "Oynat" düğmesi. */
@Composable
fun ChannelPlayButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 38.dp) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = EpgAccent,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play), tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Kanal kartının altında açılan yayın akışı (12 saat). Şu an yayındaki program turuncu ve ilerleme çubuklu; bir
 * programa dokununca "Hatırlat (5 dk önce)" çıkar, yayındaki programda "Şimdi izle". Kurulu hatırlatmada zil simgesi.
 */
@Composable
fun ChannelEpgDropdown(
    channelId: Int,
    programs: List<EPGProgram>,
    reminderKeys: Set<String>,
    onToggleReminder: (EPGProgram) -> Unit,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
    catchUp: List<com.example.util.CatchUpProgram> = emptyList(),
    onWatchCatchUp: (com.example.util.CatchUpProgram) -> Unit = {}
) {
    var picked by remember(channelId) { mutableStateOf<Long?>(null) }
    var catchUpOpen by remember(channelId) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EpgPanel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .testTag("epg_dropdown_$channelId")
    ) {
        Text(
            text = stringResource(R.string.epg_schedule_header),
            color = EpgGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp)
        )
        if (catchUp.isNotEmpty()) {
            CatchUpSection(
                channelId = channelId,
                programs = catchUp,
                open = catchUpOpen,
                onToggle = { catchUpOpen = !catchUpOpen },
                onWatch = onWatchCatchUp
            )
        }
        if (!catchUpOpen || catchUp.isEmpty()) Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            programs.forEach { program ->
                val isPicked = picked == program.startEpochMillis
                val hasReminder = ProgramReminder.key(channelId, program.startEpochMillis) in reminderKeys
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isPicked -> EpgAccent.copy(alpha = 0.12f)
                                program.isLiveNow -> EpgLive.copy(alpha = 0.08f)
                                else -> Color.Transparent
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { picked = if (isPicked) null else program.startEpochMillis }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .testTag("epg_program_${channelId}_${program.startEpochMillis}")
                    ) {
                        Text(
                            program.startTimeFormatted,
                            color = if (program.isLiveNow) EpgLive else Color(0xFFC9C3D3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(46.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                program.title,
                                color = Color(0xFFF4F1F8),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (program.isLiveNow) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                ) {
                                    Box(Modifier.fillMaxWidth(program.progressPercent.coerceIn(0f, 1f)).fillMaxHeight().background(EpgLive))
                                }
                            }
                        }
                        if (program.isLiveNow) {
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.epg_on_air), color = EpgLive, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                        if (hasReminder) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = stringResource(R.string.epg_reminder_on),
                                tint = EpgAccentSoft,
                                modifier = Modifier.size(18.dp).testTag("epg_bell_${channelId}_${program.startEpochMillis}")
                            )
                        }
                    }
                    if (isPicked) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 62.dp, end = 8.dp, bottom = 10.dp)
                        ) {
                            if (program.isLiveNow) {
                                EpgActionChip(
                                    icon = { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                                    label = stringResource(R.string.epg_watch_now),
                                    active = true,
                                    tag = "epg_watch_$channelId",
                                    onClick = onWatch
                                )
                            } else {
                                EpgActionChip(
                                    icon = {
                                        Icon(
                                            if (hasReminder) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                                            null, tint = Color.White, modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = stringResource(if (hasReminder) R.string.epg_reminder_on else R.string.epg_remind_button),
                                    active = hasReminder,
                                    tag = "epg_remind_${channelId}_${program.startEpochMillis}",
                                    onClick = { onToggleReminder(program) }
                                )
                            }
                            Text(stringResource(R.string.epg_ends_at, program.endTimeFormatted), color = EpgMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Kaçırılan programlar (catch-up): başlığa dokununca açılır; her programda "Baştan izle". */
@Composable
private fun CatchUpSection(
    channelId: Int,
    programs: List<com.example.util.CatchUpProgram>,
    open: Boolean,
    onToggle: () -> Unit,
    onWatch: (com.example.util.CatchUpProgram) -> Unit
) {
    val dayLabel = rememberCatchUpDayLabel()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EpgAccent.copy(alpha = if (open) 0.14f else 0.06f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("catchup_toggle_$channelId")
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = EpgAccentSoft, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.catchup_header, programs.size),
            color = Color(0xFFF5D0FB),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFF5D0FB),
            modifier = Modifier.size(16.dp).rotate(if (open) 180f else 0f)
        )
    }
    if (!open) {
        Spacer(Modifier.height(4.dp))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        programs.forEach { program ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onWatch(program) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("catchup_program_${channelId}_${program.startMillis}")
            ) {
                Column(Modifier.width(52.dp)) {
                    Text(catchUpClock(program.startMillis), color = Color(0xFFC9C3D3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(dayLabel(program.startMillis), color = EpgMuted, fontSize = 10.sp, maxLines = 1)
                }
                Text(
                    program.title,
                    color = Color(0xFFF4F1F8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                EpgActionChip(
                    icon = { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                    label = stringResource(R.string.catchup_watch),
                    active = true,
                    tag = "catchup_watch_${channelId}_${program.startMillis}",
                    onClick = { onWatch(program) }
                )
            }
        }
    }
}

internal fun catchUpClock(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))

/** "Bugün", "Dün" ya da "29.10". */
@Composable
internal fun rememberCatchUpDayLabel(): (Long) -> String {
    val today = stringResource(R.string.catchup_today)
    val yesterday = stringResource(R.string.catchup_yesterday)
    return remember(today, yesterday) {
        { millis ->
            val day = java.util.Calendar.getInstance()
            val d = java.util.Calendar.getInstance().apply { timeInMillis = millis }
            val sameDay = { a: java.util.Calendar, b: java.util.Calendar ->
                a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
                    a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
            }
            when {
                sameDay(day, d) -> today
                sameDay(day.apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }, d) -> yesterday
                else -> java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(java.util.Date(millis))
            }
        }
    }
}

@Composable
private fun EpgActionChip(icon: @Composable () -> Unit, label: String, active: Boolean, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        color = if (active) EpgAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (active) EpgAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f)),
        modifier = Modifier.height(34.dp).testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color(0xFFF4F1F8), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Hatırlat/kaldır: sonucu kısa bir mesajla bildirir (bildirimler kapalıysa nasıl açılacağını söyler). */
@Composable
fun rememberReminderToggle(viewModel: com.example.ui.IPTVViewModel): (com.example.data.model.IPTVItem, EPGProgram) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(viewModel, context) {
        { channel, program ->
            scope.launch {
                val result = viewModel.toggleReminder(channel, program)
                val message = when (result) {
                    true -> if (androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        val at = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(program.startEpochMillis - com.example.reminder.ProgramReminders.LEAD_TIME_MS))
                        context.getString(R.string.epg_reminder_toast, program.title, at)
                    } else {
                        context.getString(R.string.epg_notifications_off)
                    }
                    false -> context.getString(R.string.epg_reminder_removed)
                    null -> context.getString(R.string.epg_reminder_failed)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
