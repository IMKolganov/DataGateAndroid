package com.imkolganov.datagate.vpn

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Schedules OpenVPN [stop] after WSS bridge transport loss so it never deadlocks behind
 * blocking [connect] on [OvpnNativeThread].
 *
 * Same architecture rule as [OpenVpnNativePauseResumeScheduling]: **never** call stop for
 * bridge-loss recovery from [OvpnNativeThread] / the native coroutine dispatcher. ovpncli
 * expects a foreign thread so stop can post into the running io_context; launching on the
 * native dispatcher queues behind [connect] and the tunnel never tears down — UI may show
 * CONNECTED/RECONNECTING while Google/YouTube (tunneled) blackhole until a manual reconnect.
 */
internal object OpenVpnNativeBridgeLossStopScheduling {

    val foreignDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun requireForeignThread() {
        check(!OvpnNativeThread.runsOnNativeThread()) {
            "OpenVPN bridge-loss stop must run on a foreign thread (Dispatchers.IO), not OvpnNative"
        }
    }

    fun scheduleStop(
        scope: CoroutineScope,
        nativeVpnJobActive: Boolean,
        stopAction: () -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): Job = scope.launch(foreignDispatcher) {
        requireForeignThread()
        if (!OpenVpnRuntimePolicy.shouldScheduleBridgeLossStopOnForeignThread(nativeVpnJobActive)) {
            onFailure(
                IllegalStateException(
                    "bridge-loss stop scheduling policy violated (nativeVpnJobActive=$nativeVpnJobActive)"
                )
            )
            return@launch
        }
        runCatching { stopAction() }.onFailure { onFailure(it) }
    }
}
