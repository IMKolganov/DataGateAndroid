package com.imkolganov.datagate.auth

import android.app.Activity
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: BackendAuthApi,
    private val tokenStore: TokenStore,
    private val autoLoginStore: AutoLoginStore
) {
    fun isLoggedIn(): Boolean = !tokenStore.getRefreshToken().isNullOrBlank()

    fun logout() {
        android.util.Log.d("Auth", "Logout requested. Before clear token=${tokenStore.getAccessToken()?.take(12)}")
        tokenStore.clear()
        autoLoginStore.setEnabled(false)
        android.util.Log.d("Auth", "Logout done. After clear token=${tokenStore.getAccessToken()}")
    }

    suspend fun loginWithGoogle(activity: Activity): String {
        val idToken = GoogleCredentialManager.getGoogleIdTokenOrThrow(activity)

        val result = withContext(Dispatchers.IO) {
            api.googleLogin(GoogleLoginRequestDto(idToken))
        }

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
