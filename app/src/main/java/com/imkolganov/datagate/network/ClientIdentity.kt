package com.imkolganov.datagate.network

import com.imkolganov.datagate.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/**
 * Who is calling the DataGateMonitor backend.
 *
 * Source of truth: NuGet `DataGateMonitor.SharedModels` (1.0.89+),
 * `DataGateMonitor.SharedModels.ClientIdentity.ClientIdentityRules` / `ClientNames`.
 * Change these values together with the backend rules.
 */
object ClientIdentity {
    const val NAME_HEADER = "X-Client-Name"
    const val VERSION_HEADER = "X-Client-Version"
    const val NAME_QUERY = "clientName"
    const val VERSION_QUERY = "clientVersion"
    const val CLIENT_NAME = "android"

    private const val MAX_VERSION_LENGTH = 64
    private const val ERROR_CODE_PREFIX = "client_"

    val clientVersion: String by lazy {
        sanitizeVersion("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    /** Printable ASCII only (0x20–0x7E), 1–64 chars, as the backend requires. */
    fun sanitizeVersion(raw: String): String {
        val printable = raw.filter { it in ' '..'~' }.trim().take(MAX_VERSION_LENGTH).trim()
        return printable.ifEmpty { "0" }
    }

    /** Appends `clientName` / `clientVersion` to a SignalR hub URL (keeps any existing query). */
    fun appendToHubUrl(url: String, version: String = clientVersion): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder()
            .setQueryParameter(NAME_QUERY, CLIENT_NAME)
            .setQueryParameter(VERSION_QUERY, version)
            .build()
            .toString()
    }

    /** True when [url] points at the same scheme/host/port as [backendBaseUrl]. */
    fun isBackendUrl(url: HttpUrl, backendBaseUrl: String): Boolean {
        val base = backendBaseUrl.trim().toHttpUrlOrNull() ?: return false
        return url.scheme == base.scheme && url.host.equals(base.host, ignoreCase = true) && url.port == base.port
    }

    /**
     * Returns the `data.code` (`client_name_missing`, `client_version_invalid`, …) of a backend
     * client-identity rejection, or null when [body] is any other error.
     */
    fun parseRejectionCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val data = runCatching { JSONObject(body) }.getOrNull()
            ?.let { it.optJSONObject("data") ?: it.optJSONObject("Data") }
            ?: return null
        val code = data.optString("code", data.optString("Code", "")).trim()
        return code.takeIf { it.startsWith(ERROR_CODE_PREFIX) }
    }
}

/** Fired when the backend refuses this app build's client identity: the app must be updated. */
object ClientIdentityEvents {
    private val _outdated = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val outdated: SharedFlow<String> = _outdated.asSharedFlow()

    fun notifyOutdated(code: String) {
        _outdated.tryEmit(code)
    }
}
