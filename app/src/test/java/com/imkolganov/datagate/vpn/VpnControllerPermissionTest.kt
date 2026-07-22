package com.imkolganov.datagate.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.imkolganov.datagate.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, shadows = [ShadowVpnService::class])
class VpnControllerPermissionTest {

    private val noopLauncher = object : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = ActivityResultContracts.StartActivityForResult()
    }

    @Test
    fun onStart_updatesPermissionState_whenGranted() {
        ShadowVpnService.setPrepareResult(null)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = false)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onStart()

        assertTrue(state.hasVpnPermission)
    }

    @Test
    fun onStart_updatesPermissionState_whenDenied() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = true)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onStart()

        assertFalse(state.hasVpnPermission)
    }

    @Test
    fun onPermissionGranted_updatesPermissionState() {
        ShadowVpnService.setPrepareResult(null)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = false)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onPermissionGranted()

        assertTrue(state.hasVpnPermission)
    }

    @Test
    fun onPermissionDenied_updatesPermissionState_andResetsConnectRequested() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState(hasVpnPermission = true, isConnectRequested = true)
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.onPermissionDenied()

        assertFalse(state.hasVpnPermission)
        assertFalse(state.isConnectRequested)
    }

    // Regression coverage for the "Grant permission" explainer dialog (VpnStatusScreen /
    // AccessScreen): tapping its confirm button must route through the normal connect entry
    // point (which calls startWithConfig with a real config) rather than a bare permission
    // request with no pending config — otherwise granting permission there previously left the
    // user stuck on "permission granted, but config or WSS link is missing" instead of actually
    // connecting. This locks in that startWithConfig()'s pending config survives the system
    // dialog round-trip and actually starts the VPN service once permission is granted.

    @Test
    fun startWithConfig_whenPermissionMissing_doesNotStartServiceYet() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.startWithConfig(
            configText = "remote example.com 443",
            wssLink = "wss://example.com/ws",
            linkProtocol = VpnLinkProtocol.TCP
        )

        assertNull(shadowOf(activity).peekNextStartedService())
        assertEquals(activity.getString(R.string.vpn_waiting_permission), state.lastMessage)
    }

    @Test
    fun startWithConfig_thenPermissionGranted_startsVpnServiceWithThePendingConfig() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.startWithConfig(
            configText = "remote example.com 443",
            wssLink = "wss://example.com/ws",
            linkProtocol = VpnLinkProtocol.TCP
        )

        ShadowVpnService.setPrepareResult(null)
        controller.onPermissionGranted()

        val startedIntent = shadowOf(activity).peekNextStartedService()
        assertNotNull(
            "Granting permission after startWithConfig() must resume the pending connect, " +
                "not just clear it and leave the user stuck",
            startedIntent
        )
        assertEquals(OpenVpn3Service.ACTION_CONNECT, startedIntent?.action)
        assertTrue(state.isConnectRequested)
    }
}
