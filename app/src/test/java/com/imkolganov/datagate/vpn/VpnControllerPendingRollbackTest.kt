package com.imkolganov.datagate.vpn

import android.app.Activity
import android.content.Intent
import androidx.core.app.ActivityOptionsCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression: [VpnController] hardcoded a `PAUSED`/`RESUMED`/`CONNECTED`/`DISCONNECTED`
 * event-name list to decide when to clear [VpnController]'s internal `pendingCommandRollback`
 * snapshot — a list that silently drifted out of sync with
 * [VpnCommandContract.isAuthoritativeTunnelEvent] (which *also* treats `ERROR`,
 * `TUN_SETUP_FAILED`, and `DISCONNECTING` as authoritative, per the fix for the
 * "pendingUserCommand stuck" bug). Left uncleared, a stale rollback snapshot from *before* the
 * tunnel actually failed could later resurrect a wrong pre-failure UI state if a delayed
 * `PAUSE_REJECTED`/`RESUME_REJECTED` broadcast arrives after the tunnel already errored out.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnControllerPendingRollbackTest {

    private val noopLauncher = object : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = ActivityResultContracts.StartActivityForResult()
    }

    private fun statusIntent(eventName: String, eventInfo: String = "", fromQuery: Boolean = false) =
        Intent(OpenVpn3Service.ACTION_STATUS).apply {
            putExtra(OpenVpn3Service.EXTRA_EVENT_NAME, eventName)
            putExtra(OpenVpn3Service.EXTRA_EVENT_INFO, eventInfo)
            putExtra(OpenVpn3Service.EXTRA_STATUS_FROM_QUERY, fromQuery)
        }

    private fun Activity.sendStatusBroadcastAndFlush(intent: Intent) {
        sendBroadcast(intent)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun pendingPauseRollback_isNotResurrected_afterTunnelAlreadyErroredOut() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            selectedServerName = "Frankfurt",
        )
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state },
        )
        controller.onStart()

        controller.requestPause()
        assertTrue("Pause tap must be pending, not yet confirmed", state.pendingUserCommand != null)
        assertTrue("Tunnel flags must not change until PAUSED broadcast", state.isVpnConnected)

        // Tunnel fails before the native pause is confirmed — this must clear the stale rollback,
        // exactly like it already clears pendingUserCommand (see VpnEventMapper's ERROR branch).
        activity.sendStatusBroadcastAndFlush(statusIntent("ERROR", "tunnel setup failed"))
        assertFalse("ERROR is authoritative — must not stay connected", state.isVpnConnected)

        // A delayed native pause-failure callback now arrives, well after the tunnel already
        // errored out and the UI already reflects the disconnect.
        activity.sendStatusBroadcastAndFlush(statusIntent("PAUSE_REJECTED", "pause_failed"))

        assertFalse(
            "PAUSE_REJECTED must roll back to the current (post-error) state, not resurrect the " +
                "stale pre-error snapshot captured when pause was requested",
            state.isVpnConnected,
        )
    }
}
