package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalBridgePortPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocalBridgePortPreferences.resetToDefaults(context)
    }

    @Test
    fun getSettings_returnsDefaultsInitially() {
        val settings = LocalBridgePortPreferences.getSettings(context)
        assertEquals(LocalBridgePortPool.DEFAULT_POOL_START, settings.poolStart)
        assertEquals(LocalBridgePortPool.DEFAULT_POOL_END, settings.poolEnd)
    }

    @Test
    fun saveSettings_persistsCustomRange() {
        val saved = LocalBridgePortPreferences.saveSettings(context, 40_000, 40_099)
        assertEquals(40_000, saved.poolStart)
        assertEquals(40_099, saved.poolEnd)
        assertEquals(saved, LocalBridgePortPreferences.getSettings(context))
    }

    @Test
    fun candidatePorts_readsFromPreferences() {
        LocalBridgePortPreferences.saveSettings(context, 41_000, 41_049)
        val ports = LocalBridgePortPool.candidatePorts(context, java.util.Random(0))
        assertEquals(50, ports.size)
        assertTrue(ports.all { it in 41_000..41_049 })
    }
}
