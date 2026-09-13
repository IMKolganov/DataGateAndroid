package com.imkolganov.datagate.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imkolganov.datagate.vpn.xray.XrayNetworkPolicy
import com.imkolganov.datagate.vpn.xray.XrayQueryStatusPolicy
import com.imkolganov.datagate.vpn.xray.XrayVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device/emulator checks for the VPN cycle helpers that cannot run as plain JVM tests:
 * live network capabilities and a real [XrayVpnService] QUERY_STATUS start.
 */
@RunWith(AndroidJUnit4::class)
class VpnLifecycleInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packageName_isDataGate() {
        assertEquals("com.imkolganov.datagate", context.packageName)
    }

    @Test
    fun deviceNetworkCapabilities_matchUsableNetworkPolicy() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val usable = XrayNetworkPolicy.hasUsableNetwork(
            hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        )
        if (caps == null) {
            assertTrue(!usable)
        } else {
            assertEquals(
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                usable,
            )
        }
    }

    @Test
    fun queryStatusPolicy_onDevice_doesNotTrustStaleConnectedCache() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = false,
            hasTun = false,
            stopping = false,
            lastEventName = "CONNECTED",
            lastEventInfo = "stale",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
        )
        assertEquals("DISCONNECTED", resolved.first)
    }

    @Test
    fun xrayQueryStatus_startsServiceAndBroadcasts() {
        val latch = CountDownLatch(1)
        var eventName: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                eventName = intent?.getStringExtra(OpenVpn3Service.EXTRA_EVENT_NAME)
                latch.countDown()
            }
        }
        val filter = IntentFilter(OpenVpn3Service.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        try {
            context.startService(
                Intent(context, XrayVpnService::class.java).apply {
                    action = XrayVpnService.ACTION_QUERY_STATUS
                },
            )
            assertTrue(
                "Xray QUERY_STATUS should broadcast on a real service",
                latch.await(8, TimeUnit.SECONDS),
            )
            assertTrue(!eventName.isNullOrBlank())
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
