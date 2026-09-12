package com.imkolganov.datagate.vpn.traffic

data class VpnTrafficUiState(
    val isActive: Boolean = false,
    val speedInBps: Long = 0,
    val speedOutBps: Long = 0,
    val sessionBytesIn: Long = 0,
    val sessionBytesOut: Long = 0,
    val samples: List<TrafficSample> = emptyList(),
)
