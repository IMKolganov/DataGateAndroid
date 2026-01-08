package com.imkolganov.datagate.auth

import android.content.Context
import androidx.core.content.edit

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

    override fun getRefreshToken(): String? =
        prefs.getString("refresh_token", null)

    override fun saveRefreshToken(token: String) {
        prefs.edit { putString("refresh_token", token) }
    }

    override fun saveAccessTokenExpiration(value: String) {
        prefs.edit { putString("access_expiration", value) }
    }

    override fun saveRefreshTokenExpiration(value: String?) {
        prefs.edit { putString("refresh_expiration", value) }
    }

    override fun clear() {
        prefs.edit {
            remove("access_token")
            remove("refresh_token")
            remove("access_expiration")
            remove("refresh_expiration")
        }
    }
}
