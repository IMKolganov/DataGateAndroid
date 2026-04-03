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
                lastMessage = res.getString(R.string.vpn_msg_reconnecting)
            )

            "CONNECTING" -> previous.copy(
                isConnectRequested = true,
                lastMessage = res.getString(R.string.vpn_msg_connecting)
            )

            "CONNECTED" -> {
                val name = previous.selectedServerName?.takeIf { it.isNotBlank() }
                previous.copy(
                    isConnectRequested = true,
                    isVpnConnected = true,
                    lastMessage = if (name != null) {
                        res.getString(R.string.vpn_msg_connected_to, name)
                    } else {
                        res.getString(R.string.vpn_msg_connected)
                    }
                )
            }

            "DISCONNECTED" -> previous.copy(
                isConnectRequested = false,
                isVpnConnected = false,
                selectedServerId = null,
                lastMessage = res.getString(R.string.vpn_msg_disconnected)
            )

            "TUN_SETUP_FAILED" -> previous.copy(
                isConnectRequested = false,
                isVpnConnected = false,
                lastMessage = res.getString(R.string.vpn_msg_tunnel_failed)
            )

            else -> {
                val msg = sanitizeFallback(eventName, eventInfo)
                previous.copy(lastMessage = msg)
            }
        }
    }

    private fun sanitizeFallback(eventName: String, eventInfo: String): String {
        val name = eventName.trim().ifBlank { "STATUS" }
        val info = eventInfo.trim()

        if (info.isBlank()) return name

        val isLikelyNetworkJunk =
            info.length > 60 ||
                info.contains(Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")) ||
                info.contains(":") && info.any { it.isDigit() }

        return if (isLikelyNetworkJunk) name else "$name: $info"
    }
}
