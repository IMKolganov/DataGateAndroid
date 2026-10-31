package com.imkolganov.datagate.vpn.diag

/** Bumps when a probe session is replaced or cancelled so late I/O cannot emit a stale verdict. */
internal class VpnProbeGeneration {
    private val lock = Any()
    private var generation = 0

    fun next(): Int = synchronized(lock) { ++generation }

    fun invalidate() {
        synchronized(lock) { generation++ }
    }

    fun isCurrent(session: Int): Boolean = synchronized(lock) { session == generation }
}
