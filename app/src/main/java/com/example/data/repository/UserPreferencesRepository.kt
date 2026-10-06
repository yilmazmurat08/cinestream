package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException

import androidx.datastore.preferences.core.stringPreferencesKey

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val IS_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
        val IS_INITIAL_SETUP_DONE = booleanPreferencesKey("is_initial_setup_done")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_AVATAR = stringPreferencesKey("profile_avatar")
    }

    val userEmailFlow: Flow<String?> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[USER_EMAIL]?.takeIf { it.isNotBlank() }
        }

    val userNameFlow: Flow<String> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[USER_NAME] ?: ""
        }

    val profileAvatarFlow: Flow<String> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            // Eski sürümlerin dış kaynaklı varsayılan fotoğrafı yerine paket içi avatar.
            preferences[PROFILE_AVATAR]?.takeUnless { it.contains("images.unsplash.com") } ?: "avatar_1"
        }

    val isSetupCompletedFlow: Flow<Boolean> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            preferences[IS_SETUP_COMPLETED] ?: preferences[IS_INITIAL_SETUP_DONE] ?: false
        }

    val geminiApiKeyFlow: Flow<String> = context.userPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            com.example.util.SecretCipher.decrypt(preferences[GEMINI_API_KEY])
        }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[IS_SETUP_COMPLETED] = completed
            preferences[IS_INITIAL_SETUP_DONE] = completed
        }
    }

    suspend fun setUserEmail(email: String?) {
        context.userPreferencesDataStore.edit { preferences ->
            if (email.isNullOrBlank()) {
                preferences.remove(USER_EMAIL)
            } else {
                preferences[USER_EMAIL] = email.trim()
            }
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = com.example.util.SecretCipher.encrypt(apiKey)
        }
    }

    suspend fun setUserName(name: String) {
        context.userPreferencesDataStore.edit { preferences ->
            if (name.isBlank()) {
                preferences.remove(USER_NAME)
            } else {
                preferences[USER_NAME] = name.trim()
            }
        }
    }

    suspend fun setProfileAvatar(avatar: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PROFILE_AVATAR] = avatar
        }
    }

    suspend fun clearUserSession() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[IS_SETUP_COMPLETED] = false
            preferences[IS_INITIAL_SETUP_DONE] = false
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_NAME)
        }
    }
}
