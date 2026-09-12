package com.imkolganov.datagate.logger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DebugPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            DebugPreferences.setVpnDebugModeEnabled(context, false)
            DebugPreferences.setEngineJournalEnabled(context, false)
        }
    }

    @Test
    fun defaults_areOff() = runBlocking {
        assertFalse(DebugPreferences.isVpnDebugModeEnabled(context))
        assertFalse(DebugPreferences.isEngineJournalEnabled(context))
        assertFalse(DebugPreferences.vpnDebugModeFlow(context).first())
        assertFalse(DebugPreferences.engineJournalEnabledFlow(context).first())
    }

    @Test
    fun engineJournal_roundTripIndependentOfFileDebug() = runBlocking {
        DebugPreferences.setEngineJournalEnabled(context, true)
        assertTrue(DebugPreferences.isEngineJournalEnabled(context))
        assertTrue(DebugPreferences.engineJournalEnabledFlow(context).first())
        assertFalse(DebugPreferences.isVpnDebugModeEnabled(context))

        DebugPreferences.setVpnDebugModeEnabled(context, true)
        assertTrue(DebugPreferences.isVpnDebugModeEnabled(context))
        assertTrue(DebugPreferences.isEngineJournalEnabled(context))

        DebugPreferences.setEngineJournalEnabled(context, false)
        assertFalse(DebugPreferences.isEngineJournalEnabled(context))
        assertTrue(DebugPreferences.isVpnDebugModeEnabled(context))
    }
}
