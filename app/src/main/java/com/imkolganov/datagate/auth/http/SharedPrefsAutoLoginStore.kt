package com.imkolganov.datagate.auth.http

import android.content.Context
import androidx.core.content.edit
import com.imkolganov.datagate.auth.AutoLoginStore

class SharedPrefsAutoLoginStore(context: Context) : AutoLoginStore {
    private val prefs = context.getSharedPreferences("auth_store", Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean("auto_login_enabled", false)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("auto_login_enabled", enabled) }
    }
}
