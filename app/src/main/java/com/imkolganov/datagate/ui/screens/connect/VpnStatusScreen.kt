package com.imkolganov.datagate.ui.screens.connect

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.tooling.preview.Preview
import com.imkolganov.datagate.ui.components.BrandLogo
import com.imkolganov.datagate.ui.theme.DataGateAndroidTheme
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusRequester
import com.imkolganov.datagate.ui.tv.tvClickable
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.outlined.ContactSupport
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.update.GitHubLatestRelease
import com.imkolganov.datagate.util.userFriendlyApiError
import com.imkolganov.datagate.vpn.VpnCommandContract
import com.imkolganov.datagate.vpn.VpnStatusUiState
import kotlinx.coroutines.delay

@Composable
fun VpnStatusScreen(
    state: VpnStatusUiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    homeUpdateBanner: GitHubLatestRelease? = null,
    onHomeUpdateBannerAction: (GitHubLatestRelease) -> Unit = {},
    onHomeUpdateBannerDismiss: (GitHubLatestRelease) -> Unit = {},
    graceExpiresAtUtcMs: Long? = null,
    primaryFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val context = LocalContext.current
    val supportEmail = stringResource(R.string.support_contact_email)
    val supportEmailSubject = stringResource(R.string.home_report_email_subject)
    val telegramBotUrl = stringResource(R.string.support_telegram_bot_url)
    val projectTelegramUrl = stringResource(R.string.project_telegram_url)
    val githubIssuesUrl = stringResource(R.string.project_github_issues_url)
    val isConnected = state.isVpnConnected
    val isPaused = state.isVpnPaused
    val isPausing = state.pendingUserCommand == VpnCommandContract.PendingUserCommand.PAUSE
    val isResuming = state.pendingUserCommand == VpnCommandContract.PendingUserCommand.RESUME
    val isConnecting = state.isConnectRequested && !isConnected && !isPaused && !isPausing && !isResuming

    val statusTitle = when {
        isPausing -> stringResource(R.string.vpn_status_pausing)
        isResuming -> stringResource(R.string.vpn_status_resuming)
        isConnected -> stringResource(R.string.vpn_status_connected)
        isPaused -> stringResource(R.string.vpn_status_paused)
        isConnecting -> stringResource(R.string.vpn_status_connecting)
        else -> stringResource(R.string.vpn_status_disconnected)
    }

    val statusSubtitle = when {
        isPausing -> stringResource(R.string.vpn_status_pausing)
        isResuming -> stringResource(R.string.vpn_status_resuming)
        isPaused -> stringResource(R.string.vpn_msg_paused)
        state.lastMessage.isNotBlank() -> remember(state.lastMessage) {
            context.resources.userFriendlyApiError(state.lastMessage)
        }
        else -> stringResource(R.string.vpn_waiting_events)
    }

    val mainColor = when {
        isConnected -> MaterialTheme.colorScheme.primary
        isPaused -> MaterialTheme.colorScheme.secondary
        isConnecting -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val backgroundColor: Color = when {
        isConnected -> mainColor.copy(alpha = 0.18f)
        isPaused -> mainColor.copy(alpha = 0.16f)
        isConnecting -> mainColor.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vpnPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vpnPulseScale"
    )

    val targetScale = if (isConnecting) pulseScale else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "vpnButtonScale"
    )

    val scrollState = rememberScrollState()
    var showReportDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    var graceSecondsLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(graceExpiresAtUtcMs, isConnected) {
        if (graceExpiresAtUtcMs == null || !isConnected) {
            graceSecondsLeft = 0
            return@LaunchedEffect
        }
        while (true) {
            val remaining = ((graceExpiresAtUtcMs - System.currentTimeMillis()) / 1000L).toInt()
            if (remaining <= 0) {
                graceSecondsLeft = 0
                break
            }
            graceSecondsLeft = remaining
            delay(1000L)
        }
    }

    val onClick = {
        when {
            isPausing || isResuming -> Unit // Command already pending — ignore taps until confirmed/rejected.
            isPaused -> onResumeClick()
            isConnected || isConnecting -> onDisconnectClick()
            else -> {
                if (state.hasVpnPermission) {
                    onConnectClick()
                } else {
                    showPermissionDialog = true
                }
            }
        }
    }

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    fun openSupportEmail() {
        val subject = Uri.encode(supportEmailSubject)
        val uri = Uri.parse("mailto:$supportEmail?subject=$subject")
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (_: Exception) {
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            homeUpdateBanner?.let { rel ->
                HomeUpdateAvailableBanner(
                    release = rel,
                    onUpdate = { onHomeUpdateBannerAction(rel) },
                    onDismiss = { onHomeUpdateBannerDismiss(rel) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HomeBrandHeader()

            TextButton(
                onClick = { showReportDialog = true },
                modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(8.dp)),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ContactSupport,
                        contentDescription = stringResource(R.string.home_report_issue),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.home_report_issue))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .widthIn(max = 520.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (isConnected && graceSecondsLeft > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HomeGraceBanner(secondsLeft = graceSecondsLeft)
                }

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isConnected && !isPausing && !isResuming) {
                        TextButton(
                            onClick = onPauseClick,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(8.dp)),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Pause,
                                    contentDescription = stringResource(R.string.action_pause),
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(stringResource(R.string.action_pause))
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(animatedScale)
                        .then(
                            if (primaryFocusRequester != null) {
                                Modifier.focusRequester(primaryFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .clip(CircleShape)
                        .background(color = backgroundColor)
                        .border(
                            width = 2.dp,
                            color = mainColor.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .tvClickable(shape = CircleShape, onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(mainColor, mainColor.copy(alpha = 0.7f))
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
                            contentDescription = stringResource(R.string.vpn_power),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = when {
                                isConnected -> stringResource(R.string.action_disconnect)
                                isPaused -> stringResource(R.string.action_resume)
                                isConnecting -> stringResource(R.string.action_cancel)
                                else -> stringResource(R.string.action_connect)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            }

            Text(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 8.dp),
                text = stringResource(R.string.vpn_home_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HomeTelegramChannelBanner(
                onOpenChannel = { openUrl(projectTelegramUrl) },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(stringResource(R.string.home_report_issue_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.home_report_issue_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                    TextButton(
                        onClick = {
                            openUrl(telegramBotUrl)
                            showReportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.home_report_telegram),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start
                        )
                    }
                    TextButton(
                        onClick = {
                            openSupportEmail()
                            showReportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.home_report_email),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start
                        )
                    }
                    TextButton(
                        onClick = {
                            openUrl(githubIssuesUrl)
                            showReportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.home_report_github),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showReportDialog = false },
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(8.dp)),
                ) {
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
                    // request): startWithConfig() stores the pending config before launching the
                    // system dialog, so granting permission here resumes straight into connecting
                    // instead of leaving the user stuck on a "config is missing" error.
                    showPermissionDialog = false
                    onConnectClick()
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
}

@Composable
private fun HomeBrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLogo(modifier = Modifier.size(56.dp))
        Text(
            modifier = Modifier.padding(start = 6.dp),
            text = stringResource(R.string.vpn_home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HomeGraceBanner(secondsLeft: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_grace_banner_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_grace_banner_body, formatGraceCountdown(secondsLeft)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatGraceCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun HomeTelegramChannelBanner(
    onOpenChannel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.home_telegram_channel_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_telegram_channel_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
            )
            TextButton(onClick = onOpenChannel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.settings_link_telegram))
            }
        }
    }
}

@Composable
private fun HomeUpdateAvailableBanner(
    release: GitHubLatestRelease,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.home_update_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_update_banner_dismiss),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = stringResource(R.string.home_update_banner_body, release.tagName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            )
            TextButton(onClick = onUpdate, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_update_banner_action))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VpnStatusScreenPreview_Connected() {
    DataGateAndroidTheme {
        VpnStatusScreen(
            state = VpnStatusUiState(
                isConnectRequested = true,
                isVpnConnected = true,
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
    DataGateAndroidTheme {
        VpnStatusScreen(
            state = VpnStatusUiState(
                isConnectRequested = true,
                isVpnConnected = false,
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
    DataGateAndroidTheme {
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
