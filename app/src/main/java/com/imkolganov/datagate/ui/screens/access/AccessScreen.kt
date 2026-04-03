package com.imkolganov.datagate.ui.screens.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.vpn.ServerSelectionMode
import com.imkolganov.datagate.vpn.VpnServerSelectionStore
import com.imkolganov.datagate.vpn.VpnStatusUiState

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AccessScreen(
    state: AccessContract.UiState,
    vpnState: VpnStatusUiState,
    onEvent: (AccessContract.UiEvent) -> Unit,
    onConnectVpn: () -> Unit,
    onDisconnectVpn: () -> Unit,
    onReconnectVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh = { onEvent(AccessContract.UiEvent.Refresh) }
    )

    val appContext = LocalContext.current.applicationContext

    val vpnConnected = vpnState.isVpnConnected
    val connectBusy = vpnState.isConnectRequested && !vpnConnected
    val resolvedSessionId =
        vpnState.selectedServerId
            ?: if (vpnConnected) {
                VpnServerSelectionStore.getSelectedServerId(appContext)
            } else {
                null
            }
    val connectingTargetId =
        if (connectBusy) {
            vpnState.selectedServerId ?: VpnServerSelectionStore.getSelectedServerId(appContext)
        } else {
            null
        }

    var switchTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }

    fun runConnectToServer(server: AccessContract.ServerItem) {
        val sessionServerId = vpnState.selectedServerId
            ?: VpnServerSelectionStore.getSelectedServerId(appContext)
        if (vpnConnected || connectBusy) {
            if (sessionServerId == server.id) {
                if (state.serverSelectionMode == ServerSelectionMode.AUTO) {
                    onEvent(AccessContract.UiEvent.SetServerSelectionMode(ServerSelectionMode.MANUAL))
                }
                onEvent(AccessContract.UiEvent.SelectServer(server.id))
                return
            }
        }
        if (state.serverSelectionMode == ServerSelectionMode.AUTO) {
            onEvent(AccessContract.UiEvent.SetServerSelectionMode(ServerSelectionMode.MANUAL))
        }
        onEvent(AccessContract.UiEvent.SelectServer(server.id))
        if (vpnConnected || connectBusy) {
            switchTarget = server.id to server.name
        } else {
            onConnectVpn()
        }
    }

    switchTarget?.let { (_, name) ->
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            title = { Text("Switch server?") },
            text = {
                Text(
                    "Disconnect from the current VPN and connect to \"$name\"?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        switchTarget = null
                        onReconnectVpn()
                    }
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { switchTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeaderRow(
                    onRefresh = { onEvent(AccessContract.UiEvent.Refresh) }
                )
            }

            item {
                VpnStatusCard(vpnState = vpnState)
            }

            state.errorText?.let { err ->
                item {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(state.servers, key = { it.id }) { server ->
                val isSelected = state.selectedServerId == server.id
                val onThisServer = resolvedSessionId == server.id
                val connectingHere = connectingTargetId == server.id
                ServerCard(
                    server = server,
                    isSelected = isSelected,
                    isVpnSessionOnThisServer = vpnConnected && onThisServer,
                    isVpnConnectingToThisServer = connectingHere,
                    connectBusy = connectBusy,
                    onSelect = {
                        onEvent(AccessContract.UiEvent.SetServerSelectionMode(ServerSelectionMode.MANUAL))
                        onEvent(AccessContract.UiEvent.SelectServer(server.id))
                    },
                    onConnect = { runConnectToServer(server) },
                    onDisconnect = onDisconnectVpn
                )
            }

            if (state.activeConnections.isNotEmpty()) {
                item {
                    ActiveConnectionsBlock(connections = state.activeConnections)
                }
            }

            item {
                val servers = state.servers
                val totalUsers = servers.sumOf { it.activeUsers ?: 0 }
                val onlineServers = servers.count { it.isOnline }
                ServersSummaryFooter(
                    totalUsers = totalUsers,
                    onlineServers = onlineServers,
                    totalServers = servers.size,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        PullRefreshIndicator(
            refreshing = state.isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun VpnStatusCard(vpnState: VpnStatusUiState) {
    val connected = vpnState.isVpnConnected
    val busy = vpnState.isConnectRequested && !connected

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                connected -> MaterialTheme.colorScheme.primaryContainer
                busy -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when {
                    connected -> "Connected"
                    busy -> "Connecting…"
                    else -> "Not connected"
                },
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            when {
                connected -> {
                    val name = vpnState.selectedServerName?.takeIf { it.isNotBlank() }
                    Text(
                        text = name ?: "VPN active",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (vpnState.lastMessage.isNotBlank()) {
                        Text(
                            text = vpnState.lastMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                busy -> {
                    Text(
                        text = vpnState.lastMessage.ifBlank { "Establishing session…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Use Home to connect with automatic server selection. " +
                            "Here, pick a server and tap Connect—or Disconnect on the active server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Choose a server",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh"
            )
        }
    }
}
