package com.imkolganov.datagate.vpn.diag

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

internal data class VpnHttpProbeResult(
    val ok: Boolean,
    val code: Int?,
    val latencyMs: Long,
    val error: String?,
)

internal object VpnHttpProbe {
    const val DEFAULT_HOST = "www.gstatic.com"
    const val DEFAULT_URL = "https://$DEFAULT_HOST/generate_204"

    fun isSuccessStatus(code: Int): Boolean = code == 204 || code == 200

    fun run(
        url: String = DEFAULT_URL,
        timeoutMs: Int = 5_000,
        open: (URL) -> URLConnection = { it.openConnection() },
    ): VpnHttpProbeResult {
        val started = System.nanoTime()
        return try {
            val conn = open(URL(url)) as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.useCaches = false
            try {
                val code = conn.responseCode
                VpnHttpProbeResult(
                    ok = isSuccessStatus(code),
                    code = code,
                    latencyMs = elapsedMs(started),
                    error = if (isSuccessStatus(code)) null else "http_$code",
                )
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            VpnHttpProbeResult(
                ok = false,
                code = null,
                latencyMs = elapsedMs(started),
                error = t.javaClass.simpleName,
            )
        }
    }

    private fun elapsedMs(startedNs: Long): Long =
        ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
}
