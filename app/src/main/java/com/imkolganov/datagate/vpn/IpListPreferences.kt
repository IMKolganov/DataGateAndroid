package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ipListDataStore: DataStore<Preferences> by preferencesDataStore(name = "dg_ip_lists")

data class IpListSettings(
    val sourceUrl: String,
    val updateFrequency: IpListUpdateFrequency
)

object IpListPreferences {
    private val KEY_SOURCE_URL = stringPreferencesKey("source_url")
    private val KEY_UPDATE_FREQUENCY = stringPreferencesKey("update_frequency")

    const val DEFAULT_SOURCE_URL =
        "https://raw.githubusercontent.com/ipverse/country-ip-blocks/master/country/ru/ipv4-aggregated.txt"

    fun settingsFlow(context: Context): Flow<IpListSettings> =
        context.ipListDataStore.data
            .map { prefs ->
                IpListSettings(
                    sourceUrl = prefs[KEY_SOURCE_URL] ?: DEFAULT_SOURCE_URL,
                    updateFrequency = IpListUpdateFrequency.fromStorageValue(prefs[KEY_UPDATE_FREQUENCY])
                )
            }
            .distinctUntilChanged()

    suspend fun getSettings(context: Context): IpListSettings =
        settingsFlow(context).first()

    suspend fun saveSettings(
        context: Context,
        sourceUrl: String,
        updateFrequency: IpListUpdateFrequency
    ) {
        context.ipListDataStore.edit { prefs ->
            prefs[KEY_SOURCE_URL] = sourceUrl.trim()
            prefs[KEY_UPDATE_FREQUENCY] = updateFrequency.storageValue
        }
    }
}
