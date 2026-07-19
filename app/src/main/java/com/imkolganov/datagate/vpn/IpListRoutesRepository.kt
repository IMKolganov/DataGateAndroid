package com.imkolganov.datagate.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class IpListUpdateResult(
    val routeCount: Int,
    val priorityRouteCount: Int = 0,
    val reachedRouteLimit: Boolean,
    val usedFallback: Boolean,
    val error: String?
)

/** General (broad country list) and priority (curated "must always bypass") routes, kept separate
 *  so priority entries can survive truncation regardless of how the general list sorts. */
data class IpListConnectionRoutes(
    val generalRoutes: List<IpCidrRoute>,
    val priorityRoutes: List<IpCidrRoute>,
)

class IpListRoutesRepository(
    private val appContext: Context,
    private val http: OkHttpClient
) {
    suspend fun getRoutesForConnection(): IpListConnectionRoutes {
        val settings = IpListPreferences.getSettings(appContext)
        if (!settings.cidrListsEnabled) {
            Log.d("OpenVPN3", "CIDR IP lists disabled in settings; no bypass routes")
            return IpListConnectionRoutes(emptyList(), emptyList())
        }

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
        val priorityRoutes = loadPriorityRoutes(settings.priorityUrls)

        if (resolvedContent.isNullOrBlank()) {
            IpListPreferences.savePriorityRouteCount(appContext, priorityRoutes.size)
            return IpListConnectionRoutes(emptyList(), priorityRoutes)
        }

        val result = IpListRouteConfig.parseCidrRoutesResult(resolvedContent)
        IpListPreferences.saveStatus(
            appContext,
            result.routes.size,
            result.reachedRouteLimit,
            priorityRouteCount = priorityRoutes.size
        )
        Log.d(
            "OpenVPN3",
            "IP list routes loaded: ${result.routes.size} general, ${priorityRoutes.size} priority"
        )
        return IpListConnectionRoutes(generalRoutes = result.routes, priorityRoutes = priorityRoutes)
    }

    suspend fun updateNow(): IpListUpdateResult {
        val settings = IpListPreferences.getSettings(appContext)
        if (!settings.cidrListsEnabled) {
            return IpListUpdateResult(
                routeCount = 0,
                priorityRouteCount = 0,
                reachedRouteLimit = false,
                usedFallback = false,
                error = null
            )
        }

        val priorityRoutes = loadPriorityRoutes(settings.priorityUrls)
        val priorityCount = priorityRoutes.size

        return fetchConfiguredLists(settings.sourceUrls).fold(
            onSuccess = {
                val result = saveParsedList(it, priorityRouteCount = priorityCount)
                IpListUpdateResult(
                    routeCount = result.routes.size,
                    priorityRouteCount = priorityCount,
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
                IpListPreferences.saveStatus(
                    appContext,
                    result.routes.size,
                    result.reachedRouteLimit,
                    priorityRouteCount = priorityCount
                )
                val message = error.message ?: "IP list fetch failed"
                IpListPreferences.saveLastError(appContext, message)
                IpListUpdateResult(
                    routeCount = result.routes.size,
                    priorityRouteCount = priorityCount,
                    reachedRouteLimit = result.reachedRouteLimit,
                    usedFallback = fallback != null,
                    error = message
                )
            }
        )
    }

    private suspend fun loadPriorityRoutes(priorityUrls: List<String>): List<IpCidrRoute> {
        val priorityContent = fetchConfiguredLists(priorityUrls).fold(
            onSuccess = { it },
            onFailure = { loadPriorityFallbackList() }
        )
        return priorityContent
            ?.let { IpListRouteConfig.parseCidrRoutesResult(it).routes }
            .orEmpty()
    }

    private suspend fun saveParsedList(
        content: String,
        priorityRouteCount: Int? = null
    ): IpListParseResult {
        val result = IpListRouteConfig.parseCidrRoutesResult(content)
        IpListPreferences.saveCachedList(
            context = appContext,
            content = content,
            routeCount = result.routes.size,
            reachedRouteLimit = result.reachedRouteLimit,
            priorityRouteCount = priorityRouteCount
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

    private suspend fun loadPriorityFallbackList(): String? = withContext(Dispatchers.IO) {
        readAsset(PRIORITY_FALLBACK_ASSET)
    }

    private fun readAsset(path: String): String? =
        runCatching {
            appContext.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()

    private companion object {
        const val MAX_BODY_CHARS = 1_000_000
        const val FALLBACK_IPV4_ASSET = "ip_lists/ru_ipv4_fallback.txt"
        const val FALLBACK_IPV6_ASSET = "ip_lists/ru_ipv6_fallback.txt"
        const val PRIORITY_FALLBACK_ASSET = "ip_lists/ru_priority_fallback.txt"
    }
}
