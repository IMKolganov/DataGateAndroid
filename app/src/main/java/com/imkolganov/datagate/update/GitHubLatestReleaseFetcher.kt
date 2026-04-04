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
 */
class GitHubLatestReleaseFetcher(
    private val http: OkHttpClient
) {

    fun fetchLatest(repo: String): Result<GitHubLatestRelease> = runCatching {
        val url = "https://api.github.com/repos/$repo/releases/latest"
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
                error("GitHub API HTTP ${response.code}")
            }
            val body = response.body!!.string()
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
}
