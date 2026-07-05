package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.BindException
import java.util.Random

class LocalBridgePortPoolTest {

    @Test
    fun candidatePorts_startsWithEphemeralThenUsesDedicatedPool() {
        val ports = LocalBridgePortPool.candidatePorts(Random(1))
        assertEquals(0, ports.first())
        assertEquals(101, ports.size)
        assertTrue(ports.drop(1).all { it in LocalBridgePortPool.POOL_START..LocalBridgePortPool.POOL_END })
        assertEquals(ports.drop(1).toSet().size, 100)
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
