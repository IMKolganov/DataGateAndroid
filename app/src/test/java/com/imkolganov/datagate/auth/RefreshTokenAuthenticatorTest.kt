package com.imkolganov.datagate.auth

import com.imkolganov.datagate.auth.http.OkHttpBackendAuthApi
import com.imkolganov.datagate.auth.http.RefreshRejectedException
import com.imkolganov.datagate.auth.http.RefreshTokenAuthenticator
import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RefreshTokenAuthenticatorTest {

    @Test
    fun refreshRejected_clearsTokensAndSignalsExpiry() = runBlocking {
        val store = FakeTokenStore(access = "access-old", refresh = "refresh-old")
        val api = RecordingAuthApi(refreshError = RefreshRejectedException(401, "refresh failed"))
        var expired = 0
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            SessionEvents.sessionExpired.collect { expired++ }
        }

        val retry = authenticator(store, api).authenticate(null, unauthorized("access-old"))
        yield() // let the collector pick up the buffered event
        collector.cancel()

        assertNull(retry)
        assertNull(store.getAccessToken())
        assertNull(store.getRefreshToken())
        assertEquals(1, expired)
    }

    @Test
    fun refreshNetworkFailure_keepsTokens() {
        val store = FakeTokenStore(access = "access-old", refresh = "refresh-old")
        val api = RecordingAuthApi(refreshError = SocketTimeoutException("timeout"))

        val retry = authenticator(store, api).authenticate(null, unauthorized("access-old"))

        assertNull(retry)
        assertEquals("access-old", store.getAccessToken())
        assertEquals("refresh-old", store.getRefreshToken())
    }

    @Test
    fun refreshSucceeds_retriesWithNewToken() {
        val store = FakeTokenStore(access = "access-old", refresh = "refresh-old")

        val retry = authenticator(store, RecordingAuthApi()).authenticate(null, unauthorized("access-old"))

        assertEquals("Bearer access", retry?.header("Authorization"))
        assertEquals("access", store.getAccessToken())
    }

    @Test
    fun backendRefresh_mapsRejectionCodesOnly() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"success":false}"""))
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            val api = OkHttpBackendAuthApi(http = OkHttpClient(), baseUrl = server.url("/").toString())
            val request = RefreshRequestDto(refreshToken = "r", deviceId = null, userAgent = null)

            try {
                api.refresh(request)
                fail("expected rejection")
            } catch (e: RefreshRejectedException) {
                assertEquals(401, e.code)
            }

            try {
                api.refresh(request)
                fail("expected failure")
            } catch (e: IOException) {
                assertFalse(e is RefreshRejectedException)
            }
            assertTrue(server.takeRequest().path!!.endsWith(ApiConfig.REFRESH_PATH))
        }
    }

    private fun authenticator(store: TokenStore, api: RecordingAuthApi) = RefreshTokenAuthenticator(
        tokenStore = store,
        backendAuthApi = api,
        deviceIdProvider = { null },
        userAgentProvider = { null },
    )

    private fun unauthorized(accessToken: String): Response = Response.Builder()
        .request(
            Request.Builder()
                .url("https://example.test/api/v3/open-vpn-servers/get-all-with-status")
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
        .protocol(Protocol.HTTP_1_1)
        .code(401)
        .message("Unauthorized")
        .build()
}
