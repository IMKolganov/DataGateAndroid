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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.imkolganov.datagate.util.NetworkIdentityReader
import com.imkolganov.datagate.util.NetworkIdentitySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import com.imkolganov.datagate.util.userFriendlyApiError
import com.imkolganov.datagate.vpn.ServerSelectionMode
import com.imkolganov.datagate.vpn.VpnStatusUiState

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AccessScreen(
    state: AccessContract.UiState,
    vpnState: VpnStatusUiState,
    onEvent: (AccessContract.UiEvent) -> Unit,
    onConnectVpn: () -> Unit,
    onDisconnectVpn: () -> Unit,
    onPauseVpn: () -> Unit = {},
    onResumeVpn: () -> Unit = {},
    onReconnectVpn: () -> Unit,
    modifier: Modifier = Modifier,
    primaryFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val isTelevision = com.imkolganov.datagate.ui.tv.LocalIsTelevision.current
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isServersLoading,
        onRefresh = { onEvent(AccessContract.UiEvent.RefreshServers) }
    )

    val appContext = LocalContext.current.applicationContext

    val vpnConnected = vpnState.isVpnConnected
    val vpnPaused = vpnState.isVpnPaused
    val connectBusy = AccessVpnSessionPolicy.isConnectBusy(
        isConnectRequested = vpnState.isConnectRequested,
        isVpnConnected = vpnConnected,
        isVpnPaused = vpnPaused,
    )
    // Active VPN session id only — never fall back to Access list selection (that made every
    // tapped card look like "the" session / show the wrong actions).
    val activeSessionServerId = AccessVpnSessionPolicy.activeSessionServerId(
        isVpnConnected = vpnConnected,
        isVpnPaused = vpnPaused,
        isConnectRequested = vpnState.isConnectRequested,
        vpnSelectedServerId = vpnState.selectedServerId,
    )
    val sessionServerId = activeSessionServerId
    val externalIpAddress = AccessSessionNetworkInfo.resolveExternalIp(sessionServerId, state.servers)
    val externalIpLoading = state.isServersLoading && sessionServerId != null && externalIpAddress.isNullOrBlank()

    val accessibleServers = remember(state.servers) {
        state.servers.filter { it.isAccessibleForQuotaPlan }
    }
    val blockedServers = remember(state.servers) {
        state.servers.filter { !it.isAccessibleForQuotaPlan }
    }

    var switchTargetServer by remember { mutableStateOf<AccessContract.ServerItem?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var unsupportedTypeDialogName by remember { mutableStateOf<String?>(null) }
    var networkIdentity by remember { mutableStateOf(NetworkIdentitySnapshot()) }
    var networkIdentityLoading by remember { mutableStateOf(true) }

    LaunchedEffect(vpnConnected, connectBusy, state.isServersLoading) {
        networkIdentityLoading = true
        if (connectBusy) {
            networkIdentity = NetworkIdentitySnapshot()
            return@LaunchedEffect
        }
        var snapshot = withContext(Dispatchers.IO) {
            NetworkIdentityReader.read(appContext)
        }
        if (vpnConnected && (
                snapshot.vpnIpAddress.isNullOrBlank() ||
                    snapshot.dnsServers.isEmpty()
                )
        ) {
            delay(2_000)
            snapshot = withContext(Dispatchers.IO) {
                NetworkIdentityReader.read(appContext)
            }
        }
        networkIdentity = snapshot
        networkIdentityLoading = false
    }

    fun runConnectToServer(server: AccessContract.ServerItem) {
        if (AccessServerSelectionPolicy.selectableServerId(server.id, state.servers) == null) {
            return
        }
        if (!server.isOnline) {
            return
        }
        val sessionServerId = vpnState.selectedServerId
        if (vpnConnected || connectBusy) {
            if (sessionServerId != null && sessionServerId == server.id) {
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
            switchTargetServer = server
        } else {
            when (server.serverType) {
                VpnServerType.Unknown -> {
                    unsupportedTypeDialogName = server.name
                    return
                }
                VpnServerType.OpenVpn, VpnServerType.Xray -> Unit
            }
            if (vpnState.hasVpnPermission) {
                onConnectVpn()
            } else {
                showPermissionDialog = true
            }
        }
    }

    switchTargetServer?.let { target ->
        AlertDialog(
            onDismissRequest = { switchTargetServer = null },
            title = { Text(stringResource(R.string.access_switch_title)) },
            text = {
                Text(
                    stringResource(R.string.access_switch_message, target.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        switchTargetServer = null
                        when (target.serverType) {
                            VpnServerType.Unknown -> unsupportedTypeDialogName = target.name
                            VpnServerType.OpenVpn, VpnServerType.Xray -> onReconnectVpn()
                        }
                    }
                ) {
                    Text(stringResource(R.string.access_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = { switchTargetServer = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    unsupportedTypeDialogName?.let { serverName ->
        AlertDialog(
            onDismissRequest = { unsupportedTypeDialogName = null },
            title = { Text(stringResource(R.string.access_unsupported_server_type_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.vpn_requires_unsupported_server_type, serverName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { unsupportedTypeDialogName = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.vpn_permission_dialog_title)) },
            text = { Text(stringResource(R.string.vpn_permission_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    // Route through the normal connect entry point (not a bare permission
                    // request): the target server was already selected above, and
                    // VpnController.startWithConfig() stores the pending config before launching
                    // the system dialog, so granting permission here resumes straight into
                    // connecting instead of leaving the user stuck on a "config is missing" error.
                    showPermissionDialog = false
                    onConnectVpn()
                }) {
                    Text(stringResource(R.string.vpn_permission_dialog_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isTelevision) Modifier
                else Modifier.pullRefresh(pullRefreshState)
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(if (isTelevision) 24.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTelevision) 16.dp else 12.dp)
        ) {
            item {
                HeaderRow(
                    onRefresh = { onEvent(AccessContract.UiEvent.RefreshServers) },
                    primaryFocusRequester = primaryFocusRequester,
                )
            }

            item {
                VpnStatusCard(vpnState = vpnState)
            }

            state.serversErrorText?.let { err ->
                item {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.isServersLoading && state.servers.isEmpty() && state.serversErrorText == null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text(
                            text = stringResource(R.string.access_servers_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.isServersLoading && state.servers.isNotEmpty()) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (accessibleServers.isNotEmpty() && blockedServers.isNotEmpty()) {
                item(key = "available_header", contentType = "header") {
                    Text(
                        text = stringResource(R.string.access_servers_available_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = accessibleServers,
                key = { it.id },
                contentType = { "server" },
            ) { server ->
                AccessServerListCard(
                    server = server,
                    state = state,
                    vpnConnected = vpnConnected,
                    vpnPaused = vpnPaused,
                    connectBusy = connectBusy,
                    activeSessionServerId = activeSessionServerId,
                    onEvent = onEvent,
                    onConnect = { runConnectToServer(server) },
                    onDisconnect = onDisconnectVpn,
                )
            }

            if (blockedServers.isNotEmpty()) {
                item(key = "upgrade_note", contentType = "note") {
                    AccessUpgradeAccessNote()
                }
                item(key = "blocked_header", contentType = "header") {
                    Text(
                        text = stringResource(R.string.access_servers_unavailable_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(
                    items = blockedServers,
                    key = { it.id },
                    contentType = { "server_blocked" },
                ) { server ->
                    AccessServerListCard(
                        server = server,
                        state = state,
                        vpnConnected = vpnConnected,
                        vpnPaused = vpnPaused,
                        connectBusy = connectBusy,
                        activeSessionServerId = activeSessionServerId,
                        onEvent = onEvent,
                        onConnect = { runConnectToServer(server) },
                        onDisconnect = onDisconnectVpn,
                    )
                }
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
                ClientNetworkFooter(
                    vpnIpAddress = networkIdentity.vpnIpAddress,
                    externalIpAddress = externalIpAddress,
                    dnsServers = networkIdentity.dnsServers,
                    isLoading = networkIdentityLoading,
                    externalIpLoading = externalIpLoading,
                    showVpnIp = vpnConnected,
                    showPrivateDnsHint = AccessSessionNetworkInfo.shouldShowPrivateDnsHint(
                        vpnConnected = vpnConnected,
                        dnsIdentityEnabled = networkIdentity.dnsIdentityEnabled,
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        if (!isTelevision) {
            PullRefreshIndicator(
                refreshing = state.isServersLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun VpnStatusCard(vpnState: VpnStatusUiState) {
    val context = LocalContext.current
    val connected = vpnState.isVpnConnected
    val paused = vpnState.isVpnPaused
    val busy = vpnState.isConnectRequested && !connected && !paused
    val lastDisplay = remember(vpnState.lastMessage) {
        if (vpnState.lastMessage.isBlank()) ""
        else context.resources.userFriendlyApiError(vpnState.lastMessage)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.shape,
        elevation = AppCards.defaultElevation(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                connected -> MaterialTheme.colorScheme.primaryContainer
                paused -> MaterialTheme.colorScheme.secondaryContainer
                busy -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when {
                    connected -> stringResource(R.string.access_connected)
                    paused -> stringResource(R.string.vpn_status_paused)
                    busy -> stringResource(R.string.access_connecting)
                    else -> stringResource(R.string.access_not_connected)
                },
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            when {
                connected -> {
                    val name = vpnState.selectedServerName?.takeIf { it.isNotBlank() }
                    Text(
                        text = name ?: stringResource(R.string.access_vpn_active),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (lastDisplay.isNotBlank()) {
                        Text(
                            text = lastDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                paused -> {
                    val name = vpnState.selectedServerName?.takeIf { it.isNotBlank() }
                    Text(
                        text = name ?: stringResource(R.string.access_vpn_active),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (lastDisplay.isNotBlank()) {
                        Text(
                            text = lastDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                busy -> {
                    val establishing = stringResource(R.string.access_establishing)
                    Text(
                        text = lastDisplay.ifBlank { establishing },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    if (lastDisplay.isNotBlank()) {
                        Text(
                            text = lastDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.access_use_home_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessServerListCard(
    server: AccessContract.ServerItem,
    state: AccessContract.UiState,
    vpnConnected: Boolean,
    vpnPaused: Boolean,
    connectBusy: Boolean,
    activeSessionServerId: Int?,
    onEvent: (AccessContract.UiEvent) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isSessionCard = AccessVpnSessionPolicy.isSessionCard(
        activeSessionServerId = activeSessionServerId,
        serverId = server.id,
        isVpnConnected = vpnConnected,
        isVpnPaused = vpnPaused,
    )
    val isConnectingHere = AccessVpnSessionPolicy.isConnectingToServer(
        activeSessionServerId = activeSessionServerId,
        serverId = server.id,
        connectBusy = connectBusy,
    )
    val isSelected = state.selectedServerId == server.id || isSessionCard
    ServerCard(
        server = server,
        isSelected = isSelected,
        isVpnSessionOnThisServer = isSessionCard,
        isVpnConnectingToThisServer = isConnectingHere,
        connectBusy = connectBusy,
        onSelect = {
            if (AccessServerSelectionPolicy.selectableServerId(server.id, state.servers) == null) {
                return@ServerCard
            }
            onEvent(AccessContract.UiEvent.SetServerSelectionMode(ServerSelectionMode.MANUAL))
            onEvent(AccessContract.UiEvent.SelectServer(server.id))
        },
        onConnect = onConnect,
        onDisconnect = onDisconnect,
    )
}

@Composable
private fun AccessUpgradeAccessNote() {
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.support_telegram_bot_url)
    val email = stringResource(R.string.support_contact_email)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCards.shape,
        elevation = AppCards.defaultElevation(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.access_upgrade_note_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.access_upgrade_note_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(telegramUrl)
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.home_report_telegram))
            }
            TextButton(
                onClick = {
                    runCatching {
                        val uri = android.net.Uri.parse("mailto:$email")
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_SENDTO, uri)
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.access_upgrade_contact_email, email))
            }
        }
    }
}

@Composable
private fun HeaderRow(
    onRefresh: () -> Unit,
    primaryFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.access_choose_server),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .then(
                    if (primaryFocusRequester != null) {
                        Modifier.focusRequester(primaryFocusRequester)
                    } else {
                        Modifier
                    }
                )
                .tvFocusBorder(shape = RoundedCornerShape(50)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.access_refresh)
            )
        }
    }
}
