package com.imkolganov.datagate.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.servers.VpnServerType

/**
 * Server stack logos — same assets as DataGateMonitorFrontend `VpnStackLogo`
 * (`public/logos/openvpn-icon.svg`, `public/logos/xray.svg`).
 */
@DrawableRes
fun VpnServerType.iconRes(): Int = when (this) {
    VpnServerType.OpenVpn -> R.drawable.ic_server_type_openvpn
    VpnServerType.Xray -> R.drawable.ic_server_type_xray
    VpnServerType.Unknown -> R.drawable.ic_server_type_unknown
}

@StringRes
fun VpnServerType.labelRes(): Int = when (this) {
    VpnServerType.OpenVpn -> R.string.label_openvpn
    VpnServerType.Xray -> R.string.label_server_type_xray
    VpnServerType.Unknown -> R.string.label_server_type_unknown
}

@Composable
fun VpnServerTypeIcon(
    serverType: VpnServerType,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Image(
        painter = painterResource(serverType.iconRes()),
        contentDescription = stringResource(serverType.labelRes()),
        modifier = modifier.size(size),
    )
}

@Composable
fun VpnServerTypeLabel(
    serverType: VpnServerType,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(serverType.labelRes()),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
