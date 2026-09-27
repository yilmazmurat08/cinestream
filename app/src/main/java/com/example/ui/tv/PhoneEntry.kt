package com.example.ui.tv

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * "Telefonla gir": TV'de uzun metin (Gemini anahtarı, M3U adresi, Xtream bilgileri) yazmak zor olduğu için
 * TV ekranda bir QR kod gösterir. Telefon QR'ı okutunca, TV'nin kendi içinde çalışan küçük bir web sayfası
 * açılır; bilgiler oraya yapıştırılıp gönderilir. Sunucu yok, hesap yok: bilgiler evdeki ağın dışına çıkmaz.
 * Sayfa tek kullanımlık bir kodla korunur ve pencere kapanınca kapanır.
 */
enum class PhoneEntryMode { GEMINI_KEY, PLAYLIST }

enum class PhoneEntryError { INVALID_KEY, EMPTY_PLAYLIST }

data class PhoneEntryData(
    val geminiKey: String? = null,
    val m3uUrl: String? = null,
    val epgUrl: String? = null,
    val xtreamHost: String? = null,
    val xtreamUser: String? = null,
    val xtreamPass: String? = null
) {
    companion object {
        /** Biçim kontrolü: boşluk içermeyen, makul uzunlukta bir anahtar. */
        fun looksLikeApiKey(key: String): Boolean = key.length in 20..200 && key.none { it.isWhitespace() }

        /** İsim girilmediyse adresteki sunucu adını liste adı olarak kullanır. */
        fun defaultName(url: String): String {
            val host = try {
                java.net.URI(url.trim()).host
            } catch (e: Exception) {
                null
            }
            return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: "IPTV"
        }
    }
}

private fun tr(english: Boolean, turkish: String, englishText: String) = if (english) englishText else turkish

// ----------------------------------------------------------------------------------------------
// Yerel mini web sunucusu
// ----------------------------------------------------------------------------------------------

