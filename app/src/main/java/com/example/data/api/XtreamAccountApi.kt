package com.example.data.api

import com.example.util.XtreamAccount
import com.example.util.XtreamAccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Xtream hesabının bilgisini sorar (player_api.php, eylemsiz). Hata ya da Xtream olmayan sunucuda null. */
object XtreamAccountApi {
    suspend fun fetch(host: String, user: String, pass: String, now: Long = System.currentTimeMillis()): XtreamAccountInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$host/player_api.php?username=${com.example.util.XtreamUrls.queryValue(user)}" +
                    "&password=${com.example.util.XtreamUrls.queryValue(pass)}"
                // Bazı sağlayıcılar bir kimliği 403 ile reddeder: önce uygulama, sonra tarayıcı kimliği denenir.
                for (agent in listOf(com.example.util.AppUserAgent.app, com.example.util.AppUserAgent.BROWSER)) {
                    val request = Request.Builder().url(url).header("User-Agent", agent).get().build()
                    com.example.data.api.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                        if (response.code == 403) return@use
                        if (!response.isSuccessful) return@withContext null
                        val body = response.body?.string()?.takeIf { it.trimStart().startsWith("{") } ?: return@withContext null
                        return@withContext XtreamAccount.parse(body, now)
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
}
