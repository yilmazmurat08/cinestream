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
    fillArea: Boolean = false,
    onEnded: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val latestEnded by rememberUpdatedState(onEnded)
    val latestError by rememberUpdatedState(onError)
    var started by remember(videoId) { mutableStateOf(false) }
    var failed by remember(videoId) { mutableStateOf(false) }
    val webHolder = remember { arrayOfNulls<WebView>(1) }

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
                    fillArea = fillArea,
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
                    onRendererGone = {
                        // WebView yok edildi: yaşam döngüsü ve temizlik artık ona dokunmaz.
                        webHolder[0] = null
                        if (!failed) {
                            failed = true
                            latestError()
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
    }
}

private const val JS_BRIDGE = "CineTrailer"
private const val YT_ENDED = 0
private const val YT_PLAYING = 1
private const val TRAILER_START_TIMEOUT_MS = 15_000L

/**
 * Fragman WebView'inin istemcisi. Bağlantılar açılmaz (YouTube uygulamasına gidilmez). WebView'in iç işlemi
 * (renderer) bellek yetersizliğinden kapanırsa ve bu olay karşılanmazsa Android TÜM uygulamayı kapatır: ölü WebView
 * görünümden kaldırılıp yok edilir, fragman hata olarak bildirilir (yerinde kapak görseli kalır).
 */
internal class TrailerWebViewClient(private val onRendererGone: () -> Unit) : WebViewClient() {
    // YouTube logosu vb. tıklansa bile sayfadan çıkılmaz (YouTube uygulaması açılmaz).
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
        view?.let { dead ->
            runCatching { (dead.parent as? ViewGroup)?.removeView(dead) }
            runCatching { dead.destroy() }
        }
        onRendererGone()
        return true
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createTrailerWebView(
    context: Context,
    videoId: String,
    showControls: Boolean,
    fillArea: Boolean,
    onState: (Int) -> Unit,
    onError: () -> Unit,
    onRendererGone: () -> Unit
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
        webViewClient = TrailerWebViewClient(onRendererGone)
        addJavascriptInterface(object {
            @JavascriptInterface
            fun state(value: Int) {
                main.post { onState(value) }
            }

            @JavascriptInterface
            fun error(code: Int) {
                main.post { onError() }
            }
        }, JS_BRIDGE)
        // Görünüm boyutu değişince (ilk yerleşim dahil) oynatıcı kutusu yeniden boyutlandırılır.
        addOnLayoutChangeListener { view, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) (view as WebView).evaluateJavascript("if (window.fitPlayer) fitPlayer();", null)
        }
        val origin = "https://${context.packageName}"
        loadDataWithBaseURL(origin, trailerHtml(videoId, showControls, origin, fillArea), "text/html", "utf-8", null)
    }
}.getOrNull()

/** YouTube IFrame API sayfası. Sadece video kimliği içindeki güvenli karakterler kullanılır. */
internal fun trailerHtml(videoId: String, showControls: Boolean, origin: String, fillArea: Boolean = false): String {
    val safeId = videoId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    val controls = if (showControls) 1 else 0
    val cover = if (fillArea) "true" else "false"
    return """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}#player,iframe{position:fixed;top:0;left:0;width:100vw;height:100vh;border:0}</style>
</head><body>
<div id="player"></div>
<script>
var player;
var COVER = $cover;
// Android WebView'da yüzde yükseklik bazen 0 hesaplanıyor (ses var, görüntü yok). Oynatıcı kutusu her zaman
// görünen alanın piksel boyutuna ayarlanır: açılışta, boyut değişince ve uygulama istediğinde (fitPlayer).
function fitPlayer(){
  try {
    var w = window.innerWidth || document.documentElement.clientWidth;
    var h = window.innerHeight || document.documentElement.clientHeight;
    if (!w || !h) return;
    // COVER: video (16:9) alanı tamamen kaplar, taşan kenarlar kırpılır (siyah bant kalmaz).
    var vw = w, vh = h;
    if (COVER) {
      if (w / h > 16 / 9) { vh = Math.ceil(w * 9 / 16); } else { vw = Math.ceil(h * 16 / 9); }
    }
    var f = document.querySelector('iframe') || document.getElementById('player');
    if (f) {
      f.style.setProperty('width', vw + 'px', 'important');
      f.style.setProperty('height', vh + 'px', 'important');
      f.style.setProperty('left', Math.round((w - vw) / 2) + 'px', 'important');
      f.style.setProperty('top', Math.round((h - vh) / 2) + 'px', 'important');
    }
    if (player && player.setSize) player.setSize(vw, vh);
  } catch(e) {}
}
window.addEventListener('resize', fitPlayer);
var fitTimer = setInterval(fitPlayer, 500);
setTimeout(function(){ clearInterval(fitTimer); }, 15000);
function pauseTrailer(){ try { if (player) player.pauseVideo(); } catch(e) {} }
function onYouTubeIframeAPIReady(){
  player = new YT.Player('player', {
    width: '100%', height: '100%', videoId: '$safeId',
    playerVars: { autoplay: 1, controls: $controls, playsinline: 1, rel: 0, modestbranding: 1, iv_load_policy: 3, fs: 0, disablekb: 1, origin: '$origin' },
    events: {
      onReady: function(e){ fitPlayer(); e.target.playVideo(); },
      onStateChange: function(e){ fitPlayer(); $JS_BRIDGE.state(e.data); },
      onError: function(e){ $JS_BRIDGE.error(e.data); }
    }
  });
}
</script>
<script src="https://www.youtube.com/iframe_api" onerror="$JS_BRIDGE.error(-1)"></script>
</body></html>
""".trimIndent()
}
