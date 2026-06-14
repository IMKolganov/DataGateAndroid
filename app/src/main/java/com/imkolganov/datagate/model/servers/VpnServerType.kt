package com.imkolganov.datagate.model.servers

/** Matches backend [DataGateMonitor.SharedModels.Enums.VpnServerType]. */
enum class VpnServerType {
    OpenVpn,
    Xray,
    /** Unknown or future backend value — not connectable in this app. */
    Unknown,
}
