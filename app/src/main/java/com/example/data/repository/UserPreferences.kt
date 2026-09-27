package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferences(private val context: Context) {

    companion object {
        val IS_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val MEDIA_PLAYLIST_URL = stringPreferencesKey("media_playlist_url")
        val MANUAL_EPG_URL = stringPreferencesKey("manual_epg_url")
    }

    val isSetupCompleted: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[IS_SETUP_COMPLETED] ?: false }

    suspend fun saveUserSettings(apiKey: String, playlistUrl: String, epgUrl: String = "") {
        context.dataStore.edit { prefs ->
            if (apiKey.isNotBlank()) prefs[GEMINI_API_KEY] = apiKey
            if (playlistUrl.isNotBlank()) prefs[MEDIA_PLAYLIST_URL] = playlistUrl
            if (epgUrl.isNotBlank()) prefs[MANUAL_EPG_URL] = epgUrl
        }
        if (apiKey.isNotBlank()) {
            UserPreferencesRepository(context).setGeminiApiKey(apiKey)
        }
        if (epgUrl.isNotBlank()) {
            SettingsRepository(context).setManualEpgUrl(epgUrl)
        }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_SETUP_COMPLETED] = completed
        }
        UserPreferencesRepository(context).setSetupCompleted(completed)
    }
}
