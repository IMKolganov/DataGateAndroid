package com.imkolganov.datagate.auth

import android.content.Context
import androidx.core.content.edit

class SharedPrefsIdTokenStore(
    context: Context
) : IdTokenStore {

    private val prefs = context.getSharedPreferences("auth_store", Context.MODE_PRIVATE)

    override fun getIdToken(): String? = prefs.getString("google_id_token", null)

    override fun saveIdToken(idToken: String) {
        prefs.edit { putString("google_id_token", idToken) }
    }

    override fun clear() {
        prefs.edit { remove("google_id_token") }
    }
}
