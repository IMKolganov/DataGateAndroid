package com.imkolganov.datagate.profiles

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.vpn.xray.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Context.profilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "dg_local_profiles")

class LocalVpnProfilesRepository(
    private val appContext: Context,
) {
    private val indexKey = stringPreferencesKey("profiles_index_json")
    private val credsPrefs by lazy {
        appContext.getSharedPreferences(CREDS_PREFS, Context.MODE_PRIVATE)
    }

    private val profilesDir: File
        get() = File(appContext.filesDir, "profiles").also { it.mkdirs() }

    val profiles: Flow<List<LocalVpnProfile>> =
        appContext.profilesDataStore.data.map { prefs ->
            parseIndex(prefs[indexKey]).sortedByDescending { it.createdAtEpochMs }
        }

    suspend fun list(): List<LocalVpnProfile> = profiles.first()

    suspend fun getById(id: String): LocalVpnProfile? =
        list().firstOrNull { it.id == id }

    suspend fun readConfigText(profile: LocalVpnProfile): String = withContext(Dispatchers.IO) {
        File(profilesDir, profile.configFileName).readText()
    }

    fun getCredentials(profileId: String): LocalVpnProfileCredentials {
        val user = credsPrefs.getString(userKey(profileId), "").orEmpty()
        val pass = credsPrefs.getString(passKey(profileId), "").orEmpty()
        return LocalVpnProfileCredentials(username = user, password = pass)
    }

    suspend fun importOpenVpnFromUri(
        uri: Uri,
        displayName: String?,
        username: String = "",
        password: String = "",
    ): LocalVpnProfile = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read selected file")
        val text = bytes.toString(Charsets.UTF_8)
        if (text.isBlank()) error("OpenVPN profile is empty")
        importOpenVpnContent(
            content = text,
            displayName = displayName,
            username = username,
            password = password,
        )
    }

    suspend fun importOpenVpnContent(
        content: String,
        displayName: String?,
        username: String = "",
        password: String = "",
    ): LocalVpnProfile = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.ovpn"
        File(profilesDir, fileName).writeText(content)
        val name = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.removeSuffix(".ovpn")
            ?.removeSuffix(".OVPN")
            ?.takeIf { it.isNotBlank() }
            ?: "OpenVPN profile"
        val profile = LocalVpnProfile(
            id = id,
            name = name,
            type = VpnServerType.OpenVpn,
            configFileName = fileName,
            createdAtEpochMs = System.currentTimeMillis(),
            hasUsername = username.isNotBlank(),
            hasPassword = password.isNotBlank(),
        )
        saveCredentials(id, username, password)
        upsert(profile)
        profile
    }

    /**
     * Imports an Xray share link (`vless://` / `vmess://` / …) or JSON with outbounds.
     * [normalize] converts share text to a normalized `{ "outbounds": [...] }` JSON.
     */
    suspend fun importXrayContent(
        content: String,
        displayName: String?,
        normalize: (String) -> String,
    ): LocalVpnProfile = withContext(Dispatchers.IO) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) error("Xray profile is empty")
        val normalized = normalize(trimmed)
        // Validate outbounds exist.
        XrayConfigBuilder.extractOutbounds(normalized)
        val id = UUID.randomUUID().toString()
        val fileName = "$id.json"
        File(profilesDir, fileName).writeText(normalized)
        val name = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.removeSuffix(".json")
            ?.removeSuffix(".txt")
            ?.takeIf { it.isNotBlank() }
            ?: guessXrayName(trimmed)
            ?: "Xray profile"
        val profile = LocalVpnProfile(
            id = id,
            name = name,
            type = VpnServerType.Xray,
            configFileName = fileName,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        upsert(profile)
        profile
    }

    suspend fun importXrayFromUri(
        uri: Uri,
        displayName: String?,
        normalize: (String) -> String,
    ): LocalVpnProfile = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read selected file")
        importXrayContent(
            content = bytes.toString(Charsets.UTF_8),
            displayName = displayName,
            normalize = normalize,
        )
    }

    private fun guessXrayName(raw: String): String? {
        val share = XrayConfigBuilder.extractShareLink(raw) ?: return null
        val afterScheme = share.substringAfter("://", missingDelimiterValue = "")
        val fragment = afterScheme.substringAfterLast('#', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() && !it.contains('?') }
        if (!fragment.isNullOrBlank()) {
            return runCatching {
                java.net.URLDecoder.decode(fragment, Charsets.UTF_8.name())
            }.getOrDefault(fragment).take(64)
        }
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('@')
            .substringAfterLast('@')
            .takeIf { it.isNotBlank() }
        return host?.take(64)
    }

    suspend fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = list()
        val updated = current.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        writeIndex(updated)
    }

    suspend fun updateCredentials(id: String, username: String, password: String) {
        saveCredentials(id, username, password)
        val current = list()
        val updated = current.map {
            if (it.id == id) {
                it.copy(
                    hasUsername = username.isNotBlank(),
                    hasPassword = password.isNotBlank(),
                )
            } else {
                it
            }
        }
        writeIndex(updated)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val current = list()
        val target = current.firstOrNull { it.id == id }
        if (target != null && target.configFileName.isNotBlank()) {
            runCatching { File(profilesDir, target.configFileName).delete() }
        }
        clearCredentials(id)
        writeIndex(current.filterNot { it.id == id })
    }

    private suspend fun upsert(profile: LocalVpnProfile) {
        val current = list().filterNot { it.id == profile.id } + profile
        writeIndex(current)
    }

    private suspend fun writeIndex(profiles: List<LocalVpnProfile>) {
        val json = JSONArray()
        for (p in profiles) {
            json.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("configFileName", p.configFileName)
                    .put("createdAtEpochMs", p.createdAtEpochMs)
                    .put("hasUsername", p.hasUsername)
                    .put("hasPassword", p.hasPassword)
            )
        }
        appContext.profilesDataStore.edit { prefs ->
            prefs[indexKey] = json.toString()
        }
    }

    private fun parseIndex(raw: String?): List<LocalVpnProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching {
                        VpnServerType.valueOf(o.getString("type"))
                    }.getOrDefault(VpnServerType.Unknown)
                    add(
                        LocalVpnProfile(
                            id = o.getString("id"),
                            name = o.optString("name", "Profile"),
                            type = type,
                            configFileName = o.optString("configFileName", ""),
                            createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                            hasUsername = o.optBoolean("hasUsername", false),
                            hasPassword = o.optBoolean("hasPassword", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCredentials(id: String, username: String, password: String) {
        credsPrefs.edit()
            .putString(userKey(id), username)
            .putString(passKey(id), password)
            .apply()
    }

    private fun clearCredentials(id: String) {
        credsPrefs.edit()
            .remove(userKey(id))
            .remove(passKey(id))
            .apply()
    }

    private fun userKey(id: String) = "user_$id"
    private fun passKey(id: String) = "pass_$id"

    companion object {
        private const val CREDS_PREFS = "dg_profile_creds"
    }
}
