package com.imkolganov.datagate.update

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "dg_app_updates")

object UpdatePreferences {

    private val KEY_LAST_CHECK_MS = longPreferencesKey("last_check_epoch_ms")
    private val KEY_DISMISSED_TAG = stringPreferencesKey("dismissed_release_tag")
    private val KEY_CHECK_ENABLED = booleanPreferencesKey("github_update_check_enabled")
    private val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_apk_enabled")
    private val KEY_CACHED_NEWER_TAG = stringPreferencesKey("cached_newer_release_tag")
    private val KEY_CACHED_NEWER_HTML = stringPreferencesKey("cached_newer_release_html")
    private val KEY_CACHED_NEWER_APK = stringPreferencesKey("cached_newer_release_apk")
    private val KEY_PUSH_UPDATES_ENABLED = booleanPreferencesKey("push_updates_enabled")
    private val KEY_LAST_PUSHED_UPDATE_TAG = stringPreferencesKey("last_pushed_update_tag")

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
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(UpdateNotificationHelper.NOTIFICATION_ID_UPDATE_AVAILABLE)
    }

    suspend fun isPushForUpdatesEnabled(context: Context): Boolean =
        context.updateDataStore.data.map { it[KEY_PUSH_UPDATES_ENABLED] ?: true }.first()

    suspend fun setPushForUpdatesEnabled(context: Context, enabled: Boolean) {
        context.updateDataStore.edit { it[KEY_PUSH_UPDATES_ENABLED] = enabled }
    }

    suspend fun getLastPushedUpdateTag(context: Context): String? =
        context.updateDataStore.data.map { it[KEY_LAST_PUSHED_UPDATE_TAG] }.first()

    suspend fun recordUpdateNotificationShown(context: Context, tag: String) {
        context.updateDataStore.edit { it[KEY_LAST_PUSHED_UPDATE_TAG] = tag }
    }

    /**
     * Snapshot of cached newer release for opening the update dialog from a notification tap.
     */
    suspend fun getCachedNewerRelease(context: Context, currentVersionName: String): GitHubLatestRelease? {
        val prefs = context.updateDataStore.data.first()
        val tag = prefs[KEY_CACHED_NEWER_TAG] ?: return null
        val html = prefs[KEY_CACHED_NEWER_HTML] ?: return null
        val apk = prefs[KEY_CACHED_NEWER_APK]?.takeIf { it.isNotBlank() }
        val dismissed = prefs[KEY_DISMISSED_TAG]
        if (tag == dismissed) return null
        if (!SemanticVersionCompare.isRemoteNewer(tag, currentVersionName)) return null
        return GitHubLatestRelease(tagName = tag, htmlUrl = html, apkDownloadUrl = apk)
    }

    /**
     * Last GitHub [releases/latest] we saw that is newer than the installed app — keeps the home banner
     * without waiting for the next network check. Cleared when the API reports we are up to date.
     */
    suspend fun saveCachedNewerRelease(context: Context, r: GitHubLatestRelease) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_CACHED_NEWER_TAG] = r.tagName
            prefs[KEY_CACHED_NEWER_HTML] = r.htmlUrl
            prefs[KEY_CACHED_NEWER_APK] = r.apkDownloadUrl.orEmpty()
        }
    }

    suspend fun clearCachedNewerRelease(context: Context) {
        context.updateDataStore.edit { prefs ->
            prefs.remove(KEY_CACHED_NEWER_TAG)
            prefs.remove(KEY_CACHED_NEWER_HTML)
            prefs.remove(KEY_CACHED_NEWER_APK)
        }
    }

    /**
     * Shown on Home when a newer release was previously detected and the user has not dismissed that tag.
     */
    fun homeBannerReleaseFlow(
        context: Context,
        currentVersionName: String,
    ): Flow<GitHubLatestRelease?> =
        context.updateDataStore.data
            .map { prefs ->
                val tag = prefs[KEY_CACHED_NEWER_TAG] ?: return@map null
                val html = prefs[KEY_CACHED_NEWER_HTML] ?: return@map null
                val apk = prefs[KEY_CACHED_NEWER_APK]?.takeIf { it.isNotBlank() }
                val dismissed = prefs[KEY_DISMISSED_TAG]
                if (tag == dismissed) return@map null
                if (!SemanticVersionCompare.isRemoteNewer(tag, currentVersionName)) return@map null
                GitHubLatestRelease(tagName = tag, htmlUrl = html, apkDownloadUrl = apk)
            }
            .distinctUntilChanged()
}
