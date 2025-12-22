package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.auth.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class GoogleReLoginAuthenticator(
    private val tokenStore: TokenStore,
    private val idTokenProvider: GoogleIdTokenProvider,
    private val backendAuthApi: BackendAuthApi
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

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

                val idToken = idTokenProvider.getIdTokenOrNull()
                if (idToken.isNullOrBlank()) {
                    tokenStore.clear()
                    return@runBlocking null
                }

                val login = try {
                    backendAuthApi.googleLogin(GoogleLoginRequestDto(idToken))
                } catch (t: Throwable) {
                    android.util.Log.e("Auth", "googleLogin failed", t)
                    tokenStore.clear()
                    return@runBlocking null
                }

                tokenStore.saveAccessToken(login.token)

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${login.token}")
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
