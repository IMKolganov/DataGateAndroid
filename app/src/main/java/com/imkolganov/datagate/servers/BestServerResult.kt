package com.imkolganov.datagate.servers

import com.imkolganov.datagate.model.servers.VpnServerType

data class BestServerResult(
    val serverId: Int,
    val name: String? = null,
    val apiUrl: String? = null,
    val countConnectedClients: Int,
    val isDefault: Boolean,
    /** When false, connect with [com.imkolganov.datagate.vpn.VpnTransport.Direct] (no WSS bridge). */
    val useWss: Boolean = true,
    val serverType: VpnServerType = VpnServerType.OpenVpn,
)
