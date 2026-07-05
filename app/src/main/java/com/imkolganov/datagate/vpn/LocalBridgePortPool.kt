package com.imkolganov.datagate.vpn

import java.net.BindException

/**
 * Picks localhost ports for the OpenVPN → WSS bridge.
 *
 * Tries OS-assigned ephemeral port first, then scans a dedicated high range so we
 * do not collide with other apps (Spotify, dev servers, etc.) on common ports.
 */
object LocalBridgePortPool {

    const val POOL_START = 38_400
    const val POOL_END = 38_499

    fun candidatePorts(random: java.util.Random = java.util.Random()): List<Int> {
        val pool = (POOL_START..POOL_END).shuffled(random)
        return listOf(0) + pool
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
