package com.imkolganov.datagate.auth

import android.app.Activity
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.GoogleLoginRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: BackendAuthApi,
    private val tokenStore: TokenStore,
    private val idTokenStore: IdTokenStore,
    private val autoLoginStore: AutoLoginStore
) {
    fun isLoggedIn(): Boolean = !tokenStore.getAccessToken().isNullOrBlank()

    fun logout() {
        android.util.Log.d("Auth", "Logout requested. Before clear token=${tokenStore.getAccessToken()?.take(12)}")
        tokenStore.clear()
        idTokenStore.clear()
        android.util.Log.d("Auth", "Logout done. After clear token=${tokenStore.getAccessToken()}")
    }

    suspend fun loginWithGoogle(activity: Activity): String {
        val idToken = GoogleCredentialManager.getGoogleIdTokenOrThrow(activity)
        idTokenStore.saveIdToken(idToken)

        val result = withContext(Dispatchers.IO) {
            api.googleLogin(GoogleLoginRequestDto(idToken))
        }

        tokenStore.saveAccessToken(result.token)
        autoLoginStore.setEnabled(true)

        return result.token
    }
}
