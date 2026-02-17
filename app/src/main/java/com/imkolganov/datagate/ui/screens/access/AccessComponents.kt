package com.imkolganov.datagate.ui.screens.access

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ActiveConnectionsBlock(
    connections: List<AccessContract.ActiveConnectionItem>,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Active connection")
                OutlinedButton(onClick = onDisconnect) { Text(text = "Disconnect") }
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
                onSelect = { onSelect(server.id) },
                onConnect = { onConnect(server.id) }
            )
        }
    }
}

@Composable
fun ServerCard(
    server: AccessContract.ServerItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    val container = if (isSelected) CardDefaults.cardColors() else CardDefaults.cardColors()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = container,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        UsersPill(count = it)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    StatusPill(isOnline = server.isOnline)
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

            if (isSelected) {
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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

@Composable
private fun StatusPill(isOnline: Boolean) {
    val text = if (isOnline) "Online" else "Offline"

    Surface(
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 2.dp
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = text
        )
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
private fun UsersPill(count: Int) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(
                imageVector = Icons.Outlined.Group,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
