package com.imkolganov.datagate.ui.theme

import android.content.Context

enum class ThemeMode {
    /** Always use light Material scheme */
    LIGHT,
    /** Always use dark Material scheme */
    DARK,
    /** Match device night mode */
    SYSTEM;

    fun resolveDark(systemIsDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemIsDark
    }

    fun toAppCompatNightMode(): Int = when (this) {
        LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}

object ThemePreferenceStore {
    private const val PREFS_NAME = "datagate_ui"
    private const val KEY_THEME_MODE = "theme_mode"

    fun apply(context: Context) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(get(context).toAppCompatNightMode())
    }

    fun get(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }
}
