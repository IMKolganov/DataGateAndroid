package com.imkolganov.datagate.ui.screens.connect

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EngineJournalClipboardTest {

    @Test
    fun copy_putsJournalTextOnClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = "2026-01-01 I/Xray CONNECTED"
        assertTrue(copyEngineJournalToClipboard(context, text))

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        requireNotNull(clip)
        assertEquals(1, clip.itemCount)
        assertEquals(text, clip.getItemAt(0).text.toString())
        assertEquals(ENGINE_JOURNAL_CLIP_LABEL, clip.description.label)
    }

    @Test
    fun copy_skipsBlankJournal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(copyEngineJournalToClipboard(context, "  \n"))
    }
}
