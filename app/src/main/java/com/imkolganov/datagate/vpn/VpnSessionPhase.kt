package com.imkolganov.datagate.vpn

/**
 * Canonical VPN phases, in the order the product talks about them:
 * disconnect → connect → tunnel up → pause/resume → wait/reconnect → failure →
 * then the UI pre-connect steps that happen before a service is started.
 *
 * OpenVPN uses the full set. Xray uses a subset (no in-place pause / wait-network).
 */
enum class VpnSessionPhase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    PAUSED,
    RESUMED,
    WAITING_NETWORK,
    RECONNECTING,
    ERROR,
    TUN_SETUP_FAILED,
    SELECTING_SERVER,
    SELECTED_SERVER,
    GETTING_INSTALLATION_ID,
    GETTING_EXTERNAL_ID,
    BUILDING_COMMON_NAME,
    DOWNLOADING_CONFIG,
    CONFIG_RECEIVED,
    RESOLVE,
    WAIT,
    GET_CONFIG,
    ASSIGN_IP,
    ;

    val eventName: String get() = name

    companion object {
        /** Ordered product list — keep this explicit so enum reshuffles cannot silently drift. */
        val catalog: List<VpnSessionPhase> = listOf(
            DISCONNECTED,
            CONNECTING,
            CONNECTED,
            DISCONNECTING,
            PAUSED,
            RESUMED,
            WAITING_NETWORK,
            RECONNECTING,
            ERROR,
            TUN_SETUP_FAILED,
            SELECTING_SERVER,
            SELECTED_SERVER,
            GETTING_INSTALLATION_ID,
            GETTING_EXTERNAL_ID,
            BUILDING_COMMON_NAME,
            DOWNLOADING_CONFIG,
            CONFIG_RECEIVED,
            RESOLVE,
            WAIT,
            GET_CONFIG,
            ASSIGN_IP,
        )

        fun fromEventName(eventName: String): VpnSessionPhase? {
            val key = eventName.trim().uppercase()
            return catalog.firstOrNull { it.name == key }
        }
    }
}
