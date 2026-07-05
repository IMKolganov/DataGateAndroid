package com.imkolganov.datagate.vpn

import android.content.Context
import java.net.BindException

/**
 * Picks localhost ports for the OpenVPN → WSS bridge.
 *
 * Uses a configurable dedicated range on 127.0.0.1 (default 38400–38499) so we do not grab OS
 * ephemeral ports that companion apps may rely on.
 */
object LocalBridgePortPool {

    const val DEFAULT_POOL_START = 38_400
    const val DEFAULT_POOL_END = 38_499

    /** IANA user ports — safe for app-local listen sockets (RFC 6335). */
    const val MIN_USER_PORT = 1_024
    const val MAX_USER_PORT = 49_151

    const val MIN_POOL_SPAN = 10
    const val MAX_POOL_SPAN = 500

    fun candidatePorts(context: Context, random: java.util.Random = java.util.Random()): List<Int> {
        val settings = LocalBridgePortPreferences.getSettings(context)
        return candidatePorts(settings.poolStart, settings.poolEnd, random)
    }

    fun candidatePorts(
        poolStart: Int,
        poolEnd: Int,
        random: java.util.Random = java.util.Random()
    ): List<Int> {
        val range = normalizeRange(poolStart, poolEnd)
        return (range.poolStart..range.poolEnd).shuffled(random)
    }

    fun normalizeRange(poolStart: Int, poolEnd: Int): LocalBridgePortSettings {
        var start = poolStart.coerceIn(MIN_USER_PORT, MAX_USER_PORT)
        var end = poolEnd.coerceIn(MIN_USER_PORT, MAX_USER_PORT)
        if (start > end) start = end.also { end = start }

        var span = end - start + 1
        if (span < MIN_POOL_SPAN) {
            end = (start + MIN_POOL_SPAN - 1).coerceAtMost(MAX_USER_PORT)
            if (end - start + 1 < MIN_POOL_SPAN) {
                start = (end - MIN_POOL_SPAN + 1).coerceAtLeast(MIN_USER_PORT)
            }
        } else if (span > MAX_POOL_SPAN) {
            end = start + MAX_POOL_SPAN - 1
        }

        return LocalBridgePortSettings(poolStart = start, poolEnd = end)
    }

    fun isValidInput(poolStart: Int?, poolEnd: Int?): Boolean {
        if (poolStart == null || poolEnd == null) return false
        if (poolStart !in MIN_USER_PORT..MAX_USER_PORT) return false
        if (poolEnd !in MIN_USER_PORT..MAX_USER_PORT) return false
        if (poolStart > poolEnd) return false
        val span = poolEnd - poolStart + 1
        return span in MIN_POOL_SPAN..MAX_POOL_SPAN
    }

    fun isBindConflict(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is BindException) return true
            val message = current.message.orEmpty().uppercase()
            if ("EADDRINUSE" in message || "ADDRESS ALREADY IN USE" in message) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
