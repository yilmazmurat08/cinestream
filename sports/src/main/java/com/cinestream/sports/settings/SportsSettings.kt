package com.cinestream.sports.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinestream.sports.model.NotificationEntry
import com.cinestream.sports.model.NotificationType
import com.cinestream.sports.model.Sport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class LeagueFilterMode { MAJOR, ALL, CUSTOM }

/** Kullanıcının spor bölümü tercihleri. Uygulamanın diğer ayarlarından ayrı bir dosyada saklanır. */
data class SportsSettings(
    /** Gösterilen sporlar ve sırası (en az biri). */
    val sports: List<Sport> = Sport.entries,
    val leagueMode: LeagueFilterMode = LeagueFilterMode.MAJOR,
    val customLeagueIds: Set<String> = emptySet(),
    val followedTeamIds: Set<String> = emptySet(),
    val notifyReminder: Boolean = true,
    val notifyKickoff: Boolean = true,
    val notifyGoals: Boolean = false,
    val notifyFullTime: Boolean = false,
    val reminderMinutes: Int = 10,
    /** Maç kartında kullanıcının listesindeki kanalı göster. */
    val showMyChannel: Boolean = true,
    /** Yayıncı anahtarı (ChannelKey.id) → kullanıcının kanal adı. Liste yenilense de geçerli kalır. */
    val channelMappings: Map<String, String> = emptyMap(),
    /** Hatırlatma kurulmuş maçlar. */
    val reminderMatchIds: Set<String> = emptySet()
)

interface SportsSettingsStore {
    val settings: Flow<SportsSettings>
    suspend fun update(transform: (SportsSettings) -> SportsSettings)
    val notifications: Flow<List<NotificationEntry>>
    suspend fun addNotification(entry: NotificationEntry)
    suspend fun clearNotifications()
}

/** Testler, önizlemeler ve ekran görüntüleri için bellekte tutulan tercihler. */
class InMemorySportsSettingsStore(
    initial: SportsSettings = SportsSettings(),
    initialNotifications: List<NotificationEntry> = emptyList()
) : SportsSettingsStore {
    private val state = MutableStateFlow(initial)
    private val history = MutableStateFlow(initialNotifications)
    override val settings: Flow<SportsSettings> = state.asStateFlow()
    override val notifications: Flow<List<NotificationEntry>> = history.asStateFlow()

    override suspend fun update(transform: (SportsSettings) -> SportsSettings) = state.update(transform)

    override suspend fun addNotification(entry: NotificationEntry) =
        history.update { (listOf(entry) + it).take(MAX_NOTIFICATIONS) }

    override suspend fun clearNotifications() = history.update { emptyList() }
}

internal const val MAX_NOTIFICATIONS = 50

