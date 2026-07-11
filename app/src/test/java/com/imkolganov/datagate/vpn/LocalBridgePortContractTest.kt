package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.BindException

/**
 * Architecture contract: the WSS bridge must never bind OS ephemeral ports (port 0)
 * or steal localhost ports used by companion apps (Galaxy Wearable adb hub uses 4444).
 *
 * Bridges are created only during VPN connect in [OpenVpn3Service.startBridgeWithRetry],
 * not on app cold start — but a stuck VPN session keeps the bridge socket open.
 */
class LocalBridgePortContractTest {

    @Test
    fun candidatePorts_neverUsesEphemeralPortZero() {
        val ports = LocalBridgePortPool.candidatePorts(
            poolStart = LocalBridgePortPool.DEFAULT_POOL_START,
            poolEnd = LocalBridgePortPool.DEFAULT_POOL_END,
            random = java.util.Random(42),
        )
        assertFalse("Port 0 lets the OS pick ephemeral ports and can break companion apps", ports.contains(0))
    }

    @Test
    fun candidatePorts_staysInsideDedicatedPoolOnly() {
        val ports = LocalBridgePortPool.candidatePorts(38_400, 38_499, java.util.Random(1))
        assertEquals(100, ports.size)
        assertTrue(ports.all { it in 38_400..38_499 })
        assertEquals(ports.toSet().size, ports.size)
    }

    @Test
    fun defaultPool_doesNotOverlapGalaxyWearableAdbHubPort() {
        val wearableAdbHubPort = 4_444
        val start = LocalBridgePortPool.DEFAULT_POOL_START
        val end = LocalBridgePortPool.DEFAULT_POOL_END
        assertFalse(wearableAdbHubPort in start..end)
    }

    @Test
    fun defaultPool_isInUserPortRange_notPrivileged() {
        assertTrue(LocalBridgePortPool.DEFAULT_POOL_START >= LocalBridgePortPool.MIN_USER_PORT)
        assertTrue(LocalBridgePortPool.DEFAULT_POOL_END <= LocalBridgePortPool.MAX_USER_PORT)
        assertTrue(LocalBridgePortPool.DEFAULT_POOL_START < 49_152)
    }

    @Test
    fun isBindConflict_detectsPortCollision() {
        assertTrue(LocalBridgePortPool.isBindConflict(BindException("bind failed: EADDRINUSE")))
    }

    @Test
    fun normalizeRange_neverExpandsToIncludePortZero() {
        val normalized = LocalBridgePortPool.normalizeRange(38_400, 38_499)
        assertTrue(normalized.poolStart > 0)
        assertTrue(normalized.poolEnd > 0)
    }
}
