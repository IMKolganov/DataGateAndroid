package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayNetworkChangePolicyTest {

    @Test
    fun networkIdentityHandle_usesApi28HandleOnPieAndHashBefore() {
        assertEquals(99L, XrayNetworkChangePolicy.networkIdentityHandle(28, api28Handle = 99L, hashCode = 7))
        assertEquals(7L, XrayNetworkChangePolicy.networkIdentityHandle(27, api28Handle = 99L, hashCode = 7))
    }

    @Test
    fun remainingDebounce_zeroUntilFirstRestartThenCountsDown() {
        assertEquals(0L, XrayNetworkChangePolicy.remainingDebounceMs(nowMs = 100L, lastRestartAtMs = 0L))
        assertEquals(
            3_000L,
            XrayNetworkChangePolicy.remainingDebounceMs(nowMs = 6_000L, lastRestartAtMs = 1_000L, windowMs = 8_000L),
        )
        assertEquals(
            0L,
            XrayNetworkChangePolicy.remainingDebounceMs(nowMs = 10_000L, lastRestartAtMs = 1_000L, windowMs = 8_000L),
        )
    }

    @Test
    fun firstObservationAfterConnect_commitsHandleWithoutRestart() {
        val decision = resolve(
            running = true,
            switchHandle = CELL,
            nowMs = 1_000L,
            previous = XrayNetworkChangeState(),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, decision.followUp)
        assertEquals(CELL, decision.state.lastHandle)
        assertFalse(decision.scheduleSettleRecheck)
        assertTrue(decision.cancelSettleRecheck)
    }

    @Test
    fun homeWifi_unvalidatedThenQuietThenSettledRestartsOnce() {
        var state = XrayNetworkChangeState(lastHandle = CELL)

        val unvalidated = resolve(
            running = true,
            networkAvailable = true,
            switchHandle = null,
            nowMs = 2_000L,
            previous = state,
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, unvalidated.followUp)
        assertEquals(CELL, unvalidated.state.lastHandle)
        assertFalse(unvalidated.scheduleSettleRecheck)
        assertTrue(unvalidated.cancelSettleRecheck)
        state = unvalidated.state

        val firstValidated = resolve(
            running = true,
            switchHandle = WIFI,
            nowMs = 3_000L,
            previous = state,
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, firstValidated.followUp)
        assertEquals(CELL, firstValidated.state.lastHandle)
        assertTrue(firstValidated.scheduleSettleRecheck)
        assertFalse(firstValidated.cancelSettleRecheck)
        assertEquals(XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS, firstValidated.remainingWaitMs)
        state = firstValidated.state

        val stillSettling = resolve(
            running = true,
            switchHandle = WIFI,
            nowMs = 6_000L,
            previous = state,
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, stillSettling.followUp)
        assertEquals(CELL, stillSettling.state.lastHandle)
        assertTrue(stillSettling.scheduleSettleRecheck)
        assertEquals(2_000L, stillSettling.remainingWaitMs)
        state = stillSettling.state

        val settled = resolve(
            running = true,
            switchHandle = WIFI,
            nowMs = 8_000L,
            previous = state,
        )
        assertEquals(XrayNetworkFollowUp.RESTART_SWITCH, settled.followUp)
        assertEquals(WIFI, settled.state.lastHandle)
        assertEquals(8_000L, settled.state.lastRestartAtMs)
        assertFalse(settled.scheduleSettleRecheck)
        assertTrue(settled.cancelSettleRecheck)
        state = settled.state

        val inFlight = resolve(
            running = false,
            connectInFlight = true,
            switchHandle = WIFI,
            nowMs = 8_100L,
            previous = state,
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, inFlight.followUp)
        assertFalse(XrayNetworkPolicy.shouldReconnect(
            desiredConnection = true,
            stopping = false,
            running = false,
            networkAvailable = true,
            connectInFlight = true,
        ))
        state = inFlight.state

        val afterConnect = resolve(
            running = true,
            switchHandle = WIFI,
            nowMs = 40_000L,
            previous = state.copy(lastHandle = WIFI),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, afterConnect.followUp)
        assertEquals(WIFI, afterConnect.state.lastHandle)
        assertFalse(afterConnect.scheduleSettleRecheck)
    }

    @Test
    fun wifiFlap_resetsSettleClock() {
        var state = XrayNetworkChangeState(lastHandle = CELL)
        state = resolve(running = true, switchHandle = WIFI, nowMs = 1_000L, previous = state).state
        val lost = resolve(running = true, switchHandle = null, nowMs = 4_000L, previous = state)
        assertEquals(CELL, lost.state.lastHandle)
        assertEquals(0L, lost.settledForMs)
        assertTrue(lost.cancelSettleRecheck)
        state = lost.state

        val back = resolve(running = true, switchHandle = WIFI, nowMs = 4_500L, previous = state)
        assertEquals(XrayNetworkFollowUp.HEALTH, back.followUp)
        assertEquals(XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS, back.remainingWaitMs)
        assertTrue(back.scheduleSettleRecheck)
    }

    @Test
    fun sameMillisecondSecondCallback_doesNotRestartTwice() {
        val first = resolve(
            running = true,
            switchHandle = WIFI,
            nowMs = 10_000L,
            previous = XrayNetworkChangeState(
                lastHandle = CELL,
                watch = XrayNetworkPolicy.UnderlyingSwitchWatch(WIFI, 5_000L),
            ),
        )
        assertEquals(XrayNetworkFollowUp.RESTART_SWITCH, first.followUp)

        val second = resolve(
            running = true,
            switchHandle = WIFI,
            nowMs = 10_000L,
            previous = first.state,
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, second.followUp)
        assertEquals(WIFI, second.state.lastHandle)
        assertEquals(10_000L, second.state.lastRestartAtMs)
    }

    @Test
    fun debounce_blocksImmediateSecondSwitchThenAllowsLater() {
        val afterFirst = XrayNetworkChangeState(
            lastHandle = WIFI,
            lastRestartAtMs = 10_000L,
            watch = XrayNetworkPolicy.UnderlyingSwitchWatch(WIFI, 10_000L),
        )
        val tooSoon = resolve(
            running = true,
            switchHandle = CELL,
            nowMs = 12_000L,
            previous = afterFirst.copy(
                watch = XrayNetworkPolicy.UnderlyingSwitchWatch(CELL, 10_000L),
            ),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, tooSoon.followUp)
        assertEquals(WIFI, tooSoon.state.lastHandle)
        assertTrue(tooSoon.scheduleSettleRecheck)

        val later = resolve(
            running = true,
            switchHandle = CELL,
            nowMs = 20_000L,
            previous = tooSoon.state,
        )
        assertEquals(XrayNetworkFollowUp.RESTART_SWITCH, later.followUp)
        assertEquals(CELL, later.state.lastHandle)
    }

    @Test
    fun pausedAndStopping_neverRestartOrReconnect() {
        val paused = resolve(
            running = true,
            paused = true,
            switchHandle = WIFI,
            nowMs = 10_000L,
            previous = settledWatch(CELL, WIFI),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, paused.followUp)

        val stopping = resolve(
            running = false,
            stopping = true,
            switchHandle = WIFI,
            nowMs = 10_000L,
            previous = XrayNetworkChangeState(lastHandle = CELL),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, stopping.followUp)

        val undesired = resolve(
            desiredConnection = false,
            running = false,
            switchHandle = WIFI,
            nowMs = 10_000L,
            previous = XrayNetworkChangeState(lastHandle = CELL),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, undesired.followUp)
    }

    @Test
    fun sessionDown_reconnectsWhenNetworkReturnsOtherwiseWaits() {
        val wait = resolve(
            running = false,
            networkAvailable = false,
            switchHandle = null,
            nowMs = 4_000L,
            previous = XrayNetworkChangeState(lastHandle = CELL),
        )
        assertEquals(XrayNetworkFollowUp.WAIT, wait.followUp)

        val reconnect = resolve(
            running = false,
            networkAvailable = true,
            switchHandle = WIFI,
            nowMs = 5_000L,
            previous = wait.state,
        )
        assertEquals(XrayNetworkFollowUp.RECONNECT, reconnect.followUp)

        val inFlight = resolve(
            running = false,
            networkAvailable = true,
            connectInFlight = true,
            switchHandle = WIFI,
            nowMs = 5_100L,
            previous = reconnect.state,
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, inFlight.followUp)
    }

    @Test
    fun vpnOnlySnapshots_doNotCountAsSwitchKey() {
        val vpn = XrayNetworkPolicy.NetworkSnapshot(
            handle = 99L,
            hasVpnTransport = true,
            hasInternet = true,
            validated = true,
        )
        val cell = XrayNetworkPolicy.NetworkSnapshot(
            handle = CELL,
            hasVpnTransport = false,
            hasInternet = true,
            validated = true,
        )
        assertEquals(CELL, XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn, cell), requireValidated = true))
        assertNull(XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn), requireValidated = true))

        val decision = resolve(
            running = true,
            switchHandle = XrayNetworkPolicy.pickUnderlyingHandle(listOf(vpn), requireValidated = true),
            nowMs = 3_000L,
            previous = XrayNetworkChangeState(lastHandle = CELL),
        )
        assertEquals(XrayNetworkFollowUp.HEALTH, decision.followUp)
        assertEquals(CELL, decision.state.lastHandle)
    }

    @Test
    fun pickUnderlying_emptyAndInternetlessAreNull() {
        assertNull(XrayNetworkPolicy.pickUnderlyingHandle(emptyList()))
        assertNull(
            XrayNetworkPolicy.pickUnderlyingHandle(
                listOf(
                    XrayNetworkPolicy.NetworkSnapshot(
                        handle = 1L,
                        hasVpnTransport = false,
                        hasInternet = false,
                        validated = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun remainingSettle_customWindowAndOverflow() {
        assertEquals(0L, XrayNetworkPolicy.remainingSettleMs(9_000L, settleWindowMs = 5_000L))
        assertEquals(1_000L, XrayNetworkPolicy.remainingSettleMs(1_000L, settleWindowMs = 2_000L))
    }

    @Test
    fun shouldScheduleSettleRecheck_nullHandles() {
        assertFalse(
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = null,
                currentHandle = WIFI,
                connectInFlight = false,
                remainingSettleMs = 4_000L,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = CELL,
                currentHandle = null,
                connectInFlight = false,
                remainingSettleMs = 4_000L,
            ),
        )
    }

    @Test
    fun watch_keepsSinceWhenHandleUnchanged() {
        val first = XrayNetworkPolicy.UnderlyingSwitchWatch().observe(WIFI, 1_000L)
        val same = first.observe(WIFI, 4_000L)
        assertEquals(1_000L, same.pendingSinceMs)
        assertEquals(WIFI, same.pendingHandle)
        assertEquals(3_000L, same.settledForMs(WIFI, 4_000L))
        assertEquals(0L, same.settledForMs(CELL, 4_000L))
    }

    @Test
    fun switch_blockedWhenDesiredOffOrNotRunning() {
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = false,
                stopping = false,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = CELL,
                currentHandle = WIFI,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = true,
                running = true,
                networkAvailable = true,
                paused = false,
                previousHandle = CELL,
                currentHandle = WIFI,
            ),
        )
        assertFalse(
            XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
                desiredConnection = true,
                stopping = false,
                running = false,
                networkAvailable = true,
                paused = false,
                previousHandle = CELL,
                currentHandle = WIFI,
            ),
        )
    }

    private fun settledWatch(last: Long, next: Long) = XrayNetworkChangeState(
        lastHandle = last,
        watch = XrayNetworkPolicy.UnderlyingSwitchWatch(next, 0L),
    )

    private fun resolve(
        desiredConnection: Boolean = true,
        stopping: Boolean = false,
        running: Boolean,
        networkAvailable: Boolean = true,
        paused: Boolean = false,
        connectInFlight: Boolean = false,
        switchHandle: Long?,
        nowMs: Long,
        previous: XrayNetworkChangeState,
    ) = XrayNetworkChangePolicy.resolve(
        desiredConnection = desiredConnection,
        stopping = stopping,
        running = running,
        networkAvailable = networkAvailable,
        paused = paused,
        connectInFlight = connectInFlight,
        switchHandle = switchHandle,
        nowMs = nowMs,
        previous = previous,
    )

    companion object {
        private const val CELL = 11L
        private const val WIFI = 22L
    }
}
