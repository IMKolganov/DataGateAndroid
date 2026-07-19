package com.imkolganov.datagate.vpn

/**
 * Application-level idle detection for TCP↔WSS: OkHttp ping catches a dead socket, but
 * `send()==true` with no OpenVPN frames either way still leaves CONNECTED + blackhole TUN.
 */
internal object BridgeIdleProbePolicy {
    const val IDLE_TIMEOUT_MS = 90_000L

    fun shouldDeclareIdle(lastActivityMs: Long, nowMs: Long, idleTimeoutMs: Long = IDLE_TIMEOUT_MS): Boolean {
        if (lastActivityMs <= 0L) return false
        return nowMs - lastActivityMs >= idleTimeoutMs
    }

    fun formatIdleReason(idleForMs: Long): String = "wss_idle:${idleForMs}ms"
}
