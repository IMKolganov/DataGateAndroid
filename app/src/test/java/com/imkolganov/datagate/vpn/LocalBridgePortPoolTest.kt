package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.BindException
import java.util.Random

class LocalBridgePortPoolTest {

    @Test
    fun candidatePorts_usesConfiguredDedicatedPool() {
        val ports = LocalBridgePortPool.candidatePorts(
            poolStart = LocalBridgePortPool.DEFAULT_POOL_START,
            poolEnd = LocalBridgePortPool.DEFAULT_POOL_END,
            random = Random(1)
        )
        assertEquals(100, ports.size)
        assertTrue(
            ports.all {
                it in LocalBridgePortPool.DEFAULT_POOL_START..LocalBridgePortPool.DEFAULT_POOL_END
            }
        )
        assertEquals(ports.toSet().size, 100)
    }

    @Test
    fun isValidInput_acceptsDefaultRange() {
        assertTrue(
            LocalBridgePortPool.isValidInput(
                LocalBridgePortPool.DEFAULT_POOL_START,
                LocalBridgePortPool.DEFAULT_POOL_END
            )
        )
    }

    @Test
    fun isValidInput_rejectsTooSmallSpan() {
        assertFalse(LocalBridgePortPool.isValidInput(38_400, 38_405))
    }

    @Test
    fun isValidInput_rejectsInvertedRange() {
        assertFalse(LocalBridgePortPool.isValidInput(38_500, 38_400))
    }

    @Test
    fun normalizeRange_clampsCorruptedStoredValues() {
        val normalized = LocalBridgePortPool.normalizeRange(60_000, 70_000)
        assertTrue(normalized.poolEnd <= LocalBridgePortPool.MAX_USER_PORT)
        assertTrue(normalized.poolEnd - normalized.poolStart + 1 >= LocalBridgePortPool.MIN_POOL_SPAN)
    }

    @Test
    fun isBindConflict_detectsBindExceptionAndEaddrInUse() {
        assertTrue(LocalBridgePortPool.isBindConflict(BindException("bind failed")))
        assertTrue(LocalBridgePortPool.isBindConflict(RuntimeException("EADDRINUSE (Address already in use)")))
        assertTrue(
            LocalBridgePortPool.isBindConflict(
                RuntimeException("outer", BindException("nested"))
            )
        )
        assertFalse(LocalBridgePortPool.isBindConflict(IllegalStateException("other")))
    }
}
