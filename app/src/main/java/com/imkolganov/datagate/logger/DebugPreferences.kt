package com.imkolganov.datagate.logger

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.debugDataStore: DataStore<Preferences> by preferencesDataStore(name = "dg_debug")

object DebugPreferences {
    private val KEY_VPN_DEBUG_MODE = booleanPreferencesKey("vpn_debug_mode_enabled")

    fun vpnDebugModeFlow(context: Context): Flow<Boolean> =
        context.debugDataStore.data
            .map { prefs -> prefs[KEY_VPN_DEBUG_MODE] ?: false }
            .distinctUntilChanged()

    suspend fun isVpnDebugModeEnabled(context: Context): Boolean =
        context.debugDataStore.data.map { it[KEY_VPN_DEBUG_MODE] ?: false }.first()

    suspend fun setVpnDebugModeEnabled(context: Context, enabled: Boolean) {
        context.debugDataStore.edit { it[KEY_VPN_DEBUG_MODE] = enabled }
    }
}
