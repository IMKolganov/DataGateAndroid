package com.imkolganov.datagate.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class ApkUpdateDownloadTest {

    @Test
    fun updateApkFile_isUnderCacheUpdatesDir() {
        val cache = createTempDirectory("datagate-apk-cache").toFile()
        try {
            val apk = ApkUpdateInstaller.updateApkFile(cache)
            val part = ApkUpdateInstaller.updateApkPartFile(cache)
            assertEquals("datagate-update.apk", apk.name)
            assertEquals("datagate-update.apk.part", part.name)
            assertEquals("updates", apk.parentFile?.name)
            assertEquals(cache.canonicalFile, apk.parentFile?.parentFile?.canonicalFile)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun promotePartFile_replacesDestination() {
        val cache = createTempDirectory("datagate-apk-promote").toFile()
        try {
            val dest = ApkUpdateInstaller.updateApkFile(cache)
            val part = ApkUpdateInstaller.updateApkPartFile(cache)
            dest.writeBytes(byteArrayOf(1, 2, 3))
            part.writeBytes(byteArrayOf(9, 8, 7, 6))
            assertTrue(ApkUpdateInstaller.promotePartFile(part, dest))
            assertArrayEquals(byteArrayOf(9, 8, 7, 6), dest.readBytes())
            assertFalse(part.exists())
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
                assertArrayEquals(payload, file.readBytes())
                assertFalse(ApkUpdateInstaller.updateApkPartFile(cache).exists())
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
    fun download_withoutContentLength_isIndeterminateThenFinishesAt100() = runBlocking {
        MockWebServer().use { server ->
            val payload = ByteArray(4_096) { 7 }
            server.enqueue(MockResponse().setChunkedBody(Buffer().write(payload), 512))
            server.start()
            val cache = createTempDirectory("datagate-apk-chunked").toFile()
            try {
                val published = mutableListOf<ApkDownloadProgress>()
                val result = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = server.url("/chunked.apk").toString(),
                    onProgress = { published.add(it) },
                )
                assertTrue(result.isSuccess)
                assertArrayEquals(payload, result.getOrThrow().readBytes())
                assertTrue(published.size >= 2)
                assertEquals(ApkDownloadBar.Indeterminate, ApkDownloadProgressPolicy.bar(published.first()))
                assertEquals(100, published.last().percent)
                assertEquals(payload.size.toLong(), published.last().bytesRead)
            } finally {
                cache.deleteRecursively()
            }
        }
    }

    @Test
    fun writeValidatedApkPart_truncatedStream_failsBeforePromote() {
        val cache = createTempDirectory("datagate-apk-trunc").toFile()
        try {
            val dest = ApkUpdateInstaller.updateApkFile(cache)
            val part = ApkUpdateInstaller.updateApkPartFile(cache)
            val previous = byteArrayOf(11, 22, 33, 44)
            dest.writeBytes(previous)
            val error = runCatching {
                ApkUpdateInstaller.writeValidatedApkPart(
                    input = ByteArray(1_024) { 3 }.inputStream(),
                    partFile = part,
                    contentLength = 8_192,
                )
            }.exceptionOrNull()
            requireNotNull(error)
            assertTrue(error.message.orEmpty().contains("Incomplete"))
            assertTrue(part.exists())
            assertEquals(1_024L, part.length())
            assertArrayEquals(previous, dest.readBytes())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun writeValidatedApkPart_matchingStream_reportsFinishAndWritesPart() {
        val cache = createTempDirectory("datagate-apk-stream").toFile()
        try {
            val part = ApkUpdateInstaller.updateApkPartFile(cache)
            val payload = ByteArray(2_500) { 5 }
            val published = mutableListOf<ApkDownloadProgress>()
            val bytes = ApkUpdateInstaller.writeValidatedApkPart(
                input = payload.inputStream(),
                partFile = part,
                contentLength = payload.size.toLong(),
                onProgress = { published.add(it) },
            )
            assertEquals(payload.size.toLong(), bytes)
            assertArrayEquals(payload, part.readBytes())
            assertEquals(0, published.first().percent)
            assertEquals(100, published.last().percent)
        } finally {
            cache.deleteRecursively()
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
                assertFalse(ApkUpdateInstaller.updateApkFile(cache).exists())
                assertFalse(ApkUpdateInstaller.updateApkPartFile(cache).exists())
            } finally {
                cache.deleteRecursively()
            }
        }
    }

    @Test
    fun download_httpError_doesNotDeletePreviousApk() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
            server.start()
            val cache = createTempDirectory("datagate-apk-keep").toFile()
            try {
                val dest = ApkUpdateInstaller.updateApkFile(cache)
                dest.writeBytes(byteArrayOf(5, 6, 7))
                val result = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = server.url("/missing.apk").toString(),
                )
                assertTrue(result.isFailure)
                assertArrayEquals(byteArrayOf(5, 6, 7), dest.readBytes())
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
                assertFalse(ApkUpdateInstaller.updateApkFile(cache).exists())
                assertFalse(ApkUpdateInstaller.updateApkPartFile(cache).exists())
            } finally {
                cache.deleteRecursively()
            }
        }
    }

    @Test
    fun download_replacesPreviousSuccessfulApk() = runBlocking {
        MockWebServer().use { server ->
            val first = byteArrayOf(1, 2, 3, 4)
            val second = ByteArray(2_048) { 9 }
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Length", first.size.toString())
                    .setBody(Buffer().write(first)),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Length", second.size.toString())
                    .setBody(Buffer().write(second)),
            )
            server.start()
            val cache = createTempDirectory("datagate-apk-replace").toFile()
            try {
                val url = server.url("/DataGate.apk").toString()
                val firstResult = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = url,
                )
                assertArrayEquals(first, firstResult.getOrThrow().readBytes())
                val secondResult = ApkUpdateInstaller.downloadApkToCache(
                    cacheDir = cache,
                    http = OkHttpClient(),
                    downloadUrl = url,
                )
                assertArrayEquals(second, secondResult.getOrThrow().readBytes())
            } finally {
                cache.deleteRecursively()
            }
        }
    }
}
