package com.imkolganov.datagate.update

import android.content.Context
import com.imkolganov.datagate.BuildConfig
import okhttp3.OkHttpClient

sealed class ManualUpdateCheckResult {
    data class UpdateAvailable(val release: GitHubLatestRelease) : ManualUpdateCheckResult()
    /** Installed build matches or is the same as GitHub latest. */
    data class UpToDate(val latestTag: String) : ManualUpdateCheckResult()
    /** Sideload / field-test build is newer than GitHub latest. */
    data class AheadOfLatest(val latestTag: String, val installedVersion: String) : ManualUpdateCheckResult()
    data class Failed(val message: String) : ManualUpdateCheckResult()
    data object RepoNotConfigured : ManualUpdateCheckResult()
}

/**
 * User-initiated GitHub release check from Settings. Bypasses the 6h auto throttle and
 * dismissed-tag skip so "Check now" always shows a result. When [UpdateAvailable], also
 * posts [UpdatePromptController] so the install dialog survives leaving Settings mid-check.
 */
object UpdateManualCheck {

    suspend fun checkNow(
        context: Context,
        http: OkHttpClient,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        repo: String = BuildConfig.GITHUB_RELEASES_REPO,
        apiBaseUrl: String = GitHubLatestReleaseFetcher.DEFAULT_API_BASE_URL,
    ): ManualUpdateCheckResult {
        val trimmedRepo = repo.trim()
        if (trimmedRepo.isEmpty()) return ManualUpdateCheckResult.RepoNotConfigured

        val appContext = context.applicationContext
        val result = GitHubLatestReleaseFetcher(http, apiBaseUrl = apiBaseUrl).fetchLatest(trimmedRepo)

        val release = result.getOrElse { error ->
            return ManualUpdateCheckResult.Failed(
                error.message ?: error.javaClass.simpleName
            )
        }

        // Only advance the auto-check timer after a successful fetch.
        UpdatePreferences.markCheckDone(appContext)
        return classifyFetchedRelease(appContext, release, currentVersionName)
    }

    internal suspend fun classifyFetchedRelease(
        context: Context,
        release: GitHubLatestRelease,
        currentVersionName: String,
    ): ManualUpdateCheckResult {
        return when {
            SemanticVersionCompare.isRemoteNewer(release.tagName, currentVersionName) -> {
                UpdatePreferences.saveCachedNewerRelease(context, release)
                // Request dialog here so Settings leaving mid-check cannot cancel the prompt.
                UpdatePromptController.requestUpdateDialog(release)
                ManualUpdateCheckResult.UpdateAvailable(release)
            }
            SemanticVersionCompare.isRemoteNewer(currentVersionName, release.tagName) -> {
                UpdatePreferences.clearCachedNewerRelease(context)
                ManualUpdateCheckResult.AheadOfLatest(
                    latestTag = release.tagName,
                    installedVersion = currentVersionName,
                )
            }
            else -> {
                UpdatePreferences.clearCachedNewerRelease(context)
                ManualUpdateCheckResult.UpToDate(release.tagName)
            }
        }
    }
}
