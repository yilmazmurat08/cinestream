package com.example

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test için IPTV sunucusu: bir video dosyasını (Range istekleriyle) sunar. Her isteğin nasıl
 * davranacağına [behavior] karar verir: normal, yavaş, ortada donan, hata veren ya da hiç cevap vermeyen.
 */
class FakeStreamServer(private val bytes: ByteArray, private val contentType: String) {

    sealed class Behavior {
        /** Dosyayı hemen gönderir. */
        object Normal : Behavior()
        /** Saniyede [bytesPerSecond] bayt gönderir (yavaş internet). */
        data class Slow(val bytesPerSecond: Long) : Behavior()
        /** İlk [bytesBeforeStall] baytı gönderir, sonra uzun süre hiçbir şey göndermez (askıda kalan sunucu). */
        data class StallAfter(val bytesBeforeStall: Long) : Behavior()
        /** HTTP hata kodu döner. */
        data class Error(val code: Int) : Behavior()
        /** Bağlantıyı kabul eder ama hiç cevap vermez. */
        object NoResponse : Behavior()
        /** Bağlantıyı hemen koparır. */
        object Disconnect : Behavior()
    }

    val server = MockWebServer()
    val requests = AtomicInteger(0)

    /** İstek sırasına (1'den başlar) göre davranış. */
    @Volatile
    var behavior: (requestNumber: Int) -> Behavior = { Behavior.Normal }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request, behavior(requests.incrementAndGet()))
        }
    }

    fun url(path: String): String = server.url(path).toString()

    fun shutdown() {
        try {
            server.shutdown()
        } catch (_: Exception) {
        }
    }

    private fun respond(request: RecordedRequest, b: Behavior): MockResponse {
        when (b) {
            is Behavior.Error -> return MockResponse().setResponseCode(b.code)
            Behavior.NoResponse -> return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            Behavior.Disconnect -> return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
            else -> Unit
        }
        val (start, end) = range(request.getHeader("Range"))
        val body = Buffer().write(bytes, start.toInt(), (end - start + 1).toInt())
        val response = MockResponse()
            .setHeader("Content-Type", contentType)
            .setHeader("Accept-Ranges", "bytes")
        if (request.getHeader("Range") != null) {
            response.setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
        }
        response.setBody(body)
        when (b) {
            is Behavior.Slow -> response.throttleBody(maxOf(1, b.bytesPerSecond / 10), 100, TimeUnit.MILLISECONDS)
            // İlk parça hemen gelir, sonraki parça için çok uzun beklenir.
            is Behavior.StallAfter -> response.throttleBody(b.bytesBeforeStall, 10, TimeUnit.MINUTES)
            else -> Unit
        }
        return response
    }

    private fun range(header: String?): Pair<Long, Long> {
        val last = bytes.size - 1L
        val m = header?.let { Regex("bytes=(\\d+)-(\\d*)").find(it) } ?: return 0L to last
        val start = m.groupValues[1].toLong().coerceIn(0, last)
        val end = m.groupValues[2].toLongOrNull()?.coerceIn(start, last) ?: last
        return start to end
    }
}

/**
 * Ham soketle çalışan test sunucusu (MockWebServer'ın yapamadığı durumlar için). Her bağlantının nasıl
 * davranacağına [plan] karar verir:
 * - [Plan.Healthy]: dosyayı (istenen yerden) gönderir.
 * - [Plan.HangAfter]: [Plan.HangAfter.bytes] bayt gönderir, sonra bağlantıyı açık tutup hiçbir şey göndermez.
 * - [Plan.TrickleAfter]: sonra 2 sn'de bir tek bayt gönderir (bağlantı canlı, zaman aşımı dolmaz, görüntü gelmez).
 * - [Plan.FillerAfter]: sonra durmadan boş MPEG-TS paketi gönderir (kaynağı düşen canlı yayın).
 * - [Plan.NoResponse]: bağlantıyı kabul eder, hiç cevap vermez.
 * [live] ise dosya uzunluğu bildirilmez ve hep baştan gönderilir (canlı yayın gibi); değilse Range desteklenir.
 */
class RawStreamServer(private val bytes: ByteArray, private val contentType: String, private val live: Boolean) {
    sealed class Plan {
        object Healthy : Plan()
        data class HangAfter(val bytes: Int) : Plan()
        data class TrickleAfter(val bytes: Int) : Plan()
        data class FillerAfter(val bytes: Int) : Plan()
        object NoResponse : Plan()
    }

    @Volatile
    var plan: (connection: Int) -> Plan = { Plan.Healthy }
    val connections = AtomicInteger(0)
    private val socket = java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    @Volatile private var closed = false
    private val open = java.util.Collections.synchronizedList(mutableListOf<java.net.Socket>())

    init {
        Thread({
            while (!closed) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                open += client
                val n = connections.incrementAndGet()
                val p = plan(n)
                Thread({ serve(client, p) }, "RawStreamServer-$n").apply { isDaemon = true }.start()
            }
        }, "RawStreamServer").apply { isDaemon = true }.start()
    }

    fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"

    private fun serve(client: java.net.Socket, p: Plan) {
        try {
            val input = client.getInputStream().bufferedReader()
            var rangeStart = 0
            while (true) {
                val line = input.readLine() ?: return
                if (line.isEmpty()) break
                if (!live && line.startsWith("Range:", ignoreCase = true)) {
                    rangeStart = Regex("bytes=(\\d+)-").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
            }
            if (p == Plan.NoResponse) {
                while (!closed && !client.isClosed) Thread.sleep(100)
                return
            }
            val out = client.getOutputStream()
            val start = rangeStart.coerceIn(0, bytes.size)
            val header = StringBuilder()
            if (live) {
                header.append("HTTP/1.1 200 OK\r\n")
            } else if (start > 0) {
                header.append("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-${bytes.size - 1}/${bytes.size}\r\n")
            } else {
                header.append("HTTP/1.1 200 OK\r\n")
            }
            if (!live) header.append("Content-Length: ${bytes.size - start}\r\nAccept-Ranges: bytes\r\n")
            header.append("Content-Type: $contentType\r\nConnection: close\r\n\r\n")
            out.write(header.toString().toByteArray())
            val firstPart = when (p) {
                is Plan.HangAfter -> p.bytes
                is Plan.TrickleAfter -> p.bytes
                is Plan.FillerAfter -> p.bytes
                else -> Int.MAX_VALUE
            }
            val end = minOf(bytes.size.toLong(), start.toLong() + firstPart).toInt()
            out.write(bytes, start, end - start)
            out.flush()
            when (p) {
                is Plan.HangAfter -> while (!closed && !client.isClosed) Thread.sleep(100)
                is Plan.TrickleAfter -> {
                    var pos = end
                    while (!closed && !client.isClosed && pos < bytes.size) {
                        Thread.sleep(2_000)
                        out.write(bytes, pos, 1)
                        out.flush()
                        pos++
                    }
                }
                is Plan.FillerAfter -> {
                    val nullPacket = ByteArray(188) { 0xFF.toByte() }.also {
                        it[0] = 0x47; it[1] = 0x1F; it[2] = 0xFF.toByte(); it[3] = 0x10
                    }
                    while (!closed && !client.isClosed) {
                        out.write(nullPacket)
                        out.flush()
                        Thread.sleep(20)
                    }
                }
                else -> Unit
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun shutdown() {
        closed = true
        try { socket.close() } catch (_: Exception) {}
        synchronized(open) { open.forEach { try { it.close() } catch (_: Exception) {} } }
    }
}
