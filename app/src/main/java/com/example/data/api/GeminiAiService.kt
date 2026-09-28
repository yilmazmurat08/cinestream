package com.example.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CastAndDirectorInfo(
    val director: String = "",
    val cast: List<String> = emptyList()
)

object GeminiAiService {
    suspend fun generateOverview(context: Context, title: String): String = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext "Özet bulunamadı."
        val prompt = "Sana adı verilen film veya dizinin konusunu analiz et: '$title'. Bu içerik hakkında kesinlikle sürpriz gelişmeleri (spoiler) açık etmeden; akıcı, merak uyandıran ve maksimum 3-4 cümlelik Türkçe bir özet yaz. Yanıt olarak sadece özet metnini döndür."
        try {
            val result = MetadataEnricher.callGeminiApi(context, prompt)
            if (!result.isNullOrBlank() && !result.contains("şu an oluşturulamadı", ignoreCase = true)) {
                return@withContext result.trim()
            }
        } catch (e: Throwable) {
            Log.w("GeminiAiService", "generateOverview error for '$title': ${e.message}")
        }
        return@withContext try {
            MetadataEnricher.getSpoilerFreeSummary(context, title)
        } catch (e: Throwable) {
            "Bu içerik için özet bilgisi şu an hazırlanamıyor."
        }
    }

    suspend fun generatePreviousEpisodesSummary(
        context: Context,
        showTitle: String,
        lastWatchedEpisodeNumber: Int
    ): String = withContext(Dispatchers.IO) {
        if (lastWatchedEpisodeNumber <= 1) {
            return@withContext ""
        }
        val previousEpisodeNum = lastWatchedEpisodeNumber - 1
        val prompt = "'$showTitle' dizisinin sadece 1. bölümünden $previousEpisodeNum. bölümüne kadar olan olayları kısa, heyecanlı ve SPOILER içermeyecek şekilde özetle. $lastWatchedEpisodeNumber. bölüm ve sonrası hakkında kesinlikle bilgi verme."
        try {
            val result = MetadataEnricher.callGeminiApi(context, prompt)
            if (!result.isNullOrBlank()) {
                return@withContext result.trim()
            }
        } catch (e: Throwable) {
            Log.w("GeminiAiService", "generatePreviousEpisodesSummary error for '$showTitle': ${e.message}")
        }
        return@withContext "Önceki bölümlerin spoiler-suz özeti şu anda hazırlanamıyor."
    }

    /**
     * Gemini AI ile filmin/dizinin yönetmenini ve en popüler 6 oyuncusunun adını JSON olarak sorgular.
     */
    suspend fun fetchCastAndDirectorInfo(context: Context, title: String): CastAndDirectorInfo = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext CastAndDirectorInfo()

        val prompt = """
            Sana filmin veya dizinin adını veriyorum: "$title".
            Lütfen bu yapımın yönetmenini ve en popüler 6 oyuncusunun adını aşağıdaki JSON formatında döndür:
            {"director": "Christopher Nolan", "cast": ["Leonardo DiCaprio", "Scarlett Johansson"]}
            Sadece geçerli bir JSON objesi döndür, başka hiçbir metin veya açıklama ekleme.
        """.trimIndent()

        var director = ""
        val castList = mutableListOf<String>()

        try {
            val rawResult = MetadataEnricher.callGeminiApi(context, prompt)
            if (!rawResult.isNullOrBlank()) {
                val jsonStr = MetadataEnricher.extractJson(rawResult)
                val json = JSONObject(jsonStr)
                director = json.optString("director", "")
                val castArr = json.optJSONArray("cast")
                if (castArr != null) {
                    for (i in 0 until castArr.length()) {
                        val name = castArr.optString(i, "").trim()
                        if (name.isNotBlank() && name != "Bilinmiyor" && name != "Belirtilmemiş") {
                            castList.add(name)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w("GeminiAiService", "Notice: Gemini AI cast/director query for '$title': ${e.message}")
        }

        // Fallback if Gemini returned empty
        if (castList.isEmpty()) {
            try {
                val dummyItem = com.example.data.model.IPTVItem(
                    id = 0, playlistId = 0, name = title, cleanedName = title,
                    logoUrl = "", streamUrl = "", category = "", type = "MOVIE"
                )
                val localMeta = MetadataEnricher.generateLocalMovieMetadata(dummyItem)
                if (director.isBlank()) director = localMeta.director
                val splitLocal = localMeta.cast.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "Belirtilmemiş" && it != "Bilinmiyor" }
                castList.addAll(splitLocal)
            } catch (e: Throwable) {
                // Safe ignore
            }
        }

        return@withContext CastAndDirectorInfo(director = director, cast = castList)
    }

    suspend fun fetchPersonProfilePhotoFromTMDB(context: Context, name: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        try {
            val searchResult = NetworkModule.tmdbApiService.searchPerson(apiKey, name)
            val profilePath = searchResult.results?.firstOrNull()?.profile_path
            if (!profilePath.isNullOrBlank()) {
                val imageUrl = "https://image.tmdb.org/t/p/w185$profilePath"
                val role = "Oyuncu"
                val details = PersonDetails(
                    name = name,
                    role = role,
                    biography = "$name hakkında biyografi bilgisi.",
                    imageUrl = imageUrl
                )
                MetadataEnricher.savePersonToCache(context, details)
                return@withContext imageUrl
            }
        } catch (e: Throwable) {
            Log.w("GeminiAiService", "Error searching TMDB person photo for '$name': ${e.message}")
        }
        return@withContext null
    }

    data class ChatAiResult(
        val reply: String,
        val detectedTitle: String = "",
        /** Tarife uyan olası yapımlar (Türkçe ve orijinal adlarıyla); kütüphane eşleştirmesi bunlarla yapılır. */
        val candidates: List<TitleCandidate> = emptyList()
    )

    data class TitleCandidate(val title: String, val originalTitle: String, val year: Int?, val isSeries: Boolean)

    /**
     * Film/dizi hakkında serbest sohbet + tarif edilen sahnelerden yapım tahmini.
     * Konuşma geçmişi düz metin olarak prompt içine gömülüyor (mevcut callGeminiApi
     * tek string prompt aldığı için basit ve güvenilir bir yöntem).
     */
    suspend fun chatAboutMoviesAndSeries(
        context: Context,
        conversationHistory: List<Pair<String, String>>,
        latestUserMessage: String
    ): ChatAiResult = withContext(Dispatchers.IO) {
        val historyText = conversationHistory.takeLast(10).joinToString("\n") { (role, text) ->
            if (role == "user") "Kullanıcı: $text" else "Asistan: $text"
        }

        val prompt = """
            Sen bir IPTV uygulamasının film ve dizi konusunda uzman yapay zeka asistanısın.
            Kullanıcılarla SADECE filmler ve diziler hakkında sohbet ediyorsun. Özellikle
            kullanıcı bir filmin/dizinin adını hatırlamayıp sahne, karakter veya olay örgüsü
            tarif ettiğinde, elindeki ipuçlarıyla en olası yapımı tahmin etmeye çalışıyorsun.

            ${if (historyText.isNotBlank()) "Önceki konuşma:\n$historyText\n" else ""}
            Kullanıcının yeni mesajı: "$latestUserMessage"

            Kurallar:
            - Sadece film ve dizilerle ilgili konuş; alakasız bir konu sorulursa kibarca film/diziye yönlendir.
            - Tarif edilen sahnelerden belirli bir yapımı makul bir güvenle tahmin edebiliyorsan adını net söyle.
            - Emin değilsen, en olası tahminini paylaş ve netleştirici bir soru sorabilirsin.
            - Yanıtın kısa olsun (en fazla 3-4 cümle).
            - Kullanıcının yazdığı dilde (Türkçe veya İngilizce, hangisiyse) yanıt ver.
            - "candidates" listesine, yanıtında adı geçen veya tarife uyan en olası 1-3 yapımı, emin olmasan da,
              en olasıdan başlayarak yaz: Türkçe adı (Türkiye'de bilinen adı), orijinal adı, yılı ve film mi dizi mi.
              Kullanıcı bir yapım tarif etmiyorsa ya da öneri istemiyorsa liste boş olsun.
            - Yanıtını SADECE aşağıdaki JSON formatında ver, başka hiçbir açıklama/markdown ekleme:
            {"reply": "doğal sohbet yanıtın", "detectedTitle": "en olası yapımın adı, yoksa boş string", "candidates": [{"title": "Türkçe adı", "originalTitle": "orijinal adı", "year": 2007, "type": "movie veya series"}]}
        """.trimIndent()

        try {
            val rawResult = MetadataEnricher.callGeminiApi(context, prompt, fast = true)
            if (!rawResult.isNullOrBlank()) {
                return@withContext parseChatResult(MetadataEnricher.extractJson(rawResult))
            }
        } catch (e: Throwable) {
            Log.w("GeminiAiService", "chatAboutMoviesAndSeries error: ${e.message}")
        }
        return@withContext ChatAiResult(reply = "Üzgünüm, şu anda yanıt veremiyorum. Lütfen tekrar deneyin.", detectedTitle = "")
    }

    /** Sohbet yanıtını çözer; "candidates" yoksa (eski biçim) detectedTitle aday olarak kullanılır. */
    internal fun parseChatResult(jsonStr: String): ChatAiResult {
        val json = JSONObject(jsonStr)
        val reply = json.optString("reply", "").ifBlank { "Üzgünüm, tam olarak anlayamadım. Biraz daha detay verebilir misiniz?" }
        val detectedTitle = json.optString("detectedTitle", "").trim()
        val candidates = ArrayList<TitleCandidate>()
        val array = json.optJSONArray("candidates")
        if (array != null) {
            for (i in 0 until minOf(array.length(), 3)) {
                val c = array.optJSONObject(i) ?: continue
                val title = c.optString("title", "").trim()
                val original = c.optString("originalTitle", "").trim()
                if (title.isEmpty() && original.isEmpty()) continue
                val year = c.optInt("year", 0).takeIf { it in 1900..2100 }
                val isSeries = c.optString("type", "").lowercase().let { it.contains("seri") || it.contains("dizi") || it == "tv" }
                candidates.add(TitleCandidate(title.ifEmpty { original }, original.ifEmpty { title }, year, isSeries))
            }
        }
        if (candidates.isEmpty() && detectedTitle.isNotEmpty()) {
            candidates.add(TitleCandidate(detectedTitle, detectedTitle, null, false))
        }
        return ChatAiResult(reply = reply, detectedTitle = detectedTitle.ifEmpty { candidates.firstOrNull()?.title.orEmpty() }, candidates = candidates)
    }
}
