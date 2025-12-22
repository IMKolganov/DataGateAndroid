package com.imkolganov.datagate.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.GoogleIdTokenProvider
import com.imkolganov.datagate.auth.http.OkHttpBackendAuthApi
import com.imkolganov.datagate.configs.AuthConfig
import okhttp3.OkHttpClient

object AuthModule {

    fun createGoogleIdTokenProvider(activity: Activity, context: Context): GoogleIdTokenProvider {
        val idTokenStore: IdTokenStore = SharedPrefsIdTokenStore(context)
        val autoLoginStore: AutoLoginStore = SharedPrefsAutoLoginStore(context)

        return object : GoogleIdTokenProvider {
            override suspend fun getIdTokenOrNull(): String? {
                if (!autoLoginStore.isEnabled()) return null

                idTokenStore.getIdToken()?.let { return it }

                val silent = GoogleCredentialManager.tryGetGoogleIdTokenSilently(activity)
                if (!silent.isNullOrBlank()) {
                    idTokenStore.saveIdToken(silent)
                    return silent
                }

                return null
            }
        }
    }

    fun createAuthViewModel(
        owner: ViewModelStoreOwner,
        appContext: Context,
        http: OkHttpClient
    ): AuthViewModel {
        val tokenStore = SharedPrefsTokenStore(appContext)
        val idTokenStore = SharedPrefsIdTokenStore(appContext)
        val autoLoginStore: AutoLoginStore = SharedPrefsAutoLoginStore(appContext)

        val api: BackendAuthApi = OkHttpBackendAuthApi(
            http = http,
            baseUrl = AuthConfig.BACKEND_BASE_URL
        )

        val repo = AuthRepository(api, tokenStore, idTokenStore, autoLoginStore)
        val factory = AuthViewModelFactory(repo)
        return ViewModelProvider(owner, factory)[AuthViewModel::class.java]
    }
}
