package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertEquals
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
        assertFalse(
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = true,
                connectInFlight = true,
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

    @Test
    fun underlyingSwitch_restartsLiveSessionOnHomeWifi() {
        assertTrue(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = 11L,
                currentHandle = 22L,
            ),
        )
    }

    @Test
    fun underlyingSwitch_skipsFirstObservationAndSameNetwork() {
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = null,
                currentHandle = 22L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = 22L,
                currentHandle = 22L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = 11L,
                currentHandle = null,
            ),
        )
    }

    @Test
    fun underlyingSwitch_doesNotRestartWhenPausedOrOffline() {
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = true,
                previousHandle = 11L,
                currentHandle = 22L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = false,
                paused = false,
                previousHandle = 11L,
                currentHandle = 22L,
            ),
        )
    }

    @Test
    fun pickUnderlyingHandle_ignoresVpnIfaceAndPrefersValidatedWifi() {
        val cell = XrayNetworkPolicy.NetworkSnapshot(
            handle = 11L,
            hasVpnTransport = false,
            hasInternet = true,
            validated = true,
        )
        val vpn = XrayNetworkPolicy.NetworkSnapshot(
            handle = 99L,
            hasVpnTransport = true,
            hasInternet = true,
            validated = true,
        )
        val wifiUnvalidated = XrayNetworkPolicy.NetworkSnapshot(
            handle = 22L,
            hasVpnTransport = false,
            hasInternet = true,
            validated = false,
        )
        val wifi = XrayNetworkPolicy.NetworkSnapshot(
            handle = 22L,
            hasVpnTransport = false,
            hasInternet = true,
            validated = true,
        )
        assertEquals(11L, XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn, cell)))
        assertEquals(22L, XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn, wifi)))
        assertEquals(22L, XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn, wifiUnvalidated)))
        assertEquals(null, XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn)))
        assertEquals(
            null,
            XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn, wifiUnvalidated), requireValidated = true),
        )
        assertEquals(
            22L,
            XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn, wifi), requireValidated = true),
        )
    }

    @Test
    fun commitUnderlyingHandle_keepsPreviousWhileSwitchIsPending() {
        assertTrue(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = null,
                currentHandle = 11L,
                didRestart = false,
            ),
        )
        assertTrue(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = 11L,
                currentHandle = 11L,
                didRestart = false,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = 11L,
                currentHandle = 22L,
                didRestart = false,
            ),
        )
        assertTrue(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = 11L,
                currentHandle = 22L,
                didRestart = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = 11L,
                currentHandle = null,
                didRestart = false,
            ),
        )
    }

    @Test
    fun debounce_blocksImmediateSecondRestart() {
        val window = XrayNetworkPolicy.UNDERLYING_SWITCH_DEBOUNCE_MS
        assertTrue(XrayNetworkPolicy.shouldApplyRestartDebounce(nowMs = 100L, lastRestartAtMs = 0L, windowMs = window))
        assertFalse(XrayNetworkPolicy.shouldApplyRestartDebounce(nowMs = window - 1L, lastRestartAtMs = 1L, windowMs = window))
        assertTrue(XrayNetworkPolicy.shouldApplyRestartDebounce(nowMs = 1L + window, lastRestartAtMs = 1L, windowMs = window))
    }

    @Test
    fun underlyingSwitch_waitsForSettleAndSkipsInFlightConnect() {
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = 11L,
                currentHandle = 22L,
                settledForMs = 1_000L,
            ),
        )
        assertTrue(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = 11L,
                currentHandle = 22L,
                settledForMs = XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = 11L,
                currentHandle = 22L,
                connectInFlight = true,
            ),
        )
    }

    @Test
    fun switchWatch_resetsWhenWifiFlapsThenSettles() {
        var watch = XrayNetworkPolicy.UnderlyingSwitchWatch()
        watch = watch.observe(22L, nowMs = 1_000L)
        assertEquals(0L, watch.settledForMs(22L, nowMs = 1_000L))
        assertEquals(3_000L, watch.settledForMs(22L, nowMs = 4_000L))
        watch = watch.observe(null, nowMs = 4_500L)
        assertEquals(0L, watch.settledForMs(22L, nowMs = 4_500L))
        watch = watch.observe(22L, nowMs = 5_000L)
        assertEquals(0L, watch.settledForMs(22L, nowMs = 5_000L))
        assertEquals(
            XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS,
            watch.settledForMs(22L, nowMs = 5_000L + XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS),
        )
    }

    @Test
    fun homeWifiHandshake_keepsCellHandleUntilValidatedThenRestarts() {
        var lastHandle: Long? = 11L
        val wifi = 22L
        val wantsRestartWhileOffline = XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
            desiredConnection = true,
            stopping = false,
            running = true,
            networkAvailable = false,
            paused = false,
            previousHandle = lastHandle,
            currentHandle = wifi,
        )
        assertFalse(wantsRestartWhileOffline)
        assertFalse(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = lastHandle,
                currentHandle = wifi,
                didRestart = false,
            ),
        )

        val tooEarly = XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
            desiredConnection = true,
            stopping = false,
            running = true,
            networkAvailable = true,
            paused = false,
            previousHandle = lastHandle,
            currentHandle = wifi,
            settledForMs = 1_000L,
        )
        assertFalse(tooEarly)

        val switched = XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
            desiredConnection = true,
            stopping = false,
            running = true,
            networkAvailable = true,
            paused = false,
            previousHandle = lastHandle,
            currentHandle = wifi,
            settledForMs = XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS,
        )
        assertTrue(switched)
        assertTrue(
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = lastHandle,
                currentHandle = wifi,
                didRestart = true,
            ),
        )
        lastHandle = wifi
        assertEquals(22L, lastHandle)
    }

    @Test
    fun settleRecheck_scheduledOnlyWhileNewValidatedUplinkIsPending() {
        assertTrue(
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = 11L,
                currentHandle = 22L,
                connectInFlight = false,
                remainingSettleMs = 4_000L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = 11L,
                currentHandle = 22L,
                connectInFlight = false,
                remainingSettleMs = 0L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = 11L,
                currentHandle = 22L,
                connectInFlight = true,
                remainingSettleMs = 4_000L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = 22L,
                currentHandle = 22L,
                connectInFlight = false,
                remainingSettleMs = 4_000L,
            ),
        )
        assertEquals(0L, XrayNetworkPolicy.remainingSettleMs(5_000L))
        assertEquals(2_000L, XrayNetworkPolicy.remainingSettleMs(3_000L))
    }

    @Test
    fun slowPhoneHandoff_oneConnectIsNotRestartedByLaterCallbacks() {
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
                running = false,
                networkAvailable = true,
                connectInFlight = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldWaitForNetwork(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = false,
                connectInFlight = true,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartUnhealthySession(
                desiredConnection = true,
                stopping = false,
                running = true,
                coreRunning = false,
                networkAvailable = true,
                connectInFlight = true,
            ),
        )
    }
}
