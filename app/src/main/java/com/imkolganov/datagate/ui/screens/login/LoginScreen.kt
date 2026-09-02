package com.imkolganov.datagate.ui.screens.login

import android.app.Activity
import com.imkolganov.datagate.ui.components.BrandLogo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ContactSupport
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imkolganov.datagate.BuildConfig
import com.imkolganov.datagate.R
import com.imkolganov.datagate.auth.AuthLoginTab
import com.imkolganov.datagate.auth.AuthLoginScreen
import com.imkolganov.datagate.auth.AuthViewModel
import com.imkolganov.datagate.auth.EmailAuthPane
import com.imkolganov.datagate.ui.screens.auth.TotpChallengeScreen
import com.imkolganov.datagate.ui.components.AppCards
import com.imkolganov.datagate.ui.support.ReportIssueDialog
import com.imkolganov.datagate.ui.theme.AppLocale
import com.imkolganov.datagate.ui.tv.LocalIsTelevision
import com.imkolganov.datagate.ui.tv.tvFocusBorder
import java.util.Locale

@Composable
internal fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    visibleLabel: String,
    hiddenLabel: String,
    enabled: Boolean,
    leadingIcon: ImageVector? = Icons.Outlined.Lock,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null
                )
            }
        },
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisible) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) visibleLabel else hiddenLabel
                )
            }
        },
        modifier = modifier.tvFocusBorder(shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null
                )
            }
        },
        modifier = modifier.tvFocusBorder(shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
internal fun AuthActionButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(52.dp)
            .tvFocusBorder(shape = AppCards.shape),
        shape = AppCards.shape
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text)
        }
    }
}

@Composable
private fun LoginModeSwitch(
    selected: AuthLoginTab,
    enabled: Boolean,
    onSelect: (AuthLoginTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LoginModeButton(
                text = stringResource(R.string.auth_tab_google),
                selected = selected == AuthLoginTab.Google,
                enabled = enabled,
                onClick = { onSelect(AuthLoginTab.Google) }
            )
            LoginModeButton(
                text = stringResource(R.string.auth_tab_email),
                selected = selected == AuthLoginTab.Email,
                enabled = enabled,
                onClick = { onSelect(AuthLoginTab.Email) }
            )
        }
    }
}

@Composable
private fun RowScope.LoginModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .tvFocusBorder(shape = shape),
            shape = shape,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center
            )
        }
    } else {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .tvFocusBorder(shape = shape),
            shape = shape
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmailPaneSwitch(
    selected: EmailAuthPane,
    enabled: Boolean,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LoginModeButton(
                text = stringResource(R.string.auth_email_sign_in),
                selected = selected == EmailAuthPane.SignIn,
                enabled = enabled,
                onClick = onSignIn
            )
            LoginModeButton(
                text = stringResource(R.string.auth_email_register),
                selected = selected == EmailAuthPane.Register,
                enabled = enabled,
                onClick = onRegister
            )
        }
    }
}

@Composable
private fun LoginTopActions(
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onReportIssue) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ContactSupport,
                contentDescription = stringResource(R.string.home_report_issue),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LoginBrandBlock(
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val imageSize = if (compact) 116.dp else 124.dp
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            modifier = Modifier.size(imageSize),
            contentAlignment = Alignment.Center
        ) {
            BrandLogo(modifier = Modifier.size(imageSize))
        }
        Text(
            text = stringResource(R.string.login_title),
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start
        )
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.widthIn(max = 380.dp)
        )
    }
}

