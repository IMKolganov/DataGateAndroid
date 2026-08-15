package com.imkolganov.datagate.vpn.xray

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.imkolganov.datagate.logger.VpnDebugLogger
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject

/**
 * Thin wrapper around official [LibXray] (XTLS/libXray AAR).
 *
 * Release API (v26.7.28): `apiVersion` 0|1; methods include
 * `convertShareLinksToXrayJson`, `runXrayFromJson`, `stopXray`.
 */
object XrayCoreFacade {

    private const val TAG = "XrayCore"
    private const val API_VERSION = 1

    @Volatile
    private var loaded: Boolean? = null

    fun isAvailable(): Boolean {
        loaded?.let { return it }
        return try {
            LibXray.touch()
            true
        } catch (t: Throwable) {
            VpnDebugLogger.w(TAG, "libXray unavailable: ${t.message}")
            false
        }.also { loaded = it }
    }

    fun versionOrNull(): String? {
        if (!isAvailable()) return null
        return runCatching {
            val resp = invoke("xrayVersion", JSONObject())
            resp.optJSONObject("data")?.optString("version")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Converts share links / subscription text to an Xray config object JSON
     * (at least `outbounds`).
     */
    fun convertShareLinksToXrayJson(text: String): String {
        ensureAvailable()
        val payload = JSONObject().put("text", text)
        val resp = invoke("convertShareLinksToXrayJson", payload)
        if (!resp.optBoolean("success")) {
            error(resp.optString("error", "convertShareLinksToXrayJson failed"))
        }
        val data = resp.opt("data")
            ?: error("convertShareLinksToXrayJson returned empty data")
        return data.toString()
    }

    fun registerProtect(service: VpnService) {
        ensureAvailable()
        val controller = DialerController { fd ->
            runCatching { service.protect(fd.toInt()) }.getOrDefault(false)
        }
        LibXray.registerDialerController(controller)
        LibXray.registerListenerController(controller)
        runCatching {
            LibXray.setDNS(controller, "1.1.1.1:53")
        }.onFailure { VpnDebugLogger.w(TAG, "setDNS failed", it) }
    }

    fun runFromJson(configJson: String) {
        ensureAvailable()
        val payload = JSONObject().put("configJSON", configJson)
        val resp = invoke("runXrayFromJson", payload)
        if (!resp.optBoolean("success")) {
            error(resp.optString("error", "runXrayFromJson failed"))
        }
    }

    fun stop() {
        if (!isAvailable()) return
        runCatching {
            val resp = invoke("stopXray", JSONObject())
            if (!resp.optBoolean("success")) {
                VpnDebugLogger.w(TAG, "stopXray: ${resp.optString("error")}")
            }
        }
        runCatching { LibXray.resetDNS() }
    }

    fun isRunning(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            val resp = invoke("getXrayState", JSONObject())
            resp.optBoolean("success") && resp.optJSONObject("data")?.optBoolean("running") == true
        }.getOrDefault(false)
    }

    /**
     * Parses share text (or raw JSON with outbounds) into a normalized outbounds JSON object string
     * suitable for persistence / [XrayConfigBuilder.buildTunClientConfig].
     */
    fun normalizeToOutboundsConfig(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            if (obj.has("outbounds") || obj.has("OutboundConfigs")) {
                return JSONObject().put("outbounds", XrayConfigBuilder.extractOutbounds(trimmed)).toString()
            }
        }
        val share = XrayConfigBuilder.extractShareLink(trimmed) ?: trimmed
        val converted = convertShareLinksToXrayJson(share)
        return JSONObject().put("outbounds", XrayConfigBuilder.extractOutbounds(converted)).toString()
    }

    private fun ensureAvailable() {
        check(isAvailable()) { "libXray native library is not available on this device" }
    }

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", API_VERSION)
            .put("method", method)
            .put("payload", payload)
            .toString()
        VpnDebugLogger.d(TAG, "invoke method=$method")
        val raw = LibXray.invoke(request)
        return JSONObject(raw)
    }
}

/** Convenience: close TUN after stop. */
fun ParcelFileDescriptor?.safeClose() {
    runCatching { this?.close() }
}
