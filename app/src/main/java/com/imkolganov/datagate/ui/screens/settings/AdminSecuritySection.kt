package com.imkolganov.datagate.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.JwtClaimsReader
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.model.auth.TotpSetupDto
import com.imkolganov.datagate.ui.screens.login.AuthActionButton
import com.imkolganov.datagate.ui.screens.login.AuthMessage
import com.imkolganov.datagate.ui.screens.login.AuthTextField
import com.imkolganov.datagate.util.TotpQrEncoder
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AdminSecuritySection(
    tokenStore: TokenStore,
    authViewModel: AuthViewModel,
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = context.resources
    val isAdmin = remember(tokenStore) {
        JwtClaimsReader.isAdmin(tokenStore.getAccessToken())
    }

    LaunchedEffect(isAdmin) {
        if (isAdmin) authViewModel.loadTotpStatusForSettings()
    }

    if (!isAdmin) return

    val status = authState.totpStatus
    var disableCode by rememberSaveable { mutableStateOf("") }
    var disablePassword by rememberSaveable { mutableStateOf("") }
    val setup = authState.totpSetup

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.totp_admin_security_title),
            style = MaterialTheme.typography.titleMedium
        )
    }
    Text(
        text = stringResource(R.string.totp_admin_security_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    if (status == null) {
        Text(
            text = stringResource(R.string.totp_admin_security_loading),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }

    if (!authState.errorMessage.isNullOrBlank()) {
        AuthMessage(text = authState.errorMessage!!, isError = true)
    }
    if (!authState.infoMessage.isNullOrBlank()) {
        AuthMessage(text = authState.infoMessage!!, isError = false)
    }

    Text(
        text = if (status.totpEnabled) {
            stringResource(R.string.totp_admin_status_enabled)
        } else {
            stringResource(R.string.totp_admin_status_disabled)
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp)
    )

    if (status.totpEnabled) {
        Text(
            text = stringResource(R.string.totp_disable_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        AuthTextField(
            value = disableCode,
            onValueChange = { disableCode = it.filter { ch -> ch.isDigit() }.take(8) },
            label = stringResource(R.string.totp_field_code),
            enabled = !authState.isLoading,
            keyboardType = KeyboardType.NumberPassword,
            leadingIcon = Icons.Outlined.Security,
        )
        OutlinedTextField(
            value = disablePassword,
            onValueChange = { disablePassword = it },
            label = { Text(stringResource(R.string.totp_disable_password_label)) },
            supportingText = {
                Text(stringResource(R.string.totp_disable_password_hint))
            },
            singleLine = true,
            enabled = !authState.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        OutlinedButton(
            onClick = {
                authViewModel.disableTotp(resources, disableCode, disablePassword)
                disableCode = ""
                disablePassword = ""
            },
            enabled = !authState.isLoading && disableCode.trim().length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.totp_disable_action))
        }
    } else if (setup == null) {
        Button(
            onClick = { authViewModel.beginAdminTotpSetup(resources) },
            enabled = !authState.isLoading,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.totp_setup_begin))
        }
    } else {
        AdminSecuritySetupBlock(setup = setup, context = context, authViewModel = authViewModel)
    }
}

@Composable
private fun AdminSecuritySetupBlock(
    setup: TotpSetupDto,
    context: Context,
    authViewModel: AuthViewModel,
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    var confirmCode by rememberSaveable { mutableStateOf("") }
    val uri = setup.otpAuthUri.orEmpty()
    val qrBitmap = remember(uri) {
        if (uri.isNotBlank()) TotpQrEncoder.encodeOtpAuthUri(uri, 400) else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        qrBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.totp_qr_cd),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .size(180.dp)
            )
        }
        Text(setup.sharedSecret, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("totp-secret", setup.sharedSecret))
            }
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            Text(stringResource(R.string.totp_copy_secret))
        }
        AuthTextField(
            value = confirmCode,
            onValueChange = { confirmCode = it.filter { ch -> ch.isDigit() }.take(8) },
            label = stringResource(R.string.totp_setup_confirm_code_label),
            enabled = !authState.totpSetupConfirmLoading,
            keyboardType = KeyboardType.NumberPassword,
            leadingIcon = Icons.Outlined.Security,
        )
        AuthActionButton(
            text = stringResource(R.string.totp_setup_confirm_action),
            isLoading = authState.totpSetupConfirmLoading,
            enabled = confirmCode.trim().length >= 6,
            onClick = {
                authViewModel.confirmAdminTotpSetup(context.resources, confirmCode)
                authViewModel.loadTotpStatusForSettings()
            }
        )
        OutlinedButton(
            onClick = { authViewModel.cancelAdminTotpSetup() },
            enabled = !authState.totpSetupConfirmLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
