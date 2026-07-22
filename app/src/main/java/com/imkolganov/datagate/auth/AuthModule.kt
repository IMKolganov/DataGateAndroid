package com.imkolganov.datagate.auth

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.OkHttpBackendAuthApi
import com.imkolganov.datagate.auth.http.SharedPrefsAutoLoginStore
import com.imkolganov.datagate.auth.tv.TvLoginApi
import com.imkolganov.datagate.auth.tv.TvLoginSessionWatcher
import com.imkolganov.datagate.configs.AuthConfig
import okhttp3.OkHttpClient

object AuthModule {

    fun createAuthViewModel(
        owner: ViewModelStoreOwner,
        appContext: Context,
        http: OkHttpClient
    ): AuthViewModel {
        val tokenStore = SharedPrefsTokenStore(appContext)
        val autoLoginStore: AutoLoginStore = SharedPrefsAutoLoginStore(appContext)

        val api: BackendAuthApi = OkHttpBackendAuthApi(
            http = http,
            baseUrl = AuthConfig.BACKEND_BASE_URL
        )
        val tvLoginApi = TvLoginApi(
            http = http,
            baseUrl = AuthConfig.BACKEND_BASE_URL,
        )
        val tvWatcher = TvLoginSessionWatcher(
            api = tvLoginApi,
            baseUrl = AuthConfig.BACKEND_BASE_URL,
        )

        val repo = AuthRepository(api, tokenStore, autoLoginStore, tvLoginApi)
        val factory = AuthViewModelFactory(repo, tvWatcher)
        return ViewModelProvider(owner, factory)[AuthViewModel::class.java]
    }
}
