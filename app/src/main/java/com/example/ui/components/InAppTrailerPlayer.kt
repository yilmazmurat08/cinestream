package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Fragmanı YouTube uygulamasına gitmeden, bulunduğu kartın/posterin içinde oynatır (Netflix tarzı).
 *
 * Resmi YouTube IFrame oynatıcısı bir WebView içinde çalışır. YouTube, gömülü oynatıcının kimliğini HTTP Referer
 * üzerinden ister (yoksa "Hata 153"); bu yüzden sayfa, uygulama kimliğiyle ("https://<paket adı>") yüklenir.
 * Video bitince [onEnded], oynatılamazsa (gömme kapalı, WebView yok, zaman aşımı) [onError] çağrılır.
 * Oynatıcı içindeki bağlantılar açılmaz; kullanıcı uygulamadan çıkmaz.
 */
@Composable
fun InAppTrailerPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    onEnded: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val latestEnded by rememberUpdatedState(onEnded)
    val latestError by rememberUpdatedState(onError)
    var started by remember(videoId) { mutableStateOf(false) }
    var failed by remember(videoId) { mutableStateOf(false) }
    val webHolder = remember { arrayOfNulls<WebView>(1) }
    var diagnostics by remember(videoId) { mutableStateOf("") }

    // Yavaş bağlantıda sonsuza dek siyah kutu kalmasın.
    LaunchedEffect(videoId) {
        delay(TRAILER_START_TIMEOUT_MS)
        if (!started && !failed) {
            failed = true
            latestError()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val web = webHolder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { web.evaluateJavascript("pauseTrailer()", null); web.onPause() }
                Lifecycle.Event.ON_RESUME -> runCatching { web.onResume() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webHolder[0]?.let { web ->
                runCatching {
                    web.stopLoading()
                    web.loadUrl("about:blank")
                    (web.parent as? ViewGroup)?.removeView(web)
                    web.removeJavascriptInterface(JS_BRIDGE)
                    web.destroy()
                }
            }
            webHolder[0] = null
        }
    }

    Box(modifier.background(Color.Black).testTag("in_app_trailer")) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                createTrailerWebView(
                    context = context,
                    videoId = videoId,
                    showControls = showControls,
                    onState = { state ->
                        when (state) {
                            YT_PLAYING -> started = true
                            YT_ENDED -> latestEnded()
                        }
                    },
                    onError = {
                        if (!failed) {
                            failed = true
                            latestError()
                        }
                    },
                    onDiagnostics = { page ->
                        val web = webHolder[0]
                        diagnostics = buildString {
                            append("WV ").append(webViewVersion()).append(" · HW ").append(web?.isHardwareAccelerated)
                            append(" · layer ").append(web?.layerType).append(" · view ").append(web?.width).append("x").append(web?.height)
                            append(" · ").append(page)
                        }
                    }
                )?.also { webHolder[0] = it } ?: View(context).also {
                    // Cihazda WebView yoksa (bazı TV'ler) çökmek yerine hata bildirilir.
                    Handler(Looper.getMainLooper()).post {
                        if (!failed) {
                            failed = true
                            latestError()
                        }
                    }
                }
            }
        )
        // Sadece test (qa) sürümünde: görüntü gelmezse nedenini bulmak için oynatıcı durumu.
        if (com.example.BuildConfig.SHOW_DIAGNOSTICS && diagnostics.isNotEmpty()) {
            androidx.compose.material3.Text(
                diagnostics,
                color = Color.Yellow,
                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .testTag("in_app_trailer_diagnostics")
            )
        }
    }
}

private fun webViewVersion(): String =
    runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "?"

private const val JS_BRIDGE = "CineTrailer"
private const val YT_ENDED = 0
private const val YT_PLAYING = 1
private const val TRAILER_START_TIMEOUT_MS = 15_000L

@SuppressLint("SetJavaScriptEnabled")
private fun createTrailerWebView(
    context: Context,
    videoId: String,
    showControls: Boolean,
    onState: (Int) -> Unit,
    onError: () -> Unit,
    onDiagnostics: (String) -> Unit = {}
): WebView? = runCatching {
    val main = Handler(Looper.getMainLooper())
    WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        // Video karelerinin çizilmesi için WebView kendi donanım katmanına çizilir. Bu olmadan bazı cihazlarda (ve
        // animasyonlu Compose ekranlarının içinde) ses gelir ama görüntü siyah kalır.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        // TV'de kumanda odağı Compose düğmelerinde kalır; WebView odak almaz.
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = object : WebChromeClient() {
            // Varsayılan gri "oynat" afişi yerine video yüklenene kadar siyah görünür.
            override fun getDefaultVideoPoster(): android.graphics.Bitmap? =
                super.getDefaultVideoPoster() ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.RGB_565)
        }
        webViewClient = object : WebViewClient() {
            // YouTube logosu vb. tıklansa bile sayfadan çıkılmaz (YouTube uygulaması açılmaz).
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
        }
        addJavascriptInterface(object {
            @JavascriptInterface
            fun state(value: Int) {
                main.post { onState(value) }
            }

            @JavascriptInterface
            fun error(code: Int) {
                main.post { onError() }
            }

            @JavascriptInterface
            fun diag(text: String) {
                main.post { onDiagnostics(text.take(200)) }
            }
        }, JS_BRIDGE)
        val origin = "https://${context.packageName}"
        loadDataWithBaseURL(origin, trailerHtml(videoId, showControls, origin), "text/html", "utf-8", null)
    }
}.getOrNull()

/** YouTube IFrame API sayfası. Sadece video kimliği içindeki güvenli karakterler kullanılır. */
internal fun trailerHtml(videoId: String, showControls: Boolean, origin: String): String {
    val safeId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    val controls = if (showControls) 1 else 0
    return """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}#player{position:absolute;top:0;left:0;width:100%;height:100%}</style>
</head><body>
<div id="player"></div>
<script>
var player;
function reportDiag(tag){
  try {
    var f = document.querySelector('iframe');
    var size = f ? (f.offsetWidth + 'x' + f.offsetHeight) : '-';
    $JS_BRIDGE.diag(tag + ' vis=' + document.visibilityState + ' win=' + innerWidth + 'x' + innerHeight + ' iframe=' + size);
  } catch(e) {}
}
document.addEventListener('visibilitychange', function(){ reportDiag('visibility'); });
function pauseTrailer(){ try { if (player) player.pauseVideo(); } catch(e) {} }
function onYouTubeIframeAPIReady(){
  player = new YT.Player('player', {
    width: '100%', height: '100%', videoId: '$safeId',
    playerVars: { autoplay: 1, controls: $controls, playsinline: 1, rel: 0, modestbranding: 1, iv_load_policy: 3, fs: 0, disablekb: 1, origin: '$origin' },
    events: {
      onReady: function(e){ e.target.playVideo(); },
      onStateChange: function(e){ $JS_BRIDGE.state(e.data); reportDiag('state' + e.data); },
      onError: function(e){ $JS_BRIDGE.error(e.data); }
    }
  });
}
</script>
<script src="https://www.youtube.com/iframe_api" onerror="$JS_BRIDGE.error(-1)"></script>
</body></html>
""".trimIndent()
}
