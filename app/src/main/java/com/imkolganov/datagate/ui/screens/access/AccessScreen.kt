package com.imkolganov.datagate.ui.screens.access

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.vpn.VpnStatusUiState

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AccessScreen(
    state: AccessContract.UiState,
    vpnState: VpnStatusUiState,
    onEvent: (AccessContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh = { onEvent(AccessContract.UiEvent.Refresh) }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            item {
                HeaderRow(
                    onRefresh = { onEvent(AccessContract.UiEvent.Refresh) }
                )
            }

            if (state.activeConnections.isNotEmpty()) {
                item {
                    ActiveConnectionsBlock(
                        connections = state.activeConnections,
                        onDisconnect = { onEvent(AccessContract.UiEvent.Disconnect) }
                    )
                }
            }

            item {
                Text(text = "Available servers")
            }

            items(state.servers, key = { it.id }) { server ->
                val isSelected = state.selectedServerId == server.id
                ServerCard(
                    server = server,
                    isSelected = isSelected,
                    onSelect = { onEvent(AccessContract.UiEvent.SelectServer(server.id)) },
                    onConnect = { onEvent(AccessContract.UiEvent.ConnectToServer(server.id)) }
                )
            }

            item {
                // bottom breathing space so last card isn't flush to bottom
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
private fun HeaderRow(
    onRefresh: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Active connections"
        )
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh"
            )
        }
    }
}
