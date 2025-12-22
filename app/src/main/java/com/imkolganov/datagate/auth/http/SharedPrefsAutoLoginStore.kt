package com.imkolganov.datagate.auth

import android.content.Context
import androidx.core.content.edit

class SharedPrefsAutoLoginStore(context: Context) : AutoLoginStore {
    private val prefs = context.getSharedPreferences("auth_store", Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean("auto_login_enabled", true)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("auto_login_enabled", enabled) }
    }
}
