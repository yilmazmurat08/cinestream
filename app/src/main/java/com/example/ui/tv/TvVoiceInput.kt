package com.example.ui.tv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Uygulama içi sesli giriş (SpeechRecognizer). Sistemin ses arama ekranı açılmaz: bazı TV'lerde o ekran genel
 * aramaya (ör. YouTube) gidiyordu. Konuşma bitince metin [onResult] ile asistana gönderilir. Ses tanıma yoksa ya
 * da mikrofon izni verilmezse [onUnavailable] çağrılır (ekran klavyesine geçilir).
 */
class TvVoiceInput internal constructor(val available: Boolean) {
    var listening by mutableStateOf(false)
        internal set
    var partialText by mutableStateOf("")
        internal set
    var level by mutableFloatStateOf(0f)
        internal set
    internal var onStart: () -> Unit = {}
    internal var onStop: () -> Unit = {}

    fun start() = onStart()
    fun stop() = onStop()
}

@Composable
fun rememberTvVoiceInput(onResult: (String) -> Unit, onUnavailable: () -> Unit): TvVoiceInput {
    val context = LocalContext.current
    val latestResult by rememberUpdatedState(onResult)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    val available = remember(context) { runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false) }
    val input = remember { TvVoiceInput(available) }
    val recognizerHolder = remember { arrayOfNulls<SpeechRecognizer>(1) }
    val language = remember(context) {
        if (com.example.util.LocaleHelper.getSavedLanguage(context) == "en") "en-US" else "tr-TR"
    }

    fun beginListening() {
        val recognizer = recognizerHolder[0] ?: runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            ?.also { recognizerHolder[0] = it }
        if (recognizer == null) {
            latestUnavailable()
            return
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                input.listening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                input.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                input.listening = false
                input.partialText = ""
            }
            override fun onResults(results: Bundle?) {
                input.listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                input.partialText = ""
                if (text.isNotEmpty()) latestResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { input.partialText = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        input.partialText = ""
        input.listening = true
        runCatching { recognizer.startListening(intent) }.onFailure {
            input.listening = false
            latestUnavailable()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening() else latestUnavailable()
    }

    input.onStart = {
        when {
            !available -> latestUnavailable()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> beginListening()
            else -> runCatching { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }.onFailure { latestUnavailable() }
        }
    }
    input.onStop = {
        runCatching { recognizerHolder[0]?.stopListening() }
        input.listening = false
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recognizerHolder[0]?.destroy() }
            recognizerHolder[0] = null
        }
    }
    return input
}

/** "Dinliyorum…" cam kartı: konuşulan metin anlık görünür; ses seviyesine göre mikrofon nabız gibi büyür. */
@Composable
fun TvListeningCard(voice: TvVoiceInput, modifier: Modifier = Modifier) {
    val state = voice
    if (!state.listening) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(min = 360.dp, max = 640.dp)
            .tvGlass(TvTheme.CardShape, TvTheme.GlassDark)
            .padding(horizontal = 24.dp, vertical = 18.dp)
            .testTag("tv_ai_listening")
    ) {
        Icon(Icons.Filled.Mic, null, tint = TvTheme.FocusGlow, modifier = Modifier.size(34.dp).scale(1f + state.level * 0.35f))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.tv_ai_listening), color = TvTheme.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (state.partialText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(state.partialText, color = TvTheme.TextSecondary, fontSize = 17.sp, maxLines = 3)
            }
        }
    }
}
