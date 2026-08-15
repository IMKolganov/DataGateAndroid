package com.imkolganov.datagate.vpn

import android.content.res.Resources
import com.imkolganov.datagate.R

object VpnEventMapper {

    fun map(
        res: Resources,
        previous: VpnStatusUiState,
        eventName: String,
        eventInfo: String
    ): VpnStatusUiState {
        if (previous.isVpnPaused && !isAuthoritativeWhilePaused(eventName)) {
            return previous
        }
        if (previous.pendingUserCommand != null && !VpnCommandContract.isAuthoritativeTunnelEvent(eventName)) {
            return previous
        }
        return when (eventName) {

            "SELECTING_SERVER" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_selecting_server)
            )

            "SELECTED_SERVER" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_server_selected)
            )

            "GETTING_INSTALLATION_ID" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_preparing_device)
            )

            "GETTING_EXTERNAL_ID" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_checking_user)
            )

            "BUILDING_COMMON_NAME" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_creating_cert)
            )

            "DOWNLOADING_CONFIG" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_downloading_profile)
            )

            "CONFIG_RECEIVED" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_profile_received)
            )

            "RESOLVE" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_resolving_address)
            )

            "WAIT" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_waiting_server)
            )

            "GET_CONFIG" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_negotiating_config)
            )

            "ASSIGN_IP" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_establishing_tunnel)
            )

            "RECONNECTING" -> previous.copy(
                isConnectRequested = true,
                isVpnConnected = false,
                pendingUserCommand = null,
                lastMessage = res.getString(R.string.vpn_msg_reconnecting)
            )

            "NETWORK_CHANGED" -> {
                if (!shouldShowReconnectingOnNetworkChange(previous)) {
                    // Network capability callbacks can fire during a healthy connected session.
                    // Keep the user-facing status stable and avoid false "reconnecting" messaging.
                    previous
                } else {
                    previous.copy(
                        isConnectRequested = true,
                        lastMessage = res.getString(R.string.vpn_msg_reconnecting)
                    )
                }
            }

            "CONNECTING" -> previous.copy(
                isConnectRequested = true,
                isVpnConnected = false,
                lastMessage = res.getString(R.string.vpn_msg_connecting)
            )

            "WAITING_NETWORK" -> previous.copy(
                isConnectRequested = true,
                isVpnConnected = false,
                pendingUserCommand = null,
                lastMessage = res.getString(R.string.vpn_msg_reconnecting)
            )

            "DISCONNECTING" -> previous.copy(
                isConnectRequested = false,
                isVpnConnected = false,
                isVpnPaused = false,
                pendingUserCommand = null,
                lastMessage = res.getString(R.string.vpn_disconnecting)
            )

            "PAUSED" -> previous.copy(
                isConnectRequested = true,
                isVpnConnected = false,
                isVpnPaused = true,
                pendingUserCommand = null,
                lastMessage = res.getString(R.string.vpn_msg_paused)
            )

            "RESUMED" -> previous.copy(
                isConnectRequested = true,
                isVpnConnected = false,
                isVpnPaused = false,
                pendingUserCommand = null,
                lastMessage = res.getString(R.string.vpn_msg_resuming)
            )

            "CONNECTED" -> {
                val name = previous.selectedServerName?.takeIf { it.isNotBlank() }
                previous.copy(
                    isConnectRequested = true,
                    isVpnConnected = true,
                    isVpnPaused = false,
                    pendingUserCommand = null,
                    lastMessage = if (name != null) {
                        res.getString(R.string.vpn_msg_connected_to, name)
                    } else {
                        res.getString(R.string.vpn_msg_connected)
                    }
                )
            }

            "DISCONNECTED" -> {
                // Always end the session in the mapper. Peer-engine teardown during OpenVPN↔Xray
                // switch is restored by VpnController.expectPeerEngineDisconnect so notification
                // Disconnect mid-connect cannot leave connectBusy stuck forever.
                previous.copy(
                    isConnectRequested = false,
                    isVpnConnected = false,
                    isVpnPaused = false,
                    pendingUserCommand = null,
                    selectedServerId = null,
                    selectedServerName = null,
                    lastMessage = res.getString(R.string.vpn_msg_disconnected),
                )
            }

            "TUN_SETUP_FAILED" -> previous.copy(
                isConnectRequested = false,
                isVpnConnected = false,
                isVpnPaused = false,
                pendingUserCommand = null,
                lastMessage = res.getString(R.string.vpn_msg_tunnel_failed)
            )

            "ERROR" -> previous.copy(
                isConnectRequested = false,
                isVpnConnected = false,
                isVpnPaused = false,
                pendingUserCommand = null,
                lastMessage = res.getString(
                    R.string.vpn_connect_failed,
                    eventInfo.trim().ifBlank { eventName }
                )
            )

            // Stale query before any real tunnel event — never show "UNKNOWN: No status yet".
            "UNKNOWN" -> {
                if (previous.isConnectRequested || previous.isVpnConnected || previous.isVpnPaused) {
                    previous
                } else {
                    previous.copy(lastMessage = res.getString(R.string.vpn_msg_disconnected))
                }
            }

            else -> {
                val msg = sanitizeFallback(eventName, eventInfo)
                previous.copy(lastMessage = msg)
            }
        }
    }

    internal fun shouldShowReconnectingOnNetworkChange(previous: VpnStatusUiState): Boolean {
        return !previous.isVpnConnected
    }

    internal fun isAuthoritativeWhilePaused(eventName: String): Boolean {
        return when (eventName.uppercase()) {
            "PAUSED",
            "RESUMED",
            "CONNECTED",
            "DISCONNECTED",
            "DISCONNECTING",
            "ERROR",
            "TUN_SETUP_FAILED" -> true
            else -> false
        }
    }

    internal fun sanitizeFallbackMessage(eventName: String, eventInfo: String): String =
        sanitizeFallback(eventName, eventInfo)

    private fun sanitizeFallback(eventName: String, eventInfo: String): String {
        val name = eventName.trim().ifBlank { "STATUS" }
        if (name.equals("UNKNOWN", ignoreCase = true)) {
            return eventInfo.trim().takeIf { it.isNotEmpty() && !it.equals("No status yet", ignoreCase = true) }
                ?: name
        }
        val info = eventInfo.trim()

        if (info.isBlank() || info.equals("No status yet", ignoreCase = true)) return name

        val isLikelyNetworkJunk =
            info.length > 60 ||
                info.contains(Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")) ||
                info.contains(":") && info.any { it.isDigit() }

        return if (isLikelyNetworkJunk) name else "$name: $info"
    }
}
