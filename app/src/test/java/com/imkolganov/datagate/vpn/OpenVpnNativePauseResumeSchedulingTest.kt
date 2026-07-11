package com.imkolganov.datagate.vpn

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OpenVpnNativePauseResumeSchedulingTest {

    @Test
    fun policy_mustNotSchedulePauseResumeOnNativeExecutor() {
        assertTrue(OpenVpnRuntimePolicy.mustNotSchedulePauseResumeOnNativeExecutor(false))
        assertFalse(OpenVpnRuntimePolicy.mustNotSchedulePauseResumeOnNativeExecutor(true))
    }

    @Test
    fun policy_pauseResumeAlwaysUsesForeignThreadWhileConnectActive() {
        assertTrue(OpenVpnRuntimePolicy.shouldSchedulePauseResumeOnForeignThread(nativeVpnJobActive = true))
        assertTrue(OpenVpnRuntimePolicy.shouldSchedulePauseResumeOnForeignThread(nativeVpnJobActive = false))
    }

    @Test(timeout = 5_000)
    fun regression_nativeExecutorScheduling_whileConnectBlocks_pauseNeverRuns() {
        val executor = OvpnNativeThread.createExecutorService()
        val connectStarted = CountDownLatch(1)
        val pauseRan = AtomicBoolean(false)

        try {
            executor.submit {
                connectStarted.countDown()
                Thread.sleep(60_000)
            }
            assertTrue(connectStarted.await(2, TimeUnit.SECONDS))

            BrokenPauseSchedulingMirror.scheduleOnNativeExecutor(
                nativeExecutor = executor,
                action = Runnable { pauseRan.set(true) },
            )

            Thread.sleep(500)
            assertFalse(pauseRan.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000)
    fun contract_foreignThreadScheduling_whileConnectBlocks_pauseReachesNativeWithinTwoSeconds() {
        val executor = OvpnNativeThread.createExecutorService()
        val connectStarted = CountDownLatch(1)
        val pauseFinished = CountDownLatch(1)
        val ioContextQueue = ConcurrentLinkedQueue<Runnable>()

        try {
            executor.submit {
                connectStarted.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted) {
                    ioContextQueue.poll()?.run()
                    Thread.sleep(5)
                }
                Thread.sleep(60_000)
            }
            assertTrue(connectStarted.await(2, TimeUnit.SECONDS))

            runBlocking {
                val job = OpenVpnNativePauseResumeScheduling.schedulePauseOrResume(
                    scope = this,
                    nativeVpnJobActive = true,
                    action = {
                        ioContextQueue.offer(Runnable { pauseFinished.countDown() })
                    },
                )
                job.join()
            }

            assertTrue(
                "Foreign-thread pause must reach the native io_context while connect() blocks OvpnNative",
                pauseFinished.await(2, TimeUnit.SECONDS),
            )
        } finally {
            executor.shutdownNow()
        }
    }
}

/**
 * Pre-fix scheduling — kept so regression tests document the deadlock pattern.
 */
private object BrokenPauseSchedulingMirror {
    fun scheduleOnNativeExecutor(
        nativeExecutor: java.util.concurrent.ExecutorService,
        action: Runnable,
    ) {
        nativeExecutor.submit(action)
    }
}
