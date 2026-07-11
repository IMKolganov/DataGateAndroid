package com.imkolganov.datagate

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.imkolganov.datagate.auth.LegacyAuthMigration
import com.imkolganov.datagate.logger.CrashLogger
import com.imkolganov.datagate.logger.CrashUploadWorkScheduler
import com.imkolganov.datagate.ui.theme.LanguagePreferenceStore
import com.imkolganov.datagate.ui.theme.ThemePreferenceStore

class DataGateApp : Application() {

    lateinit var crashLogger: CrashLogger
        private set

    override fun onCreate() {
        ThemePreferenceStore.apply(this)
        LanguagePreferenceStore.apply(this)
        super.onCreate()
        migrateLegacyAuthSessionIfNeeded()
        crashLogger = CrashLogger(this)
        crashLogger.install()
        CrashUploadWorkScheduler.enqueue(this)
    }

    private fun migrateLegacyAuthSessionIfNeeded() {
        val prefs = getSharedPreferences("auth_store", MODE_PRIVATE)
        val packageInfo = getPackageInfoCompat()
        val isAppUpdate = packageInfo?.let { it.lastUpdateTime > it.firstInstallTime } == true
        val isFreshInstall = packageInfo?.let { it.lastUpdateTime == it.firstInstallTime } == true
        val hasLegacySessionData = !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() ||
            !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()

        val decision = LegacyAuthMigration.evaluate(
            LegacyAuthMigration.Input(
                migrationAlreadyDone = prefs.getBoolean(KEY_LEGACY_AUTH_MIGRATION_DONE, false),
                isAppUpdate = isAppUpdate,
                isFreshInstall = isFreshInstall,
                hasLegacySessionData = hasLegacySessionData,
            )
        )
        if (decision.shouldClearSession) {
            prefs.edit {
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_ACCESS_EXPIRATION)
                remove(KEY_REFRESH_EXPIRATION)
                putBoolean(KEY_AUTO_LOGIN_ENABLED, false)
            }
        }
        if (decision.shouldMarkMigrationDone) {
            prefs.edit { putBoolean(KEY_LEGACY_AUTH_MIGRATION_DONE, true) }
        }
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
