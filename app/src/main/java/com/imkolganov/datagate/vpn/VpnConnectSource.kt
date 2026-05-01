package com.imkolganov.datagate.vpn

/**
 * Where Connect was invoked from. Home always uses best WSS server (lowest load);
 * Access follows [ServerSelectionMode] and selected server id.
 */
enum class VpnConnectSource {
    Home,
    Access
}