@Composable
internal fun AuthMessage(
    text: String,
    isError: Boolean,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.auth_dismiss))
                }
            }
        }
    }
}

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
    onGoForgotPassword: () -> Unit,
    onGoResetPassword: () -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onResetPasswordWithCode: (String, String, String) -> Unit,
    onDismissInfo: () -> Unit,
    appLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit,
) {
    val versionLabel = stringResource(
        R.string.login_version,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE
    )
    val scroll = rememberScrollState()

    var signInLogin by rememberSaveable { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }
    var signInPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var regDisplay by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var regLogin by rememberSaveable { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirm by remember { mutableStateOf("") }
    var regPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var regConfirmVisible by rememberSaveable { mutableStateOf(false) }

    var confirmEmail by rememberSaveable { mutableStateOf("") }
    var confirmCode by rememberSaveable { mutableStateOf("") }

    var forgotLoginOrEmail by rememberSaveable { mutableStateOf("") }
    var resetCode by rememberSaveable { mutableStateOf("") }
    var resetNew by remember { mutableStateOf("") }
    var resetConfirm by remember { mutableStateOf("") }
    var resetNewVisible by rememberSaveable { mutableStateOf(false) }
    var resetConfirmVisible by rememberSaveable { mutableStateOf(false) }

    val showPwd = stringResource(R.string.auth_password_show)
    val hidePwd = stringResource(R.string.auth_password_hide)
    val configuration = LocalConfiguration.current
    val uiLocale = configuration.locales[0] ?: Locale.getDefault()
    val isWide = configuration.screenWidthDp >= 760
    val isTelevision = LocalIsTelevision.current
    var showReportDialog by remember { mutableStateOf(false) }

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
                        .fillMaxWidth()
                        .padding(
                            horizontal = when {
                                isTelevision -> 48.dp
                                isWide -> 32.dp
                                else -> 20.dp
                            },
                            vertical = if (isTelevision) 28.dp else 16.dp,
                        )
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(
                        when {
                            isTelevision -> 28.dp
                            isWide -> 24.dp
                            else -> 18.dp
                        }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoginTopActions(
                        onReportIssue = { showReportDialog = true }
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(
                                max = when {
                                    isTelevision -> 560.dp
                                    isWide -> 480.dp
                                    else -> 440.dp
                                }
                            ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LoginBrandBlock(
                                compact = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            LoginModeSwitch(
                                selected = loginTab,
                                enabled = !isLoading,
                                onSelect = onSelectTab
                            )

                            when (loginTab) {
                                AuthLoginTab.Google -> {
                                    AuthActionButton(
                                        text = stringResource(R.string.login_sign_in_google),
                                        isLoading = isLoading,
                                        enabled = true,
                                        onClick = onGoogleSignIn
                                    )
                                }

                                AuthLoginTab.Email -> {
                                    when (emailPane) {
                                        EmailAuthPane.SignIn,
                                        EmailAuthPane.Register -> {
                                            EmailPaneSwitch(
                                                selected = emailPane,
                                                enabled = !isLoading,
                                                onSignIn = onGoEmailSignIn,
                                                onRegister = onGoEmailRegister
                                            )
                                        }

                                        else -> { }
                                    }

                                    when (emailPane) {
                                        EmailAuthPane.SignIn -> {
                                            AuthTextField(
                                                value = signInLogin,
                                                onValueChange = { signInLogin = it },
                                                label = stringResource(R.string.auth_field_login),
                                                enabled = !isLoading,
                                                leadingIcon = Icons.Outlined.AccountCircle
                                            )
                                            AuthPasswordField(
                                                value = signInPassword,
                                                onValueChange = { signInPassword = it },
                                                label = stringResource(R.string.auth_field_password),
                                                passwordVisible = signInPasswordVisible,
                                                onTogglePasswordVisible = {
                                                    signInPasswordVisible = !signInPasswordVisible
                                                },
                                                visibleLabel = hidePwd,
                                                hiddenLabel = showPwd,
                                                enabled = !isLoading
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                TextButton(
                                                    onClick = onGoForgotPassword,
                                                    enabled = !isLoading
                                                ) {
                                                    Text(stringResource(R.string.auth_forgot_password_link))
                                                }
                                                TextButton(
                                                    onClick = onGoResetPassword,
                                                    enabled = !isLoading
                                                ) {
                                                    Text(stringResource(R.string.auth_have_reset_code))
                                                }
                                            }
                                            AuthActionButton(
                                                text = stringResource(R.string.auth_sign_in),
                                                isLoading = isLoading,
                                                enabled = true,
                                                onClick = {
                                                    onEmailSignIn(signInLogin, signInPassword)
                                                }
                                            )
                                        }

                                        EmailAuthPane.Register -> {
                                            AuthTextField(
                                                value = regDisplay,
                                                onValueChange = { regDisplay = it },
                                                label = stringResource(R.string.auth_field_display_name),
                                                enabled = !isLoading,
                                                leadingIcon = Icons.Outlined.Badge
                                            )
                                            AuthTextField(
                                                value = regEmail,
                                                onValueChange = { regEmail = it },
                                                label = stringResource(R.string.auth_field_email),
                                                enabled = !isLoading,
                                                keyboardType = KeyboardType.Email,
                                                leadingIcon = Icons.Outlined.MailOutline
                                            )
                                            AuthTextField(
                                                value = regLogin,
                                                onValueChange = { regLogin = it },
                                                label = stringResource(R.string.auth_field_login),
                                                enabled = !isLoading,
                                                leadingIcon = Icons.Outlined.AccountCircle
                                            )
                                            AuthPasswordField(
                                                value = regPassword,
                                                onValueChange = { regPassword = it },
                                                label = stringResource(R.string.auth_field_password),
                                                passwordVisible = regPasswordVisible,
                                                onTogglePasswordVisible = {
                                                    regPasswordVisible = !regPasswordVisible
                                                },
                                                visibleLabel = hidePwd,
                                                hiddenLabel = showPwd,
                                                enabled = !isLoading
                                            )
                                            AuthPasswordField(
                                                value = regConfirm,
                                                onValueChange = { regConfirm = it },
                                                label = stringResource(R.string.auth_field_confirm_password),
                                                passwordVisible = regConfirmVisible,
                                                onTogglePasswordVisible = {
                                                    regConfirmVisible = !regConfirmVisible
                                                },
                                                visibleLabel = hidePwd,
                                                hiddenLabel = showPwd,
                                                enabled = !isLoading
                                            )
                                            AuthActionButton(
                                                text = stringResource(R.string.auth_create_account),
                                                isLoading = isLoading,
                                                enabled = true,
                                                onClick = {
                                                    onRegister(regDisplay, regEmail, regLogin, regPassword, regConfirm)
                                                }
                                            )
                                        }

                                        EmailAuthPane.ConfirmEmail -> {
                                            Text(
                                                text = stringResource(R.string.auth_confirm_email_help),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            AuthTextField(
                                                value = confirmEmail,
                                                onValueChange = { confirmEmail = it },
                                                label = stringResource(R.string.auth_field_email),
                                                enabled = !isLoading,
                                                keyboardType = KeyboardType.Email,
                                                leadingIcon = Icons.Outlined.MailOutline
                                            )
                                            AuthTextField(
                                                value = confirmCode,
                                                onValueChange = {
                                                    confirmCode = it.filter { ch -> ch.isDigit() }.take(6)
                                                },
                                                label = stringResource(R.string.auth_field_confirmation_code),
                                                enabled = !isLoading,
                                                keyboardType = KeyboardType.Number,
                                                leadingIcon = Icons.Outlined.VerifiedUser
                                            )
                                            AuthActionButton(
                                                text = stringResource(R.string.auth_confirm_email_action),
                                                isLoading = isLoading,
                                                enabled = true,
                                                onClick = { onConfirmEmail(confirmEmail, confirmCode) }
                                            )
                                            OutlinedButton(
                                                onClick = { onResendConfirmation(confirmEmail) },
                                                enabled = !isLoading,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp)
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

                                        EmailAuthPane.ForgotPassword -> {
                                            TextButton(onClick = onGoEmailSignIn, enabled = !isLoading) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.action_back))
                                            }
                                            Text(
                                                text = stringResource(R.string.auth_forgot_password_intro),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            AuthTextField(
                                                value = forgotLoginOrEmail,
                                                onValueChange = { forgotLoginOrEmail = it },
                                                label = stringResource(R.string.auth_field_login_or_email),
                                                enabled = !isLoading,
                                                leadingIcon = Icons.Outlined.AlternateEmail
                                            )
                                            AuthActionButton(
                                                text = stringResource(R.string.auth_forgot_password_submit),
                                                isLoading = isLoading,
                                                enabled = true,
                                                onClick = { onRequestPasswordReset(forgotLoginOrEmail) }
                                            )
                                        }

                                        EmailAuthPane.ResetPassword -> {
                                            TextButton(onClick = onGoEmailSignIn, enabled = !isLoading) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.action_back))
                                            }
                                            Text(
                                                text = stringResource(R.string.auth_reset_password_intro),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            AuthTextField(
                                                value = resetCode,
                                                onValueChange = {
                                                    resetCode = it.filter { ch -> ch.isLetterOrDigit() }
                                                        .uppercase(Locale.US)
                                                        .take(12)
                                                },
                                                label = stringResource(R.string.auth_field_reset_code),
                                                enabled = !isLoading,
                                                leadingIcon = Icons.Outlined.Key
                                            )
                                            AuthPasswordField(
                                                value = resetNew,
                                                onValueChange = { resetNew = it },
                                                label = stringResource(R.string.auth_field_new_password),
                                                passwordVisible = resetNewVisible,
                                                onTogglePasswordVisible = {
                                                    resetNewVisible = !resetNewVisible
                                                },
                                                visibleLabel = hidePwd,
                                                hiddenLabel = showPwd,
                                                enabled = !isLoading
                                            )
                                            AuthPasswordField(
                                                value = resetConfirm,
                                                onValueChange = { resetConfirm = it },
                                                label = stringResource(R.string.auth_field_confirm_password),
                                                passwordVisible = resetConfirmVisible,
                                                onTogglePasswordVisible = {
                                                    resetConfirmVisible = !resetConfirmVisible
                                                },
                                                visibleLabel = hidePwd,
                                                hiddenLabel = showPwd,
                                                enabled = !isLoading
                                            )
                                            AuthActionButton(
                                                text = stringResource(R.string.auth_reset_password_submit),
                                                isLoading = isLoading,
                                                enabled = true,
                                                onClick = {
                                                    onResetPasswordWithCode(resetCode, resetNew, resetConfirm)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (!infoMessage.isNullOrBlank()) {
                                AuthMessage(
                                    text = infoMessage,
                                    isError = false,
                                    onDismiss = onDismissInfo
                                )
                            }

                            if (!errorMessage.isNullOrBlank()) {
                                AuthMessage(
                                    text = errorMessage,
                                    isError = true
                                )
                            }
                        }
                    }

                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                ReportIssueDialog(
                    visible = showReportDialog,
                    onDismiss = { showReportDialog = false }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    appLocale: AppLocale,
    onAppLocaleChange: (AppLocale) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val resources = LocalResources.current
    val isTelevision = LocalIsTelevision.current

    if (isTelevision) {
        TvLinkLoginScreen(viewModel = viewModel)
        return
    }

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
        onGoForgotPassword = { viewModel.goToForgotPassword() },
        onGoResetPassword = { viewModel.goToResetPassword() },
        onRequestPasswordReset = { viewModel.requestPasswordReset(resources, it) },
        onResetPasswordWithCode = { code, a, b ->
            viewModel.resetPasswordWithCode(resources, code, a, b)
        },
        onDismissInfo = { viewModel.dismissInfo() },
        appLocale = appLocale,
        onAppLocaleChange = onAppLocaleChange
    )
}
