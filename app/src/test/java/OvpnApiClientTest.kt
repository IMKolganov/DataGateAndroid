import com.imkolganov.datagate.configs.ApiConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OvpnApiClientTest {

    @Test
    fun ensureAndDownloadDeviceFile_postsExternalIdToAddWithToken() = runBlocking {
        MockWebServer().use { server ->
            server.start()

            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"success":false,"message":"Issued OVPN file not found"}""")
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

            val downloadBody = JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("issuedOvpn", JSONObject().put("fileName", "client.ovpn"))
                        .put("content", Base64.getEncoder().encodeToString("ovpn-content".toByteArray()))
                )
            server.enqueue(MockResponse().setResponseCode(200).setBody(downloadBody.toString()))

            val client = OvpnApiClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                tokenProvider = { "test-jwt" }
            )

            val googleSub = "accounts.google.com:sub-android"
            val commonName = "adg-1-$googleSub-device01"

            client.ensureAndDownloadDeviceFile(
                vpnServerId = 1,
                commonName = commonName,
                externalId = googleSub,
                issuedTo = "datagate android user $googleSub device device01"
            )

            server.takeRequest()
            val addRequest = server.takeRequest()
            assertEquals("POST", addRequest.method)
            assertTrue(addRequest.path!!.endsWith(ApiConfig.API_OPEN_VPN_FILES_ADD_WITH_TOKEN_PATH))

            val addBody = JSONObject(addRequest.body.readUtf8())
            assertEquals(googleSub, addBody.getString("externalId"))
            assertEquals(commonName, addBody.getString("commonName"))
        }
    }

    @Test
    fun ensureAndDownloadDeviceFile_skipsCreateWhenDownloadSucceeds() = runBlocking {
        MockWebServer().use { server ->
            server.start()

            val downloadBody = JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("issuedOvpn", JSONObject().put("fileName", "existing.ovpn"))
                        .put("content", Base64.getEncoder().encodeToString("existing".toByteArray()))
                )
            server.enqueue(MockResponse().setResponseCode(200).setBody(downloadBody.toString()))

            val client = OvpnApiClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                tokenProvider = { null }
            )

            client.ensureAndDownloadDeviceFile(
                vpnServerId = 2,
                commonName = "adg-2-google-sub-dev",
                externalId = "google-sub",
                issuedTo = "issued-to"
            )

            assertEquals(1, server.requestCount)
            val downloadRequest: RecordedRequest = server.takeRequest()
            assertTrue(downloadRequest.path!!.endsWith(ApiConfig.API_OPEN_VPN_FILES_DOWNLOAD_FILE_BY_CN_PATH))
        }
    }
}
