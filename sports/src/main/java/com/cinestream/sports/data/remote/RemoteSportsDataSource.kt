package com.cinestream.sports.data.remote

import com.cinestream.sports.data.SportsDataSource
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchDetail
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.Team
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Lisanslı spor verisine CineStream'in küçük aracı sunucusu üzerinden bağlanır.
 *
 * Veri sağlayıcının (ör. Sportmonks, API-Football) anahtarı uygulamaya konmaz: yalnızca aracı sunucuda durur.
 * Uygulama sunucuya kişisel veri göndermez; yalnızca spor, tarih, saat dilimi ve dil ister.
 *
 * Uç noktalar ve JSON biçimi [SportsJson] dosyasında anlatılır.
 */
class RemoteSportsDataSource(
    baseUrl: String,
    private val language: String = "tr",
    private val client: OkHttpClient = defaultClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO
) : SportsDataSource {

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun matches(sport: Sport, day: LocalDate, zone: ZoneId): List<Match> = get(
        base.newBuilder().addPathSegments("v1/matches")
            .addQueryParameter("sport", sport.id)
            .addQueryParameter("date", day.toString())
            .addQueryParameter("tz", zone.id)
            .addQueryParameter("lang", language)
            .build()
    ) { SportsJson.parseMatches(it) }

    override suspend fun matchDetail(matchId: String): MatchDetail? = get(
        base.newBuilder().addPathSegments("v1/matches").addPathSegment(matchId)
            .addQueryParameter("lang", language).build()
    ) { SportsJson.parseDetail(it) }

    override suspend fun searchTeams(query: String): List<Team> {
        if (query.isBlank()) return emptyList()
        return get(
            base.newBuilder().addPathSegments("v1/teams")
                .addQueryParameter("q", query.trim()).addQueryParameter("lang", language).build()
        ) { SportsJson.parseTeams(it) }
    }

    override suspend fun teams(ids: Set<String>): List<Team> {
        if (ids.isEmpty()) return emptyList()
        return get(
            base.newBuilder().addPathSegments("v1/teams")
                .addQueryParameter("ids", ids.sorted().joinToString(",")).addQueryParameter("lang", language).build()
        ) { SportsJson.parseTeams(it) }
    }

    override suspend fun nextMatch(teamId: String, fromEpochMillis: Long): Match? = get(
        base.newBuilder().addPathSegments("v1/teams").addPathSegment(teamId).addPathSegment("next")
            .addQueryParameter("from", fromEpochMillis.toString()).addQueryParameter("lang", language).build()
    ) { SportsJson.parseMatches(it).firstOrNull() }

    private suspend fun <T> get(url: HttpUrl, parse: (String) -> T): T = withContext(io) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Boş yanıt")
            parse(body)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
