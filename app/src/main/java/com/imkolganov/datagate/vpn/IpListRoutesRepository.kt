package com.imkolganov.datagate.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class IpListRoutesRepository(
    private val appContext: Context,
    private val http: OkHttpClient
) {
    suspend fun getRoutesForConnection(): List<Ipv4CidrRoute> {
        val settings = IpListPreferences.getSettings(appContext)

        val content = if (IpListPreferences.shouldRefreshCachedList(appContext, settings)) {
            fetchList(settings.sourceUrl)
                ?.also { IpListPreferences.saveCachedList(appContext, it) }
                ?: IpListPreferences.getCachedList(appContext)
        } else {
            IpListPreferences.getCachedList(appContext)
        }

        if (content.isNullOrBlank()) return emptyList()

        val routes = IpListRouteConfig.parseIpv4CidrRoutes(content)
        Log.d("OpenVPN3", "IP list routes loaded: ${routes.size}")
        return routes
    }

    private suspend fun fetchList(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("OpenVPN3", "IP list fetch failed: HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body.string()
                if (body.length > MAX_BODY_CHARS) {
                    Log.w("OpenVPN3", "IP list ignored: too large (${body.length} chars)")
                    return@withContext null
                }

                body
            }
        } catch (t: Throwable) {
            Log.w("OpenVPN3", "IP list fetch failed", t)
            null
        }
    }

    private companion object {
        const val MAX_BODY_CHARS = 1_000_000
    }
}
