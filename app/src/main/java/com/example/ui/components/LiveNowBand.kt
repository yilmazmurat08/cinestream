package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVItem
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.DeepPurpleBg
import kotlinx.coroutines.delay

/** Bandın yüksekliği (sistem çubuğu payı hariç); oynatıcının üst çubuğu bu kadar aşağıdan başlar. */
val LIVE_NOW_BAND_HEIGHT = 32.dp

/** Kanalın yayın akışında [now] anında yayında olan program (yoksa null). */
fun currentProgramOf(channel: IPTVItem, programsByChannel: Map<String, List<EPGProgram>>, now: Long): EPGProgram? {
    val key = channel.tvgId?.lowercase(java.util.Locale.ROOT)?.trim().orEmpty()
    if (key.isEmpty()) return null
    return programsByChannel[key]?.firstOrNull { now >= it.startEpochMillis && now < it.endEpochMillis }
}

/**
 * Bandı kanalın yayın akışından besler. 30 saniyede bir yeniden bakar: program bitince sıradaki gelir.
 * Saat yalnızca bu küçük parçayı yeniler, oynatıcı ekranının tamamını değil.
 */
@Composable
fun LiveNowBandFor(channel: IPTVItem, viewModel: com.example.ui.IPTVViewModel?, modifier: Modifier = Modifier) {
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
    LiveNowBand(program, modifier)
}

/** Canlı yayında oynatıcının üstündeki bant: o an yayında olan program. Bilgi yoksa bant boş kalır; tıklanmaz. */
@Composable
fun LiveNowBand(program: EPGProgram?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DeepPurpleBg.copy(alpha = 0.75f))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(LIVE_NOW_BAND_HEIGHT)
            .padding(horizontal = 16.dp)
            .testTag("live_now_band"),
        contentAlignment = Alignment.CenterStart
    ) {
        if (program != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AccentNeonPurple)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tv_now_playing, program.title),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("live_now_title")
                )
                if (program.startTimeFormatted.isNotBlank() && program.endTimeFormatted.isNotBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${program.startTimeFormatted} – ${program.endTimeFormatted}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
