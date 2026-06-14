package com.imkolganov.datagate.ui.screens.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.TotpChallengeUi
import com.imkolganov.datagate.model.auth.TotpSetupDto
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.screens.login.AuthActionButton
import com.imkolganov.datagate.ui.screens.login.AuthMessage
import com.imkolganov.datagate.ui.screens.login.AuthTextField
import com.imkolganov.datagate.util.TotpQrEncoder

@Composable
fun TotpChallengeScreen(
    challenge: TotpChallengeUi,
    isLoading: Boolean,
    errorMessage: String?,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        val lead = if (!challenge.displayName.isNullOrBlank()) {
                            stringResource(R.string.totp_challenge_lead_named, challenge.displayName)
                        } else {
                            stringResource(R.string.totp_challenge_lead)
                        }
                        Text(
                            text = lead,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        AuthTextField(
                            value = code,
                            onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(8) },
                            label = stringResource(R.string.totp_field_code),
                            enabled = !isLoading && !challenge.challengeExpired,
                            keyboardType = KeyboardType.NumberPassword,
                            leadingIcon = Icons.Outlined.Security,
                        )
                        if (!errorMessage.isNullOrBlank()) {
                            AuthMessage(text = errorMessage, isError = true)
                        }
                        AuthActionButton(
                            text = stringResource(R.string.totp_verify_sign_in),
                            isLoading = isLoading,
                            enabled = code.trim().length >= 6 && !challenge.challengeExpired,
                            onClick = { onVerify(code) }
                        )
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppCards.shape
                        ) {
                            Text(
                                if (challenge.challengeExpired) {
                                    stringResource(R.string.totp_sign_in_again)
                                } else {
                                    stringResource(R.string.totp_back_to_sign_in)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTotpSetupScreen(
    isLoading: Boolean,
    totpSetupConfirmLoading: Boolean,
    setup: TotpSetupDto?,
    errorMessage: String?,
    infoMessage: String?,
    onBeginSetup: () -> Unit,
    onCancelSetup: () -> Unit,
    onConfirm: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var confirmCode by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.totp_setup_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = stringResource(R.string.totp_setup_required_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.totp_setup_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!infoMessage.isNullOrBlank()) {
                            AuthMessage(text = infoMessage, isError = false)
                        }
                        if (!errorMessage.isNullOrBlank()) {
                            AuthMessage(text = errorMessage, isError = true)
                        }

                        if (setup == null) {
                            AuthActionButton(
                                text = stringResource(R.string.totp_setup_begin),
                                isLoading = isLoading,
                                enabled = true,
                                onClick = onBeginSetup
                            )
                        } else {
                            TotpSetupDetails(
                                setup = setup,
                                context = context,
                                confirmCode = confirmCode,
                                onConfirmCodeChange = { confirmCode = it },
                                isLoading = totpSetupConfirmLoading,
                                onConfirm = { onConfirm(confirmCode) },
                                onCancel = onCancelSetup,
                            )
                        }

                        TextButton(
                            onClick = onLogout,
                            enabled = !isLoading && !totpSetupConfirmLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.totp_setup_logout))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotpSetupDetails(
    setup: TotpSetupDto,
    context: Context,
    confirmCode: String,
    onConfirmCodeChange: (String) -> Unit,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val uri = setup.otpAuthUri.orEmpty()
    var qrBitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        qrBitmap = if (uri.isNotBlank()) TotpQrEncoder.encodeOtpAuthUri(uri, 400) else null
    }

    Text(
        text = stringResource(R.string.totp_setup_scan_hint),
        style = MaterialTheme.typography.bodySmall
    )
    qrBitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = stringResource(R.string.totp_qr_cd),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .size(200.dp)
        )
    }
    if (!setup.issuer.isNullOrBlank() || !setup.accountName.isNullOrBlank()) {
        Text(
            text = buildString {
                setup.issuer?.let { append(stringResource(R.string.totp_setup_issuer, it)).append('\n') }
                setup.accountName?.let { append(stringResource(R.string.totp_setup_account, it)) }
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = setup.sharedSecret,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("totp-secret", setup.sharedSecret))
            }
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.size(4.dp))
            Text(stringResource(R.string.totp_copy_secret))
        }
    }
    AuthTextField(
        value = confirmCode,
        onValueChange = { onConfirmCodeChange(it.filter { ch -> ch.isDigit() }.take(8)) },
        label = stringResource(R.string.totp_setup_confirm_code_label),
        enabled = !isLoading,
        keyboardType = KeyboardType.NumberPassword,
        leadingIcon = Icons.Outlined.Security,
    )
    AuthActionButton(
        text = stringResource(R.string.totp_setup_confirm_action),
        isLoading = isLoading,
        enabled = confirmCode.trim().length >= 6,
        onClick = onConfirm
    )
    OutlinedButton(
        onClick = onCancel,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.action_cancel))
    }
}
