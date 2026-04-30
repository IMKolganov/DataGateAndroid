package com.imkolganov.datagate.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class IpListUpdateResult(
    val routeCount: Int,
    val reachedRouteLimit: Boolean,
    val usedFallback: Boolean,
    val error: String?
)

class IpListRoutesRepository(
    private val appContext: Context,
    private val http: OkHttpClient
) {
    suspend fun getRoutesForConnection(): List<IpCidrRoute> {
        val settings = IpListPreferences.getSettings(appContext)

        val content = if (IpListPreferences.shouldRefreshCachedList(appContext, settings)) {
            fetchConfiguredLists(settings.sourceUrls).fold(
                onSuccess = { it.also { saveParsedList(it) } },
                onFailure = {
                    IpListPreferences.saveLastError(appContext, it.message ?: "IP list fetch failed")
                    IpListPreferences.getCachedList(appContext) ?: loadFallbackList()
                }
            )
        } else {
            IpListPreferences.getCachedList(appContext)
        }

        val resolvedContent = content ?: loadFallbackList()
        if (resolvedContent.isNullOrBlank()) return emptyList()

        val result = IpListRouteConfig.parseCidrRoutesResult(resolvedContent)
        IpListPreferences.saveStatus(appContext, result.routes.size, result.reachedRouteLimit)
        val routes = result.routes
        Log.d("OpenVPN3", "IP list routes loaded: ${routes.size}")
        return routes
    }

    suspend fun updateNow(): IpListUpdateResult {
        val settings = IpListPreferences.getSettings(appContext)
        return fetchConfiguredLists(settings.sourceUrls).fold(
            onSuccess = {
                val result = saveParsedList(it)
                IpListUpdateResult(
                    routeCount = result.routes.size,
                    reachedRouteLimit = result.reachedRouteLimit,
                    usedFallback = false,
                    error = null
                )
            },
            onFailure = { error ->
                val fallback = IpListPreferences.getCachedList(appContext) ?: loadFallbackList()
                val result = fallback
                    ?.let { IpListRouteConfig.parseCidrRoutesResult(it) }
                    ?: IpListParseResult(emptyList(), reachedRouteLimit = false)
                IpListPreferences.saveStatus(appContext, result.routes.size, result.reachedRouteLimit)
                val message = error.message ?: "IP list fetch failed"
                IpListPreferences.saveLastError(appContext, message)
                IpListUpdateResult(
                    routeCount = result.routes.size,
                    reachedRouteLimit = result.reachedRouteLimit,
                    usedFallback = fallback != null,
                    error = message
                )
            }
        )
    }

    private suspend fun saveParsedList(content: String): IpListParseResult {
        val result = IpListRouteConfig.parseCidrRoutesResult(content)
        IpListPreferences.saveCachedList(
            context = appContext,
            content = content,
            routeCount = result.routes.size,
            reachedRouteLimit = result.reachedRouteLimit
        )
        return result
    }

    private suspend fun fetchConfiguredLists(urls: List<String>): Result<String> {
        val trimmedUrls = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (trimmedUrls.isEmpty()) {
            return Result.failure(IllegalStateException("No IP list URLs configured"))
        }

        val contents = ArrayList<String>(trimmedUrls.size)
        val errors = ArrayList<String>(trimmedUrls.size)
        for (url in trimmedUrls) {
            fetchList(url).fold(
                onSuccess = { contents.add(it) },
                onFailure = { errors.add("${url}: ${it.message ?: it.javaClass.simpleName}") }
            )
        }

        if (contents.isEmpty()) {
            return Result.failure(IllegalStateException(errors.joinToString("; ")))
        }
        if (errors.isNotEmpty()) {
            Log.w("OpenVPN3", "Some IP lists failed: ${errors.joinToString("; ")}")
        }
        return Result.success(contents.joinToString("\n"))
    }

    private suspend fun fetchList(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = "IP list fetch failed: HTTP ${response.code}"
                    Log.w("OpenVPN3", message)
                    return@withContext Result.failure(IllegalStateException(message))
                }

                val body = response.body.string()
                if (body.length > MAX_BODY_CHARS) {
                    val message = "IP list ignored: too large (${body.length} chars)"
                    Log.w("OpenVPN3", message)
                    return@withContext Result.failure(IllegalStateException(message))
                }

                Result.success(body)
            }
        } catch (t: Throwable) {
            Log.w("OpenVPN3", "IP list fetch failed", t)
            Result.failure(t)
        }
    }

    private suspend fun loadFallbackList(): String? = withContext(Dispatchers.IO) {
        val ipv4 = readAsset(FALLBACK_IPV4_ASSET)
        val ipv6 = readAsset(FALLBACK_IPV6_ASSET)
        listOfNotNull(ipv4, ipv6).joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun readAsset(path: String): String? =
        runCatching {
            appContext.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()

    private companion object {
        const val MAX_BODY_CHARS = 1_000_000
        const val FALLBACK_IPV4_ASSET = "ip_lists/ru_ipv4_fallback.txt"
        const val FALLBACK_IPV6_ASSET = "ip_lists/ru_ipv6_fallback.txt"
    }
}
