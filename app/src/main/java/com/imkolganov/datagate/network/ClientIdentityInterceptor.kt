package com.imkolganov.datagate.network

import android.util.Log
import com.imkolganov.datagate.configs.AuthConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `X-Client-Name` / `X-Client-Version` to every request to the DataGateMonitor backend and
 * reports client-identity rejections (HTTP 400 with `data.code` = `client_*`).
 *
 * Requests to other hosts (VPN node WSS, GitHub releases, IP lists, …) pass through untouched,
 * so one interceptor can sit on every shared [okhttp3.OkHttpClient].
 */
class ClientIdentityInterceptor(
    private val backendBaseUrl: () -> String = { AuthConfig.BACKEND_BASE_URL },
    private val clientVersion: () -> String = { ClientIdentity.clientVersion },
    private val onRejected: (String) -> Unit = ClientIdentityEvents::notifyOutdated,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!ClientIdentity.isBackendUrl(request.url, backendBaseUrl())) {
            return chain.proceed(request)
        }

        val response = chain.proceed(
            request.newBuilder()
                .header(ClientIdentity.NAME_HEADER, ClientIdentity.CLIENT_NAME)
                .header(ClientIdentity.VERSION_HEADER, clientVersion())
                .build()
        )

        if (response.code == 400) {
            val code = ClientIdentity.parseRejectionCode(
                runCatching { response.peekBody(MAX_PEEK_BYTES).string() }.getOrNull()
            )
            if (code != null) {
                Log.w(TAG, "Backend rejected client identity ($code) for ${request.url.encodedPath}")
                onRejected(code)
            }
        }
        return response
    }

    private companion object {
        private const val TAG = "ClientIdentity"
        private const val MAX_PEEK_BYTES = 16L * 1024
    }
}
