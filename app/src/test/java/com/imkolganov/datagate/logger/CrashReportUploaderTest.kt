package com.imkolganov.datagate.logger

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashReportUploaderTest {

    @Test
    fun uploadBatch_http204_deletesFile() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()

            val dir = createCrashDir()
            val file = writeCrashFile(dir, "fatal_2026-05-01T00-00-00.000Z.txt")
            val uploader = createUploader(dir)

            val result = uploader.uploadBatch(
                endpointUrl = server.url("/api/v1/mobile/crash-ingest").toString(),
                crashReportToken = null
            )

            assertEquals(CrashReportUploader.Outcome.COMPLETED, result.outcome)
            assertFalse(file.exists())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals(file.name, request.getHeader("X-Crash-Filename"))
            assertEquals("com.imkolganov.datagate.dev", request.getHeader("X-Crash-Process"))
            assertNull(request.getHeader("X-Crash-Token"))
        }
    }

    @Test
    fun uploadBatch_http500_keepsFileAndStopsBatch() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()

            val dir = createCrashDir()
            val first = writeCrashFile(dir, "fatal_2026-05-01T00-00-00.000Z.txt")
            val second = writeCrashFile(dir, "fatal_2026-05-01T00-00-01.000Z.txt")
            val uploader = createUploader(dir)

            val result = uploader.uploadBatch(
                endpointUrl = server.url("/api/v1/mobile/crash-ingest").toString(),
                crashReportToken = "token"
            )

            assertEquals(CrashReportUploader.Outcome.RETRY_LATER, result.outcome)
            assertTrue(first.exists())
            assertTrue(second.exists())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun uploadBatch_http429_keepsFileStopsBatchAndExposesRetryAfter() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .addHeader("Retry-After", "120")
            )
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()

            val dir = createCrashDir()
            val first = writeCrashFile(dir, "fatal_2026-05-01T00-00-00.000Z.txt")
            writeCrashFile(dir, "fatal_2026-05-01T00-00-01.000Z.txt")
            val uploader = createUploader(dir)

            val result = uploader.uploadBatch(
                endpointUrl = server.url("/api/v1/mobile/crash-ingest").toString(),
                crashReportToken = ""
            )

            assertEquals(CrashReportUploader.Outcome.RETRY_LATER, result.outcome)
            assertEquals(120L, result.retryAfterSeconds)
            assertTrue(first.exists())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun uploadBatch_http413_deletesBadFileAndContinues() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(413))
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()

            val dir = createCrashDir()
            val first = writeCrashFile(dir, "fatal_2026-05-01T00-00-00.000Z.txt")
            val second = writeCrashFile(dir, "fatal_2026-05-01T00-00-01.000Z.txt")
            val uploader = createUploader(dir)

            val result = uploader.uploadBatch(
                endpointUrl = server.url("/api/v1/mobile/crash-ingest").toString(),
                crashReportToken = null
            )

            assertEquals(CrashReportUploader.Outcome.COMPLETED, result.outcome)
            assertFalse(first.exists())
            assertFalse(second.exists())
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun uploadBatch_networkError_keepsFileAndStopsBatch() {
        val server = MockWebServer()
        server.start()
        val endpointUrl = server.url("/api/v1/mobile/crash-ingest").toString()
        server.shutdown()

        val dir = createCrashDir()
        val first = writeCrashFile(dir, "fatal_2026-05-01T00-00-00.000Z.txt")
        writeCrashFile(dir, "fatal_2026-05-01T00-00-01.000Z.txt")
        val uploader = createUploader(dir)

        val result = uploader.uploadBatch(
            endpointUrl = endpointUrl,
            crashReportToken = null
        )

        assertEquals(CrashReportUploader.Outcome.RETRY_LATER, result.outcome)
        assertTrue(first.exists())
    }

    private fun createUploader(dir: File): CrashReportUploader {
        return CrashReportUploader(
            crashDir = dir,
            httpClient = OkHttpClient(),
            processNameProvider = { "com.imkolganov.datagate.dev" }
        )
    }

    private fun createCrashDir(): File {
        return Files.createTempDirectory("crash-report-test-").toFile()
    }

    private fun writeCrashFile(dir: File, name: String): File {
        val file = File(dir, name)
        file.writeText(
            """
            timestamp_utc=2026-05-01T00-00-00.000Z
            process=com.imkolganov.datagate.dev

            java.lang.IllegalStateException: test
            	at com.example.Main(main.kt:10)
            """.trimIndent()
        )
        return file
    }
}
