package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var libVLC: LibVLC? = null
    var mediaPlayer: MediaPlayer? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _useFallbackPlayer = MutableStateFlow(true)
    val useFallbackPlayer: StateFlow<Boolean> = _useFallbackPlayer.asStateFlow()

    private var currentUrl: String = ""

    fun initAndPlay(url: String, startPositionMs: Long = 0L) {
        if (url.isEmpty()) return

        try {
            if (libVLC == null) {
                val options = arrayListOf(
                    "--http-reconnect",
                    "--network-caching=3000",
                    "--clock-jitter=0",
                    "--no-drop-late-frames",
                    "--no-skip-frames",
                    "--avcodec-hw=none"
                )
                libVLC = LibVLC(getApplication(), options)
            }

            if (mediaPlayer == null) {
                val mp = MediaPlayer(libVLC)
                mp.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            _isPlaying.value = true
                            _isBuffering.value = false
                            _errorState.value = null
                        }
                        MediaPlayer.Event.Paused -> {
                            _isPlaying.value = false
                        }
                        MediaPlayer.Event.Stopped -> {
                            _isPlaying.value = false
                        }
                        MediaPlayer.Event.Buffering -> {
                            _isBuffering.value = event.buffering < 100f
                        }
                        MediaPlayer.Event.TimeChanged -> {
                            _currentPosition.value = event.timeChanged
                        }
                        MediaPlayer.Event.LengthChanged -> {
                            _duration.value = event.lengthChanged
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            Log.e("PlayerViewModel", "VLC EncounteredError for $url. Switching to ExoPlayer fallback.")
                            try {
                                mp.stop()
                                mp.detachViews()
                            } catch (e: Exception) {
                                Log.e("PlayerViewModel", "Error stopping player on EncounteredError", e)
                            }
                            _isPlaying.value = false
                            _isBuffering.value = false
                            _errorState.value = null
                            _useFallbackPlayer.value = true
                        }
                    }
                }
                mediaPlayer = mp
            }

            if (currentUrl != url) {
                currentUrl = url
                _errorState.value = null
                val mp = mediaPlayer ?: return
                try {
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error stopping player", e)
                }

                val media = Media(libVLC, Uri.parse(url.trim()))
                media.apply {
                    // Disable HW decoding to prevent native Vout / opaque buffer crashes in emulators
                    setHWDecoderEnabled(false, false)
                    addOption(":network-caching=3000")
                    addOption(":clock-jitter=0")
                    addOption(":clock-synchro=0")
                }
                mp.media = media
                media.release()
                mp.play()

                if (startPositionMs > 0L) {
                    mp.time = startPositionMs
                }
            } else {
                val mp = mediaPlayer
                if (mp != null && !mp.isPlaying) {
                    mp.play()
                }
            }
        } catch (e: Throwable) {
            Log.e("PlayerViewModel", "Failed to initialize LibVLC, enabling fallback", e)
            try {
                mediaPlayer?.stop()
                mediaPlayer?.detachViews()
            } catch (ex: Exception) {
                Log.e("PlayerViewModel", "Error detaching on LibVLC init failure", ex)
            }
            _useFallbackPlayer.value = true
            _errorState.value = "VLC Oynatıcı başlatılamadı: ${e.localizedMessage}"
        }
    }

    fun play() {
        try {
            mediaPlayer?.play()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Play error", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Pause error", e)
        }
    }

    fun togglePlayPause() {
        try {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                mp.play()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Toggle play error", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.time = positionMs
            _currentPosition.value = positionMs
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Seek error", e)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error stopping LibVLC", e)
        }
    }

    fun switchToFallback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error stopping LibVLC on switchToFallback", e)
        }
        _useFallbackPlayer.value = true
    }

    override fun onCleared() {
        super.onCleared()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            libVLC?.release()
            libVLC = null
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error clearing LibVLC resources", e)
        }
    }
}
