package com.imkolganov.datagate

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.imkolganov.datagate.logger.CrashLogger
import com.imkolganov.datagate.logger.CrashUploadWorkScheduler
import com.imkolganov.datagate.ui.theme.LanguagePreferenceStore

class DataGateApp : Application() {

    lateinit var crashLogger: CrashLogger
        private set

    override fun onCreate() {
        super.onCreate()
        migrateLegacyAuthSessionIfNeeded()
        LanguagePreferenceStore.apply(this)
        crashLogger = CrashLogger(this)
        crashLogger.install()
        CrashUploadWorkScheduler.enqueue(this)
    }

    private fun migrateLegacyAuthSessionIfNeeded() {
        val prefs = getSharedPreferences("auth_store", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_AUTH_MIGRATION_DONE, false)) {
            return
        }

        val packageInfo = getPackageInfoCompat() ?: run {
            prefs.edit { putBoolean(KEY_LEGACY_AUTH_MIGRATION_DONE, true) }
            return
        }
        val isAppUpdate = packageInfo.lastUpdateTime > packageInfo.firstInstallTime
        val isFreshInstall = packageInfo.lastUpdateTime == packageInfo.firstInstallTime
        val hasLegacySessionData = !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() ||
            !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()

        // Two problematic scenarios:
        // 1) App update with legacy auth schema/state.
        // 2) Fresh reinstall where Android backup restored old auth prefs.
        if ((isAppUpdate || isFreshInstall) && hasLegacySessionData) {
            prefs.edit {
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_ACCESS_EXPIRATION)
                remove(KEY_REFRESH_EXPIRATION)
                putBoolean(KEY_AUTO_LOGIN_ENABLED, false)
            }
        }

        prefs.edit { putBoolean(KEY_LEGACY_AUTH_MIGRATION_DONE, true) }
    }

    private fun getPackageInfoCompat(): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
    }

    private companion object {
        private const val KEY_LEGACY_AUTH_MIGRATION_DONE = "legacy_auth_migration_v1_done"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRATION = "access_expiration"
        private const val KEY_REFRESH_EXPIRATION = "refresh_expiration"
        private const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
    }
}
