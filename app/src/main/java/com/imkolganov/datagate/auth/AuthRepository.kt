package com.imkolganov.datagate.auth

import android.app.Activity
import android.util.Log
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.tv.TvDeviceInfo
import com.imkolganov.datagate.auth.tv.TvLoginApi
import com.imkolganov.datagate.model.auth.ConfirmEmailResultDto
import com.imkolganov.datagate.model.auth.CreateTvLoginSessionResponse
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import com.imkolganov.datagate.model.auth.LoginPasswordRequestDto
import com.imkolganov.datagate.model.auth.LoginResponseDto
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RegisterUserRequestDto
import com.imkolganov.datagate.model.auth.RegisterUserResponseDto
import com.imkolganov.datagate.model.auth.ResetPasswordResultDto
import com.imkolganov.datagate.model.auth.TotpConfirmRequestDto
import com.imkolganov.datagate.model.auth.TotpDisableRequestDto
import com.imkolganov.datagate.model.auth.TotpSetupDto
import com.imkolganov.datagate.model.auth.TotpStatusDto
import com.imkolganov.datagate.model.auth.TotpVerifyLoginRequestDto
import com.imkolganov.datagate.model.auth.TvLoginSessionPollResponse
import com.imkolganov.datagate.model.auth.TvLoginSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class LoginOutcome {
    data class TotpChallenge(
        val loginChallengeId: String,
        val displayName: String?,
    ) : LoginOutcome()

    data class Authenticated(val requiresTotpSetup: Boolean) : LoginOutcome()
}

