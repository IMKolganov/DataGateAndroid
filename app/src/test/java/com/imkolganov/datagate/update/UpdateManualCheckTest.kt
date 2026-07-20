package com.imkolganov.datagate.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class UpdateManualCheckTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            UpdatePreferences.clearCachedNewerRelease(context)
            UpdatePreferences.clearLastCheckTimestamp(context)
            UpdatePromptController.consumeUpdateDialogRequest()
        }
    }

    @Test
    fun checkNow_emptyRepo_returnsNotConfigured() = runBlocking {
        val result = UpdateManualCheck.checkNow(
            context = context,
            http = OkHttpClient(),
            repo = "  ",
        )
        assertEquals(ManualUpdateCheckResult.RepoNotConfigured, result)
        assertEquals(0L, UpdatePreferences.lastCheckEpochMs(context))
    }

    @Test
    fun checkNow_http403_returnsFailed_andDoesNotMarkCheckDone() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("{\"message\":\"API rate limit exceeded\"}"))
            server.start()

            val before = UpdatePreferences.lastCheckEpochMs(context)
            val result = UpdateManualCheck.checkNow(
                context = context,
                http = OkHttpClient(),
                currentVersionName = "1.0.14",
                repo = "owner/repo",
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )

            assertTrue(result is ManualUpdateCheckResult.Failed)
            val msg = (result as ManualUpdateCheckResult.Failed).message
            assertTrue(msg.contains("403") || msg.contains("rate", ignoreCase = true))
            assertEquals(
                "Failed GitHub check must not advance the 6h auto-check timer",
                before,
                UpdatePreferences.lastCheckEpochMs(context),
            )
            assertNull(UpdatePromptController.showUpdateDialog.value)
        }
    }

    @Test
    fun checkNow_http429_returnsFailed_andDoesNotMarkCheckDone() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429))
            server.start()

            val result = UpdateManualCheck.checkNow(
                context = context,
                http = OkHttpClient(),
                currentVersionName = "1.0.14",
                repo = "owner/repo",
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )

            assertTrue(result is ManualUpdateCheckResult.Failed)
            assertTrue((result as ManualUpdateCheckResult.Failed).message.contains("429"))
            assertEquals(0L, UpdatePreferences.lastCheckEpochMs(context))
        }
    }

    @Test
    fun checkNow_newerReleaseWithApk_returnsUpdateAvailable_caches_andRequestsDialog() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "1.0.15",
                      "html_url": "https://github.com/owner/repo/releases/tag/1.0.15",
                      "assets": [
                        {
                          "name": "app-prod-release.apk",
                          "browser_download_url": "https://example.com/app.apk"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            server.start()

            val result = UpdateManualCheck.checkNow(
                context = context,
                http = OkHttpClient(),
                currentVersionName = "1.0.14",
                repo = "owner/repo",
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )

            assertTrue(result is ManualUpdateCheckResult.UpdateAvailable)
            val release = (result as ManualUpdateCheckResult.UpdateAvailable).release
            assertEquals("1.0.15", release.tagName)
            assertEquals("https://example.com/app.apk", release.apkDownloadUrl)
            assertEquals(
                "1.0.15",
                UpdatePreferences.getCachedNewerRelease(context, "1.0.14")?.tagName,
            )
            assertTrue(UpdatePreferences.lastCheckEpochMs(context) > 0L)
            assertEquals("1.0.15", UpdatePromptController.showUpdateDialog.value?.tagName)
            assertEquals("https://example.com/app.apk", UpdatePromptController.showUpdateDialog.value?.apkDownloadUrl)
            UpdatePromptController.consumeUpdateDialogRequest()
        }
    }

    @Test
    fun checkNow_installedAheadOfGithub_returnsAheadOfLatest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "1.0.13",
                      "html_url": "https://github.com/owner/repo/releases/tag/1.0.13",
                      "assets": []
                    }
                    """.trimIndent()
                )
            )
            server.start()

            val ahead = UpdateManualCheck.checkNow(
                context = context,
                http = OkHttpClient(),
                currentVersionName = "1.0.14",
                repo = "owner/repo",
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )
            assertTrue(ahead is ManualUpdateCheckResult.AheadOfLatest)
            assertEquals("1.0.13", (ahead as ManualUpdateCheckResult.AheadOfLatest).latestTag)
            assertEquals("1.0.14", ahead.installedVersion)
            assertNull(UpdatePromptController.showUpdateDialog.value)
            assertTrue(UpdatePreferences.lastCheckEpochMs(context) > 0L)
        }
    }

    @Test
    fun checkNow_equalRelease_returnsUpToDate() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "1.0.14",
                      "html_url": "https://github.com/owner/repo/releases/tag/1.0.14",
                      "assets": []
                    }
                    """.trimIndent()
                )
            )
            server.start()

            val result = UpdateManualCheck.checkNow(
                context = context,
                http = OkHttpClient(),
                currentVersionName = "1.0.14",
                repo = "owner/repo",
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )
            assertTrue(result is ManualUpdateCheckResult.UpToDate)
            assertEquals("1.0.14", (result as ManualUpdateCheckResult.UpToDate).latestTag)
            assertNull(UpdatePromptController.showUpdateDialog.value)
        }
    }

    @Test
    fun checkNow_newerWithoutApk_stillUpdateAvailable_forOpenReleasePage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "v1.0.20",
                      "html_url": "https://github.com/owner/repo/releases/tag/v1.0.20",
                      "assets": []
                    }
                    """.trimIndent()
                )
            )
            server.start()

            val result = UpdateManualCheck.checkNow(
                context = context,
                http = OkHttpClient(),
                currentVersionName = "1.0.14-dev",
                repo = "owner/repo",
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )

            assertTrue(result is ManualUpdateCheckResult.UpdateAvailable)
            val release = (result as ManualUpdateCheckResult.UpdateAvailable).release
            assertEquals(null, release.apkDownloadUrl)
            assertTrue(release.htmlUrl.contains("1.0.20"))
            assertNotNull(UpdatePromptController.showUpdateDialog.value)
            UpdatePromptController.consumeUpdateDialogRequest()
        }
    }
}

class SemanticVersionCompareTest {

    @Test
    fun isRemoteNewer_handlesVPrefixAndDevSuffix() {
        assertTrue(SemanticVersionCompare.isRemoteNewer("v1.0.15", "1.0.14"))
        assertTrue(SemanticVersionCompare.isRemoteNewer("1.0.15", "1.0.14-dev"))
        assertTrue(!SemanticVersionCompare.isRemoteNewer("1.0.13", "1.0.14"))
        assertTrue(!SemanticVersionCompare.isRemoteNewer("1.0.14", "1.0.14"))
    }
}

class GitHubLatestReleaseFetcherTest {

    @Test
    fun formatHttpFailure_rateLimitCodes() {
        assertTrue(GitHubLatestReleaseFetcher.formatHttpFailure(403).contains("403"))
        assertTrue(GitHubLatestReleaseFetcher.formatHttpFailure(429).contains("429"))
        assertEquals("GitHub API HTTP 500", GitHubLatestReleaseFetcher.formatHttpFailure(500))
    }

    @Test
    fun fetchLatest_parsesApkAsset() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "tag_name": "1.0.15",
                      "html_url": "https://github.com/o/r/releases/tag/1.0.15",
                      "assets": [
                        { "name": "notes.txt", "browser_download_url": "https://example.com/notes.txt" },
                        { "name": "DataGate.apk", "browser_download_url": "https://example.com/dg.apk" }
                      ]
                    }
                    """.trimIndent()
                )
            )
            server.start()
            val fetcher = GitHubLatestReleaseFetcher(
                OkHttpClient(),
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )
            val release = fetcher.fetchLatest("o/r").getOrThrow()
            assertEquals("1.0.15", release.tagName)
            assertEquals("https://example.com/dg.apk", release.apkDownloadUrl)
        }
    }

    @Test
    fun fetchLatest_refusesNonSuccess() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.start()
            val fetcher = GitHubLatestReleaseFetcher(
                OkHttpClient(),
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )
            val result = fetcher.fetchLatest("o/r")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
        }
    }

    @Test
    fun fetchLatest_emptyBody_fails() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            server.start()
            val fetcher = GitHubLatestReleaseFetcher(
                OkHttpClient(),
                apiBaseUrl = server.url("/").toString().trimEnd('/'),
            )
            val result = fetcher.fetchLatest("o/r")
            assertTrue(result.isFailure)
        }
    }
}
