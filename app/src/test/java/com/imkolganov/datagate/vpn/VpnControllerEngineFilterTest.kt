package com.imkolganov.datagate.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Late DISCONNECTED from the stopped peer must not wipe a live / in-flight session
 * on the active engine (shared ACTION_STATUS channel).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnControllerEngineFilterTest {

    private val noopLauncher = object : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = ActivityResultContracts.StartActivityForResult()
    }

    private fun statusIntent(
        eventName: String,
        engine: String,
        eventInfo: String = "",
        fromQuery: Boolean = false,
    ) = Intent(OpenVpn3Service.ACTION_STATUS).apply {
        putExtra(OpenVpn3Service.EXTRA_EVENT_NAME, eventName)
        putExtra(OpenVpn3Service.EXTRA_EVENT_INFO, eventInfo)
        putExtra(OpenVpn3Service.EXTRA_STATUS_FROM_QUERY, fromQuery)
        putExtra(OpenVpn3Service.EXTRA_STATUS_ENGINE, engine)
    }

    @Test
    fun lateOpenVpnPeerDisconnected_whileXrayActive_doesNotClearSession() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            selectedServerId = 88,
            selectedServerName = "Norway Xray",
        )
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state },
        )
        controller.onStart()

        activity.getSharedPreferences("vpn_state", Context.MODE_PRIVATE).edit {
            putString("vpn_active_engine", OpenVpn3Service.ENGINE_XRAY)
            putInt("vpn_session_server_id", 88)
        }

        activity.sendBroadcast(
            statusIntent("DISCONNECTED", OpenVpn3Service.ENGINE_OPENVPN, "late peer stop"),
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(state.isVpnConnected)
        assertTrue(state.isConnectRequested)
        assertEquals(88, state.selectedServerId)
        assertEquals("Norway Xray", state.selectedServerName)
    }

    @Test
    fun activeEngineDisconnected_stillClearsSession() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            selectedServerId = 7,
            selectedServerName = "Berlin",
        )
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state },
        )
        controller.onStart()

        activity.getSharedPreferences("vpn_state", Context.MODE_PRIVATE).edit {
            putString("vpn_active_engine", OpenVpn3Service.ENGINE_OPENVPN)
        }

        activity.sendBroadcast(
            statusIntent("DISCONNECTED", OpenVpn3Service.ENGINE_OPENVPN, "user cancel"),
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(state.isVpnConnected)
        assertFalse(state.isConnectRequested)
        assertEquals(null, state.selectedServerId)
    }
}
