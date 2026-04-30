package com.imkolganov.datagate.ui.screens.login

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AuthLoginTab
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.EmailAuthPane

@Composable
fun LoginScreenContent(
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    loginTab: AuthLoginTab,
    emailPane: EmailAuthPane,
    pendingVerificationEmail: String?,
    onSelectTab: (AuthLoginTab) -> Unit,
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onRegister: (String, String, String, String, String) -> Unit,
    onConfirmEmail: (String, String) -> Unit,
    onResendConfirmation: (String) -> Unit,
    onGoEmailSignIn: () -> Unit,
    onGoEmailRegister: () -> Unit,
    onDismissInfo: () -> Unit
) {
    val versionLabel = stringResource(
        R.string.login_version,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE
    )
    val scroll = rememberScrollState()

    var signInLogin by rememberSaveable { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }

    var regDisplay by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var regLogin by rememberSaveable { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirm by remember { mutableStateOf("") }

    var confirmEmail by rememberSaveable { mutableStateOf("") }
    var confirmCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pendingVerificationEmail, emailPane) {
        if (emailPane == EmailAuthPane.ConfirmEmail) {
            val p = pendingVerificationEmail
            if (!p.isNullOrBlank()) confirmEmail = p
        }
    }

    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSelectTab(AuthLoginTab.Google) },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && loginTab != AuthLoginTab.Google
                        ) {
                            Text(
                                stringResource(R.string.auth_tab_google),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedButton(
                            onClick = { onSelectTab(AuthLoginTab.Email) },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && loginTab != AuthLoginTab.Email
                        ) {
                            Text(
                                stringResource(R.string.auth_tab_email),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    when (loginTab) {
                        AuthLoginTab.Google -> {
                            if (isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Button(
                                    onClick = onGoogleSignIn,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.login_sign_in_google))
                                }
                            }
                            Text(
                                text = stringResource(R.string.login_welcome),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        AuthLoginTab.Email -> {
                            when (emailPane) {
                                EmailAuthPane.SignIn -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        TextButton(onClick = onGoEmailSignIn, enabled = !isLoading) {
                                            Text(stringResource(R.string.auth_email_sign_in))
                                        }
                                        TextButton(onClick = onGoEmailRegister, enabled = !isLoading) {
                                            Text(stringResource(R.string.auth_email_register))
                                        }
                                    }
                                    OutlinedTextField(
                                        value = signInLogin,
                                        onValueChange = { signInLogin = it },
                                        label = { Text(stringResource(R.string.auth_field_login)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = signInPassword,
                                        onValueChange = { signInPassword = it },
                                        label = { Text(stringResource(R.string.auth_field_password)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            onEmailSignIn(signInLogin, signInPassword)
                                        },
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.height(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(stringResource(R.string.auth_sign_in))
                                        }
                                    }
                                }

                                EmailAuthPane.Register -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        TextButton(onClick = onGoEmailSignIn, enabled = !isLoading) {
                                            Text(stringResource(R.string.auth_email_sign_in))
                                        }
                                        TextButton(onClick = onGoEmailRegister, enabled = !isLoading) {
                                            Text(stringResource(R.string.auth_email_register))
                                        }
                                    }
                                    OutlinedTextField(
                                        value = regDisplay,
                                        onValueChange = { regDisplay = it },
                                        label = { Text(stringResource(R.string.auth_field_display_name)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = regEmail,
                                        onValueChange = { regEmail = it },
                                        label = { Text(stringResource(R.string.auth_field_email)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = regLogin,
                                        onValueChange = { regLogin = it },
                                        label = { Text(stringResource(R.string.auth_field_login)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = regPassword,
                                        onValueChange = { regPassword = it },
                                        label = { Text(stringResource(R.string.auth_field_password)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = regConfirm,
                                        onValueChange = { regConfirm = it },
                                        label = { Text(stringResource(R.string.auth_field_confirm_password)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            onRegister(regDisplay, regEmail, regLogin, regPassword, regConfirm)
                                        },
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.height(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(stringResource(R.string.auth_create_account))
                                        }
                                    }
                                }

                                EmailAuthPane.ConfirmEmail -> {
                                    Text(
                                        text = stringResource(R.string.auth_confirm_email_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedTextField(
                                        value = confirmEmail,
                                        onValueChange = { confirmEmail = it },
                                        label = { Text(stringResource(R.string.auth_field_email)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = confirmCode,
                                        onValueChange = { confirmCode = it.filter { ch -> ch.isDigit() }.take(6) },
                                        label = { Text(stringResource(R.string.auth_field_confirmation_code)) },
                                        singleLine = true,
                                        enabled = !isLoading,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = { onConfirmEmail(confirmEmail, confirmCode) },
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.height(22.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(stringResource(R.string.auth_confirm_email_action))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onResendConfirmation(confirmEmail) },
                                        enabled = !isLoading,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.auth_resend_code))
                                    }
                                    TextButton(
                                        onClick = {
                                            onGoEmailSignIn()
                                            onDismissInfo()
                                        },
                                        enabled = !isLoading
                                    ) {
                                        Text(stringResource(R.string.auth_skip_to_sign_in))
                                    }
                                }
                            }
                        }
                    }

                    if (!infoMessage.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = infoMessage,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissInfo) {
                                Text(stringResource(R.string.auth_dismiss))
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = versionLabel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val resources = LocalResources.current

    LoginScreenContent(
        isLoading = state.isLoading,
        errorMessage = state.errorMessage,
        infoMessage = state.infoMessage,
        loginTab = state.loginTab,
        emailPane = state.emailPane,
        pendingVerificationEmail = state.pendingVerificationEmail,
        onSelectTab = { viewModel.selectLoginTab(it) },
        onGoogleSignIn = {
            if (activity != null) viewModel.login(activity)
        },
        onEmailSignIn = { login, password ->
            viewModel.loginWithPassword(resources, login, password)
        },
        onRegister = { display, email, login, pass, confirm ->
            viewModel.register(resources, display, email, login, pass, confirm)
        },
        onConfirmEmail = { email, code ->
            viewModel.confirmEmail(resources, email, code)
        },
        onResendConfirmation = { email ->
            viewModel.resendConfirmationEmail(resources, email)
        },
        onGoEmailSignIn = { viewModel.goToEmailSignIn() },
        onGoEmailRegister = { viewModel.goToEmailRegister() },
        onDismissInfo = { viewModel.dismissInfo() }
    )
}
