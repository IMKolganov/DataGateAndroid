package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnClientCommonNameTest {

    @Test
    fun build_usesAdgPrefixOnPhone() {
        assertEquals(
            "adg-69-105824625148468116460-H5qRftkdSoGTbyofy-3lrA",
            VpnClientCommonName.build(
                isTelevision = false,
                serverId = 69,
                externalId = "105824625148468116460",
                shortInstallationId = "H5qRftkdSoGTbyofy-3lrA",
            ),
        )
    }

    @Test
    fun build_usesAdgtvPrefixOnTelevision() {
        assertEquals(
            "adgtv-69-105824625148468116460-H5qRftkdSoGTbyofy-3lrA",
            VpnClientCommonName.build(
                isTelevision = true,
                serverId = 69,
                externalId = "105824625148468116460",
                shortInstallationId = "H5qRftkdSoGTbyofy-3lrA",
            ),
        )
    }
}
