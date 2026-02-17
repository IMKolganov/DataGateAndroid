package com.imkolganov.datagate.vpn

object VpnEventMapper {

    fun map(previous: VpnStatusUiState, eventName: String, eventInfo: String): VpnStatusUiState {
        return when (eventName) {

            // Pre-connect flow (friendly)
            "SELECTING_SERVER" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Selecting the best server..."
            )

            "SELECTED_SERVER" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Server selected"
            )

            "GETTING_INSTALLATION_ID" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Preparing device identity..."
            )

            "GETTING_EXTERNAL_ID" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Checking user identity..."
            )

            "BUILDING_COMMON_NAME" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Creating certificate request..."
            )

            "DOWNLOADING_CONFIG" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Downloading VPN profile..."
            )

            "CONFIG_RECEIVED" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "VPN profile received"
            )

            // OpenVPN core flow (friendly)
            "RESOLVE" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Resolving server address..."
            )

            "WAIT" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Waiting for server response..."
            )

            "GET_CONFIG" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Negotiating configuration..."
            )

            "ASSIGN_IP" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Establishing tunnel..."
            )

            "RECONNECTING" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Reconnecting..."
            )

            "CONNECTING" -> previous.copy(
                isConnectRequested = true,
                lastMessage = "Connecting..."
            )

            "CONNECTED" -> {
                val name = previous.selectedServerName?.takeIf { it.isNotBlank() }
                previous.copy(
                    isConnectRequested = true,
                    lastMessage = if (name != null) "Connected to $name" else "Connected"
                )
            }

            "DISCONNECTED" -> previous.copy(
                isConnectRequested = false,
                lastMessage = "Disconnected"
            )

            "TUN_SETUP_FAILED" -> previous.copy(
                isConnectRequested = false,
                lastMessage = "Tunnel setup failed"
            )

            else -> {
                // Fallback: keep it readable and avoid dumping raw IP blobs
                val msg = sanitizeFallback(eventName, eventInfo)
                previous.copy(lastMessage = msg)
            }
        }
    }

    private fun sanitizeFallback(eventName: String, eventInfo: String): String {
        val name = eventName.trim().ifBlank { "STATUS" }
        val info = eventInfo.trim()

        if (info.isBlank()) return name

        // Avoid showing raw IP/ports/long technical blobs
        val isLikelyNetworkJunk =
            info.length > 60 ||
                    info.contains(Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")) ||
                    info.contains(":") && info.any { it.isDigit() }

        return if (isLikelyNetworkJunk) name else "$name: $info"
    }
}
