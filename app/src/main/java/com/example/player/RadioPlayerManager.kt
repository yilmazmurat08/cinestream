package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.model.IPTVItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
object RadioPlayerManager {

    private var exoPlayer: ExoPlayer? = null

    private val _activeRadioItem = MutableStateFlow<IPTVItem?>(null)
    val activeRadioItem: StateFlow<IPTVItem?> = _activeRadioItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    fun toggleRadio(context: Context, item: IPTVItem) {
        val current = _activeRadioItem.value
        if (current?.id == item.id) {
            val player = exoPlayer
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                    _isPlaying.value = false
                } else {
                    player.play()
                    _isPlaying.value = true
                }
            } else {
                playRadio(context, item)
            }
        } else {
            playRadio(context, item)
        }
    }

    fun playRadio(context: Context, item: IPTVItem) {
        val appContext = context.applicationContext

        // Eski ses akışını güvenli şekilde durdur
        stopRadio()

        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 IPTVRadio/2.0")

            val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(8_000, 30_000, 1_000, 2_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val player = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setHandleAudioBecomingNoisy(true)
                .build()

            player.setAudioAttributes(audioAttributes, true)

            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _isPlaying.value = false
                    _isBuffering.value = false
                }
            })

            val mediaItem = MediaItem.fromUri(item.streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.play()

            exoPlayer = player
            _activeRadioItem.value = item
            _isPlaying.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
            _isBuffering.value = false
        }
    }

    fun pauseRadio() {
        exoPlayer?.pause()
        _isPlaying.value = false
    }

    fun stopRadio() {
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        exoPlayer = null
        _activeRadioItem.value = null
        _isPlaying.value = false
        _isBuffering.value = false
    }
}
