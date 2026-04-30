package com.imkolganov.datagate.auth

import android.app.Activity
import android.util.Log
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: BackendAuthApi,
    private val tokenStore: TokenStore,
    private val autoLoginStore: AutoLoginStore
) {
    private companion object {
        const val TAG = "Auth"
    }

    fun isLoggedIn(): Boolean = !tokenStore.getRefreshToken().isNullOrBlank()

    fun logout() {
        Log.d(TAG, "Logout requested. Before clear token=${tokenStore.getAccessToken()?.take(12)}")
        tokenStore.clear()
        autoLoginStore.setEnabled(false)
        Log.d(TAG, "Logout done. After clear token=${tokenStore.getAccessToken()}")
    }

    suspend fun loginWithGoogle(activity: Activity): String {
        Log.d(TAG, "Google credential request started")
        val idToken = GoogleCredentialManager.getGoogleIdTokenOrThrow(activity)
        Log.d(TAG, "Google ID token received; backend login request started")

        val result = try {
            withContext(Dispatchers.IO) {
                api.googleLogin(GoogleLoginRequestDto(idToken))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Backend google-login failed", t)
            throw t
        }
        Log.d(TAG, "Backend google-login succeeded")

        tokenStore.saveAccessToken(result.token)
        tokenStore.saveAccessTokenExpiration(result.expiration)

        if (result.refreshToken.isNullOrBlank()) {
            tokenStore.clear()
            autoLoginStore.setEnabled(false)
            throw IllegalStateException("Missing refresh token in login response.")
        }

        tokenStore.saveRefreshToken(result.refreshToken)
        tokenStore.saveRefreshTokenExpiration(result.refreshExpiration)

        autoLoginStore.setEnabled(true)
        return result.token
    }
}
