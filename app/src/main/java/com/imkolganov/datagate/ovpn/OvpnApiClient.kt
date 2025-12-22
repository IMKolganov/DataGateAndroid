import android.util.Base64
import com.imkolganov.datagate.configs.ApiConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class OvpnDownloadResult(
    val fileName: String,
    val content: ByteArray,
    val contentType: String?
)

class OvpnApiClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val tokenProvider: () -> String?
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()



    suspend fun ensureAndDownloadDeviceFile(
        vpnServerId: Int,
        commonName: String,
        externalId: String,
        issuedTo: String
    ): OvpnDownloadResult {
        val first = tryDownload(vpnServerId, commonName)
        if (first != null) return first

        createFileOnServer(vpnServerId, commonName, externalId, issuedTo)

        val second = tryDownload(vpnServerId, commonName)
        if (second != null) return second

        throw IllegalStateException("File still not found after create. vpnServerId=$vpnServerId commonName=$commonName")
    }

    private suspend fun tryDownload(vpnServerId: Int, commonName: String): OvpnDownloadResult? {
        val url = joinUrl(baseUrl, ApiConfig.API_OPEN_VPN_FILES_DOWNLOAD_FILE_BY_CN_PATH)

        val bodyJson = JSONObject()
            .put("vpnServerId", vpnServerId)
            .put("commonName", commonName)
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .applyAuth()
            .build()

        return http.executeSuspending(request).use { resp ->
            when (resp.code) {
                200 -> {
                    val rawText = resp.body?.string().orEmpty()
                    val obj = JSONObject(rawText)
                    val data = obj.getJSONObject("data")

                    val fileName = data.getJSONObject("issuedOvpn").optString("fileName", "client.ovpn")
                    val contentBase64 = data.getString("content")

                    val decodedBytes = Base64.decode(contentBase64, Base64.DEFAULT)
                    val ct = resp.header("Content-Type")

                    OvpnDownloadResult(
                        fileName = fileName,
                        content = decodedBytes,
                        contentType = ct
                    )
                }
                404 -> null
                400 -> {
                    val err = resp.body?.string().orEmpty()
                    if (isNotFoundApiMessage(err)) null
                    else throw IOException("Download failed: ${resp.code} ${resp.message}. Body=$err")
                }
                else -> {
                    val err = resp.body?.string().orEmpty()
                    throw IOException("Download failed: ${resp.code} ${resp.message}. Body=$err")
                }
            }
        }
    }

    private fun isNotFoundApiMessage(body: String): Boolean {
        if (body.isBlank()) return false

        return try {
            val obj = JSONObject(body)
            val success = obj.optBoolean("success", true)
            val msg = obj.optString("message", "")
            !success && msg.contains("not found", ignoreCase = true)
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun createFileOnServer(
        vpnServerId: Int,
        commonName: String,
        externalId: String,
        issuedTo: String
    ) {
        val url = joinUrl(baseUrl, ApiConfig.API_OPEN_VPN_FILES_ADD_WITH_TOKEN_PATH)

        val bodyJson = JSONObject()
            .put("vpnServerId", vpnServerId)
            .put("commonName", commonName)
            .put("externalId", externalId)
            .put("issuedTo", issuedTo)
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .applyAuth()
            .build()

        http.executeSuspending(request).use { resp ->
            if (resp.code !in 200..299) {
                val err = resp.body?.string().orEmpty()
                throw IOException("Create failed: ${resp.code} ${resp.message}. Body=$err")
            }
        }
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            header("Authorization", "Bearer $token")
        }
        return this
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith("/")) base.dropLast(1) else base
        val p = if (path.startsWith("/")) path.drop(1) else path
        return "$b/$p"
    }

    private fun parseFileName(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null

        val fileNameStar = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)

        if (!fileNameStar.isNullOrBlank()) return fileNameStar.trim().trim('"')

        val fileName = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(contentDisposition)?.groupValues?.getOrNull(1)

        return fileName?.trim()?.trim('"')
    }
}
