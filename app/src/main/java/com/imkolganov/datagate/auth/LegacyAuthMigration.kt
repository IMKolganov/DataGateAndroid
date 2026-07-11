package com.imkolganov.datagate.auth

/**
 * One-time migration for legacy auth prefs (pre–auto-login schema and Android backup restore).
 */
object LegacyAuthMigration {
    data class Input(
        val migrationAlreadyDone: Boolean,
        val isAppUpdate: Boolean,
        val isFreshInstall: Boolean,
        val hasLegacySessionData: Boolean,
    )

    data class Decision(
        val shouldClearSession: Boolean,
        val shouldMarkMigrationDone: Boolean,
    )

    fun evaluate(input: Input): Decision {
        if (input.migrationAlreadyDone) {
            return Decision(shouldClearSession = false, shouldMarkMigrationDone = false)
        }
        val shouldClear = (input.isAppUpdate || input.isFreshInstall) && input.hasLegacySessionData
        return Decision(shouldClearSession = shouldClear, shouldMarkMigrationDone = true)
    }
}
