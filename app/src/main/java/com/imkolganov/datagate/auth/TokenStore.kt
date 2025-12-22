package com.imkolganov.datagate.auth

import android.content.Context
import androidx.core.content.edit

interface TokenStore {
    fun getAccessToken(): String?
    fun saveAccessToken(token: String)
    fun clear()
}

class SharedPrefsTokenStore(
    context: Context
) : TokenStore {

    private val prefs =
        context.getSharedPreferences("auth_store", Context.MODE_PRIVATE)

    override fun getAccessToken(): String? =
        prefs.getString("access_token", null)

    override fun saveAccessToken(token: String) {
        prefs.edit { putString("access_token", token) }
    }

    override fun clear() {
        prefs.edit { remove("access_token") }
    }
}
