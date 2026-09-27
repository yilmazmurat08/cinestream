import re

with open("app/src/main/java/com/example/data/api/MetadataEnricher.kt", "r") as f:
    content = f.read()

# 1. New enrichWithXtreamOrFallback
new_enrichWithXtreamOrFallback = """    suspend fun enrichWithXtreamOrFallback(context: android.content.Context, item: IPTVItem): EnrichedMetadata = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            try {
                val xtreamMeta = try {
                    fetchXtreamMetadata(context, item)
                } catch (e: Exception) {
                    Log.w(TAG, "Xtream metadata fetch failed for ${item.name}: ${e.message}")
                    null
                }
                if (xtreamMeta != null && xtreamMeta.summary.isNotBlank()) {
                    return@withPermit xtreamMeta
                }

                Log.d(TAG, "Xtream metadata missing or empty for ${item.name}. Running Gemini AI fallback motor...")
                val geminiMeta = try {
                    enrichWithGemini(context, item)
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini metadata enrichment failed for ${item.name}: ${e.message}")
                    null
                }
                if (geminiMeta != null && (geminiMeta.cast.isNotBlank() || geminiMeta.director.isNotBlank() || geminiMeta.logoUrl != null || geminiMeta.trailerUrl != null)) {
                    return@withPermit geminiMeta.copy(summary = item.summary)
                }

                generateLocalMovieMetadata(item)
            } catch (e: Throwable) {
                Log.e(TAG, "enrichWithXtreamOrFallback safe catch for ${item.name}", e)
                try {
                    generateLocalMovieMetadata(item)
                } catch (_: Throwable) {
                    EnrichedMetadata(
                        summary = item.summary,
                        cast = item.cast,
                        director = item.director,
                        rating = if (item.rating > 0.0) item.rating else 8.0,
                        logoUrl = item.logoUrl,
                        trailerUrl = item.trailerUrl,
                        releaseDate = item.releaseDate,
                        genre = item.genre
                    )
                }
            }
        }
    }"""

