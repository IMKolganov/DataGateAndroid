package com.imkolganov.datagate.vpn

import java.util.concurrent.ExecutorService

/**
 * Schedules [client.stop] on the dedicated OpenVPN native executor without deadlocking
 * disconnect when [connect] still blocks that single-thread pool.
 */
internal object OpenVpnNativeStopScheduling {
    fun runOrQueueStop(
        nativeExecutor: ExecutorService,
        nativeVpnJobActive: Boolean,
        stopAction: Runnable,
    ) {
        if (OpenVpnRuntimePolicy.shouldAwaitNativeStopOnCallerThread(
                runsOnNativeThread = OvpnNativeThread.runsOnNativeThread(),
                nativeVpnJobActive = nativeVpnJobActive,
            )
        ) {
            stopAction.run()
        } else {
            nativeExecutor.submit(stopAction)
        }
    }
}
