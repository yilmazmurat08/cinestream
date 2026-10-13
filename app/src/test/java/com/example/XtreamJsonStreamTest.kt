package com.example

import com.example.data.api.XtreamJsonStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Reader
import java.io.StringReader

/**
 * Xtream listelerini parça parça okuyan okuyucu, eski yöntemle (tüm yanıt → JSONArray) birebir aynı öğeleri,
 * aynı sırayla ve aynı değerlerle vermeli; bozuk yanıtta eskisi gibi hata vermeli.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XtreamJsonStreamTest {

    private fun streamed(text: String): Pair<List<String>, XtreamJsonStream.Summary> {
        val objects = mutableListOf<String>()
        val summary = XtreamJsonStream.forEachObject(StringReader(text)) { objects.add(it.toString()) }
        return objects to summary
    }

    private fun assertSameAsJsonArray(text: String) {
        val array = JSONArray(text)
        val expected = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toString() }
        val (actual, summary) = streamed(text)
        assertNull("dizi geçerli sayılmalı: $text", summary.invalidPreview)
        assertEquals("öğeler aynı olmalı: $text", expected, actual)
        assertEquals("öğe sayısı aynı olmalı: $text", array.length(), summary.total)
    }

    @Test
    fun givesExactlyTheSameObjectsAsJsonArray() {
        listOf(
            "[]",
            " \n [ ] ",
            """[{"a":1}]""",
            """[{"stream_id":7,"name":"Kanal 7","category_id":"1"},{"stream_id":8,"name":"Kanal 8","category_id":2}]""",
            // Metin içinde köşeli/süslü parantez, virgül, kaçışlı tırnak ve ters bölü
            """[{"name":"A, [B] {C}","x":"q\"uote\\","y":"\\"},{"n":"O'Reilly ; ]"}]""",
            """[{"name":"Çocuk Şarkı 🎬"}]""",
            // Sayı/metin/null/mantıksal karışık değerler (optInt/optString davranışı aynı kalmalı)
            """[{"stream_id":"123","rating":"7.5","n":null,"b":true,"d":5.0,"big":12345678901,"neg":-3,"exp":1e3}]""",
            // İç içe dizi/nesne
            """[{"backdrop_path":["http://a/b.jpg","c"],"info":{"x":[1,{"y":"]"}],"z":{}}}]""",
            // Nesne olmayan öğeler atlanır
            """[1,"s",null,{"a":1},[2,3],{"b":2},true]""",
            // Boş öğeler (org.json'da null)
            "[1,,2]",
            "[,]",
            """[{"a":1},]""",
            """[,{"a":1}]""",
            // org.json'un esnek söz dizimi: tek tırnak, noktalı virgül
            """[{'a':'b, ]'}, {"c":"d"}]""",
            """[{"a":1};{"b":2}]""",
            "[\n  {\"a\": 1} ,\n\t{\"b\": 2}\r\n]\n",
            // Dizinin ardındaki fazlalık org.json'da da yok sayılır
            """[{"a":1}] fazlalık"""
        ).forEach { assertSameAsJsonArray(it) }
    }

    @Test
    fun largeProviderList_isIdenticalToJsonArray() {
        val text = buildString {
            append('[')
            for (i in 1..5_000) {
                if (i > 1) append(',')
                append("""{"num":$i,"name":"Film $i [${i % 7}] \"özel\", {x}","stream_id":${if (i % 3 == 0) "\"$i\"" else i},""")
                append(""""stream_icon":${if (i % 5 == 0) "null" else "\"http://img/$i.jpg\""},"rating":${i % 10}.${i % 3},""")
                append(""""category_id":"${i % 40}","container_extension":"mkv","backdrop_path":["http://b/$i.jpg"]}""")
            }
            append(']')
        }
        assertSameAsJsonArray(text)
    }

    @Test
    fun brokenJson_failsLikeBefore() {
        listOf(
            """[{"a":1}""",
            """[{"a":1} {"b":2}]""",
            """[{"a":}]""",
            """["kapanmayan]""",
            """[{"a":1}}]"""
        ).forEach { text ->
            assertTrue("eski yöntem hata vermeli: $text", runCatching { JSONArray(text) }.isFailure)
            assertTrue("yeni yöntem de hata vermeli: $text", runCatching { streamed(text) }.isFailure)
        }
    }

    @Test
    fun responseThatIsNotAList_givesNoItemsAndAPreview() {
        listOf("", "   ", """{"user_info":{"auth":0}}""", "<html><body>403 Forbidden</body></html>").forEach { text ->
            val (objects, summary) = streamed(text)
            assertTrue(objects.isEmpty())
            assertNotNull("dizi değil sayılmalı: $text", summary.invalidPreview)
            assertTrue(summary.invalidPreview!!.length <= 300)
        }
    }

    /** Yanıtın tamamı hiçbir zaman belleğe alınmaz: sonsuz akıştan gelen öğeler okundukça verilir. */
    @Test
    fun objectsAreDeliveredWhileTheResponseIsStillBeingRead() {
        val total = 200_000
        val reader = object : Reader() {
            private var index = 0
            private var pending = "["
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                if (pending.isEmpty()) {
                    if (index > total) return -1
                    index++
                    pending = when {
                        index <= total -> (if (index > 1) "," else "") + """{"stream_id":$index,"name":"Kanal $index","category_id":"1"}"""
                        else -> "]"
                    }
                }
                val n = minOf(len, pending.length)
                pending.toCharArray(cbuf, off, 0, n)
                pending = pending.substring(n)
                return n
            }
            override fun close() {}
        }
        var count = 0
        var lastId = 0
        val summary = XtreamJsonStream.forEachObject(reader) { obj: JSONObject ->
            count++
            lastId = obj.optInt("stream_id", -1)
        }
        assertEquals(total, count)
        assertEquals(total, lastId)
        assertEquals(total, summary.total)
    }
}

