package com.imkolganov.datagate.identity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "dg_installation")

object InstallationIdDataStoreProvider {
    private val KeyInstallationId = stringPreferencesKey("installation_id")

    suspend fun getOrCreate(context: Context): String {
        val prefs = context.dataStore.data.first()
        val existing = prefs[KeyInstallationId]
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[KeyInstallationId] = newId }
        return newId
    }
}
