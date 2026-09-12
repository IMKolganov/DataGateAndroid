package com.imkolganov.datagate.vpn.traffic

/** OpenVPN prefers unified TUN iface counters; [tun_stats] is the fallback. */
internal object OpenVpnLiveTrafficRead {
    fun resolve(
        ifaceCounters: TrafficCounters?,
        tunStatsProvider: () -> TrafficCounters?,
    ): TrafficCounters? = ifaceCounters ?: tunStatsProvider()
}
