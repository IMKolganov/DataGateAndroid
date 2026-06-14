package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ipListDataStore: DataStore<Preferences> by preferencesDataStore(name = "dg_ip_lists")

data class IpListSettings(
    val sourceUrls: List<String>,
    val updateFrequency: IpListUpdateFrequency,
    val coverageMode: IpListCoverageMode,
    val android12OvpnRouteLimit: Int,
    /** When false, CIDR lists are not loaded or applied (all traffic via VPN). */
    val cidrListsEnabled: Boolean = true
)

data class IpListStatus(
    val lastUpdatedEpochMs: Long?,
    val loadedRouteCount: Int,
    val lastError: String?,
    val reachedRouteLimit: Boolean
)

object IpListPreferences {
    private val KEY_SOURCE_URL = stringPreferencesKey("source_url")
    private val KEY_SOURCE_URLS = stringPreferencesKey("source_urls")
    private val KEY_UPDATE_FREQUENCY = stringPreferencesKey("update_frequency")
    private val KEY_COVERAGE_MODE = stringPreferencesKey("coverage_mode")
    private val KEY_ANDROID12_OVPN_ROUTE_LIMIT = intPreferencesKey("android12_ovpn_route_limit")
    private val KEY_CIDR_LISTS_ENABLED = booleanPreferencesKey("cidr_lists_enabled")
    private val KEY_CACHED_LIST = stringPreferencesKey("cached_list")
    private val KEY_CACHED_AT_MS = longPreferencesKey("cached_at_epoch_ms")
    private val KEY_LOADED_ROUTE_COUNT = intPreferencesKey("loaded_route_count")
    private val KEY_LAST_ERROR = stringPreferencesKey("last_error")
    private val KEY_REACHED_ROUTE_LIMIT = booleanPreferencesKey("reached_route_limit")

    const val DEFAULT_SOURCE_URL =
        "https://raw.githubusercontent.com/ipverse/country-ip-blocks/master/country/ru/ipv4-aggregated.txt"
    const val DEFAULT_IPV6_SOURCE_URL =
        "https://raw.githubusercontent.com/ipverse/country-ip-blocks/master/country/ru/ipv6-aggregated.txt"
    val DEFAULT_SOURCE_URLS: List<String>
        get() = listOf(DEFAULT_SOURCE_URL, DEFAULT_IPV6_SOURCE_URL)

    fun settingsFlow(context: Context): Flow<IpListSettings> =
        context.ipListDataStore.data
            .map { prefs ->
                IpListSettings(
                    sourceUrls = decodeSourceUrls(
                        prefs[KEY_SOURCE_URLS] ?: prefs[KEY_SOURCE_URL]
                    ),
                    updateFrequency = IpListUpdateFrequency.fromStorageValue(prefs[KEY_UPDATE_FREQUENCY]),
                    coverageMode = IpListCoverageMode.fromStorageValue(prefs[KEY_COVERAGE_MODE]),
                    android12OvpnRouteLimit = IpListRouteConfig.sanitizeAndroid12OvpnRouteLimit(
                        prefs[KEY_ANDROID12_OVPN_ROUTE_LIMIT]
                            ?: IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT
                    ),
                    cidrListsEnabled = prefs[KEY_CIDR_LISTS_ENABLED] ?: true
                )
            }
            .distinctUntilChanged()

    suspend fun getSettings(context: Context): IpListSettings =
        settingsFlow(context).first()

    suspend fun saveSettings(
        context: Context,
        sourceUrls: List<String>,
        updateFrequency: IpListUpdateFrequency,
        coverageMode: IpListCoverageMode,
        android12OvpnRouteLimit: Int,
        cidrListsEnabled: Boolean
    ) {
        context.ipListDataStore.edit { prefs ->
            prefs[KEY_SOURCE_URLS] = encodeSourceUrls(sourceUrls)
            prefs.remove(KEY_SOURCE_URL)
            prefs[KEY_UPDATE_FREQUENCY] = updateFrequency.storageValue
            prefs[KEY_COVERAGE_MODE] = coverageMode.storageValue
            prefs[KEY_ANDROID12_OVPN_ROUTE_LIMIT] =
                IpListRouteConfig.sanitizeAndroid12OvpnRouteLimit(android12OvpnRouteLimit)
            prefs[KEY_CIDR_LISTS_ENABLED] = cidrListsEnabled
        }
    }

    suspend fun setCidrListsEnabled(context: Context, enabled: Boolean) {
        context.ipListDataStore.edit { prefs ->
            prefs[KEY_CIDR_LISTS_ENABLED] = enabled
        }
    }

    private fun decodeSourceUrls(value: String?): List<String> {
        val urls = value
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.toList()
            .orEmpty()
        if (urls == listOf(DEFAULT_SOURCE_URL)) return DEFAULT_SOURCE_URLS
        return urls.ifEmpty { DEFAULT_SOURCE_URLS }
    }

    private fun encodeSourceUrls(urls: List<String>): String =
        urls.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")

    suspend fun getCachedList(context: Context): String? =
        context.ipListDataStore.data.map { it[KEY_CACHED_LIST]?.takeIf(String::isNotBlank) }.first()

    fun statusFlow(context: Context): Flow<IpListStatus> =
        context.ipListDataStore.data
            .map { prefs ->
                IpListStatus(
                    lastUpdatedEpochMs = prefs[KEY_CACHED_AT_MS]?.takeIf { it > 0L },
                    loadedRouteCount = prefs[KEY_LOADED_ROUTE_COUNT] ?: 0,
                    lastError = prefs[KEY_LAST_ERROR]?.takeIf(String::isNotBlank),
                    reachedRouteLimit = prefs[KEY_REACHED_ROUTE_LIMIT] ?: false
                )
            }
            .distinctUntilChanged()

    suspend fun getStatus(context: Context): IpListStatus =
        statusFlow(context).first()

    suspend fun shouldRefreshCachedList(context: Context, settings: IpListSettings): Boolean {
        if (settings.updateFrequency == IpListUpdateFrequency.MANUAL) return getCachedList(context).isNullOrBlank()
        val prefs = context.ipListDataStore.data.first()
        val last = prefs[KEY_CACHED_AT_MS] ?: 0L
        val cached = prefs[KEY_CACHED_LIST]
        if (cached.isNullOrBlank()) return true
        val intervalMs = settings.updateFrequency.hours * 60L * 60L * 1000L
        return System.currentTimeMillis() - last >= intervalMs
    }

    suspend fun saveCachedList(
        context: Context,
        content: String,
        routeCount: Int,
        reachedRouteLimit: Boolean
    ) {
        context.ipListDataStore.edit { prefs ->
            prefs[KEY_CACHED_LIST] = content
            prefs[KEY_CACHED_AT_MS] = System.currentTimeMillis()
            prefs[KEY_LOADED_ROUTE_COUNT] = routeCount
            prefs[KEY_REACHED_ROUTE_LIMIT] = reachedRouteLimit
            prefs.remove(KEY_LAST_ERROR)
        }
    }

    suspend fun saveStatus(
        context: Context,
        routeCount: Int,
        reachedRouteLimit: Boolean
    ) {
        context.ipListDataStore.edit { prefs ->
            prefs[KEY_LOADED_ROUTE_COUNT] = routeCount
            prefs[KEY_REACHED_ROUTE_LIMIT] = reachedRouteLimit
        }
    }

    suspend fun saveLastError(context: Context, error: String) {
        context.ipListDataStore.edit { prefs ->
            prefs[KEY_LAST_ERROR] = error
        }
    }
}
