package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Utility helper class for dynamic runtime locale management (Turkish / English).
 * Persists language choice in SharedPreferences and updates application Context resources.
 */
object LocaleHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "app_language"

    /**
     * Gets the currently saved language code ("tr" or "en").
     * Defaults to "tr" if system default is Turkish, otherwise "en".
     */
    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        if (!saved.isNullOrEmpty()) {
            return saved
        }
        val deviceLang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (deviceLang == "tr") "tr" else "en"
    }

    /**
     * Updates locale in SharedPreferences and updates configuration context.
     */
    fun setLocale(context: Context, language: String): Context {
        persistLanguage(context, language)
        val updatedContext = updateResources(context, language)
        try {
            updateResources(context.applicationContext, language)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return updatedContext
    }

    private fun persistLanguage(context: Context, language: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * Applies locale configuration to the Context.
     */
    fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val res: Resources = context.resources
        val config: Configuration = Configuration(res.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            return context
        }
    }

    /**
     * Uygulama içinde seçilen dile (TR/EN) göre metin döndürür. ViewModel, servis ve bildirim gibi
     * Activity dışı kodlarda kullanılır; Android 7+ uygulama bağlamı seçilen dili kendiliğinden bilmez.
     */
    fun getString(context: Context, resId: Int, vararg formatArgs: Any): String {
        val localized = try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale(getSavedLanguage(context)))
            context.createConfigurationContext(config)
        } catch (e: Exception) {
            context
        }
        return if (formatArgs.isEmpty()) localized.getString(resId) else localized.getString(resId, *formatArgs)
    }

    /**
     * Wraps attachBaseContext for Activities to enforce saved Locale on startup.
     */
    fun wrap(context: Context): ContextWrapper {
        val lang = getSavedLanguage(context)
        val newContext = updateResources(context, lang)
        return ContextWrapper(newContext)
    }
}

/**
 * Extension to safely resolve the Activity instance across ConfigurationContexts and ContextWrappers.
 */
fun Context.findActivity(): android.app.Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is android.app.Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}
