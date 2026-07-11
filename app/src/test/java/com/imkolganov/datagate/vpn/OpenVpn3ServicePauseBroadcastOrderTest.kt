package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Broadcast order: UI [PAUSED] only after core/native confirmation, not on user command.
 */
class OpenVpn3ServicePauseBroadcastOrderTest {

    @Test
    fun policy_userPauseCommand_doesNotAuthorizePausedBroadcast() {
        assertFalse(OpenVpnPauseBroadcastPolicy.mayBroadcastPausedFromUserCommand())
    }

    @Test(timeout = 5_000)
    fun fixedProcessPause_doesNotBroadcastUntilNativePauseRuns() {
        val executor = OvpnNativeThread.createExecutorService()
        val connectHold = CountDownLatch(1)
        val connectMayFinish = CountDownLatch(1)
        val pauseExecuted = AtomicBoolean(false)
        val broadcastSent = AtomicBoolean(false)

        try {
            executor.submit {
                connectHold.countDown()
                connectMayFinish.await(2, TimeUnit.SECONDS)
            }
            assertTrue(connectHold.await(2, TimeUnit.SECONDS))

            val trace = FixedPauseSchedulingMirror.traceProcessPause(
                nativeExecutor = executor,
                connectBlocksNativeThread = { connectMayFinish.await(2, TimeUnit.SECONDS) },
                scheduleNativePause = { onNativeRan ->
                    executor.submit {
                        onNativeRan.run()
                    }
                },
                nativePauseAction = Runnable { pauseExecuted.set(true) },
                maybeBroadcastPaused = {
                    if (OpenVpnPauseBroadcastPolicy.mayBroadcastPausedFromUserCommand()) {
                        broadcastSent.set(true)
                    }
                },
                onCorePauseEvent = {
                    if (OpenVpnPauseBroadcastPolicy.shouldBroadcastPausedOnCoreEvent("PAUSE")) {
                        broadcastSent.set(true)
                    }
                },
            )

            assertFalse(
                "User command must not broadcast PAUSED before native pause runs",
                trace.broadcastBeforeNativePause,
            )
            assertFalse(broadcastSent.get())

            connectMayFinish.countDown()
            Thread.sleep(200)
            assertTrue(pauseExecuted.get())

            trace.onCorePauseEvent.run()
            assertTrue(broadcastSent.get())
        } finally {
            executor.shutdownNow()
        }
    }
}

private object FixedPauseSchedulingMirror {

    data class Trace(
        val broadcastBeforeNativePause: Boolean,
        val onCorePauseEvent: Runnable,
    )

    fun traceProcessPause(
        nativeExecutor: java.util.concurrent.ExecutorService,
        connectBlocksNativeThread: Runnable,
        scheduleNativePause: (Runnable) -> Unit,
        nativePauseAction: Runnable,
        maybeBroadcastPaused: Runnable,
        onCorePauseEvent: Runnable,
    ): Trace {
        var broadcastBeforeNative = false
        var nativeRan = false

        nativeExecutor.submit(connectBlocksNativeThread)

        scheduleNativePause(Runnable {
            nativeRan = true
            nativePauseAction.run()
        })

        maybeBroadcastPaused.run()
        if (!nativeRan) {
            broadcastBeforeNative = OpenVpnPauseBroadcastPolicy.mayBroadcastPausedFromUserCommand()
        }

        return Trace(
            broadcastBeforeNativePause = broadcastBeforeNative,
            onCorePauseEvent = onCorePauseEvent,
        )
    }
}
