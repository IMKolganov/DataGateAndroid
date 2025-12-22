package com.imkolganov.datagate.network

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.http.AuthHeaderInterceptor
import com.imkolganov.datagate.auth.http.BackendAuthApi
import com.imkolganov.datagate.auth.http.GoogleIdTokenProvider
import com.imkolganov.datagate.auth.http.GoogleReLoginAuthenticator
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
        idTokenProvider: GoogleIdTokenProvider,
        backendAuthApi: BackendAuthApi
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthHeaderInterceptor(tokenStore))
            .authenticator(
                GoogleReLoginAuthenticator(
                    tokenStore = tokenStore,
                    idTokenProvider = idTokenProvider,
                    backendAuthApi = backendAuthApi
                )
            )
            .build()
}