# 2. New enrichWithGemini
new_enrichWithGemini = '''    suspend fun enrichWithGemini(context: android.content.Context, item: IPTVItem): EnrichedMetadata? = withContext(Dispatchers.IO) {
        val queryName = item.cleanedName.lowercase().trim()
        val cached = localMetadataCache.entries.find { queryName.contains(it.key) || it.key.contains(queryName) }?.value
        if (cached != null) {
            Log.d(TAG, "Found local cached metadata for $queryName. Using offline mode.")
            return@withContext cached.copy(summary = "")
        }

        val savedKey = context.dataStore.data.firstOrNull()?.get(stringPreferencesKey("gemini_api_key"))
        val apiKey = if (!savedKey.isNullOrEmpty()) savedKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "placeholder") {
            Log.w(TAG, "Gemini API Key is missing or default. Using local generator fallback.")
            return@withContext generateLocalMovieMetadata(item)
        }

        val typeLabel = if (item.type == "SERIES") "Dizi (TV Series)" else "Film (Movie)"
        val prompt = """
            Sana bir IPTV yayınının adını vereceğim. Bu yapımın gerçek bir film veya dizi olduğunu biliyoruz.
            Yapım Adı: "${item.cleanedName}"
            Türü: $typeLabel
            
            Lütfen bu yapım hakkında araştırma yap ve aşağıdaki bilgileri Türkçe olarak JSON formatında döndür:
            1. "cast": Başrol oyuncuları (en fazla 4 isim, virgülle ayrılmış, örn: "Leonardo DiCaprio, Joseph Gordon-Levitt").
            2. "director": Yönetmen adı (örn: "Christopher Nolan" veya diziyse yaratıcısı / yapımcısı).
            3. "rating": IMDb veya genel izleyici puanı (0.0 - 10.0 arasında ondalıklı sayı, örn: 8.8).
            4. "logoUrl": Bu film veya dizinin resmi afişinin/posterinin gerçek TMDB (The Movie Database) poster resmi URL'si (genellikle 'https://image.tmdb.org/t/p/w500/...' ile başlar. Eğer TMDB poster yolunu tam olarak biliyorsan onu yaz, bilmiyorsan bu film/dizinin türüne/atmosferine çok uygun, yüksek kaliteli ve telifsiz bir Unsplash görseli URL'si yaz, örn. 'https://images.unsplash.com/...').
            5. "trailerUrl": Bu film veya dizinin resmi Türkçe veya İngilizce YouTube fragmanının/fragman tanıtımının tam YouTube URL'si (örn. 'https://www.youtube.com/watch?v=...' veya 'https://youtu.be/...'). Kesinlikle boş bırakma, en uygun YouTube fragman linkini bulup ekle.
            
            Sadece geçerli bir JSON objesi döndür. Markdown kod blokları veya 'json' kelimesi dahil hiçbir ek açıklama ekleme.
            Format:
            {
              "cast": "...",
              "director": "...",
              "rating": 8.2,
              "logoUrl": "https://image.tmdb.org/t/p/w500/...",
              "trailerUrl": "https://www.youtube.com/watch?v=..."
            }
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val models = listOf("gemini-2.5-flash", "gemini-1.5-flash")
            var responseBody: String? = null
            var lastError: String? = null

            for (model in models) {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    .post(requestBody)
                    .build()

                try {
                    val response = clientByTimeout.newCall(request).execute()
                    if (response.isSuccessful) {
                        responseBody = response.body?.string()
                        Log.d(TAG, "Model $model response successful")
                        break
                    } else {
                        val err = response.body?.string()
                        lastError = "Model $model failed with code ${response.code}: $err"
                        Log.w(TAG, lastError)
                    }
                } catch (e: Exception) {
                    lastError = "Model $model failed with exception: ${e.message}"
                    Log.w(TAG, lastError, e)
                }
            }

            if (responseBody == null) {
                Log.w(TAG, "All Gemini attempts failed. Last error: $lastError. Using local metadata generator fallback.")
                return@withContext generateLocalMovieMetadata(item)
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: return@withContext generateLocalMovieMetadata(item)

            val cleanedJsonText = extractJson(rawText)
            val jsonOutput = JSONObject(cleanedJsonText)
            
            return@withContext EnrichedMetadata(
                summary = "",
                cast = jsonOutput.optString("cast", item.cast).ifEmpty { generateLocalMovieMetadata(item).cast },
                director = jsonOutput.optString("director", item.director).ifEmpty { generateLocalMovieMetadata(item).director },
                rating = jsonOutput.optDouble("rating", item.rating).let { if (it <= 0.0) 8.2 else it },
                logoUrl = jsonOutput.optString("logoUrl", "").ifEmpty { null },
                trailerUrl = jsonOutput.optString("trailerUrl", "").ifEmpty { null }
            )

        } catch (e: Exception) {
            System.err.println("--- enrichWithGemini EXCEPTION: ${e.message} ---")
            e.printStackTrace()
            Log.w(TAG, "Failed to enrich metadata using Gemini API. Falling back to local generator.", e)
            generateLocalMovieMetadata(item)
        }
    }'''

# Replace enrichWithXtreamOrFallback and enrichWithGemini using regex
pattern_funcs = re.compile(r"    suspend fun enrichWithXtreamOrFallback.*?    fun generateLocalMovieMetadata", re.DOTALL)
replacement_funcs = new_enrichWithXtreamOrFallback + "\n\n" + new_enrichWithGemini + "\n\n    fun generateLocalMovieMetadata"

match = pattern_funcs.search(content)
if match:
    print("Found functions pattern, replacing...")
    content = content[:match.start()] + replacement_funcs + content[match.end():]
else:
    print("WARNING: functions pattern not found!")

# 3. In generateLocalMovieMetadata, replace all summary = "..." with summary = item.summary,
func_start = content.find("fun generateLocalMovieMetadata(item: IPTVItem): EnrichedMetadata {")
func_end = content.find("suspend fun callGeminiApi", func_start)

if func_start != -1 and func_end != -1:
    gen_func = content[func_start:func_end]
    lines = gen_func.split("\n")
    new_lines = []
    count_replaced = 0
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('summary = "') and (stripped.endswith('",') or stripped.endswith('"')):
            indent = line[:len(line) - len(line.lstrip())]
            new_lines.append(f"{indent}summary = item.summary,")
            count_replaced += 1
        elif "val fallbackSummary =" in line:
            indent = line[:len(line) - len(line.lstrip())]
            new_lines.append(f"{indent}val fallbackSummary = item.summary")
        else:
            new_lines.append(line)
    
    print(f"Replaced {count_replaced} hardcoded summary lines in generateLocalMovieMetadata")
    new_gen_func = "\n".join(new_lines)
    content = content[:func_start] + new_gen_func + content[func_end:]
else:
    print("WARNING: generateLocalMovieMetadata boundaries not found!")

with open("app/src/main/java/com/example/data/api/MetadataEnricher.kt", "w") as f:
    f.write(content)

print("Saved MetadataEnricher.kt successfully")
