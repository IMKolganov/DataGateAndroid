package com.imkolganov.datagate.vpn

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VpnServerSelectionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        VpnServerSelectionStore.clear(context)
    }

    @Test
    fun clear_removesManualModeAndSelectedProServerId() {
        VpnServerSelectionStore.setMode(context, ServerSelectionMode.MANUAL)
        VpnServerSelectionStore.setSelectedServerId(context, 75)

        VpnServerSelectionStore.clear(context)

        assertEquals(ServerSelectionMode.AUTO, VpnServerSelectionStore.getMode(context))
        assertNull(VpnServerSelectionStore.getSelectedServerId(context))
    }
}
