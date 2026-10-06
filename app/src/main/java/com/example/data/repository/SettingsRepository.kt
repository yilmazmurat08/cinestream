package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import coil.imageLoader

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        const val DEFAULT_PARENTAL_PIN = "0000"
        val HARDWARE_ACCELERATION = booleanPreferencesKey("hardware_acceleration")
        val BUFFER_SIZE = stringPreferencesKey("buffer_size")
        val REDUCE_CELLULAR_QUALITY = booleanPreferencesKey("reduce_cellular_quality")
        val AUTO_REFRESH_LIST = booleanPreferencesKey("auto_refresh_list")
        val SUBTITLE_SIZE = intPreferencesKey("subtitle_size")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
        val PARENTAL_LOCK = booleanPreferencesKey("parental_lock")
        val PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val LAST_EPG_FETCH_TIME = stringPreferencesKey("last_epg_fetch_time")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val IS_PRO_USER = booleanPreferencesKey("is_pro_user")
        val TOTAL_WATCH_SECONDS = longPreferencesKey("total_watch_seconds")
        // Ücretsiz izleme süresi günlüktür: sayaç hangi güne ait olduğunu da tutar (yyyy-MM-dd, cihaz saat dilimi).
        val WATCH_DAY = stringPreferencesKey("watch_day")
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val COMPLETED_PLAYBACK_SESSIONS = intPreferencesKey("completed_playback_sessions")
        val HAS_TRIGGERED_IN_APP_REVIEW = booleanPreferencesKey("has_triggered_in_app_review")
        val SYNC_INTERVAL = stringPreferencesKey("sync_interval")
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_AVATAR = stringPreferencesKey("profile_avatar")
        val MANUAL_EPG_URL = stringPreferencesKey("manual_epg_url")
        val VIEW_MODE = stringPreferencesKey("view_mode")
    }

    /** Görünüm modu: [ViewMode.PHONE] / [ViewMode.TV]; hiç seçilmediyse [ViewMode.UNSET] (ilk açılışta sorulur). */
    val viewModeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[VIEW_MODE] ?: ViewMode.UNSET
        }

    suspend fun setViewMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[VIEW_MODE] = mode
        }
    }

    val manualEpgUrlFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[MANUAL_EPG_URL] ?: ""
        }

    val userNameFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[USER_NAME] ?: "Kullanıcı"
        }

    val profileAvatarFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            // Eski sürümlerin dış kaynaklı varsayılan fotoğrafı yerine paket içi avatar.
            preferences[PROFILE_AVATAR]?.takeUnless { it.contains("images.unsplash.com") } ?: "avatar_1"
        }

    val syncIntervalFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[SYNC_INTERVAL] ?: "24_HOURS"
        }

    val completedPlaybackSessionsFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[COMPLETED_PLAYBACK_SESSIONS] ?: 0
        }

    val hasTriggeredInAppReviewFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[HAS_TRIGGERED_IN_APP_REVIEW] ?: false
        }

    val isProUserFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[IS_PRO_USER] ?: false
        }

    /** İzleme sayacı ve ait olduğu gün. Gün bugün değilse bugünkü izleme 0 sayılır. */
    val watchUsageFlow: Flow<WatchUsage> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            WatchUsage(day = preferences[WATCH_DAY], seconds = preferences[TOTAL_WATCH_SECONDS] ?: 0L)
        }

    val firstLaunchTimeFlow: Flow<Long> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val time = preferences[FIRST_LAUNCH_TIME] ?: 0L
            if (time == 0L) {
                val now = System.currentTimeMillis()
                context.dataStore.edit { prefs -> prefs[FIRST_LAUNCH_TIME] = now }
                now
            } else {
                time
            }
        }

    val appLanguageFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[APP_LANGUAGE] ?: com.example.util.LocaleHelper.getSavedLanguage(context)
        }

    val hardwareAccelerationFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[HARDWARE_ACCELERATION] ?: true
        }

    val bufferSizeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[BUFFER_SIZE] ?: "Normal"
        }

    val reduceCellularQualityFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[REDUCE_CELLULAR_QUALITY] ?: false
        }

    val autoRefreshListFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[AUTO_REFRESH_LIST] ?: true
        }

    val subtitleSizeFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[SUBTITLE_SIZE] ?: 16
        }

    val subtitleColorFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[SUBTITLE_COLOR] ?: "Beyaz"
        }

    val parentalLockFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            // Yetişkin kategorileri varsayılan olarak kilitli; kullanıcı Ayarlar'dan kapatabilir.
            preferences[PARENTAL_LOCK] ?: true
        }

    val parentalPinFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[PARENTAL_PIN] ?: DEFAULT_PARENTAL_PIN
        }

    val geminiApiKeyFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[GEMINI_API_KEY] ?: ""
        }

    val tmdbApiKeyFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            val key = preferences[TMDB_API_KEY] ?: ""
            // ÖNEMLİ: Geçmişte bu alana yanlışlıkla bir URL (TMDB'nin "anahtar iste" sayfasının
            // linki) kaydedilmiş, gerçek bir anahtar değil. Artık sadece TMDB v3 anahtarlarının
            // gerçek formatına (32 karakter, küçük harf+rakam) uyan değerler kabul ediliyor;
            // uymayan her şey (URL dahil) geçersiz sayılıp gömülü anahtara düşülüyor.
            val looksLikeValidKey = key.matches(Regex("^[a-f0-9]{32}$"))
            if (key.isBlank() || key == "placeholder" || !looksLikeValidKey) {
                val fromConfig = try {
                    com.example.BuildConfig.TMDB_API_KEY
                } catch (e: Exception) {
                    ""
                }
                if (fromConfig.isBlank() || fromConfig == "placeholder" || fromConfig == "MY_TMDB_API_KEY") {
                    ""
                } else {
                    fromConfig
                }
            } else {
                key
            }
        }

    suspend fun setHardwareAcceleration(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HARDWARE_ACCELERATION] = enabled
        }
    }

    suspend fun setBufferSize(size: String) {
        context.dataStore.edit { preferences ->
            preferences[BUFFER_SIZE] = size
        }
    }

    suspend fun setReduceCellularQuality(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REDUCE_CELLULAR_QUALITY] = enabled
        }
    }

    suspend fun setAutoRefreshList(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_REFRESH_LIST] = enabled
        }
    }

    suspend fun setSubtitleSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[SUBTITLE_SIZE] = size
        }
    }

    suspend fun setSubtitleColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[SUBTITLE_COLOR] = color
        }
    }

    suspend fun setParentalLock(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PARENTAL_LOCK] = enabled
        }
    }

    suspend fun setParentalPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[PARENTAL_PIN] = pin
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = key
        }
    }

    suspend fun setManualEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[MANUAL_EPG_URL] = url.trim()
        }
    }

    val lastEpgFetchTimeFlow: Flow<Long> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[LAST_EPG_FETCH_TIME]?.toLongOrNull() ?: 0L
        }

    suspend fun setLastEpgFetchTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_EPG_FETCH_TIME] = timeMillis.toString()
        }
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = language
        }
    }

    suspend fun setSyncInterval(interval: String) {
        context.dataStore.edit { preferences ->
            preferences[SYNC_INTERVAL] = interval
        }
        com.example.worker.SyncWorker.scheduleSync(context, interval)
    }

    suspend fun setProUser(isPro: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_PRO_USER] = isPro
        }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }

    suspend fun setProfileAvatar(avatar: String) {
        context.dataStore.edit { preferences ->
            preferences[PROFILE_AVATAR] = avatar
        }
    }

    suspend fun addWatchSeconds(secondsToAdd: Long): Long {
        if (secondsToAdd <= 0L) return 0L
        var updatedSeconds = 0L
        val today = WatchUsage.today()
        context.dataStore.edit { preferences ->
            // Yeni günde sayaç sıfırdan başlar.
            val current = if (preferences[WATCH_DAY] == today) preferences[TOTAL_WATCH_SECONDS] ?: 0L else 0L
            updatedSeconds = current + secondsToAdd
            preferences[TOTAL_WATCH_SECONDS] = updatedSeconds
            preferences[WATCH_DAY] = today
        }
        return updatedSeconds
    }

    suspend fun incrementWatchSeconds(): Long {
        return addWatchSeconds(1L)
    }

    suspend fun resetWatchSeconds() {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_WATCH_SECONDS] = 0L
        }
    }

    suspend fun incrementCompletedPlaybackSessions(): Int {
        var updatedCount = 0
        context.dataStore.edit { preferences ->
            val current = preferences[COMPLETED_PLAYBACK_SESSIONS] ?: 0
            updatedCount = current + 1
            preferences[COMPLETED_PLAYBACK_SESSIONS] = updatedCount
        }
        return updatedCount
    }

    suspend fun setHasTriggeredInAppReview(triggered: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_TRIGGERED_IN_APP_REVIEW] = triggered
        }
    }

    suspend fun clearAppCache() {
        try {
            context.imageLoader.diskCache?.clear()
            context.imageLoader.memoryCache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val cacheDir = context.cacheDir
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/** Uygulamanın arayüz modu (telefon arayüzü / TV arayüzü). */
object ViewMode {
    const val PHONE = "PHONE"
    const val TV = "TV"
    const val UNSET = "UNSET"
    /** Ayar henüz okunmadı (açılışta seçim ekranının bir an görünmesini önler). */
    const val LOADING = "LOADING"
}

/** Ücretsiz izleme sayacı: [seconds], [day] gününe aittir. */
data class WatchUsage(val day: String?, val seconds: Long) {
    /** Bugün izlenen süre (sayaç eski bir güne aitse 0). */
    fun secondsOn(dayKey: String): Long = if (day == dayKey) seconds else 0L

    companion object {
        fun today(now: Long = System.currentTimeMillis()): String =
            java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
}
