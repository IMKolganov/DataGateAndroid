package com.imkolganov.datagate.network

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.http.BackendAuthApi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class HttpClientsTest {

    @Test
    fun authClient_hasOverallCallTimeout() {
        val client = HttpClients.createAuth(
            tokenStore = unused(),
            backendAuthApi = unused(),
            deviceIdProvider = { null },
            userAgentProvider = { null },
        )
        assertEquals(HttpClients.AUTH_CALL_TIMEOUT_SECONDS * 1000, client.callTimeoutMillis.toLong())
    }

    // Building the client never calls these collaborators.
    private inline fun <reified T> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("unexpected call: ${method.name}")
        } as T
}
