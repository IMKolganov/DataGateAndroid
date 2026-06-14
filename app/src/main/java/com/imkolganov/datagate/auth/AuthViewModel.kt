package com.imkolganov.datagate.auth

import android.app.Activity
import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.auth.RegisterUserRequestDto
import com.imkolganov.datagate.model.auth.TotpSetupDto
import com.imkolganov.datagate.model.auth.TotpStatusDto
import com.imkolganov.datagate.util.deepMessageForApiError
import com.imkolganov.datagate.util.userFriendlyApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthLoginTab {
    Google,
    Email
}

enum class EmailAuthPane {
    SignIn,
    Register,
    ConfirmEmail,
    ForgotPassword,
    ResetPassword
}

enum class AuthLoginScreen {
    SignIn,
    TotpChallenge,
}

enum class AdminTotpGate {
    Unknown,
    Allowed,
    SetupRequired,
}

data class TotpChallengeUi(
    val loginChallengeId: String,
    val displayName: String? = null,
    val challengeExpired: Boolean = false,
)

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val loginTab: AuthLoginTab = AuthLoginTab.Google,
    val emailPane: EmailAuthPane = EmailAuthPane.SignIn,
    val loginScreen: AuthLoginScreen = AuthLoginScreen.SignIn,
    val totpChallenge: TotpChallengeUi? = null,
    val adminTotpGate: AdminTotpGate = AdminTotpGate.Unknown,
    /** After registration, user confirms this address. */
    val pendingVerificationEmail: String? = null,
    val totpSetup: TotpSetupDto? = null,
    val totpSetupConfirmLoading: Boolean = false,
    val totpStatus: TotpStatusDto? = null,
)

