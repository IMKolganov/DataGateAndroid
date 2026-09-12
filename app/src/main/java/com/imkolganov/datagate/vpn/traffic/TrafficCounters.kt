package com.imkolganov.datagate.vpn.traffic

/** Cumulative TUN counters. [bytesIn] is download, [bytesOut] is upload. */
data class TrafficCounters(
    val bytesIn: Long,
    val bytesOut: Long,
)

data class TrafficSample(
    val speedInBps: Long,
    val speedOutBps: Long,
)

fun interface VpnTrafficSource {
    fun read(): TrafficCounters?
}
