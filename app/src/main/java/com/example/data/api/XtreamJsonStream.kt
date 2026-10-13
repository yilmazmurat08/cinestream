package com.example.data.api

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.Reader

/**
 * Xtream listeleri (get_live_streams / get_vod_streams / get_series) on binlerce kayıt içerebilir. Yanıtın tamamı
 * önce tek bir metne, sonra JSONArray'e alınınca (50 bin filmde 150 MB'ı aşan) bellek düşük bellekli TV kutularında
 * uygulamayı kapatabiliyordu. Bu okuyucu dizinin öğelerini sırayla okur; bellekte aynı anda tek öğe bulunur.
 * Her öğe yine org.json ile ayrıştırılır, yani alanların okunuşu eskisiyle birebir aynıdır.
 */
internal object XtreamJsonStream {

    /** [total]: dizideki öğe sayısı (JSONArray.length() ile aynı). [invalidPreview]: yanıt dizi değilse ilk 300 karakteri. */
    class Summary(val total: Int, val invalidPreview: String?)

    /**
     * [input] üst düzeyde bir JSON dizisi olmalıdır; nesne olan her öğe sırayla [onObject]'e verilir.
     * Yanıt boşsa veya dizi değilse hiçbir öğe verilmez. Bozuk JSON'da eskisi gibi [JSONException] fırlatılır.
     */
    fun forEachObject(input: Reader, onObject: (JSONObject) -> Unit): Summary {
        val reader = CharSource(input)
        var c = reader.read()
        while (c != -1 && c.toChar().isWhitespace()) c = reader.read()
        if (c != '['.code) {
            val preview = StringBuilder()
            while (c != -1 && preview.length < 300) {
                preview.append(c.toChar())
                c = reader.read()
            }
            return Summary(0, preview.toString())
        }

        val element = StringBuilder()
        var total = 0
        var depth = 0
        var quote = 0
        var escaped = false
        var sawSeparator = false

        fun flushElement() {
            total++
            if (element.isBlank()) {
                element.setLength(0)
                return // org.json'da boş öğe ([1,,2]) null'dır; nesne olmadığı için atlanır.
            }
            val tokener = JSONTokener(element.toString())
            element.setLength(0)
            val value = tokener.nextValue()
            // Öğeden sonra virgülsüz başka bir değer gelmesi org.json'da da hatadır.
            if (tokener.nextClean() != '\u0000') throw JSONException("Unterminated array")
            if (value is JSONObject) onObject(value)
        }

        while (true) {
            c = reader.read()
            if (c == -1) throw JSONException("Unterminated array")
            val ch = c.toChar()
            if (quote != 0) {
                element.append(ch)
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    c == quote -> quote = 0
                }
                continue
            }
            when (ch) {
                '"', '\'' -> {
                    quote = c
                    element.append(ch)
                }
                '{', '[' -> {
                    depth++
                    element.append(ch)
                }
                '}', ']' -> {
                    if (depth > 0) {
                        depth--
                        element.append(ch)
                    } else if (ch == ']') {
                        if (element.isNotBlank()) flushElement() else if (sawSeparator) total++
                        return Summary(total, null)
                    } else {
                        throw JSONException("Unexpected '}'")
                    }
                }
                ',', ';' -> if (depth > 0) {
                    element.append(ch)
                } else {
                    sawSeparator = true
                    flushElement()
                }
                else -> element.append(ch)
            }
        }
    }

    /** Karakterleri parça parça okur (karakter başına Reader.read() çağrısı on milyonlarca karakterde yavaştı). */
    private class CharSource(private val reader: Reader) {
        private val buffer = CharArray(16 * 1024)
        private var length = 0
        private var position = 0

        fun read(): Int {
            if (position >= length) {
                length = reader.read(buffer)
                position = 0
                if (length <= 0) return -1
            }
            return buffer[position++].code
        }
    }
}