class AuthViewModel(
    private val repo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(isLoading = true, isLoggedIn = false)
    )
    val state: StateFlow<AuthUiState> = _state

    init {
        viewModelScope.launch {
            val restored = repo.tryRestoreSession()
            val loggedIn = restored || repo.isLoggedIn()
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = loggedIn,
                )
            }
            if (loggedIn) {
                refreshAdminTotpGate()
            } else {
                _state.update { it.copy(adminTotpGate = AdminTotpGate.Allowed) }
            }
        }
    }

    fun refreshAdminTotpGate() {
        viewModelScope.launch {
            if (!_state.value.isLoggedIn) {
                _state.update { it.copy(adminTotpGate = AdminTotpGate.Allowed) }
                return@launch
            }
            _state.update { it.copy(adminTotpGate = AdminTotpGate.Unknown) }
            val required = repo.adminRequiresTotpSetup()
            _state.update {
                it.copy(
                    adminTotpGate = if (required) AdminTotpGate.SetupRequired else AdminTotpGate.Allowed
                )
            }
        }
    }

    private fun handleLoginOutcome(outcome: LoginOutcome) {
        when (outcome) {
            is LoginOutcome.TotpChallenge -> _state.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = false,
                    loginScreen = AuthLoginScreen.TotpChallenge,
                    totpChallenge = TotpChallengeUi(
                        loginChallengeId = outcome.loginChallengeId,
                        displayName = outcome.displayName,
                    ),
                    errorMessage = null,
                )
            }
            is LoginOutcome.Authenticated -> {
                val gate = if (outcome.requiresTotpSetup) {
                    AdminTotpGate.SetupRequired
                } else {
                    AdminTotpGate.Unknown
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        loginScreen = AuthLoginScreen.SignIn,
                        totpChallenge = null,
                        adminTotpGate = gate,
                        errorMessage = null,
                    )
                }
                if (!outcome.requiresTotpSetup) {
                    refreshAdminTotpGate()
                }
            }
        }
    }

    fun selectLoginTab(tab: AuthLoginTab) {
        _state.update {
            it.copy(
                loginTab = tab,
                errorMessage = null,
                infoMessage = null,
                emailPane = if (tab == AuthLoginTab.Google) EmailAuthPane.SignIn else it.emailPane,
                pendingVerificationEmail = if (tab == AuthLoginTab.Google) null else it.pendingVerificationEmail
            )
        }
    }

    fun goToEmailSignIn() {
        _state.update {
            it.copy(
                emailPane = EmailAuthPane.SignIn,
                errorMessage = null,
                pendingVerificationEmail = null
            )
        }
    }

    fun goToEmailRegister() {
        _state.update {
            it.copy(
                emailPane = EmailAuthPane.Register,
                errorMessage = null,
                infoMessage = null,
                pendingVerificationEmail = null
            )
        }
    }

    fun goToForgotPassword() {
        _state.update {
            it.copy(
                emailPane = EmailAuthPane.ForgotPassword,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun goToResetPassword() {
        _state.update {
            it.copy(
                emailPane = EmailAuthPane.ResetPassword,
                errorMessage = null
            )
        }
    }

    fun backFromTotpChallenge() {
        _state.update {
            it.copy(
                loginScreen = AuthLoginScreen.SignIn,
                totpChallenge = null,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun dismissInfo() {
        _state.update { it.copy(infoMessage = null) }
    }

    fun login(activity: Activity) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                handleLoginOutcome(repo.loginWithGoogle(activity))
            } catch (e: Exception) {
                val msg = activity.resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun loginWithPassword(resources: Resources, login: String, password: String) {
        val l = login.trim()
        if (l.isEmpty() || password.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_login_password_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                handleLoginOutcome(repo.loginWithPassword(l, password))
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun verifyTotpLogin(resources: Resources, code: String) {
        val challenge = _state.value.totpChallenge ?: return
        val c = code.trim()
        if (c.length < 6) {
            _state.update {
                it.copy(errorMessage = resources.getString(R.string.totp_error_code_required))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                handleLoginOutcome(repo.verifyTotpLogin(challenge.loginChallengeId, c))
            } catch (e: Exception) {
                val raw = e.deepMessageForApiError()
                val expired = isLoginChallengeExpiredMessage(raw)
                val msg = if (expired) {
                    resources.getString(R.string.totp_challenge_expired)
                } else {
                    resources.userFriendlyApiError(raw)
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = msg,
                        totpChallenge = challenge.copy(challengeExpired = expired),
                    )
                }
            }
        }
    }

    fun beginAdminTotpSetup(resources: Resources) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val setup = repo.beginTotpSetup()
                _state.update {
                    it.copy(isLoading = false, totpSetup = setup, infoMessage = null)
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun cancelAdminTotpSetup() {
        _state.update { it.copy(totpSetup = null, errorMessage = null) }
    }

    fun confirmAdminTotpSetup(resources: Resources, code: String) {
        val c = code.trim()
        if (c.length < 6) {
            _state.update {
                it.copy(errorMessage = resources.getString(R.string.totp_error_code_required))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(totpSetupConfirmLoading = true, errorMessage = null) }
            try {
                repo.confirmTotpSetup(c)
                _state.update {
                    it.copy(
                        totpSetupConfirmLoading = false,
                        totpSetup = null,
                        adminTotpGate = AdminTotpGate.Allowed,
                        infoMessage = resources.getString(R.string.totp_setup_enabled_message),
                    )
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update {
                    it.copy(totpSetupConfirmLoading = false, errorMessage = msg)
                }
            }
        }
    }

    fun loadTotpStatusForSettings() {
        viewModelScope.launch {
            val status = repo.fetchTotpStatus()
            _state.update { it.copy(totpStatus = status) }
        }
    }

    fun disableTotp(resources: Resources, code: String, password: String) {
        val c = code.trim()
        if (c.length < 6) {
            _state.update {
                it.copy(errorMessage = resources.getString(R.string.totp_error_code_required))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                repo.disableTotp(c, password)
                val status = repo.fetchTotpStatus()
                _state.update {
                    it.copy(
                        isLoading = false,
                        totpStatus = status,
                        infoMessage = resources.getString(R.string.totp_disabled_message),
                    )
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun register(
        resources: Resources,
        displayName: String,
        email: String,
        login: String,
        password: String,
        confirmPassword: String
    ) {
        val dn = displayName.trim()
        val em = email.trim()
        val lg = login.trim()
        if (dn.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_display_name_required)) }
            return
        }
        if (em.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_email_required)) }
            return
        }
        if (lg.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_login_required)) }
            return
        }
        if (password.length < 8) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_password_short)) }
            return
        }
        if (password != confirmPassword) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_password_mismatch)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                repo.register(
                    RegisterUserRequestDto(
                        displayName = dn,
                        email = em,
                        login = lg,
                        password = password,
                        confirmPassword = confirmPassword
                    )
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        emailPane = EmailAuthPane.ConfirmEmail,
                        pendingVerificationEmail = em,
                        infoMessage = resources.getString(R.string.auth_register_check_email)
                    )
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun confirmEmail(resources: Resources, email: String, code: String) {
        val em = email.trim()
        val c = code.trim()
        if (em.isEmpty() || c.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_email_code_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = repo.confirmEmail(em, c)
                if (result.success) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            emailPane = EmailAuthPane.SignIn,
                            pendingVerificationEmail = null,
                            infoMessage = resources.getString(R.string.auth_email_confirmed_sign_in)
                        )
                    }
                } else {
                    val msg = result.message.ifBlank {
                        resources.getString(R.string.auth_error_confirm_failed)
                    }
                    _state.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun requestPasswordReset(resources: Resources, loginOrEmail: String) {
        val id = loginOrEmail.trim()
        if (id.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_forgot_login_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val raw = repo.forgotPassword(id)
                val rateLimited = raw.contains("Too many", ignoreCase = true)
                val msg = if (rateLimited) {
                    resources.getString(R.string.auth_forgot_rate_limited)
                } else {
                    resources.getString(R.string.auth_forgot_password_done)
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        emailPane = EmailAuthPane.ResetPassword,
                        infoMessage = msg
                    )
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun resetPasswordWithCode(resources: Resources, code: String, newPassword: String, confirmPassword: String) {
        val c = code.trim()
        if (c.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_reset_code_required)) }
            return
        }
        if (newPassword.length < 8) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_password_short)) }
            return
        }
        if (newPassword != confirmPassword) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_password_mismatch)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = repo.resetPasswordWithCode(c, newPassword, confirmPassword)
                if (result.success) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            emailPane = EmailAuthPane.SignIn,
                            infoMessage = resources.getString(R.string.auth_password_reset_done)
                        )
                    }
                } else {
                    val msg = result.message.ifBlank {
                        resources.getString(R.string.auth_error_reset_failed)
                    }
                    _state.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun resendConfirmationEmail(resources: Resources, email: String) {
        val em = email.trim()
        if (em.isEmpty()) {
            _state.update { it.copy(errorMessage = resources.getString(R.string.auth_error_email_required)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repo.requestEmailConfirmation(em)
                _state.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = resources.getString(R.string.auth_resend_confirmation_hint)
                    )
                }
            } catch (e: Exception) {
                val msg = resources.userFriendlyApiError(e.deepMessageForApiError())
                _state.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun clearAuthMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun logout() {
        repo.logout()
        _state.update {
            AuthUiState(isLoggedIn = false, adminTotpGate = AdminTotpGate.Allowed)
        }
    }
}

class AuthViewModelFactory(
    private val repo: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
