package com.imkolganov.datagate.freetier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramLinksTest {

    @Test
    fun telegramHandleFromRequiredChannel_stripsAtPrefix() {
        assertEquals("DataGateVPNBot", telegramHandleFromRequiredChannel("@DataGateVPNBot"))
    }

    @Test
    fun telegramHandleFromRequiredChannel_returnsNullForBlank() {
        assertNull(telegramHandleFromRequiredChannel("  "))
    }

    @Test
    fun telegramChannelUrl_buildsTMeLinkFromRequiredChannel() {
        assertEquals(
            "https://t.me/DataGateVPNBot",
            telegramChannelUrl("@DataGateVPNBot")
        )
    }

    @Test
    fun telegramChannelUrl_usesDefaultHandleWhenMissing() {
        assertEquals(
            "https://t.me/DataGateVPNBot",
            telegramChannelUrl(null)
        )
    }

    @Test
    fun telegramChannelUrl_usesCustomDefaultHandle() {
        assertEquals(
            "https://t.me/CustomChannel",
            telegramChannelUrl(requiredChannel = null, defaultHandle = "CustomChannel")
        )
    }
}
