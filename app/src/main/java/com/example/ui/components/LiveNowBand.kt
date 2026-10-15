package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.ui.theme.AccentNeonPurple
import kotlinx.coroutines.delay

/** Kanalın yayın akışında [now] anında yayında olan program (yoksa null). */
fun currentProgramOf(channel: IPTVItem, programsByChannel: Map<String, List<EPGProgram>>, now: Long): EPGProgram? {
    val key = channel.tvgId?.lowercase(java.util.Locale.ROOT)?.trim().orEmpty()
    if (key.isEmpty()) return null
    return programsByChannel[key]?.firstOrNull { now >= it.startEpochMillis && now < it.endEpochMillis }
}

/**
 * Bilgiyi kanalın yayın akışından besler. 30 saniyede bir yeniden bakar: program bitince sıradaki gelir.
 * Saat yalnızca bu küçük parçayı yeniler, oynatıcı ekranının tamamını değil. Akışta bilgi yoksa [fallback] gösterilir.
 */
@Composable
fun LiveNowInfoFor(
    channel: IPTVItem,
    viewModel: com.example.ui.IPTVViewModel?,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {}
) {
    val programs = viewModel?.realEpgPrograms?.collectAsState()?.value.orEmpty()
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            clock = System.currentTimeMillis()
        }
    }
    val program = remember(channel.id, channel.tvgId, programs, clock) {
        currentProgramOf(channel, programs, System.currentTimeMillis())
    }
    if (program == null) fallback() else LiveNowInfo(program, clock, fontSize, modifier)
}

/**
 * Canlı yayında oynatıcının üst çubuğunda, kanal adının altındaki satır: o an yayında olan program, saatleri ve
 * ince bir ilerleme çizgisi. Denetimlerle birlikte görünür ve kaybolur; tıklanmaz.
 */
@Composable
fun LiveNowInfo(program: EPGProgram, now: Long, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val span = program.endEpochMillis - program.startEpochMillis
    val progress = if (span > 0) ((now - program.startEpochMillis).toFloat() / span).coerceIn(0f, 1f) else 0f
    Column(modifier = modifier.testTag("live_now_info")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(AccentNeonPurple)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.tv_now_playing, program.title),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("live_now_title")
            )
            if (program.startTimeFormatted.isNotBlank() && program.endTimeFormatted.isNotBlank()) {
                Text(
                    text = "  ·  ${program.startTimeFormatted} – ${program.endTimeFormatted}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = fontSize,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .width(120.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(AccentNeonPurple)
                    .testTag("live_now_progress")
            )
        }
    }
}
