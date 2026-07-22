package com.imkolganov.datagate.ui.screens.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.TvLinkPhase
import com.imkolganov.datagate.auth.TvLinkUiState
import com.imkolganov.datagate.auth.tv.TvDeviceInfo
import com.imkolganov.datagate.model.auth.TvLoginSessionStatus
import com.imkolganov.datagate.ui.components.BrandLogo
import com.imkolganov.datagate.ui.screens.auth.TotpChallengeScreen
import com.imkolganov.datagate.auth.AuthLoginScreen
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import com.imkolganov.datagate.util.TotpQrEncoder
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

@Composable
fun TvLinkLoginScreen(
    viewModel: AuthViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val deviceName = remember(context) { TvDeviceInfo.deviceName(context) }

    val challenge = state.totpChallenge
    if (state.loginScreen == AuthLoginScreen.TotpChallenge && challenge != null) {
        TotpChallengeScreen(
            challenge = challenge,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onVerify = { viewModel.verifyTotpLogin(resources, it) },
            onBack = { viewModel.backFromTotpChallenge() },
        )
        return
    }

    DisposableEffect(Unit) {
        viewModel.startTvLinkLogin(resources, deviceName)
        onDispose { viewModel.stopTvLinkLogin() }
    }

    TvLinkLoginContent(
        tvLink = state.tvLink,
        onRetry = { viewModel.retryTvLinkLogin(resources, deviceName) },
    )
}

@Composable
fun TvLinkLoginContent(
    tvLink: TvLinkUiState,
    onRetry: () -> Unit,
) {
    val qrBitmap: Bitmap? = remember(tvLink.qrPayload) {
        val payload = tvLink.qrPayload
        if (payload.isNullOrBlank()) null
        else TotpQrEncoder.encodeOtpAuthUri(payload, sizePx = 512)
    }

    var remainingMs by remember(tvLink.expiresAt) { mutableLongStateOf(remainingMillis(tvLink.expiresAt)) }
    LaunchedEffect(tvLink.expiresAt, tvLink.phase) {
        while (
            tvLink.phase == TvLinkPhase.Waiting ||
            tvLink.phase == TvLinkPhase.Creating ||
            tvLink.phase == TvLinkPhase.Completing
        ) {
            remainingMs = remainingMillis(tvLink.expiresAt)
            delay(1000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BrandLogo(modifier = Modifier.size(64.dp))
            Text(
                text = stringResource(R.string.tv_link_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tv_link_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp),
            )

            when (tvLink.phase) {
                TvLinkPhase.Idle, TvLinkPhase.Creating -> {
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.tv_link_creating),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TvLinkPhase.Waiting, TvLinkPhase.Completing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp),
                        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.tv_link_qr_cd),
                                    modifier = Modifier.size(280.dp),
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.tv_link_code_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatUserCode(tvLink.userCode.orEmpty()),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 64.sp,
                                    letterSpacing = 6.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                                textAlign = TextAlign.Center,
                            )
                            if (remainingMs > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.tv_link_expires_in,
                                        formatCountdown(remainingMs),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = statusLabel(tvLink.status, tvLink.phase),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                            if (tvLink.phase == TvLinkPhase.Completing) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
                TvLinkPhase.Denied, TvLinkPhase.Expired, TvLinkPhase.Failed -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = tvLink.errorMessage
                            ?: when (tvLink.phase) {
                                TvLinkPhase.Denied -> stringResource(R.string.tv_link_status_denied)
                                TvLinkPhase.Expired -> stringResource(R.string.tv_link_status_expired)
                                else -> stringResource(R.string.tv_link_error_create)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text(stringResource(R.string.tv_link_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: String?, phase: TvLinkPhase): String {
    if (phase == TvLinkPhase.Completing) {
        return stringResource(R.string.tv_link_status_approved)
    }
    return when (TvLoginSessionStatus.normalize(status)) {
        TvLoginSessionStatus.VIEWED -> stringResource(R.string.tv_link_status_viewed)
        TvLoginSessionStatus.APPROVED -> stringResource(R.string.tv_link_status_approved)
        else -> stringResource(R.string.tv_link_status_pending)
    }
}

private fun formatUserCode(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length == 6) {
        "${digits.substring(0, 3)} ${digits.substring(3)}"
    } else {
        raw
    }
}

private fun remainingMillis(expiresAt: String?): Long {
    if (expiresAt.isNullOrBlank()) return 0L
    return try {
        val end = Instant.parse(expiresAt)
        Duration.between(Instant.now(), end).toMillis().coerceAtLeast(0L)
    } catch (_: DateTimeParseException) {
        0L
    } catch (_: Exception) {
        0L
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
