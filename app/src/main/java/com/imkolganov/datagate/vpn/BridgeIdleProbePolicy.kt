package com.imkolganov.datagate.vpn

/**
 * Application-level stall detection for TCP↔WSS.
 *
 * OkHttp [okhttp3.OkHttpClient.Builder.pingInterval] catches a dead WebSocket. Quiet tunnels
 * with no OpenVPN payload must not be torn down. We only declare loss when OpenVPN bytes were
 * pushed toward WSS and nothing came back for [IDLE_TIMEOUT_MS] (half-open / silent blackhole).
 */
internal object BridgeIdleProbePolicy {
    const val IDLE_TIMEOUT_MS = 90_000L

    /**
     * @param lastOutboundMs last TCP→WSS application send (0 = never)
     * @param lastInboundMs last WSS→TCP application receive (0 = never)
     */
    fun shouldDeclareStall(
        lastOutboundMs: Long,
        lastInboundMs: Long,
        nowMs: Long,
        stallTimeoutMs: Long = IDLE_TIMEOUT_MS,
    ): Boolean {
        if (lastOutboundMs <= 0L) return false
        // Inbound caught up or is newer — no unanswered outbound.
        if (lastInboundMs >= lastOutboundMs) return false
        // How long since the last reply (or since epoch if never replied after outbound).
        val silentSince = if (lastInboundMs > 0L) lastInboundMs else lastOutboundMs
        return nowMs - silentSince >= stallTimeoutMs
    }

    fun formatIdleReason(idleForMs: Long): String = "wss_stall:${idleForMs}ms"
}