class PhoneEntryServer(
    private val mode: PhoneEntryMode,
    private val english: Boolean,
    private val onSubmit: suspend (PhoneEntryData) -> PhoneEntryError?
) {
    val token: String = randomToken()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var address: String? = null

    /** Sunucuyu başlatır. Ağ yoksa ya da port açılamazsa false döner. */
    fun start(scope: CoroutineScope): Boolean {
        val ip = localIpv4Address() ?: return false
        val socket = try {
            ServerSocket(PREFERRED_PORT)
        } catch (e: Exception) {
            try {
                ServerSocket(0)
            } catch (e2: Exception) {
                return false
            }
        }
        serverSocket = socket
        address = "http://$ip:${socket.localPort}/?t=$token"
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                launch { handle(client) }
            }
        }
        return true
    }

    fun url(): String? = address

    fun stop() {
        acceptJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private suspend fun handle(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.use { socket ->
                socket.soTimeout = 15_000
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val requestLine = readLine(input) ?: return@use
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@use
                val method = parts[0].uppercase()
                val target = parts[1]
                val path = target.substringBefore('?')
                val query = parseForm(target.substringAfter('?', ""))

                if (query["t"] != token) {
                    respond(output, "403 Forbidden", simplePage(tr(english, "Bu bağlantının süresi dolmuş. TV'deki QR kodu yeniden okut.", "This link has expired. Scan the QR code on the TV again.")))
                    return@use
                }
                when {
                    method == "GET" && path == "/" -> respond(output, "200 OK", formPage(null))
                    method == "POST" && path == "/submit" -> {
                        val length = headers["content-length"]?.toIntOrNull() ?: 0
                        if (length <= 0 || length > MAX_BODY_BYTES) {
                            respond(output, "400 Bad Request", formPage(tr(english, "Gönderilen bilgi okunamadı.", "Could not read the data.")))
                            return@use
                        }
                        val body = readExactly(input, length)
                        val form = parseForm(String(body, Charsets.UTF_8))
                        val data = PhoneEntryData(
                            geminiKey = form["gemini_key"],
                            m3uUrl = form["m3u_url"],
                            epgUrl = form["epg_url"],
                            xtreamHost = form["xtream_host"],
                            xtreamUser = form["xtream_user"],
                            xtreamPass = form["xtream_pass"]
                        )
                        val error = onSubmit(data)
                        if (error == null) {
                            respond(output, "200 OK", simplePage(tr(english, "Gönderildi ✓ TV ekranına bakabilirsin.", "Sent ✓ Check your TV screen.")))
                        } else {
                            respond(output, "200 OK", formPage(errorMessage(error, english)))
                        }
                    }
                    else -> respond(output, "404 Not Found", simplePage("404"))
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---------------- HTML ----------------

    private fun formPage(error: String?): String {
        val fields = when (mode) {
            PhoneEntryMode.GEMINI_KEY -> """
                <label>${tr(english, "Gemini API anahtarı", "Gemini API key")}</label>
                <input name="gemini_key" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="AIza..." required>
                <p class="hint">${tr(english, "Anahtarın yok mu?", "No key yet?")} <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener">${tr(english, "Google AI Studio'dan ücretsiz al", "Get one free in Google AI Studio")}</a></p>
            """.trimIndent()
            PhoneEntryMode.PLAYLIST -> """
                <p class="hint">${tr(english, "İkisinden birini doldurman yeterli.", "Fill in one of the two.")}</p>
                <h2>M3U</h2>
                <label>${tr(english, "Liste adresi (M3U)", "Playlist URL (M3U)")}</label>
                <input name="m3u_url" type="text" inputmode="url" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="http://...">
                <label>${tr(english, "EPG adresi (isteğe bağlı)", "EPG URL (optional)")}</label>
                <input name="epg_url" type="text" inputmode="url" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="http://...">
                <h2>Xtream Codes</h2>
                <label>${tr(english, "Sunucu adresi", "Server URL")}</label>
                <input name="xtream_host" type="text" inputmode="url" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="http://server.com:8080">
                <label>${tr(english, "Kullanıcı adı", "Username")}</label>
                <input name="xtream_user" type="text" autocomplete="off" autocapitalize="off" spellcheck="false">
                <label>${tr(english, "Şifre", "Password")}</label>
                <input name="xtream_pass" type="text" autocomplete="off" autocapitalize="off" spellcheck="false">
            """.trimIndent()
        }
        val title = when (mode) {
            PhoneEntryMode.GEMINI_KEY -> tr(english, "Gemini anahtarını TV'ye gönder", "Send your Gemini key to the TV")
            PhoneEntryMode.PLAYLIST -> tr(english, "IPTV listeni TV'ye gönder", "Send your IPTV playlist to the TV")
        }
        val errorHtml = if (error != null) "<p class=\"error\">${escapeHtml(error)}</p>" else ""
        return page(
            title,
            """
            <h1>$title</h1>
            $errorHtml
            <form method="post" action="/submit?t=$token" accept-charset="utf-8">
            $fields
            <button type="submit">${tr(english, "TV'ye gönder", "Send to TV")}</button>
            </form>
            """.trimIndent()
        )
    }

    private fun simplePage(message: String): String =
        page("CineStream", "<h1>CineStream</h1><p class=\"big\">${escapeHtml(message)}</p>")

    private fun page(title: String, body: String): String = """
        <!doctype html>
        <html lang="${if (english) "en" else "tr"}">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escapeHtml(title)}</title>
        <style>
        body{margin:0;padding:24px 18px;background:#120d1c;color:#f3eefb;font-family:system-ui,-apple-system,Roboto,sans-serif}
        h1{font-size:22px;margin:0 0 16px}
        h2{font-size:16px;margin:22px 0 6px;color:#e879f9}
        label{display:block;font-size:14px;margin:14px 0 6px;color:#c9bfdc}
        input{box-sizing:border-box;width:100%;padding:14px;font-size:16px;border-radius:12px;border:1px solid #3a2d52;background:#1d1530;color:#fff}
        button{margin-top:22px;width:100%;padding:16px;font-size:17px;font-weight:700;border:0;border-radius:14px;background:#c026d3;color:#fff}
        a{color:#e879f9}
        .hint{font-size:13px;color:#a99cc2}
        .error{background:#3b1520;border:1px solid #9f1239;padding:12px;border-radius:12px}
        .big{font-size:18px;line-height:1.5}
        </style>
        </head>
        <body>
        $body
        </body>
        </html>
    """.trimIndent()

    companion object {
        private const val PREFERRED_PORT = 8765
        private const val MAX_BODY_BYTES = 16 * 1024

        fun errorMessage(error: PhoneEntryError, english: Boolean): String = when (error) {
            PhoneEntryError.INVALID_KEY -> tr(english, "Anahtar geçersiz görünüyor. Tamamını kopyalayıp tekrar gönder.", "The key looks invalid. Copy the whole key and send it again.")
            PhoneEntryError.EMPTY_PLAYLIST -> tr(english, "Liste adresi ya da Xtream bilgileri boş geldi.", "The playlist URL or Xtream details were empty.")
        }

        private fun randomToken(): String {
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            val random = SecureRandom()
            return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
        }

        /** TV'nin evdeki ağdaki adresi (ör. 192.168.1.23). Önce Wi-Fi/Ethernet arayüzleri tercih edilir. */
        fun localIpv4Address(): String? {
            val candidates = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { nif ->
                        nif.inetAddresses.toList()
                            .filterIsInstance<Inet4Address>()
                            .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                            .mapNotNull { address -> address.hostAddress?.let { nif.name to it } }
                    }
            } catch (e: Exception) {
                emptyList()
            }
            val preferred = candidates.firstOrNull { (name, _) -> name.startsWith("wlan") || name.startsWith("eth") }
            return (preferred ?: candidates.firstOrNull())?.second
        }

        private fun readLine(input: InputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
                if (b == '\n'.code) break
                if (b != '\r'.code) buffer.write(b)
                if (buffer.size() > 8192) return null
            }
            return buffer.toString("UTF-8")
        }

        private fun readExactly(input: InputStream, length: Int): ByteArray {
            val data = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(data, read, length - read)
                if (n <= 0) break
                read += n
            }
            return data.copyOf(read)
        }

        private fun parseForm(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            return raw.split("&").mapNotNull { pair ->
                val key = pair.substringBefore('=')
                if (key.isBlank()) return@mapNotNull null
                val value = pair.substringAfter('=', "")
                try {
                    URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
                } catch (e: Exception) {
                    null
                }
            }.toMap()
        }

        private fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        private fun respond(output: OutputStream, status: String, html: String) {
            val bytes = html.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $status\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"
            output.write(header.toByteArray(Charsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Arayüz
// ----------------------------------------------------------------------------------------------

/** QR kodu çizer (ZXing ile üretilir, Canvas'a kare kare çizilir). */
@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val matrix: BitMatrix? = remember(content) {
        try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            null
        }
    }
    Canvas(modifier = modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}

/** Kurulum ve Ayarlar ekranlarındaki "📱 Telefonla gir" düğmesi. */
@Composable
fun PhoneEntryButton(mode: PhoneEntryMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val english = remember { com.example.util.LocaleHelper.getSavedLanguage(context) == "en" }
    val label = when (mode) {
        PhoneEntryMode.GEMINI_KEY -> tr(english, "📱 Anahtarı telefonla gir", "📱 Enter key from phone")
        PhoneEntryMode.PLAYLIST -> tr(english, "📱 Liste bilgilerini telefonla gir", "📱 Enter playlist from phone")
    }
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("phone_entry_button_${mode.name.lowercase()}")
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private enum class PhoneEntryStatus { WAITING, CHECKING, DONE }

/**
 * QR penceresi. Telefon bilgileri gönderince onReceived çağrılır: null dönerse başarı (pencere kapanır),
 * PhoneEntryError dönerse hem TV'de hem telefonda hata gösterilir ve yeniden gönderim beklenir.
 */
@Composable
fun PhoneEntryDialog(
    mode: PhoneEntryMode,
    onReceived: (PhoneEntryData) -> PhoneEntryError?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val english = remember { com.example.util.LocaleHelper.getSavedLanguage(context) == "en" }
    val scope = rememberCoroutineScope()
    val incoming = remember { Channel<Pair<PhoneEntryData, CompletableDeferred<PhoneEntryError?>>>(Channel.UNLIMITED) }
    val server = remember(mode) {
        PhoneEntryServer(mode, english) { data ->
            val reply = CompletableDeferred<PhoneEntryError?>()
            incoming.send(data to reply)
            reply.await()
        }
    }
    var url by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(PhoneEntryStatus.WAITING) }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(server) {
        val started = server.start(scope)
        url = if (started) server.url() else null
        failed = !started || url == null
        onDispose { server.stop() }
    }

    LaunchedEffect(incoming) {
        for ((data, reply) in incoming) {
            status = PhoneEntryStatus.CHECKING
            val error = onReceived(data)
            reply.complete(error)
            if (error == null) {
                errorText = null
                status = PhoneEntryStatus.DONE
                delay(1500)
                onDismiss()
            } else {
                errorText = PhoneEntryServer.errorMessage(error, english)
                status = PhoneEntryStatus.WAITING
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF160E1E)),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .testTag("phone_entry_dialog")
        ) {
            Column(
                modifier = Modifier
                    // İçerik küçük/yatay ekranda veya büyük yazı boyutunda sığmazsa kaydırılabilsin.
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tr(english, "Telefonla gir", "Enter from your phone"),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (mode) {
                        PhoneEntryMode.GEMINI_KEY -> tr(english, "Gemini API anahtarını telefonundan gönder.", "Send your Gemini API key from your phone.")
                        PhoneEntryMode.PLAYLIST -> tr(english, "M3U ya da Xtream bilgilerini telefonundan gönder.", "Send your M3U or Xtream details from your phone.")
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                val currentUrl = url
                if (failed || currentUrl == null) {
                    Text(
                        text = tr(
                            english,
                            "Ağ bağlantısı bulunamadı. TV'nin Wi-Fi'a ya da kabloyla internete bağlı olduğundan emin ol.",
                            "No network connection found. Make sure the TV is connected to Wi-Fi or Ethernet."
                        ),
                        color = Color(0xFFF59E0B),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    ) {
                        QrCodeImage(content = currentUrl, modifier = Modifier.size(220.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tr(english, "Aynı Wi-Fi ağında olduğunuza emin olun.", "Make sure you are on the same Wi-Fi network."),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = tr(
                            english,
                            "1. Telefonunun kamerasıyla QR kodu okut.\n2. Açılan sayfada bilgileri yapıştır, \"TV'ye gönder\"e bas.",
                            "1. Scan the QR code with your phone camera.\n2. Paste your details on the page and tap \"Send to TV\"."
                        ),
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tr(english, "QR okutamıyorsan tarayıcıya yaz: ", "Can't scan? Type in a browser: ") + currentUrl,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (status) {
                            PhoneEntryStatus.WAITING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFE879F9)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tr(english, "Telefondan bekleniyor…", "Waiting for your phone…"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            }
                            PhoneEntryStatus.CHECKING -> Text(tr(english, "Alındı, kontrol ediliyor…", "Received, checking…"), color = Color.White, fontSize = 13.sp)
                            PhoneEntryStatus.DONE -> Text(tr(english, "Kaydedildi ✓", "Saved ✓"), color = Color(0xFF4ADE80), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    errorText?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = Color(0xFFF87171), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("phone_entry_close")) {
                    Text(tr(english, "Kapat", "Close"), color = Color(0xFFE879F9), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
