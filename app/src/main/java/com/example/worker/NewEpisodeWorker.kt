package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.api.NetworkModule
import com.example.data.api.TmdbAuth
import com.example.data.db.AppDatabase
import com.example.data.repository.TmdbCoverSearchResponse
import com.example.data.repository.TmdbTvAirInfo
import com.example.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Yalnızca yeni bölüm kontrolü için küçük bir arayüz; mevcut TmdbService'e ve eşleştirmeye dokunmaz. */
interface TmdbNewEpisodeService {
    @GET("search/tv")
    suspend fun searchTv(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("include_adult") adult: Boolean = false): TmdbCoverSearchResponse

    @GET("tv/{id}")
    suspend fun tv(@Path("id") id: Int, @Query("api_key") apiKey: String): TmdbTvAirInfo
}

/**
 * Favori dizinin yeni bölümü yayınlanınca bildirim (TMDB'nin "son yayınlanan bölüm" bilgisi). Günde bir kez,
 * internet varken çalışır; aynı bölüm için bir kez bildirilir. Ayarlar'dan kapatılabilir (varsayılan açık).
 * Bölümün kullanıcının listesine (sağlayıcıya) gelmesi biraz sürebilir; bildirim bunu söyler.
 */
object NewEpisodeAlerts {
    private const val PREFS = "new_episode_alerts"
    private const val KEY_ENABLED = "__enabled"
    const val CHANNEL_ID = "cinestream_new_episodes"
    private const val WORK = "new_episode_check"
    const val MAX_SHOWS = 25
    const val MAX_NOTIFICATIONS = 5

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        _enabled.value = runCatching { prefs(context).getBoolean(KEY_ENABLED, true) }.getOrDefault(true)
        schedule(context)
    }

    fun setEnabled(context: Context, on: Boolean) {
        _enabled.value = on
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        schedule(context)
    }

    fun schedule(context: Context) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (!_enabled.value) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<NewEpisodeWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun yesterday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86_400_000L))

    /** Bildirilecek mi: son bölüm bugün ya da dün yayınlandıysa ve daha önce bildirilmediyse. */
    fun shouldNotify(airDate: String?, alreadyNotified: Boolean, today: String = today(), yesterday: String = yesterday()): Boolean =
        !alreadyNotified && airDate != null && (airDate == today || airDate == yesterday)

    /** Favori dizilerin adları: veritabanındaki favori bölümlerin dizi adı + favori katalog dizileri. */
    suspend fun favoriteShowTitles(context: Context): List<String> {
        val db = AppDatabase.getDatabase(context)
        val fromItems = runCatching { db.iptvDao().getFavoriteItemsFlow().first() }.getOrDefault(emptyList())
            .filter { it.type == "SERIES" }
            .map { com.example.data.model.SeriesParser.episodeInfoOf(it)?.showTitle ?: it.cleanedName.ifBlank { it.name } }
        val ids = runCatching {
            context.getSharedPreferences("favorite_series", Context.MODE_PRIVATE).getStringSet("series_ids", emptySet()).orEmpty()
                .mapNotNull { it.toIntOrNull() }
        }.getOrDefault(emptyList())
        val fromCatalog = if (ids.isEmpty()) emptyList() else runCatching { db.iptvDao().seriesCatalogByIds(ids).map { it.name } }.getOrDefault(emptyList())
        return (fromCatalog + fromItems).map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }.take(MAX_SHOWS)
    }
}

class NewEpisodeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val service: TmdbNewEpisodeService by lazy { NetworkModule.retrofit.create(TmdbNewEpisodeService::class.java) }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = NewEpisodeAlerts.prefs(context)
        if (!prefs.getBoolean("__enabled", true)) return Result.success()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
        val apiKey = TmdbAuth.resolve()
        if (apiKey.isBlank()) return Result.success()
        var sent = 0
        return try {
            for (title in NewEpisodeAlerts.favoriteShowTitles(context)) {
                if (sent >= NewEpisodeAlerts.MAX_NOTIFICATIONS) break
                val idKey = "id|" + title.lowercase(Locale.ROOT)
                var tmdbId = prefs.getInt(idKey, 0)
                if (tmdbId == 0) {
                    val clean = com.example.util.TitleNormalizer.cleanTitleForTMDB(title).first.ifBlank { title }
                    tmdbId = service.searchTv(apiKey, clean).results.firstOrNull()?.id ?: -1
                    prefs.edit().putInt(idKey, tmdbId).apply()
                }
                if (tmdbId <= 0) continue
                val last = runCatching { service.tv(tmdbId, apiKey).lastEpisode }.getOrNull() ?: continue
                val season = last.seasonNumber ?: continue
                val episode = last.episodeNumber ?: continue
                val notifiedKey = "done|$tmdbId|$season|$episode"
                if (NewEpisodeAlerts.shouldNotify(last.airDate, prefs.getBoolean(notifiedKey, false))) {
                    notify(context, title, season, episode, tmdbId)
                    prefs.edit().putBoolean(notifiedKey, true).apply()
                    sent++
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(context: Context, title: String, season: Int, episode: Int, tmdbId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NewEpisodeAlerts.CHANNEL_ID,
                        LocaleHelper.getString(context, R.string.new_episode_channel),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, tmdbId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = LocaleHelper.getString(context, R.string.new_episode_text, season, episode)
        val notification = NotificationCompat.Builder(context, NewEpisodeAlerts.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(LocaleHelper.getString(context, R.string.new_episode_title, title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(("new_ep_$tmdbId").hashCode(), notification) }
    }
}
