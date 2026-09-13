package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayPausePolicyTest {

    @Test
    fun pauseKeepsServiceAndBlocksAutoReconnect() {
        assertTrue(XrayPausePolicy.shouldKeepServiceOnPause())
        assertFalse(
            XrayPausePolicy.shouldAutoReconnect(
                desiredConnection = true,
                stopping = false,
                paused = true,
                running = false,
                networkAvailable = true,
            ),
        )
        assertTrue(
            XrayPausePolicy.shouldAutoReconnect(
                desiredConnection = true,
                stopping = false,
                paused = false,
                running = false,
                networkAvailable = true,
            ),
        )
    }
}
