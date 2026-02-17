package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class RefreshTokenAuthenticator(
    private val tokenStore: TokenStore,
    private val backendAuthApi: BackendAuthApi,
    private val deviceIdProvider: () -> String?,
    private val userAgentProvider: () -> String?
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val path = response.request.url.encodedPath
        if (path.endsWith(ApiConfig.REFRESH_PATH)) return null

        return runBlocking {
            mutex.withLock {
                val currentAccess = tokenStore.getAccessToken()
                val requestAccess = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")
                    ?.trim()

                if (!currentAccess.isNullOrBlank() && currentAccess != requestAccess) {
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccess")
                        .build()
                }

                val refresh = tokenStore.getRefreshToken()
                if (refresh.isNullOrBlank()) {
                    tokenStore.clear()
                    return@runBlocking null
                }

                val refreshed = try {
                    backendAuthApi.refresh(
                        RefreshRequestDto(
                            refreshToken = refresh,
                            deviceId = deviceIdProvider(),
                            userAgent = userAgentProvider()
                        )
                    )
                } catch (t: Throwable) {
                    android.util.Log.e("Auth", "refresh failed", t)
                    tokenStore.clear()
                    return@runBlocking null
                }

                tokenStore.saveAccessToken(refreshed.token)
                tokenStore.saveAccessTokenExpiration(refreshed.expiration)

                if (!refreshed.refreshToken.isNullOrBlank()) {
                    tokenStore.saveRefreshToken(refreshed.refreshToken)
                }
                tokenStore.saveRefreshTokenExpiration(refreshed.refreshExpiration)

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.token}")
                    .build()
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var res: Response? = response
        var count = 1
        while (res?.priorResponse != null) {
            count++
            res = res.priorResponse
        }
        return count
    }
}
