package com.imkolganov.datagate.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguagePreferenceStore {
    private const val PREFS_NAME = "datagate_ui"
    private const val KEY_APP_LOCALE = "app_locale"

    fun get(context: Context): AppLocale {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LOCALE, null) ?: return AppLocale.SYSTEM
        return runCatching { AppLocale.valueOf(raw) }.getOrDefault(AppLocale.SYSTEM)
    }

    fun save(context: Context, locale: AppLocale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LOCALE, locale.name)
            .apply()
    }

    /**
     * Persists [locale] and applies it immediately. Use this when the user picks a language so
     * [apply] does not read prefs before async [save] has finished (that race left the old locale).
     */
    fun setLocale(context: Context, locale: AppLocale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LOCALE, locale.name)
            .commit()
        applyLocale(locale)
    }

    private fun applyLocale(locale: AppLocale) {
        val locales = when (locale) {
            AppLocale.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(locale.languageTag!!)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Apply saved locale to the process. Call from [android.app.Activity.onCreate] before [androidx.activity.compose.setContent].
     * [AppLocale.SYSTEM] clears the override so the device locale is used.
     */
    fun apply(context: Context) {
        applyLocale(get(context))
    }
}
