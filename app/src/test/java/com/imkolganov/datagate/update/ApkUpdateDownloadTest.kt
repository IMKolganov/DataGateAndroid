package com.imkolganov.datagate.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ApkUpdateDownloadTest {

    @Test
    fun updateApkFile_isUnderCacheUpdatesDir() {
        val cache = createTempDirectory("datagate-apk-cache").toFile()
        try {
            val apk = ApkUpdateInstaller.updateApkFile(cache)
            assertEquals("datagate-update.apk", apk.name)
            assertEquals("updates", apk.parentFile?.name)
            assertEquals(cache.canonicalFile, apk.parentFile?.parentFile?.canonicalFile)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun download_reportsPercentsAndWritesBytes() = runBlocking {
        MockWebServer().use { server ->
            val payload = ByteArray(32_768) { index -> (index % 251).toByte() }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload)),
            )
            server.start()
            val cache = createTempDirectory("datagate-apk").toFile()
            try {
                val published = mutableListOf<ApkDownloadProgress>()
                val result = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = server.url("/DataGate.apk").toString(),
                    onProgress = { published.add(it) },
                )
                assertTrue(result.isSuccess)
                val file = result.getOrThrow()
                assertEquals(payload.size.toLong(), file.length())
                assertTrue(published.isNotEmpty())
                assertEquals(0, published.first().percent)
                assertEquals(payload.size.toLong(), published.last().bytesRead)
                assertEquals(100, published.last().percent)
                val percents = published.mapNotNull { it.percent }
                assertTrue(percents.contains(0))
                assertTrue(percents.contains(100))
                assertTrue(percents.zipWithNext().all { (a, b) -> a <= b })
            } finally {
                cache.deleteRecursively()
            }
        }
    }

    @Test
    fun download_httpError_failsWithoutFile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
            server.start()
            val cache = createTempDirectory("datagate-apk-fail").toFile()
            try {
                val result = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = server.url("/missing.apk").toString(),
                )
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("404"))
            } finally {
                cache.deleteRecursively()
            }
        }
    }

    @Test
    fun download_emptyBody_fails() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Length", "0").setBody(""))
            server.start()
            val cache = createTempDirectory("datagate-apk-empty").toFile()
            try {
                val result = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = server.url("/empty.apk").toString(),
                )
                assertTrue(result.isFailure)
            } finally {
                cache.deleteRecursively()
            }
        }
    }
}
