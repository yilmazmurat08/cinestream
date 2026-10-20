package com.example.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinestream.sports.data.ChannelProvider
import com.cinestream.sports.data.demo.DemoSportsDataSource
import com.cinestream.sports.model.PlaylistChannel
import com.cinestream.sports.notify.WorkManagerReminderScheduler
import com.cinestream.sports.settings.DataStoreSportsSettingsStore
import com.cinestream.sports.ui.SportsApp
import com.cinestream.sports.ui.SportsDependencies
import com.example.R
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/**
 * CineStream ile Spor modülü arasındaki köprü (telefon ve TV).
 * Kanallar kullanıcının canlı kanallarından gelir; "İzle" listedeki kanalı oynatıcıda açar.
 * Veri sunucusu henüz kurulmadığı için maçlar örnek veridir; ekranın üstünde bu açıkça yazılır.
 */
@Composable
fun SportsHost(
    viewModel: IPTVViewModel,
    onPlayItem: (IPTVItem) -> Unit,
    tvMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val dependencies = remember(viewModel) {
        SportsDependencies(
            dataSource = DemoSportsDataSource(Clock.systemDefaultZone()),
            settings = DataStoreSportsSettingsStore(context),
            channels = object : ChannelProvider {
                override val channels: Flow<List<PlaylistChannel>> =
                    viewModel.allLiveChannels.map { list -> list.map { PlaylistChannel(it.id.toString(), it.name) } }
            },
            reminders = WorkManagerReminderScheduler(context)
        )
    }
    Column(modifier = modifier.fillMaxSize().testTag("sports_host")) {
        Text(
            text = stringResource(R.string.sports_demo_notice),
            color = Color(0xFF1A0F24),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFD4A853))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            SportsApp(
                dependencies = dependencies,
                onWatch = { channel ->
                    viewModel.allLiveChannels.value.firstOrNull { it.id.toString() == channel.id }?.let(onPlayItem)
                },
                tvMode = tvMode
            )
        }
    }
}
