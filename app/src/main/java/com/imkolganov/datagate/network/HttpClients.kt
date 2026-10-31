package com.imkolganov.datagate.network

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.http.AuthHeaderInterceptor
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.RefreshTokenAuthenticator
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClients {

    internal const val AUTH_CALL_TIMEOUT_SECONDS = 45L

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
            // Hard cap for the whole call: DNS lookups and multi-route connect retries are not
            // bounded by connect/read timeouts, so a stalled tunnel could keep a refresh spinning.
            .callTimeout(AUTH_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
