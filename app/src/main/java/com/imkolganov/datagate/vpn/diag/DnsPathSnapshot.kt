package com.imkolganov.datagate.vpn.diag

internal data class DnsPathSnapshot(
    val vpnIp: String?,
    val vpnDns: List<String>,
    val privateDnsMode: String?,
    val privateDnsName: String?,
    val iface: String?,
    val hasVpnTransport: Boolean,
    val activeTransport: String,
) {
    fun eventDetails(): Map<String, Any?> = mapOf(
        "vpnIp" to vpnIp,
        "vpnDns" to vpnDns.joinToString(",").ifEmpty { null },
        "privateDnsMode" to privateDnsMode,
        "privateDnsName" to privateDnsName,
        "iface" to iface,
        "hasVpnTransport" to hasVpnTransport,
        "activeTransport" to activeTransport,
    )
}
