package com.imkolganov.datagate.vpn.traffic

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnTunIfaceCountersReadTest {

    @Test
    fun read_withoutVpnNetwork_returnsNull() {
        assertNull(VpnTunIfaceCounters.read(ApplicationProvider.getApplicationContext()))
    }
}
