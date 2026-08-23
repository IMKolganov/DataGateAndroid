package com.imkolganov.datagate.vpn

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.imkolganov.datagate.R
import com.imkolganov.datagate.vpn.xray.XrayVpnService
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, shadows = [ShadowVpnService::class])
class VpnControllerPermissionTest {

    private val noopLauncher = object : ActivityResultLauncher<Intent>() {
        override fun launch(input: Intent, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = ActivityResultContracts.StartActivityForResult()
    }

    private fun findStartedService(activity: Activity, action: String): Intent? {
        val shadow = shadowOf(activity)
        while (true) {
            val next = shadow.getNextStartedService() ?: return null
            if (next.action == action) return next
        }
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

        val startedIntent = findStartedService(activity, OpenVpn3Service.ACTION_CONNECT)
        assertNotNull(
            "Granting permission after startWithConfig() must resume the pending connect, " +
                "not just clear it and leave the user stuck",
            startedIntent
        )
        assertTrue(state.isConnectRequested)
    }

    @Test
    fun startWithConfig_directTransport_whenPermissionGranted_startsWithoutWssUrl() {
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
            configText = "remote vpn.example.com 1194\nproto udp\n",
            wssLink = null,
            linkProtocol = VpnLinkProtocol.UDP,
            transport = VpnTransport.Direct,
        )

        ShadowVpnService.setPrepareResult(null)
        controller.onPermissionGranted()

        val startedIntent = findStartedService(activity, OpenVpn3Service.ACTION_CONNECT)
        assertNotNull(startedIntent)
        assertEquals(
            VpnTransport.Direct.intentValue(),
            startedIntent?.getStringExtra(OpenVpn3Service.EXTRA_TRANSPORT)
        )
        assertNull(startedIntent?.getStringExtra(OpenVpn3Service.EXTRA_WSS_URL))
        assertTrue(state.isConnectRequested)
    }

    @Test
    fun startWithXrayConfig_whenPermissionMissing_doesNotStartServiceYet() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        controller.startWithXrayConfig("""{"outbounds":[{"tag":"proxy","protocol":"freedom"}]}""")

        assertNull(shadowOf(activity).peekNextStartedService())
        assertEquals(activity.getString(R.string.vpn_waiting_permission), state.lastMessage)
    }

    @Test
    fun startWithXrayConfig_thenPermissionGranted_startsXrayServiceWithPendingConfig() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        val cfg = """{"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}]}"""
        controller.startWithXrayConfig(cfg)

        ShadowVpnService.setPrepareResult(null)
        controller.onPermissionGranted()

        val startedIntent = findStartedService(activity, XrayVpnService.ACTION_CONNECT)
        assertNotNull(startedIntent)
        assertNotNull(startedIntent?.getStringExtra(XrayVpnService.EXTRA_CONFIG_PATH))
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            startedIntent?.getStringArrayListExtra(XrayVpnService.EXTRA_DNS_SERVERS),
        )
        assertTrue(state.isConnectRequested)
    }

    @Test
    fun startWithXrayConfig_withDnsServers_passesDnsExtra() {
        ShadowVpnService.setPrepareResult(null)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        val cfg = """{"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{
          "vnext":[{"address":"node.example.com","port":443}]
        }}]}"""
        controller.startWithXrayConfig(
            configText = cfg,
            dnsServers = listOf("172.20.0.1"),
            dnsIdentityEnabled = true,
        )

        val startedIntent = findStartedService(activity, XrayVpnService.ACTION_CONNECT)
        assertEquals(
            listOf("172.20.0.1"),
            startedIntent?.getStringArrayListExtra(XrayVpnService.EXTRA_DNS_SERVERS),
        )
        assertEquals(
            true,
            startedIntent?.getBooleanExtra(XrayVpnService.EXTRA_DNS_IDENTITY_ENABLED, false),
        )
    }

    @Test
    fun startWithXrayConfig_thenPermissionGranted_restoresPendingDnsAndIdentity() {
        ShadowVpnService.setPrepareResult(Intent())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        val cfg = """{"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}]}"""
        controller.startWithXrayConfig(
            configText = cfg,
            dnsServers = listOf("172.20.0.1"),
            dnsIdentityEnabled = true,
        )

        ShadowVpnService.setPrepareResult(null)
        controller.onPermissionGranted()

        val startedIntent = findStartedService(activity, XrayVpnService.ACTION_CONNECT)
        assertNotNull(startedIntent)
        assertEquals(
            listOf("172.20.0.1"),
            startedIntent?.getStringArrayListExtra(XrayVpnService.EXTRA_DNS_SERVERS),
        )
        assertEquals(
            true,
            startedIntent?.getBooleanExtra(XrayVpnService.EXTRA_DNS_IDENTITY_ENABLED, false),
        )
        assertTrue(state.isConnectRequested)
    }

    @Test
    fun startWithXrayConfig_withBypassRoutes_passesExcludedRoutesPathExtra() {
        ShadowVpnService.setPrepareResult(null)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var state = VpnStatusUiState()
        val controller = VpnController(
            activity = activity,
            permissionLauncher = noopLauncher,
            onStateChange = { state = it },
            getState = { state }
        )

        val cfg = """{"outbounds":[{"tag":"proxy","protocol":"freedom","settings":{}}]}"""
        controller.startWithXrayConfig(
            configText = cfg,
            bypassRoutes = listOf(
                Ipv4CidrRoute(networkAddress = "1.2.3.0", netmask = "255.255.255.0", prefixLength = 24),
            ),
        )

        val startedIntent = findStartedService(activity, XrayVpnService.ACTION_CONNECT)
        assertNotNull(startedIntent)
        val routesPath = startedIntent?.getStringExtra(XrayVpnService.EXTRA_EXCLUDED_ROUTES_PATH)
        assertNotNull(routesPath)
        val routesFile = File(routesPath!!)
        assertTrue(routesFile.isFile)
        assertTrue(routesFile.readText().contains("1.2.3.0/24"))
        assertTrue(state.isConnectRequested)
    }
}
