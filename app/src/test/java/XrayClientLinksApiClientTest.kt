import com.imkolganov.datagate.configs.ApiConfig
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class XrayClientLinksApiClientTest {

    @Test
    fun ensureAndDownloadDeviceFile_parsesShareLinkContent() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val payload = "vless://uuid@host:443?encryption=none#n"
            val downloadBody = JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("issuedOvpn", JSONObject().put("fileName", "link.txt"))
                        .put("content", Base64.getEncoder().encodeToString(payload.toByteArray())),
                )
            server.enqueue(MockResponse().setResponseCode(200).setBody(downloadBody.toString()))

            val client = XrayClientLinksApiClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                tokenProvider = { "tok" },
            )
            val result = client.ensureAndDownloadDeviceFile(
                vpnServerId = 1,
                commonName = "cn",
                externalId = "ext",
                issuedTo = "me",
            )
            assertEquals("link.txt", result.fileName)
            assertEquals(payload, result.content.toString(Charsets.UTF_8))
            val req = server.takeRequest()
            assertTrue(req.path!!.endsWith(ApiConfig.API_XRAY_CLIENT_LINKS_DOWNLOAD_FILE_BY_CN_PATH))
            assertEquals("Bearer tok", req.getHeader("Authorization"))
        }
    }

    @Test
    fun ensureAndDownloadDeviceFile_createsWhenMissing() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"success":false,"message":"Issued OVPN file not found"}"""),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))
            val payload = "vless://a@b:1"
            val downloadBody = JSONObject()
                .put(
                    "data",
                    JSONObject()
                        .put("issuedOvpn", JSONObject().put("fileName", "c.txt"))
                        .put("content", Base64.getEncoder().encodeToString(payload.toByteArray())),
                )
            server.enqueue(MockResponse().setResponseCode(200).setBody(downloadBody.toString()))

            val client = XrayClientLinksApiClient(
                http = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
                tokenProvider = { "tok" },
            )
            val result = client.ensureAndDownloadDeviceFile(2, "cn", "ext", "me")
            assertEquals(payload, result.content.toString(Charsets.UTF_8))
            assertEquals(3, server.requestCount)
            server.takeRequest()
            val add = server.takeRequest()
            assertTrue(add.path!!.endsWith(ApiConfig.API_XRAY_CLIENT_LINKS_ADD_WITH_TOKEN_PATH))
        }
    }
}
