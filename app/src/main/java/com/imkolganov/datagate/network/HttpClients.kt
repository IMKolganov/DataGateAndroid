package com.imkolganov.datagate.network

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.http.AuthHeaderInterceptor
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.RefreshTokenAuthenticator
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClients {

    fun createPlain(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    fun createAuth(
        tokenStore: TokenStore,
        backendAuthApi: BackendAuthApi,
        deviceIdProvider: () -> String?,
        userAgentProvider: () -> String?
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthHeaderInterceptor(tokenStore))
            .authenticator(
                RefreshTokenAuthenticator(
                    tokenStore = tokenStore,
                    backendAuthApi = backendAuthApi,
                    deviceIdProvider = deviceIdProvider,
                    userAgentProvider = userAgentProvider
                )
            )
            .build()
}
