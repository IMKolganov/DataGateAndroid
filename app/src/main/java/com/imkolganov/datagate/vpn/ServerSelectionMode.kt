package com.imkolganov.datagate.vpn

/**
 * How the app picks an OpenVPN server when the user taps Connect (Home or Access).
 * Persisted in [VpnServerSelectionStore]; mirrored in Access UI state for display.
 */
enum class ServerSelectionMode {
    /** Best online WSS server (lowest load). */
    AUTO,

    /** User-selected server id from [VpnServerSelectionStore]. */
    MANUAL
}
