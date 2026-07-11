package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnPauseBroadcastPolicyTest {

    @Test
    fun userPauseCommand_mustNotBroadcastPausedOptimistically() {
        assertFalse(OpenVpnPauseBroadcastPolicy.mayBroadcastPausedFromUserCommand())
    }

    @Test
    fun userResumeCommand_mustNotBroadcastResumedOptimistically() {
        assertFalse(OpenVpnPauseBroadcastPolicy.mayBroadcastResumedFromUserCommand())
    }

    @Test
    fun corePauseEvent_isAuthoritativeForPausedBroadcast() {
        assertTrue(OpenVpnPauseBroadcastPolicy.shouldBroadcastPausedOnCoreEvent("PAUSE"))
        assertTrue(OpenVpnPauseBroadcastPolicy.shouldBroadcastPausedOnCoreEvent("pause"))
        assertFalse(OpenVpnPauseBroadcastPolicy.shouldBroadcastPausedOnCoreEvent("PAUSED"))
    }

    @Test
    fun coreResumeEvent_isAuthoritativeForResumedBroadcast() {
        assertTrue(OpenVpnPauseBroadcastPolicy.shouldBroadcastResumedOnCoreEvent("RESUME"))
        assertFalse(OpenVpnPauseBroadcastPolicy.shouldBroadcastResumedOnCoreEvent("RESUMED"))
    }
}
