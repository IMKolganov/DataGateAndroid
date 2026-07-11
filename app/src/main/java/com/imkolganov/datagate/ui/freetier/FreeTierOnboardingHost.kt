package com.imkolganov.datagate.ui.freetier

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AdminTotpGate
import com.imkolganov.datagate.freetier.FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS
import com.imkolganov.datagate.freetier.FREE_TIER_STATUS_FETCH_MAX_ATTEMPTS
import com.imkolganov.datagate.freetier.FREE_TIER_STATUS_FETCH_RETRY_DELAY_MS
import com.imkolganov.datagate.freetier.FreeTierApi
import com.imkolganov.datagate.freetier.FreeTierComplianceController
import com.imkolganov.datagate.freetier.FreeTierOnboardingCopyMode
import com.imkolganov.datagate.freetier.FreeTierStatusFetchOutcome
import com.imkolganov.datagate.freetier.FreeTierStatusFetchResult
import com.imkolganov.datagate.freetier.evaluateFreeTierStatusFetch
import com.imkolganov.datagate.freetier.freeTierOnboardingCopyMode
import com.imkolganov.datagate.freetier.isFreeTierLinkCodeExpired
import com.imkolganov.datagate.freetier.shouldRefreshFreeTierStatusOnPoll
import com.imkolganov.datagate.freetier.shouldRefreshFreeTierStatusOnResume
import com.imkolganov.datagate.freetier.shouldWarnLinkCodeExpiringSoon
import com.imkolganov.datagate.freetier.telegramChannelUrl
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.freetier.FreeTierAccessStatusResponse
import com.imkolganov.datagate.model.freetier.RequestTelegramAccountLinkCodeResponse
import com.imkolganov.datagate.update.ApkUpdateInstaller
import com.imkolganov.datagate.util.userFriendlyApiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun FreeTierOnboardingHost(
    adminTotpGate: AdminTotpGate,
    freeTierApi: FreeTierApi,
) {
    if (adminTotpGate != AdminTotpGate.Allowed) {
        FreeTierComplianceController.setOnboardingVisible(false)
        return
    }

    val activity = LocalActivity.current ?: return
    val appContext = activity.applicationContext
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<FreeTierAccessStatusResponse?>(null) }
    var linkCode by remember { mutableStateOf<RequestTelegramAccountLinkCodeResponse?>(null) }
    var codeExpiresAtMs by remember { mutableLongStateOf(0L) }
    var codeSecondsLeft by remember { mutableIntStateOf(0) }
    var onboardingVisible by remember { mutableStateOf(false) }
    var statusErrorVisible by remember { mutableStateOf(false) }
    var statusLoading by remember { mutableStateOf(false) }
    var linkCodeLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var linkCodeExpiredNotice by remember { mutableStateOf(false) }

    var lastStatusFetchMs by remember { mutableLongStateOf(0L) }
    var refreshOnNextResume by remember { mutableStateOf(false) }
    val fetchMutex = remember { Mutex() }

    val defaultChannelLabel = stringResource(R.string.free_tier_default_channel)
    val defaultChannelHandle = stringResource(R.string.free_tier_default_channel_handle)
    val botUrl = stringResource(R.string.support_telegram_bot_url)

    fun openTelegramUrl(url: String) {
        refreshOnNextResume = true
        ApkUpdateInstaller.openUrl(activity, url)
    }

    fun applyFetchResult(result: FreeTierStatusFetchResult) {
        when (result.outcome) {
            FreeTierStatusFetchOutcome.HideOnboarding -> {
                onboardingVisible = false
                statusErrorVisible = false
                status = result.status
                linkCode = null
                codeExpiresAtMs = 0L
                codeSecondsLeft = 0
                errorMessage = null
                linkCodeExpiredNotice = false
            }
            FreeTierStatusFetchOutcome.ShowOnboarding -> {
                statusErrorVisible = false
                status = result.status
                onboardingVisible = true
                errorMessage = null
            }
            FreeTierStatusFetchOutcome.ShowStatusError -> {
                statusErrorVisible = true
                onboardingVisible = false
                errorMessage = result.errorMessage
                    ?: appContext.getString(R.string.free_tier_status_check_failed)
            }
        }
    }

    suspend fun fetchAccessStatusWithRetry(): ApiResponse<FreeTierAccessStatusResponse> {
        var lastError: Exception? = null
        repeat(FREE_TIER_STATUS_FETCH_MAX_ATTEMPTS) { attempt ->
            try {
                return withContext(Dispatchers.IO) { freeTierApi.getAccessStatus() }
            } catch (e: Exception) {
                lastError = e
                // Transient network blips (e.g. "Socket closed" while the VPN tunnel is coming up or
                // down) shouldn't surface an error dialog on the first failure — retry quietly first.
                if (attempt < FREE_TIER_STATUS_FETCH_MAX_ATTEMPTS - 1) {
                    delay(FREE_TIER_STATUS_FETCH_RETRY_DELAY_MS)
                }
            }
        }
        throw lastError ?: IllegalStateException("Free tier status fetch failed with no exception recorded")
    }

    suspend fun fetchStatus(force: Boolean = false) {
        if (!force) {
            val now = System.currentTimeMillis()
            if (!shouldRefreshFreeTierStatusOnPoll(lastStatusFetchMs, now)) return
        }
        fetchMutex.withLock {
            if (!force) {
                val now = System.currentTimeMillis()
                if (!shouldRefreshFreeTierStatusOnPoll(lastStatusFetchMs, now)) return@withLock
            }
            statusLoading = true
            try {
                val response = fetchAccessStatusWithRetry()
                lastStatusFetchMs = System.currentTimeMillis()
                applyFetchResult(evaluateFreeTierStatusFetch(response))
            } catch (e: Exception) {
                lastStatusFetchMs = System.currentTimeMillis()
                applyFetchResult(
                    evaluateFreeTierStatusFetch(
                        response = ApiResponse(success = false, message = null, data = null),
                        apiFailureMessage = appContext.resources.userFriendlyApiError(e),
                    )
                )
            } finally {
                statusLoading = false
            }
        }
    }

    suspend fun requestLinkCode() {
        linkCodeLoading = true
        errorMessage = null
        linkCodeExpiredNotice = false
        try {
            val response = withContext(Dispatchers.IO) { freeTierApi.requestAccountLinkCode() }
            if (response.success) {
                val data = response.data
                if (data != null && data.code.isNotBlank()) {
                    linkCode = data
                    codeExpiresAtMs = System.currentTimeMillis() + data.expiresInSeconds * 1000L
                } else {
                    errorMessage = appContext.getString(R.string.free_tier_link_code_failed)
                }
            } else {
                errorMessage = response.message?.ifBlank { null }
                    ?: appContext.getString(R.string.free_tier_link_code_failed)
            }
        } catch (e: Exception) {
            errorMessage = appContext.resources.userFriendlyApiError(e)
        } finally {
            linkCodeLoading = false
        }
    }

    val overlayVisible = onboardingVisible || statusErrorVisible
    DisposableEffect(overlayVisible) {
        FreeTierComplianceController.setOnboardingVisible(overlayVisible)
        onDispose { FreeTierComplianceController.setOnboardingVisible(false) }
    }

    LaunchedEffect(adminTotpGate) {
        if (adminTotpGate != AdminTotpGate.Allowed) {
            status = null
            linkCode = null
            onboardingVisible = false
            statusErrorVisible = false
            return@LaunchedEffect
        }
        fetchStatus(force = true)
    }

    val statusRefreshNonce by FreeTierComplianceController.statusRefreshNonce.collectAsState()
    LaunchedEffect(statusRefreshNonce) {
        if (adminTotpGate != AdminTotpGate.Allowed || statusRefreshNonce == 0) return@LaunchedEffect
        fetchStatus(force = false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adminTotpGate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && adminTotpGate == AdminTotpGate.Allowed) {
                val now = System.currentTimeMillis()
                if (shouldRefreshFreeTierStatusOnResume(
                        lastStatusFetchMs = lastStatusFetchMs,
                        refreshOnNextResume = refreshOnNextResume,
                        nowMs = now,
                        minIntervalMs = FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS,
                    )
                ) {
                    refreshOnNextResume = false
                    scope.launch { fetchStatus(force = true) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(codeExpiresAtMs) {
        if (codeExpiresAtMs <= 0L) {
            codeSecondsLeft = 0
            return@LaunchedEffect
        }
        while (true) {
            val now = System.currentTimeMillis()
            if (isFreeTierLinkCodeExpired(codeExpiresAtMs, now)) {
                codeSecondsLeft = 0
                linkCode = null
                linkCodeExpiredNotice = true
                codeExpiresAtMs = 0L
                break
            }
            codeSecondsLeft = ((codeExpiresAtMs - now) / 1000L).toInt()
            delay(1000L)
        }
    }

    if (statusErrorVisible) {
        AlertDialog(
            onDismissRequest = { statusErrorVisible = false },
            title = { Text(stringResource(R.string.free_tier_status_error_title)) },
            text = {
                Text(
                    text = errorMessage ?: stringResource(R.string.free_tier_status_check_failed),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !statusLoading,
                    onClick = { scope.launch { fetchStatus(force = true) } }
                ) {
                    Text(
                        if (statusLoading) {
                            stringResource(R.string.free_tier_check_again_loading)
                        } else {
                            stringResource(R.string.free_tier_check_again)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { statusErrorVisible = false }) {
                    Text(stringResource(R.string.free_tier_close))
                }
            }
        )
    }

    val currentStatus = status
    if (!onboardingVisible || currentStatus == null) return

    val channelLabel = currentStatus.requiredChannel?.takeIf { it.isNotBlank() } ?: defaultChannelLabel
    val channelUrl = telegramChannelUrl(
        requiredChannel = currentStatus.requiredChannel,
        defaultHandle = defaultChannelHandle,
    )
    val copyMode = freeTierOnboardingCopyMode(currentStatus)

    AlertDialog(
        onDismissRequest = { onboardingVisible = false },
        title = {
            Text(
                when (copyMode) {
                    FreeTierOnboardingCopyMode.LinkAccount ->
                        stringResource(R.string.free_tier_title_link)
                    FreeTierOnboardingCopyMode.SubscribeOnly,
                    FreeTierOnboardingCopyMode.Generic ->
                        stringResource(R.string.free_tier_title_subscribe)
                }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when (copyMode) {
                        FreeTierOnboardingCopyMode.LinkAccount ->
                            stringResource(R.string.free_tier_body_link, channelLabel)
                        FreeTierOnboardingCopyMode.SubscribeOnly ->
                            stringResource(R.string.free_tier_body_subscribe_only, channelLabel)
                        FreeTierOnboardingCopyMode.Generic ->
                            stringResource(R.string.free_tier_body_generic, channelLabel)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.free_tier_telegram_blocked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (linkCodeExpiredNotice && linkCode == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.free_tier_code_expired),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                linkCode?.let { code ->
                    Spacer(modifier = Modifier.height(16.dp))
                    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = code.code,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.free_tier_code_paste_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (codeSecondsLeft > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val expiryText = if (shouldWarnLinkCodeExpiringSoon(codeSecondsLeft)) {
                            stringResource(
                                R.string.free_tier_code_expires_soon,
                                formatCountdown(codeSecondsLeft),
                            )
                        } else {
                            stringResource(
                                R.string.free_tier_code_expires,
                                formatCountdown(codeSecondsLeft),
                            )
                        }
                        Text(
                            text = expiryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shouldWarnLinkCodeExpiringSoon(codeSecondsLeft)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyLinkCodeToClipboard(appContext, code.code)
                                Toast.makeText(
                                    appContext,
                                    appContext.getString(R.string.copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(stringResource(R.string.free_tier_copy_code))
                        }
                        Button(
                            onClick = { openTelegramUrl(botUrl) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.free_tier_open_bot))
                        }
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    enabled = !statusLoading,
                    onClick = { scope.launch { fetchStatus(force = true) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (statusLoading) {
                            stringResource(R.string.free_tier_check_again_loading)
                        } else {
                            stringResource(R.string.free_tier_done_check)
                        }
                    )
                }
            }
        },
        confirmButton = {
            when {
                copyMode == FreeTierOnboardingCopyMode.LinkAccount && linkCode == null -> {
                    Button(
                        enabled = !linkCodeLoading && !statusLoading,
                        onClick = { scope.launch { requestLinkCode() } },
                    ) {
                        Text(
                            if (linkCodeLoading) {
                                stringResource(R.string.free_tier_get_code_loading)
                            } else {
                                stringResource(R.string.free_tier_get_code)
                            }
                        )
                    }
                }
                copyMode != FreeTierOnboardingCopyMode.Generic -> {
                    OutlinedButton(onClick = { openTelegramUrl(channelUrl) }) {
                        Text(stringResource(R.string.free_tier_open_channel))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onboardingVisible = false }) {
                Text(stringResource(R.string.free_tier_close))
            }
        },
    )
}

private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun copyLinkCodeToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("datagate-link-code", code))
}
