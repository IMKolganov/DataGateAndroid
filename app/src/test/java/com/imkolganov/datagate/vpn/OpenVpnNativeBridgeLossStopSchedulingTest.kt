package com.imkolganov.datagate.vpn

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Regression for the "works for a while, then Google/YouTube die until manual reconnect" report
 * on TCP↔WSS: bridge-loss [stop] must not share [OvpnNativeThread] with blocking [connect].
 *
 * Mirrors [OpenVpnNativePauseResumeSchedulingTest] — pause was fixed the same way; bridge-loss
 * stop still used `launch(ovpnNativeDispatcher)` until this contract landed.
 */
class OpenVpnNativeBridgeLossStopSchedulingTest {

    @Test
    fun policy_bridgeLossStopAlwaysUsesForeignThreadWhileConnectActive() {
        assertTrue(OpenVpnRuntimePolicy.shouldScheduleBridgeLossStopOnForeignThread(nativeVpnJobActive = true))
        assertTrue(OpenVpnRuntimePolicy.shouldScheduleBridgeLossStopOnForeignThread(nativeVpnJobActive = false))
    }

    @Test(timeout = 5_000)
    fun regression_nativeDispatcherScheduling_whileConnectBlocks_stopNeverRuns() {
        // Documents the pre-fix OpenVpn3Service.processBridgeTransportLost pattern:
        // serviceScope.launch(ovpnNativeDispatcher) { vpnClient?.stop() }
        val executor = OvpnNativeThread.createExecutorService()
        val connectStarted = CountDownLatch(1)
        val stopRan = AtomicBoolean(false)

        try {
            executor.submit {
                connectStarted.countDown()
                Thread.sleep(60_000)
            }
            assertTrue(connectStarted.await(2, TimeUnit.SECONDS))

            BrokenBridgeLossStopSchedulingMirror.scheduleOnNativeExecutor(
                nativeExecutor = executor,
                action = Runnable { stopRan.set(true) },
            )

            Thread.sleep(500)
            assertFalse(
                "stop queued on the native executor must not run while connect blocks that thread",
                stopRan.get(),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000)
    fun contract_foreignThreadScheduling_whileConnectBlocks_stopRunsWithinTwoSeconds() {
        val executor = OvpnNativeThread.createExecutorService()
        val connectStarted = CountDownLatch(1)
        val stopFinished = CountDownLatch(1)

        try {
            executor.submit {
                connectStarted.countDown()
                Thread.sleep(60_000)
            }
            assertTrue(connectStarted.await(2, TimeUnit.SECONDS))

            runBlocking {
                val job = OpenVpnNativeBridgeLossStopScheduling.scheduleStop(
                    scope = this,
                    nativeVpnJobActive = true,
                    stopAction = { stopFinished.countDown() },
                )
                job.join()
            }

            assertTrue(
                "Foreign-thread bridge-loss stop must run while connect() still blocks OvpnNative",
                stopFinished.await(2, TimeUnit.SECONDS),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun reconnectAfterBridgeLoss_stillRequiresPendingFlag() {
        // Auto-reconnect after stop only fires when processBridgeTransportLost armed the flag.
        assertTrue(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = true,
                desiredConnection = true,
                isStopping = false,
                isPaused = false,
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = false,
                desiredConnection = true,
                isStopping = false,
                isPaused = false,
            )
        )
    }
}

/**
 * Pre-fix scheduling — kept so regression tests document the deadlock pattern that left
 * TUN up and tunneled (Google/YouTube) traffic blackholed until a manual reconnect.
 */
private object BrokenBridgeLossStopSchedulingMirror {
    fun scheduleOnNativeExecutor(
        nativeExecutor: java.util.concurrent.ExecutorService,
        action: Runnable,
    ) {
        nativeExecutor.submit(action)
    }
}
