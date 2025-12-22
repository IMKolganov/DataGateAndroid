package com.imkolganov.datagate.ui.screens.access

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.imkolganov.datagate.ui.theme.DataGateOpenVpn3Theme
import com.imkolganov.datagate.vpn.VpnStatusUiState

@Preview(showBackground = true)
@Composable
fun AccessScreenPreview() {
    DataGateOpenVpn3Theme {
        AccessScreen(
            state = AccessContract.UiState(
                isLoading = false,
                servers = listOf(
                    AccessContract.ServerItem(
                        id = 1,
                        name = "OpenVPN Server (udp)",
                        protocol = "udp",
                        isOnline = true,
                        uptimeText = "12/17/2025, 5:15:17 PM",
                        openVpnVersionText = "2.6.17",
                        totalInText = "200.01 MB",
                        totalOutText = "2.30 GB",
                        subtitle = "Low latency",
                        loadPercent = 40,
                        activeUsers = 120
                    ),
                    AccessContract.ServerItem(
                        id = 2,
                        name = "OpenVPN Server (tcp)",
                        protocol = "tcp",
                        isOnline = false,
                        uptimeText = "12/16/2025, 11:02:41 AM",
                        openVpnVersionText = "2.6.17",
                        totalInText = "95.4 MB",
                        totalOutText = "1.10 GB",
                        subtitle = "High bandwidth",
                        loadPercent = 70,
                        activeUsers = 42
                    )
                ),
                activeConnections = listOf(
                    AccessContract.ActiveConnectionItem(
                        id = "conn-1",
                        serverId = 1,
                        serverTitle = "OpenVPN Server (udp)",
                        connectedSinceText = "5 min ago",
                        virtualIpText = "10.8.0.2"
                    )
                ),
                selectedServerId = 1
            ),
            vpnState = VpnStatusUiState(),
            onEvent = {}
        )
    }
}