private val Context.sportsDataStore by preferencesDataStore(
    name = "sports_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** Kalıcı tercihler (DataStore). Bozuk dosyada boş ayarlarla açılır, uygulama çökmez. */
class DataStoreSportsSettingsStore(private val store: DataStore<Preferences>) : SportsSettingsStore {

    constructor(context: Context) : this(context.applicationContext.sportsDataStore)

    override val settings: Flow<SportsSettings> = store.data.map { it.toSettings() }

    override val notifications: Flow<List<NotificationEntry>> =
        store.data.map { decodeNotifications(it[NOTIFICATIONS]) }

    override suspend fun update(transform: (SportsSettings) -> SportsSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    override suspend fun addNotification(entry: NotificationEntry) {
        store.edit { prefs ->
            val list = (listOf(entry) + decodeNotifications(prefs[NOTIFICATIONS])).take(MAX_NOTIFICATIONS)
            prefs[NOTIFICATIONS] = encodeNotifications(list)
        }
    }

    override suspend fun clearNotifications() {
        store.edit { it.remove(NOTIFICATIONS) }
    }

    private companion object {
        val SPORTS = stringPreferencesKey("sports")
        val LEAGUE_MODE = stringPreferencesKey("league_mode")
        val CUSTOM_LEAGUES = stringSetPreferencesKey("custom_leagues")
        val FOLLOWED = stringSetPreferencesKey("followed_teams")
        val NOTIFY_REMINDER = booleanPreferencesKey("notify_reminder")
        val NOTIFY_KICKOFF = booleanPreferencesKey("notify_kickoff")
        val NOTIFY_GOALS = booleanPreferencesKey("notify_goals")
        val NOTIFY_FULL_TIME = booleanPreferencesKey("notify_full_time")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val SHOW_MY_CHANNEL = booleanPreferencesKey("show_my_channel")
        val MAPPINGS = stringPreferencesKey("channel_mappings")
        val REMINDERS = stringSetPreferencesKey("reminder_match_ids")
        val NOTIFICATIONS = stringPreferencesKey("notifications")
    }

    private fun Preferences.toSettings(): SportsSettings {
        val defaults = SportsSettings()
        val sports = this[SPORTS]?.split(',')?.mapNotNull { Sport.fromId(it) }?.distinct().orEmpty()
        return SportsSettings(
            sports = sports.ifEmpty { defaults.sports },
            leagueMode = this[LEAGUE_MODE]?.let { runCatching { LeagueFilterMode.valueOf(it) }.getOrNull() }
                ?: defaults.leagueMode,
            customLeagueIds = this[CUSTOM_LEAGUES] ?: emptySet(),
            followedTeamIds = this[FOLLOWED] ?: emptySet(),
            notifyReminder = this[NOTIFY_REMINDER] ?: defaults.notifyReminder,
            notifyKickoff = this[NOTIFY_KICKOFF] ?: defaults.notifyKickoff,
            notifyGoals = this[NOTIFY_GOALS] ?: defaults.notifyGoals,
            notifyFullTime = this[NOTIFY_FULL_TIME] ?: defaults.notifyFullTime,
            reminderMinutes = this[REMINDER_MINUTES] ?: defaults.reminderMinutes,
            showMyChannel = this[SHOW_MY_CHANNEL] ?: defaults.showMyChannel,
            channelMappings = decodeMappings(this[MAPPINGS]),
            reminderMatchIds = this[REMINDERS] ?: emptySet()
        )
    }

    private fun MutablePreferences.write(s: SportsSettings) {
        this[SPORTS] = s.sports.ifEmpty { Sport.entries }.joinToString(",") { it.id }
        this[LEAGUE_MODE] = s.leagueMode.name
        this[CUSTOM_LEAGUES] = s.customLeagueIds
        this[FOLLOWED] = s.followedTeamIds
        this[NOTIFY_REMINDER] = s.notifyReminder
        this[NOTIFY_KICKOFF] = s.notifyKickoff
        this[NOTIFY_GOALS] = s.notifyGoals
        this[NOTIFY_FULL_TIME] = s.notifyFullTime
        this[REMINDER_MINUTES] = s.reminderMinutes
        this[SHOW_MY_CHANNEL] = s.showMyChannel
        this[MAPPINGS] = JSONObject(s.channelMappings as Map<*, *>).toString()
        this[REMINDERS] = s.reminderMatchIds
    }
}

internal fun decodeMappings(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    }.getOrDefault(emptyMap())
}

internal fun encodeNotifications(list: List<NotificationEntry>): String = JSONArray().apply {
    list.forEach { n ->
        put(JSONObject().apply {
            put("id", n.id)
            put("type", n.type.name)
            put("title", n.title)
            put("body", n.body)
            put("time", n.timeEpochMillis)
            if (n.matchId != null) put("match", n.matchId)
        })
    }
}.toString()

internal fun decodeNotifications(raw: String?): List<NotificationEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = runCatching { NotificationType.valueOf(o.optString("type")) }.getOrNull() ?: return@mapNotNull null
            NotificationEntry(
                id = o.optString("id"),
                type = type,
                title = o.optString("title"),
                body = o.optString("body"),
                timeEpochMillis = o.optLong("time"),
                matchId = if (o.has("match")) o.optString("match") else null
            )
        }
    }.getOrDefault(emptyList())
}
