package com.imkolganov.datagate.logger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnDebugLoggerRoutingTest {

    private lateinit var context: Context
    private lateinit var logger: VpnDebugLogger

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EngineJournal.resetForTests()
        VpnDebugLogger.uninstallForTests()
        logger = VpnDebugLogger(context)
        logger.clearLogs()
    }

    @After
    fun tearDown() {
        logger.setEnabled(false)
        logger.clearLogs()
        EngineJournal.resetForTests()
        VpnDebugLogger.uninstallForTests()
    }

    @Test
    fun journalOn_fileOff_writesMemoryNotFile() {
        EngineJournal.setEnabled(true)
        logger.i("OpenVPN3", "CONNECTED")
        EngineJournal.publishNow()
        assertTrue(EngineJournal.text.value.contains("CONNECTED"))
        assertTrue(logger.readTail().isEmpty())
        assertTrue(logger.currentFile().length() == 0L || !logger.currentFile().isFile)
    }

    @Test
    fun fileOn_journalOff_writesFileNotMemory() {
        logger.setEnabled(true)
        logger.i("OpenVPN3", "file-only")
        EngineJournal.publishNow()
        assertTrue(logger.readTail().contains("file-only"))
        assertTrue(EngineJournal.text.value.isEmpty())
    }

    @Test
    fun bothOn_writesMemoryAndFile() {
        EngineJournal.setEnabled(true)
        logger.setEnabled(true)
        logger.w("OpenVPN3", "slow", IllegalStateException("late"))
        EngineJournal.publishNow()
        assertTrue(EngineJournal.text.value.contains("slow"))
        assertTrue(EngineJournal.text.value.contains("IllegalStateException"))
        assertTrue(logger.readTail().contains("slow"))
    }

    @Test
    fun bothOff_writesNeither() {
        logger.e("OpenVPN3", "silent")
        EngineJournal.publishNow()
        assertTrue(EngineJournal.text.value.isEmpty())
        assertTrue(logger.readTail().isEmpty())
    }

    @Test
    fun companionFallback_journalsWhenNoInstanceInstalled() {
        EngineJournal.setEnabled(true)
        VpnDebugLogger.i("Tag", "fallback-info")
        VpnDebugLogger.w("Tag", "fallback-warn", IllegalArgumentException("bad"))
        VpnDebugLogger.e("Tag", "fallback-error")
        VpnDebugLogger.d("Tag", "fallback-debug")
        EngineJournal.publishNow()
        val text = EngineJournal.text.value
        assertTrue(text.contains("fallback-info"))
        assertTrue(text.contains("fallback-warn"))
        assertTrue(text.contains("IllegalArgumentException"))
        assertTrue(text.contains("fallback-error"))
        assertTrue(text.contains("fallback-debug"))
    }

    @Test
    fun companionEvent_withoutInstance_isNoOp() {
        EngineJournal.setEnabled(true)
        VpnDebugLogger.event("vpn", "connected")
        EngineJournal.publishNow()
        assertTrue(EngineJournal.text.value.isEmpty())
    }

    @Test
    fun companion_usesInstalledInstance() {
        VpnDebugLogger.install(logger)
        EngineJournal.setEnabled(true)
        VpnDebugLogger.i("Tag", "via-instance")
        EngineJournal.publishNow()
        assertTrue(EngineJournal.text.value.contains("via-instance"))
        assertTrue(logger.readTail().isEmpty())
    }

}
