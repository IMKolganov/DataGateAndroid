package com.imkolganov.datagate.ui.tv

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TelevisionUiTest {

    @Test
    fun isTelevision_falseOnDefaultPhoneConfig() {
        val context: Context = ApplicationProvider.getApplicationContext()
        assertFalse(isTelevision(context))
    }

    @Test
    @Config(qualifiers = "television")
    fun isTelevision_trueOnTelevisionQualifier() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        // Robolectric television qualifier should set TV uiMode; assert helper agrees when set.
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            assertTrue(isTelevision(context))
        } else {
            // Fallback: helper still reads configuration — force via overlay if qualifier unsupported.
            val forced = Configuration(context.resources.configuration).apply {
                this.uiMode = (this.uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                    Configuration.UI_MODE_TYPE_TELEVISION
            }
            context.resources.updateConfiguration(forced, context.resources.displayMetrics)
            assertTrue(isTelevision(context))
        }
    }
}
