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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.ui.components.AppCards
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

    var switchTargetServer by remember { mutableStateOf<AccessContract.ServerItem?>(null) }
    var noWssDialogName by remember { mutableStateOf<String?>(null) }
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
                    snapshot.externalIpAddress.isNullOrBlank() ||
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
            if (!server.isEnableWss) {
                noWssDialogName = server.name
                return
            }
            onConnectVpn()
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
                        if (!target.isEnableWss) {
                            noWssDialogName = target.name
                        } else {
                            onReconnectVpn()
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

    noWssDialogName?.let { serverName ->
        AlertDialog(
            onDismissRequest = { noWssDialogName = null },
            title = { Text(stringResource(R.string.access_no_wss_dialog_title)) },
            text = {
                Text(
                    stringResource(R.string.vpn_requires_openvpn_connect, serverName),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { noWssDialogName = null }) {
                    Text(stringResource(R.string.action_ok))
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
                    externalIpAddress = networkIdentity.externalIpAddress,
                    dnsServers = networkIdentity.dnsServers,
                    isLoading = networkIdentityLoading,
                    showVpnIp = vpnConnected,
                    modifier = Modifier.padding(top = 8.dp)
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
    val context = LocalContext.current
    val connected = vpnState.isVpnConnected
    val busy = vpnState.isConnectRequested && !connected
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
                busy -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when {
                    connected -> stringResource(R.string.access_connected)
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
    onRefresh: () -> Unit
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
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.access_refresh)
            )
        }
    }
}
