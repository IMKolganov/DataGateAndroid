package com.imkolganov.datagate.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imkolganov.datagate.vpn.VpnStatusUiState
import com.imkolganov.datagate.ui.theme.DataGateOpenVpn3Theme

@Composable
fun VpnStatusScreen(
    state: VpnStatusUiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val isConnected =
        state.isConnectRequested && state.lastMessage.startsWith("Connected", ignoreCase = true)
    val isConnecting = state.isConnectRequested && !isConnected

    val statusTitle = when {
        isConnected -> "Connected"
        isConnecting -> "Connecting..."
        else -> "Disconnected"
    }

    val statusSubtitle = if (state.lastMessage.isNotBlank()) {
        state.lastMessage
    } else {
        "Waiting for VPN events..."
    }

    val mainColor = when {
        isConnected -> MaterialTheme.colorScheme.primary
        isConnecting -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val backgroundColor: Color = when {
        isConnected -> mainColor.copy(alpha = 0.18f)
        isConnecting -> mainColor.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Pulse animation for "connecting" state
    val infiniteTransition = rememberInfiniteTransition(label = "vpnPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vpnPulseScale"
    )

    // Smooth scale change when switching states
    val targetScale = if (isConnecting) pulseScale else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "vpnButtonScale"
    )

    val onClick = {
        if (isConnected || isConnecting) {
            onDisconnectClick()
        } else {
            onConnectClick()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = "DataGate OpenVPN 3",
                style = MaterialTheme.typography.headlineSmall
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(mainColor)
                    )

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Circular animated button
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(animatedScale)
                    .clip(CircleShape)
                    .background(color = backgroundColor)
                    .border(
                        width = 2.dp,
                        color = mainColor.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                // Inner gradient circle
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    mainColor,
                                    mainColor.copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = when {
                                isConnected -> "Disconnect"
                                isConnecting -> "Cancel"
                                else -> "Connect"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VpnStatusScreenPreview_Connected() {
    DataGateOpenVpn3Theme {
        VpnStatusScreen(
            state = VpnStatusUiState(
                isConnectRequested = true,
                lastMessage = "Connected to DataGate VPN (10.0.0.2)"
            ),
            onConnectClick = {},
            onDisconnectClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun VpnStatusScreenPreview_Connecting() {
    DataGateOpenVpn3Theme {
        VpnStatusScreen(
            state = VpnStatusUiState(
                isConnectRequested = true,
                lastMessage = "Connecting to server..."
            ),
            onConnectClick = {},
            onDisconnectClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun VpnStatusScreenPreview_Disconnected() {
    DataGateOpenVpn3Theme {
        VpnStatusScreen(
            state = VpnStatusUiState(
                isConnectRequested = false,
                lastMessage = ""
            ),
            onConnectClick = {},
            onDisconnectClick = {}
        )
    }
}
