package com.imkolganov.datagate.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.imkolganov.datagate.ui.components.AppCards
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ServerBadgeWidth = 88.dp
private val ServerBadgeHeight = 36.dp
private val ServerBadgeShape = RoundedCornerShape(10.dp)
/** Material green 800 — readable on light cards; works on tinted cards too */
private val StatusOnlineGreen = Color(0xFF2E7D32)
private val StatusOfflineRed = Color(0xFFC62828)

/**
 * Footer line: total reported users across listed servers and how many servers are online.
 */
@Composable
fun ServersSummaryFooter(
    totalUsers: Int,
    onlineServers: Int,
    totalServers: Int,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = buildString {
            append("$totalUsers users total")
            append(" · ")
            append("$onlineServers of $totalServers servers online")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ActiveConnectionsBlock(
    connections: List<AccessContract.ActiveConnectionItem>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = AppCards.shape,
        colors = AppCards.defaultColors(),
        elevation = AppCards.defaultElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Active connection")
            }

            connections.forEach { c ->
                Spacer(modifier = Modifier.padding(top = 8.dp))
                KeyValueRow(label = "Server", value = c.serverTitle)
                c.virtualIpText?.let { KeyValueRow(label = "IP", value = it) }
                c.connectedSinceText?.let { KeyValueRow(label = "Since", value = it) }
            }
        }
    }
}

@Composable
fun ServersList(
    servers: List<AccessContract.ServerItem>,
    selectedServerId: Int?,
    onSelect: (Int) -> Unit,
    onConnect: (Int) -> Unit
) {
    Column {
        Text(text = "Available servers")

        servers.forEach { server ->
            ServerCard(
                server = server,
                isSelected = server.id == selectedServerId,
                isVpnSessionOnThisServer = false,
                isVpnConnectingToThisServer = false,
                connectBusy = false,
                onSelect = { onSelect(server.id) },
                onConnect = { onConnect(server.id) },
                onDisconnect = {}
            )
        }
    }
}

@Composable
fun ServerCard(
    server: AccessContract.ServerItem,
    isSelected: Boolean,
    isVpnSessionOnThisServer: Boolean,
    isVpnConnectingToThisServer: Boolean,
    connectBusy: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val cardShape = AppCards.shape
    val connectedFill = MaterialTheme.colorScheme.primaryContainer
    val pillBackground = when {
        isVpnSessionOnThisServer -> connectedFill
        else -> MaterialTheme.colorScheme.surface
    }
    val selectionBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)

    val outerModifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp)
        .then(
            if (isSelected && !isVpnSessionOnThisServer) {
                Modifier.border(width = 1.5.dp, color = selectionBorder, shape = cardShape)
            } else {
                Modifier
            }
        )
        .clickable(onClick = onSelect)

    val inner: @Composable () -> Unit = {
        ServerCardInner(
            server = server,
            cardContainerColor = pillBackground,
            isSelected = isSelected,
            isVpnSessionOnThisServer = isVpnSessionOnThisServer,
            isVpnConnectingToThisServer = isVpnConnectingToThisServer,
            connectBusy = connectBusy,
            onConnect = onConnect,
            onDisconnect = onDisconnect
        )
    }

    if (isVpnSessionOnThisServer) {
        // Opaque fill + tonalElevation 0: avoids the “double frame” from semi-transparent Card layers.
        Surface(
            modifier = outerModifier,
            shape = cardShape,
            color = connectedFill,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp
        ) {
            inner()
        }
    } else {
        Card(
            modifier = outerModifier,
            shape = cardShape,
            colors = AppCards.defaultColors(),
            elevation = AppCards.defaultElevation()
        ) {
            inner()
        }
    }
}

@Composable
private fun ServerCardInner(
    server: AccessContract.ServerItem,
    cardContainerColor: Color,
    isSelected: Boolean,
    isVpnSessionOnThisServer: Boolean,
    isVpnConnectingToThisServer: Boolean,
    connectBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                server.protocol?.let {
                    Text(text = it.uppercase())
                }
            }

            Row {
                server.activeUsers?.let {
                    UsersPill(count = it, containerColor = cardContainerColor)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                StatusPill(isOnline = server.isOnline, containerColor = cardContainerColor)
            }
        }

        server.subtitle?.let {
            Spacer(modifier = Modifier.padding(top = 6.dp))
            Text(text = it)
        }

        Spacer(modifier = Modifier.padding(top = 10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                IconKeyValueRow(
                    icon = Icons.Outlined.Cloud,
                    label = "OpenVPN",
                    value = server.openVpnVersionText ?: "-"
                )
                server.uptimeText?.let { uptime ->
                    val parts = uptime.split(", ")
                    IconKeyValueRow(
                        icon = Icons.Outlined.Schedule,
                        label = "Uptime",
                        value = parts.firstOrNull() ?: uptime
                    )
                    if (parts.size > 1) {
                        Text(
                            modifier = Modifier.padding(start = 28.dp),
                            text = parts[1],
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } ?: run {
                    IconKeyValueRow(
                        icon = Icons.Outlined.Schedule,
                        label = "Uptime",
                        value = "-"
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                IconKeyValueRow(
                    icon = Icons.Outlined.SwapVert,
                    label = "IN",
                    value = server.totalInText ?: "-"
                )
                IconKeyValueRow(
                    icon = Icons.Outlined.SwapVert,
                    label = "OUT",
                    value = server.totalOutText ?: "-"
                )
            }
        }

        val showActionRow = isVpnSessionOnThisServer || isSelected
        if (showActionRow) {
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when {
                    isVpnSessionOnThisServer -> {
                        Button(onClick = onDisconnect) {
                            Text(text = "Disconnect")
                        }
                    }
                    isSelected -> {
                        when {
                            isVpnConnectingToThisServer -> {
                                Button(onClick = {}, enabled = false) {
                                    Text(text = "Connecting…")
                                }
                            }
                            !server.isOnline -> {
                                Button(onClick = {}, enabled = false) {
                                    Text(text = "Offline")
                                }
                            }
                            connectBusy -> {
                                Button(onClick = {}, enabled = false) {
                                    Text(text = "Connect")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = onConnect,
                                    enabled = server.isOnline
                                ) {
                                    Text(text = "Connect")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(isOnline: Boolean, containerColor: Color) {
    val text = if (isOnline) "Online" else "Offline"
    val borderColor = when {
        isOnline -> StatusOnlineGreen.copy(alpha = 0.38f)
        else -> StatusOfflineRed.copy(alpha = 0.35f)
    }
    val textColor = when {
        isOnline -> StatusOnlineGreen
        else -> StatusOfflineRed
    }

    Box(
        modifier = Modifier
            .size(ServerBadgeWidth, ServerBadgeHeight)
            .background(color = containerColor, shape = ServerBadgeShape)
            .border(width = 0.5.dp, color = borderColor, shape = ServerBadgeShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.sp,
                    lineHeight = 16.sp
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label:")
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun IconKeyValueRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UsersPill(count: Int, containerColor: Color) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val fg = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(ServerBadgeWidth, ServerBadgeHeight)
            .background(color = containerColor, shape = ServerBadgeShape)
            .border(width = 0.5.dp, color = outline, shape = ServerBadgeShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Group,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = fg
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
