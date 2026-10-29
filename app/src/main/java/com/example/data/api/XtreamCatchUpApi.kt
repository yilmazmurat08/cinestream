package com.example.data.api

import com.example.util.CatchUp
import com.example.util.CatchUpProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Kanalın arşivdeki geçmiş programlarını sorar (player_api.php, get_simple_data_table). Hata olursa boş liste. */
object XtreamCatchUpApi {
    suspend fun fetch(
        c: CatchUp.Credentials,
        now: Long = System.currentTimeMillis(),
        serverZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): List<CatchUpProgram> = withContext(Dispatchers.IO) {
        try {
            val url = "${c.host}/player_api.php?username=${com.example.util.XtreamUrls.queryValue(c.user)}" +
                "&password=${com.example.util.XtreamUrls.queryValue(c.pass)}&action=get_simple_data_table&stream_id=${c.streamId}"
            // Bazı sağlayıcılar bir kimliği 403 ile reddeder: önce uygulama, sonra tarayıcı kimliği denenir.
            for (agent in listOf(com.example.util.AppUserAgent.app, com.example.util.AppUserAgent.BROWSER)) {
                val request = Request.Builder().url(url).header("User-Agent", agent).get().build()
                com.example.data.api.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 403) return@use
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string()?.takeIf { it.trimStart().startsWith("{") } ?: return@withContext emptyList()
                    return@withContext CatchUp.parse(body, now, serverZone)
                }
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
