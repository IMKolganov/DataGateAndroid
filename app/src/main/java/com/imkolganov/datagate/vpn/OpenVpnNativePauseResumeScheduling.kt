package com.imkolganov.datagate.vpn

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Schedules OpenVPN [pause]/[resume] so they never deadlock behind blocking [connect].
 *
 * ovpncli requires a **foreign** thread: [OpenVPNClient.pause] posts into the native
 * io_context via [thread_safe_pause]. Launching pause on [OvpnNativeThread] queues it
 * behind [connect] and the tunnel never stops (2ip.ru unchanged).
 *
 * Architecture rule: **never** call pause/resume from [OvpnNativeThread] or [ovpnNativeDispatcher].
 */
internal object OpenVpnNativePauseResumeScheduling {

    /** Never [OvpnNativeThread] — enforced by [requireForeignThread]. */
    val foreignDispatcher: CoroutineDispatcher = Dispatchers.IO

    fun requireForeignThread() {
        check(!OvpnNativeThread.runsOnNativeThread()) {
            "OpenVPN pause/resume must run on a foreign thread (Dispatchers.IO), not OvpnNative"
        }
    }

    /**
     * @param nativeVpnJobActive true while [connect] blocks the single native executor thread.
     */
    fun schedulePauseOrResume(
        scope: CoroutineScope,
        nativeVpnJobActive: Boolean,
        action: () -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): Job = scope.launch(foreignDispatcher) {
        requireForeignThread()
        if (!OpenVpnRuntimePolicy.shouldSchedulePauseResumeOnForeignThread(nativeVpnJobActive)) {
            onFailure(
                IllegalStateException(
                    "pause/resume scheduling policy violated (nativeVpnJobActive=$nativeVpnJobActive)"
                )
            )
            return@launch
        }
        runCatching { action() }.onFailure { onFailure(it) }
    }
}
