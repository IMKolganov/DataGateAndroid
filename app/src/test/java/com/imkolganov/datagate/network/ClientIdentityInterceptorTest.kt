package com.imkolganov.datagate.network

import com.imkolganov.datagate.auth.FakeTokenStore
import com.imkolganov.datagate.auth.RecordingAuthApi
import com.imkolganov.datagate.auth.http.AuthHeaderInterceptor
import com.imkolganov.datagate.auth.http.OkHttpBackendAuthApi
import com.imkolganov.datagate.auth.http.RefreshRejectedException
import com.imkolganov.datagate.auth.http.RefreshTokenAuthenticator
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ClientIdentityInterceptorTest {

    @Test
    fun backendRequest_getsNameAndVersionHeaders() {
        MockWebServer().use { backend ->
            backend.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            backend.start()

            client(backend).newCall(get(backend.url("/api/v3/open-vpn-servers/get-all-with-status").toString()))
                .execute().close()

            val sent = backend.takeRequest()
            assertEquals("android", sent.getHeader(ClientIdentity.NAME_HEADER))
            assertEquals(VERSION, sent.getHeader(ClientIdentity.VERSION_HEADER))
        }
    }

    @Test
    fun otherHost_getsNoHeaders() {
        MockWebServer().use { backend ->
            MockWebServer().use { other ->
                other.enqueue(MockResponse().setResponseCode(200))
                backend.start()
                other.start()

                client(backend).newCall(get(other.url("/ip-lists/ru.txt").toString())).execute().close()

                val sent = other.takeRequest()
                assertNull(sent.getHeader(ClientIdentity.NAME_HEADER))
                assertNull(sent.getHeader(ClientIdentity.VERSION_HEADER))
            }
        }
    }

    @Test
    fun retryAfterRefresh_keepsHeaders() {
        MockWebServer().use { backend ->
            backend.enqueue(MockResponse().setResponseCode(401))
            backend.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            backend.start()

            val store = FakeTokenStore(access = "access-old", refresh = "refresh-old")
            val http = OkHttpClient.Builder()
                .addInterceptor(identity(backend))
                .addInterceptor(AuthHeaderInterceptor(store))
                .authenticator(
                    RefreshTokenAuthenticator(
                        tokenStore = store,
                        backendAuthApi = RecordingAuthApi(),
                        deviceIdProvider = { null },
                        userAgentProvider = { null },
                    )
                )
                .build()

            http.newCall(get(backend.url("/api/v3/open-vpn-servers/get-all-with-status").toString()))
                .execute().use { assertEquals(200, it.code) }

            backend.takeRequest()
            val retried = backend.takeRequest()
            assertEquals("Bearer access", retried.getHeader("Authorization"))
            assertEquals("android", retried.getHeader(ClientIdentity.NAME_HEADER))
            assertEquals(VERSION, retried.getHeader(ClientIdentity.VERSION_HEADER))
        }
    }

    @Test
    fun clientIdentityRejection_isReported_otherBadRequestIsNot() {
        MockWebServer().use { backend ->
            backend.enqueue(MockResponse().setResponseCode(400).setBody(REJECTION_BODY))
            backend.enqueue(MockResponse().setResponseCode(400).setBody("""{"success":false,"message":"bad email"}"""))
            backend.start()
            val reported = mutableListOf<String>()
            val http = OkHttpClient.Builder().addInterceptor(identity(backend, reported::add)).build()

            http.newCall(get(backend.url("/api/auth/login").toString())).execute().use {
                assertEquals(400, it.code)
                // The interceptor only peeks: callers still read the body.
                assertTrue(it.body.string().contains("client_version_invalid"))
            }
            http.newCall(get(backend.url("/api/auth/login").toString())).execute().close()

            assertEquals(listOf("client_version_invalid"), reported)
        }
    }

    @Test
    fun refresh_clientIdentityRejection_isNotATokenRejection() = runBlocking {
        MockWebServer().use { backend ->
            backend.enqueue(MockResponse().setResponseCode(400).setBody(REJECTION_BODY))
            backend.start()
            val api = OkHttpBackendAuthApi(http = OkHttpClient(), baseUrl = backend.url("/").toString())

            try {
                api.refresh(RefreshRequestDto(refreshToken = "r", deviceId = null, userAgent = null))
                fail("expected failure")
            } catch (e: IOException) {
                assertFalse("session must survive a client-identity 400", e is RefreshRejectedException)
            }
        }
    }

    @Test
    fun hubUrl_getsQueryParameters_keepingExistingOnes() {
        val url = ClientIdentity.appendToHubUrl("https://api.example.test/api/hubs/tv-login?sessionId=abc", "1.2.3 (45)")

        assertTrue(url.startsWith("https://api.example.test/api/hubs/tv-login?sessionId=abc&"))
        assertTrue(url.contains("clientName=android"))
        assertTrue(url.contains("clientVersion=1.2.3%20%2845%29"))
    }

    @Test
    fun sanitizeVersion_keepsPrintableAsciiWithinLimit() {
        assertEquals("1.0.19 (19)", ClientIdentity.sanitizeVersion(" 1.0.19 (19)\n"))
        assertEquals("1.0-", ClientIdentity.sanitizeVersion("1.0-é"))
        assertEquals(64, ClientIdentity.sanitizeVersion("9".repeat(100)).length)
        assertEquals("0", ClientIdentity.sanitizeVersion("é"))
    }

    @Test
    fun parseRejectionCode_onlyMatchesClientCodes() {
        assertEquals("client_name_missing", ClientIdentity.parseRejectionCode("""{"data":{"code":"client_name_missing"}}"""))
        assertNull(ClientIdentity.parseRejectionCode("""{"data":{"code":"invalid_token"}}"""))
        assertNull(ClientIdentity.parseRejectionCode("not json"))
        assertNull(ClientIdentity.parseRejectionCode(null))
    }

    private fun identity(backend: MockWebServer, onRejected: (String) -> Unit = {}) =
        ClientIdentityInterceptor(
            backendBaseUrl = { backend.url("/").toString() },
            clientVersion = { VERSION },
            onRejected = onRejected,
        )

    private fun client(backend: MockWebServer) = OkHttpClient.Builder().addInterceptor(identity(backend)).build()

    private fun get(url: String) = Request.Builder().url(url).build()

    private companion object {
        const val VERSION = "1.0.19 (19)"
        const val REJECTION_BODY =
            """{"success":false,"message":"Invalid client version","data":{"code":"client_version_invalid","nameHeader":"X-Client-Name","versionHeader":"X-Client-Version","allowedNames":["android"]}}"""
    }
}