/** Canlı / film / dizi listeleri parça parça okunurken sonuç eskisiyle aynı; bozuk yanıtta liste yine boş döner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XtreamListImportTest {

    private val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun withServer(responses: Map<String, String>, block: (String) -> Unit) {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                return okhttp3.mockwebserver.MockResponse().setBody(responses[action] ?: "[]")
            }
        }
        server.start()
        try {
            block(server.url("/").toString().trimEnd('/'))
        } finally {
            server.shutdown()
        }
    }

    private fun movieCarrier(host: String) = com.example.data.model.IPTVItem(
        id = 0, playlistId = 3, name = "", cleanedName = "", logoUrl = null, category = "",
        streamUrl = "$host/movie/u/p/1", type = "MOVIE"
    )

    @Test
    fun liveMovieAndSeriesLists_areReadCorrectly() = kotlinx.coroutines.runBlocking {
        withServer(
            mapOf(
                "get_live_categories" to """[{"category_id":"1","category_name":"Ulusal"},{"category_id":"2","category_name":"Radyo"}]""",
                "get_live_streams" to """[{"stream_id":7,"name":"TRT 1","category_id":"1","stream_icon":"http://l/7.png","epg_channel_id":"trt1"},
                    {"stream_id":"8","name":"Kral FM","category_id":2},{"stream_id":0,"name":"Geçersiz"},{"stream_id":9,"name":"  "}]""",
                "get_vod_categories" to """[{"category_id":"5","category_name":"Aksiyon, [Yeni]"}]""",
                "get_vod_streams" to """[{"stream_id":101,"name":"Film \"Bir\"","category_id":"5","container_extension":"mkv","rating":"7.5","stream_icon":"http://p/101.jpg"},
                    {"stream_id":102,"name":"Film İki","category_id":"99","rating":8}]""",
                "get_series_categories" to """[{"category_id":"3","category_name":"Netflix"}]""",
                "get_series" to """[{"series_id":500,"name":"İstila","category_id":"3","cover":"http://c/500.jpg","backdrop_path":["http://b/500.jpg"],"plot":"Özet; [1]","rating_5based":4}]"""
            )
        ) { host ->
            kotlinx.coroutines.runBlocking {
                val live = com.example.data.api.MetadataEnricher.fetchXtreamLiveStreamsAsItems(app, host, "u", "p", 3)
                assertEquals(listOf(7, 8), live.map { it.id })
                assertEquals(listOf("Ulusal", "Radyo"), live.map { it.category })
                assertEquals(listOf("LIVE", "RADIO"), live.map { it.type })
                assertEquals("$host/live/u/p/7.ts", live[0].streamUrl)
                assertEquals("trt1", live[0].tvgId)
                assertEquals("http://l/7.png", live[0].logoUrl)

                val movies = com.example.data.api.MetadataEnricher.fetchXtreamVodStreamsAsItems(app, movieCarrier(host))
                assertEquals(listOf(101, 102), movies.map { it.id })
                assertEquals(listOf("Film \"Bir\"", "Film İki"), movies.map { it.name })
                assertEquals(listOf("Aksiyon, [Yeni]", "Diğer"), movies.map { it.category })
                assertEquals(listOf("$host/movie/u/p/101.mkv", "$host/movie/u/p/102.mp4"), movies.map { it.streamUrl })
                assertEquals(listOf(7.5, 8.0), movies.map { it.rating })

                val series = com.example.data.api.MetadataEnricher.fetchFullXtreamSeriesCatalog(app, movieCarrier(host))
                assertEquals(1, series.size)
                assertEquals(500, series[0].seriesId)
                assertEquals("İstila", series[0].name)
                assertEquals("Netflix", series[0].categoryId)
                assertEquals("http://c/500.jpg", series[0].coverUrl)
                assertEquals("http://b/500.jpg", series[0].backdropUrl)
                assertEquals("Özet; [1]", series[0].plot)
                assertEquals(8.0, series[0].rating, 0.0001)

                val covers = com.example.data.api.MetadataEnricher.fetchAllSeriesCoversFromXtreamBulk(app, movieCarrier(host))
                assertEquals(mapOf("İstila".lowercase(java.util.Locale.ROOT) to "http://c/500.jpg"), covers)
            }
        }
    }

    @Test
    fun invalidOrBrokenResponses_giveEmptyListsLikeBefore() = kotlinx.coroutines.runBlocking {
        withServer(
            mapOf(
                "get_live_streams" to """{"user_info":{"auth":0}}""",
                // Yarıda kesilmiş yanıt: eskisi gibi hiçbir film eklenmez (yarım liste kaydedilmez).
                "get_vod_streams" to """[{"stream_id":101,"name":"Film Bir"},{"stream_id":102,"name":"Film""",
                "get_series" to "<html>403</html>"
            )
        ) { host ->
            kotlinx.coroutines.runBlocking {
                assertTrue(com.example.data.api.MetadataEnricher.fetchXtreamLiveStreamsAsItems(app, host, "u", "p", 3).isEmpty())
                assertTrue(com.example.data.api.MetadataEnricher.fetchXtreamVodStreamsAsItems(app, movieCarrier(host)).isEmpty())
                assertTrue(com.example.data.api.MetadataEnricher.fetchFullXtreamSeriesCatalog(app, movieCarrier(host)).isEmpty())
                assertTrue(com.example.data.api.MetadataEnricher.fetchAllSeriesCoversFromXtreamBulk(app, movieCarrier(host)).isEmpty())
            }
        }
    }
}
