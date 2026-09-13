package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayNetworkPolicyTest {

    @Test
    fun usableNetwork_requiresInternetAndValidated() {
        assertFalse(XrayNetworkPolicy.hasUsableNetwork(hasInternet = true, validated = false))
        assertFalse(XrayNetworkPolicy.hasUsableNetwork(hasInternet = false, validated = true))
        assertTrue(XrayNetworkPolicy.hasUsableNetwork(hasInternet = true, validated = true))
    }

    @Test
    fun reconnect_onlyWhenDesiredIdleAndNetworkIsBack() {
        assertTrue(
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = false,
                stopping = false,
                running = false,
                networkAvailable = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = true,
                stopping = true,
                running = false,
                networkAvailable = true,
            ),
        )
    }

    @Test
    fun waitForNetwork_whenDesiredAndOffline() {
        assertTrue(
            XrayNetworkPolicy.shouldWaitForNetwork(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = false,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldWaitForNetwork(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = false,
            ),
        )
    }

    @Test
    fun restartUnhealthy_whenCoreDiedButUserStillWantsTunnel() {
        assertTrue(
            XrayNetworkPolicy.shouldRestartUnhealthySession(
                desiredConnection = true,
                stopping = false,
                running = true,
                coreRunning = false,
                networkAvailable = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartUnhealthySession(
                desiredConnection = true,
                stopping = false,
                running = true,
                coreRunning = true,
                networkAvailable = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartUnhealthySession(
                desiredConnection = true,
                stopping = false,
                running = true,
                coreRunning = false,
                networkAvailable = false,
            ),
        )
    }

    @Test
    fun networkCycle_offlineThenOnlineReconnects() {
        var running = false
        val desired = true
        val stopping = false

        assertTrue(
            XrayNetworkPolicy.shouldWaitForNetwork(desired, stopping, running, networkAvailable = false),
        )
        assertTrue(
            XrayNetworkPolicy.shouldReconnect(desired, stopping, running, networkAvailable = true),
        )
        running = true
        assertFalse(
            XrayNetworkPolicy.shouldReconnect(desired, stopping, running, networkAvailable = true),
        )
    }

    @Test
    fun paused_doesNotReconnectOrWait() {
        assertFalse(
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = true,
                paused = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldWaitForNetwork(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = false,
                paused = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartUnhealthySession(
                desiredConnection = true,
                stopping = false,
                running = true,
                coreRunning = false,
                networkAvailable = true,
                paused = true,
            ),
        )
    }

    @Test
    fun warnWhenConnectedWithoutVpnTransport() {
        assertTrue(XrayNetworkPolicy.shouldWarnMissingVpnTransport(running = true, hasVpnTransport = false))
        assertFalse(XrayNetworkPolicy.shouldWarnMissingVpnTransport(running = true, hasVpnTransport = true))
        assertFalse(XrayNetworkPolicy.shouldWarnMissingVpnTransport(running = false, hasVpnTransport = false))
    }
}
