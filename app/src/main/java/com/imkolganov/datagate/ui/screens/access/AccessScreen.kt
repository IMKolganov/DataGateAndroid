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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import com.imkolganov.datagate.util.formatBytes
import com.imkolganov.datagate.util.formatQuotaEffectiveFromForDisplay
import com.imkolganov.datagate.util.userFriendlyApiError
import com.imkolganov.datagate.vpn.ServerSelectionMode
import com.imkolganov.datagate.vpn.VpnServerSelectionStore
import com.imkolganov.datagate.vpn.VpnStatusUiState
import java.util.Locale

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
        refreshing = state.isLoading,
        onRefresh = { onEvent(AccessContract.UiEvent.Refresh) }
    )

    val appContext = LocalContext.current.applicationContext

    val vpnConnected = vpnState.isVpnConnected
    val vpnPaused = vpnState.isVpnPaused
    val connectBusy = vpnState.isConnectRequested && !vpnConnected && !vpnPaused
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

    val sessionServerId =
        resolvedSessionId ?: state.selectedServerId ?: connectingTargetId
    val externalIpAddress = AccessSessionNetworkInfo.resolveExternalIp(sessionServerId, state.servers)
    val externalIpLoading = state.isLoading && sessionServerId != null && externalIpAddress.isNullOrBlank()

    var switchTargetServer by remember { mutableStateOf<AccessContract.ServerItem?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var unsupportedTypeDialogName by remember { mutableStateOf<String?>(null) }
    var networkIdentity by remember { mutableStateOf(NetworkIdentitySnapshot()) }
    var networkIdentityLoading by remember { mutableStateOf(true) }

    LaunchedEffect(vpnConnected, connectBusy, state.isLoading) {
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
                    onRefresh = { onEvent(AccessContract.UiEvent.Refresh) },
                    primaryFocusRequester = primaryFocusRequester,
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
                    isVpnSessionOnThisServer = (vpnConnected || vpnPaused) && onThisServer,
                    isVpnConnectingToThisServer = connectingHere,
                    connectBusy = connectBusy,
                    onSelect = {
                        if (AccessServerSelectionPolicy.selectableServerId(server.id, state.servers) == null) {
                            return@ServerCard
                        }
                        onEvent(AccessContract.UiEvent.SetServerSelectionMode(ServerSelectionMode.MANUAL))
                        onEvent(AccessContract.UiEvent.SelectServer(server.id))
                    },
                    onConnect = { runConnectToServer(server) },
                    onDisconnect = onDisconnectVpn
                )
            }

            item {
                AccessQuotaSection(quota = state.quota)
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Box(modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        if (!isTelevision) {
            PullRefreshIndicator(
                refreshing = state.isLoading,
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
private fun AccessQuotaSection(quota: AccessContract.QuotaUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.access_quota_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        quota.errorText?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppCards.shape,
            elevation = AppCards.defaultElevation(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.access_quota_current_plan),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (quota.currentPlanName != null) {
                    Text(
                        text = quota.currentPlanName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    quota.currentEffectiveFrom?.takeIf { it.isNotBlank() }?.let { from ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.access_quota_effective_from,
                                formatQuotaEffectiveFromForDisplay(from)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    quota.currentNote?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.access_quota_no_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (quota.errorText == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.access_quota_traffic_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                quota.trafficUsageNeedsExternalId -> {
                    Text(
                        text = stringResource(R.string.access_quota_needs_external_id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                quota.quotaLimitBytes <= 0L -> {
                    Text(
                        text = stringResource(R.string.access_quota_no_traffic_cap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                quota.trafficUsedBytesForPeriod < 0L -> {
                    Text(
                        text = stringResource(R.string.access_quota_usage_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val used = quota.trafficUsedBytesForPeriod
                    val lim = quota.quotaLimitBytes
                    val pct = if (lim > 0) 100.0 * used.toDouble() / lim.toDouble() else 0.0
                    val over = used > lim && lim > 0
                    val barFraction =
                        if (lim > 0) (used.toDouble() / lim.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
                    val periodLabel = if (quota.quotaPeriodIsMonthly) {
                        stringResource(R.string.access_quota_period_month)
                    } else {
                        stringResource(R.string.access_quota_period_today)
                    }
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { barFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (over) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.access_quota_used_line,
                            formatBytes(used),
                            formatBytes(lim),
                            String.format(Locale.US, "%.1f", pct)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (over) {
                            stringResource(
                                R.string.access_quota_over_by,
                                formatBytes(used - lim)
                            )
                        } else {
                            stringResource(
                                R.string.access_quota_remaining,
                                formatBytes(lim - used)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        if (quota.allPlans.isNotEmpty()) {
            Text(
                text = stringResource(R.string.access_quota_all_plans_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            quota.allPlans.forEach { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape,
                    elevation = AppCards.defaultElevation()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plan.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (plan.isDefault) {
                                    Text(
                                        text = stringResource(R.string.access_quota_plan_default),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (!plan.isActive) {
                                    Text(
                                        text = stringResource(R.string.access_quota_plan_inactive),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        plan.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
