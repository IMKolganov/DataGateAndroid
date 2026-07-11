package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Regression tests for VPN disconnect while [connect] blocks the single native executor thread.
 *
 * Production bug (1.0.9+): [OpenVpn3Service.stopVpnInternal] used [ExecutorService.submit]
 * followed by [java.util.concurrent.Future.get], which deadlocked when [connect] still held
 * the only native thread — VPN stayed up until the process was killed.
 */
class VpnNativeStopSchedulingTest {

    @Test(timeout = 5_000)
    fun regression_blockingGetWhileConnectHoldsNativeThread_preventsStopFromRunning() {
        val executor = OvpnNativeThread.createExecutorService()
        val connectStarted = CountDownLatch(1)
        val stopRan = AtomicBoolean(false)

        try {
            executor.submit {
                connectStarted.countDown()
                Thread.sleep(60_000)
            }

            assertTrue(connectStarted.await(2, TimeUnit.SECONDS))

            var getTimedOut = false
            val caller = Thread {
                try {
                    executor.submit { stopRan.set(true) }.get(500, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    getTimedOut = true
                }
            }
            caller.start()
            caller.join(2_000)

            assertTrue("submit().get() must time out while connect blocks the native thread", getTimedOut)
            assertFalse("stop must not run while caller blocks on .get()", stopRan.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000)
    fun disconnectSchedule_whileConnectBlocksNativeThread_stopRunsWithoutBlockingCaller() {
        val executor = OvpnNativeThread.createExecutorService()
        val connectStarted = CountDownLatch(1)
        val connectMayFinish = CountDownLatch(1)
        val stopFinished = CountDownLatch(1)

        try {
            executor.submit {
                connectStarted.countDown()
                connectMayFinish.await(2, TimeUnit.SECONDS)
            }

            assertTrue(connectStarted.await(2, TimeUnit.SECONDS))

            OpenVpnNativeStopScheduling.runOrQueueStop(
                nativeExecutor = executor,
                nativeVpnJobActive = true,
                stopAction = Runnable { stopFinished.countDown() },
            )

            connectMayFinish.countDown()

            assertTrue(
                "stop must run after connect releases the native thread without blocking disconnect",
                stopFinished.await(2, TimeUnit.SECONDS),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(timeout = 3_000)
    fun disconnectSchedule_whenNativeJobInactive_runsStopImmediatelyOnCaller() {
        val executor = OvpnNativeThread.createExecutorService()
        val stopFinished = CountDownLatch(1)

        try {
            OpenVpnNativeStopScheduling.runOrQueueStop(
                nativeExecutor = executor,
                nativeVpnJobActive = false,
                stopAction = Runnable { stopFinished.countDown() },
            )

            assertTrue(stopFinished.await(1, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }
}
