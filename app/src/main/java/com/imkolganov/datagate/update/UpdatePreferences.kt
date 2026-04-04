package com.imkolganov.datagate.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "dg_app_updates")

object UpdatePreferences {

    private val KEY_LAST_CHECK_MS = longPreferencesKey("last_check_epoch_ms")
    private val KEY_DISMISSED_TAG = stringPreferencesKey("dismissed_release_tag")
    private val KEY_CHECK_ENABLED = booleanPreferencesKey("github_update_check_enabled")
    private val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_apk_enabled")

    private const val MIN_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000 // 6 hours

    fun checkEnabledFlow(context: Context): Flow<Boolean> =
        context.updateDataStore.data.map { prefs -> prefs[KEY_CHECK_ENABLED] ?: true }

    fun autoDownloadFlow(context: Context): Flow<Boolean> =
        context.updateDataStore.data.map { prefs -> prefs[KEY_AUTO_DOWNLOAD] ?: false }

    suspend fun isCheckEnabled(context: Context): Boolean =
        context.updateDataStore.data.map { it[KEY_CHECK_ENABLED] ?: true }.first()

    suspend fun setCheckEnabled(context: Context, enabled: Boolean) {
        context.updateDataStore.edit { it[KEY_CHECK_ENABLED] = enabled }
    }

    suspend fun isAutoDownloadEnabled(context: Context): Boolean =
        context.updateDataStore.data.map { it[KEY_AUTO_DOWNLOAD] ?: false }.first()

    suspend fun setAutoDownloadEnabled(context: Context, enabled: Boolean) {
        context.updateDataStore.edit { it[KEY_AUTO_DOWNLOAD] = enabled }
    }

    suspend fun shouldRunCheck(context: Context): Boolean {
        if (!isCheckEnabled(context)) return false
        val last = context.updateDataStore.data.map { it[KEY_LAST_CHECK_MS] ?: 0L }.first()
        return System.currentTimeMillis() - last >= MIN_CHECK_INTERVAL_MS
    }

    suspend fun markCheckDone(context: Context) {
        context.updateDataStore.edit { it[KEY_LAST_CHECK_MS] = System.currentTimeMillis() }
    }

    suspend fun getDismissedTag(context: Context): String? =
        context.updateDataStore.data.map { it[KEY_DISMISSED_TAG] }.first()

    suspend fun dismissRelease(context: Context, tag: String) {
        context.updateDataStore.edit { it[KEY_DISMISSED_TAG] = tag }
    }
}
