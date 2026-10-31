package com.imkolganov.datagate.vpn.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnProbeGenerationTest {

    @Test
    fun next_invalidatesPreviousSession() {
        val gen = VpnProbeGeneration()
        val first = gen.next()
        assertTrue(gen.isCurrent(first))
        val second = gen.next()
        assertFalse(gen.isCurrent(first))
        assertTrue(gen.isCurrent(second))
    }

    @Test
    fun invalidate_dropsInFlightSession() {
        val gen = VpnProbeGeneration()
        val session = gen.next()
        gen.invalidate()
        assertFalse(gen.isCurrent(session))
    }
}
