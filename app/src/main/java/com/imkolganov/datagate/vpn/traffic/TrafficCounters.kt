package com.imkolganov.datagate.vpn.traffic

/** Cumulative TUN counters. [bytesIn] is download, [bytesOut] is upload. */
data class TrafficCounters(
    val bytesIn: Long,
    val bytesOut: Long,
)

data class TrafficSample(
    val speedInBps: Long,
    val speedOutBps: Long,
    /** Monotonic chart X (seconds of session samples); survives ring-buffer drops. */
    val seq: Long = 0L,
)

fun interface VpnTrafficSource {
    fun read(): TrafficCounters?
}
