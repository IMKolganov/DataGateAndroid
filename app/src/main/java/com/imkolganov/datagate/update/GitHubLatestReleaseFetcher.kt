package com.imkolganov.datagate.update

import com.imkolganov.datagate.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class GitHubLatestRelease(
    val tagName: String,
    val htmlUrl: String,
    /** First .apk asset in the release, if any */
    val apkDownloadUrl: String?
)

/**
 * Fetches [GET /repos/{owner}/{repo}/releases/latest](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).
 * Unauthenticated rate limit: 60 req/h per IP — we throttle checks in [UpdatePreferences].
 *
 * @param apiBaseUrl override for tests (MockWebServer); production uses GitHub.
 */
class GitHubLatestReleaseFetcher(
    private val http: OkHttpClient,
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
) {

    fun fetchLatest(repo: String): Result<GitHubLatestRelease> = runCatching {
        val base = apiBaseUrl.trimEnd('/')
        val url = "$base/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header(
                "User-Agent",
                "DataGate-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            )
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(formatHttpFailure(response.code))
            }
            val body = response.body.string()
            if (body.isBlank()) error("GitHub API returned an empty body")
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val htmlUrl = json.getString("html_url")
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }
            GitHubLatestRelease(
                tagName = tag,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl
            )
        }
    }

    companion object {
        const val DEFAULT_API_BASE_URL = "https://api.github.com"

        fun formatHttpFailure(code: Int): String = when (code) {
            403, 429 -> "GitHub rate limited or refused the request (HTTP $code)"
            404 -> "GitHub release not found (HTTP $code)"
            else -> "GitHub API HTTP $code"
        }
    }
}
