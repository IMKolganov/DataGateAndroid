package com.imkolganov.datagate.ui.freetier

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AdminTotpGate
import com.imkolganov.datagate.freetier.FREE_TIER_RESUME_REFRESH_MIN_INTERVAL_MS
import com.imkolganov.datagate.freetier.FreeTierApi
import com.imkolganov.datagate.freetier.FreeTierComplianceController
import com.imkolganov.datagate.freetier.FreeTierOnboardingCopyMode
import com.imkolganov.datagate.freetier.FreeTierStatusFetchOutcome
import com.imkolganov.datagate.freetier.evaluateFreeTierStatusFetch
import com.imkolganov.datagate.freetier.freeTierOnboardingCopyMode
import com.imkolganov.datagate.freetier.FreeTierStatusFetchResult
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.freetier.isFreeTierLinkCodeExpired
import com.imkolganov.datagate.freetier.shouldRefreshFreeTierStatusOnResume
import com.imkolganov.datagate.freetier.telegramChannelUrl
import com.imkolganov.datagate.model.freetier.FreeTierAccessStatusResponse
import com.imkolganov.datagate.model.freetier.RequestTelegramAccountLinkCodeResponse
import com.imkolganov.datagate.update.ApkUpdateInstaller
import com.imkolganov.datagate.util.deepMessageForApiError
import com.imkolganov.datagate.util.userFriendlyApiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    var lastStatusFetchMs by remember { mutableLongStateOf(0L) }
    var refreshOnNextResume by remember { mutableStateOf(false) }

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

    suspend fun fetchStatus() {
        statusLoading = true
        try {
            val response = withContext(Dispatchers.IO) { freeTierApi.getAccessStatus() }
            lastStatusFetchMs = System.currentTimeMillis()
            applyFetchResult(evaluateFreeTierStatusFetch(response))
        } catch (e: Exception) {
            lastStatusFetchMs = System.currentTimeMillis()
            applyFetchResult(
                evaluateFreeTierStatusFetch(
                    response = ApiResponse(success = false, message = null, data = null),
                    apiFailureMessage = appContext.resources.userFriendlyApiError(
                        e.deepMessageForApiError()
                    ),
                )
            )
        } finally {
            statusLoading = false
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
        fetchStatus()
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
                    scope.launch { fetchStatus() }
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
                    onClick = { scope.launch { fetchStatus() } }
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
            Column {
                Text(
                    text = when (copyMode) {
                        FreeTierOnboardingCopyMode.LinkAccount ->
                            stringResource(R.string.free_tier_body_link, channelLabel)
                        FreeTierOnboardingCopyMode.SubscribeOnly ->
                            stringResource(R.string.free_tier_body_subscribe_only, channelLabel)
                        FreeTierOnboardingCopyMode.Generic ->
                            stringResource(R.string.free_tier_body_generic, channelLabel)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                linkCode?.let { code ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = code.code,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (codeSecondsLeft > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.free_tier_code_expires,
                                formatCountdown(codeSecondsLeft)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.free_tier_code_instruction_1),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.free_tier_code_instruction_2, code.code),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.free_tier_code_instruction_3),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    enabled = !statusLoading,
                    onClick = { scope.launch { fetchStatus() } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (statusLoading) {
                            stringResource(R.string.free_tier_check_again_loading)
                        } else {
                            stringResource(R.string.free_tier_check_again)
                        }
                    )
                }
                if (copyMode != FreeTierOnboardingCopyMode.Generic) {
                    TextButton(
                        onClick = { openTelegramUrl(channelUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.free_tier_open_channel))
                    }
                }
            }
        },
        confirmButton = {
            when {
                copyMode == FreeTierOnboardingCopyMode.LinkAccount && linkCode == null -> {
                    TextButton(
                        enabled = !linkCodeLoading && !statusLoading,
                        onClick = {
                            scope.launch {
                                linkCodeLoading = true
                                errorMessage = null
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                        freeTierApi.requestAccountLinkCode()
                                    }
                                    if (response.success) {
                                        val data = response.data
                                        if (data != null && data.code.isNotBlank()) {
                                            linkCode = data
                                            codeExpiresAtMs =
                                                System.currentTimeMillis() + data.expiresInSeconds * 1000L
                                        } else {
                                            errorMessage =
                                                appContext.getString(R.string.free_tier_link_code_failed)
                                        }
                                    } else {
                                        errorMessage = response.message?.ifBlank { null }
                                            ?: appContext.getString(R.string.free_tier_link_code_failed)
                                    }
                                } catch (e: Exception) {
                                    errorMessage =
                                        appContext.resources.userFriendlyApiError(
                                            e.deepMessageForApiError()
                                        )
                                } finally {
                                    linkCodeLoading = false
                                }
                            }
                        }
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
                linkCode != null -> {
                    TextButton(onClick = { openTelegramUrl(botUrl) }) {
                        Text(stringResource(R.string.free_tier_open_bot))
                    }
                }
                else -> {
                    TextButton(onClick = { openTelegramUrl(channelUrl) }) {
                        Text(stringResource(R.string.free_tier_open_channel))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onboardingVisible = false }) {
                Text(stringResource(R.string.free_tier_close))
            }
        }
    )
}

private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