class AuthRepository(
    private val api: BackendAuthApi,
    private val tokenStore: TokenStore,
    private val autoLoginStore: AutoLoginStore,
    private val tvLoginApi: TvLoginApi? = null,
) {
    private companion object {
        const val TAG = "Auth"
    }

    fun isLoggedIn(): Boolean =
        !tokenStore.getAccessToken().isNullOrBlank() && !tokenStore.getRefreshToken().isNullOrBlank()

    suspend fun tryRestoreSession(): Boolean {
        val refresh = tokenStore.getRefreshToken()
        if (refresh.isNullOrBlank()) return false

        if (!tokenStore.getAccessToken().isNullOrBlank()) {
            return true
        }

        return try {
            val refreshed = withContext(Dispatchers.IO) {
                api.refresh(
                    RefreshRequestDto(
                        refreshToken = refresh,
                        deviceId = null,
                        userAgent = null
                    )
                )
            }
            tokenStore.saveAccessToken(refreshed.token)
            tokenStore.saveAccessTokenExpiration(refreshed.expiration)
            if (!refreshed.refreshToken.isNullOrBlank()) {
                tokenStore.saveRefreshToken(refreshed.refreshToken)
            }
            tokenStore.saveRefreshTokenExpiration(refreshed.refreshExpiration)
            autoLoginStore.setEnabled(true)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Session restore failed, clearing tokens", t)
            tokenStore.clear()
            autoLoginStore.setEnabled(false)
            false
        }
    }

    fun logout() {
        Log.d(TAG, "Logout requested. Before clear token=${tokenStore.getAccessToken()?.take(12)}")
        tokenStore.clear()
        autoLoginStore.setEnabled(false)
        Log.d(TAG, "Logout done. After clear token=${tokenStore.getAccessToken()}")
    }

    suspend fun loginWithGoogle(activity: Activity): LoginOutcome =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Google credential request started")
            val idToken = GoogleCredentialManager.getGoogleIdTokenOrThrow(activity)
            Log.d(TAG, "Google ID token received; backend login request started")
            val result = api.googleLogin(GoogleLoginRequestDto(idToken))
            Log.d(TAG, "Backend google-login succeeded")
            applyLoginResponse(result)
        }

    suspend fun loginWithPassword(login: String, password: String): LoginOutcome =
        withContext(Dispatchers.IO) {
            val result = api.loginWithPassword(LoginPasswordRequestDto(login = login, password = password))
            Log.d(TAG, "Backend password login succeeded")
            applyLoginResponse(result)
        }

    suspend fun verifyTotpLogin(loginChallengeId: String, code: String): LoginOutcome =
        withContext(Dispatchers.IO) {
            val result = api.totpVerifyLogin(
                TotpVerifyLoginRequestDto(loginChallengeId = loginChallengeId, code = code)
            )
            applyLoginResponse(result)
        }

    suspend fun createTvLoginSession(deviceName: String?): CreateTvLoginSessionResponse =
        withContext(Dispatchers.IO) {
            requireTvLoginApi().createSession(
                deviceName = deviceName,
                client = TvDeviceInfo.CLIENT_ANDROID_TV,
            )
        }

    suspend fun pollTvLoginSession(sessionId: String): TvLoginSessionPollResponse =
        withContext(Dispatchers.IO) {
            requireTvLoginApi().getSession(sessionId)
        }

    /**
     * Persists tokens from an approved TV poll response (one-time delivery).
     * Call only when [TvLoginSessionPollResponse.status] is approved and tokens are present.
     */
    fun completeTvLogin(poll: TvLoginSessionPollResponse): LoginOutcome {
        val status = TvLoginSessionStatus.normalize(poll.status)
        if (status != TvLoginSessionStatus.APPROVED) {
            throw IOException("TV login session is not approved (status=$status).")
        }
        return applyLoginResponse(
            LoginResponseDto(
                token = poll.token,
                expiration = poll.expiration,
                refreshToken = poll.refreshToken,
                refreshExpiration = poll.refreshExpiration,
                requiresTotp = poll.requiresTotp,
                loginChallengeId = poll.loginChallengeId,
                displayName = poll.displayName,
                requiresTotpSetup = poll.requiresTotpSetup,
            )
        )
    }

    private fun requireTvLoginApi(): TvLoginApi =
        tvLoginApi ?: throw IllegalStateException("TV login API is not configured.")

    /**
     * Returns true when admin must complete TOTP enrollment before using the app.
     * On status API failure, returns false (same as web RequireAdminTotpSetup).
     */
    suspend fun adminRequiresTotpSetup(): Boolean = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken()
        if (token.isNullOrBlank() || !JwtClaimsReader.isAdmin(token)) {
            return@withContext false
        }
        try {
            val status = api.totpStatus(token)
            status.isAdmin && status.requiresTotpSetup
        } catch (t: Throwable) {
            Log.w(TAG, "TOTP status check failed", t)
            false
        }
    }

    suspend fun fetchTotpStatus(): TotpStatusDto? = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken() ?: return@withContext null
        try {
            api.totpStatus(token)
        } catch (t: Throwable) {
            Log.w(TAG, "fetchTotpStatus failed", t)
            null
        }
    }

    suspend fun beginTotpSetup(): TotpSetupDto = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken()
            ?: throw IllegalStateException("Not signed in.")
        api.totpSetup(token)
    }

    suspend fun confirmTotpSetup(code: String) = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken()
            ?: throw IllegalStateException("Not signed in.")
        api.totpConfirm(token, TotpConfirmRequestDto(code = code))
    }

    suspend fun disableTotp(code: String, password: String?) = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken()
            ?: throw IllegalStateException("Not signed in.")
        api.totpDisable(
            token,
            TotpDisableRequestDto(
                code = code,
                password = password?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }

    suspend fun register(request: RegisterUserRequestDto): RegisterUserResponseDto =
        withContext(Dispatchers.IO) {
            api.register(request)
        }

    suspend fun requestEmailConfirmation(email: String): String =
        withContext(Dispatchers.IO) {
            api.requestEmailConfirmation(email)
        }

    suspend fun confirmEmail(email: String, code: String): ConfirmEmailResultDto =
        withContext(Dispatchers.IO) {
            api.confirmEmail(email, code)
        }

    suspend fun forgotPassword(loginOrEmail: String): String =
        withContext(Dispatchers.IO) {
            api.forgotPassword(loginOrEmail)
        }

    suspend fun resetPasswordWithCode(code: String, newPassword: String, confirmPassword: String): ResetPasswordResultDto =
        withContext(Dispatchers.IO) {
            api.resetPassword(code, newPassword, confirmPassword)
        }

    private fun applyLoginResponse(result: LoginResponseDto): LoginOutcome {
        return when (val flow = resolveLoginFlow(result)) {
            is ResolvedLoginFlow.TotpChallenge -> LoginOutcome.TotpChallenge(
                loginChallengeId = flow.loginChallengeId,
                displayName = flow.displayName,
            )
            is ResolvedLoginFlow.Tokens -> {
                persistSessionOrThrow(flow.response)
                LoginOutcome.Authenticated(requiresTotpSetup = flow.requiresTotpSetup)
            }
        }
    }

    private fun persistSessionOrThrow(result: LoginResponseDto) {
        val token = result.token?.trim().orEmpty()
        val expiration = result.expiration?.trim().orEmpty()
        if (token.isEmpty() || expiration.isEmpty()) {
            throw IllegalStateException("Missing token in login response.")
        }

        tokenStore.saveAccessToken(token)
        tokenStore.saveAccessTokenExpiration(expiration)

        if (result.refreshToken.isNullOrBlank()) {
            tokenStore.clear()
            autoLoginStore.setEnabled(false)
            throw IllegalStateException("Missing refresh token in login response.")
        }

        tokenStore.saveRefreshToken(result.refreshToken)
        tokenStore.saveRefreshTokenExpiration(result.refreshExpiration)

        autoLoginStore.setEnabled(true)
    }
}
