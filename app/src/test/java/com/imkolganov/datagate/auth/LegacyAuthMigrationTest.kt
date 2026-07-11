package com.imkolganov.datagate.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyAuthMigrationTest {

    @Test
    fun migrationAlreadyDone_returnsNoActions() {
        val input = LegacyAuthMigration.Input(
            migrationAlreadyDone = true,
            isAppUpdate = true,
            isFreshInstall = false,
            hasLegacySessionData = true
        )
        val decision = LegacyAuthMigration.evaluate(input)
        assertFalse(decision.shouldClearSession)
        assertFalse(decision.shouldMarkMigrationDone)
    }

    @Test
    fun appUpdate_withLegacyData_clearsSession() {
        val input = LegacyAuthMigration.Input(
            migrationAlreadyDone = false,
            isAppUpdate = true,
            isFreshInstall = false,
            hasLegacySessionData = true
        )
        val decision = LegacyAuthMigration.evaluate(input)
        assertTrue(decision.shouldClearSession)
        assertTrue(decision.shouldMarkMigrationDone)
    }

    @Test
    fun freshInstall_withLegacyData_clearsSession() {
        // This simulates Android backup restore of old prefs on a new install
        val input = LegacyAuthMigration.Input(
            migrationAlreadyDone = false,
            isAppUpdate = false,
            isFreshInstall = true,
            hasLegacySessionData = true
        )
        val decision = LegacyAuthMigration.evaluate(input)
        assertTrue(decision.shouldClearSession)
        assertTrue(decision.shouldMarkMigrationDone)
    }

    @Test
    fun normalLaunch_noLegacyData_marksDoneWithoutClearing() {
        val input = LegacyAuthMigration.Input(
            migrationAlreadyDone = false,
            isAppUpdate = false,
            isFreshInstall = false,
            hasLegacySessionData = false
        )
        val decision = LegacyAuthMigration.evaluate(input)
        assertFalse(decision.shouldClearSession)
        assertTrue(decision.shouldMarkMigrationDone)
    }
}
