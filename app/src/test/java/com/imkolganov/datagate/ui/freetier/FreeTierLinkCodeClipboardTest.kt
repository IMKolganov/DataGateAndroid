package com.imkolganov.datagate.ui.freetier

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FreeTierLinkCodeClipboardTest {

    @Test
    fun copyLinkCodeToClipboard_putsPlainTextOnClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        copyLinkCodeToClipboard(context, "ABCD2345")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        requireNotNull(clip)
        assertEquals(1, clip.itemCount)
        assertEquals("ABCD2345", clip.getItemAt(0).text)
        assertEquals("datagate-link-code", clip.description.label)
    }
}
