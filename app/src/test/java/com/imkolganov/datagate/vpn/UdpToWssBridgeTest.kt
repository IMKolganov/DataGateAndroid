package com.imkolganov.datagate.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpToWssBridgeTest {

    @Test
    fun frameDatagramForWs_prefixesBigEndianLength() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val framed = UdpToWssBridge.frameDatagramForWs(payload, payload.size)

        assertEquals(5, framed.size)
        assertEquals(0, framed[0].toInt())
        assertEquals(3, framed[1].toInt())
        assertArrayEquals(payload, framed.copyOfRange(2, 5))
    }

    @Test
    fun frameDatagramForWs_clampsLengthToPayloadSize() {
        val payload = byteArrayOf(0x0A, 0x0B)
        val framed = UdpToWssBridge.frameDatagramForWs(payload, 99)

        assertEquals(4, framed.size)
        assertEquals(2, framed[1].toInt())
    }

    @Test
    fun unframeWsPayloadsToDatagrams_decodesSingleDatagram() {
        val payload = byteArrayOf(0x64, 0x65, 0x66)
        val framed = UdpToWssBridge.frameDatagramForWs(payload, payload.size)

        val datagrams = UdpToWssBridge.unframeWsPayloadsToDatagrams(framed)

        assertEquals(1, datagrams.size)
        assertArrayEquals(payload, datagrams[0])
    }

    @Test
    fun unframeWsPayloadsToDatagrams_decodesMultipleConcatenatedDatagrams() {
        val first = byteArrayOf(0x01)
        val second = byteArrayOf(0x02, 0x03)
        val framed = UdpToWssBridge.frameDatagramForWs(first, first.size) +
            UdpToWssBridge.frameDatagramForWs(second, second.size)

        val datagrams = UdpToWssBridge.unframeWsPayloadsToDatagrams(framed)

        assertEquals(2, datagrams.size)
        assertArrayEquals(first, datagrams[0])
        assertArrayEquals(second, datagrams[1])
    }

    @Test
    fun unframeWsPayloadsToDatagrams_stopsOnInvalidLength() {
        val invalid = byteArrayOf(0x00, 0x05, 0x01, 0x02)
        val datagrams = UdpToWssBridge.unframeWsPayloadsToDatagrams(invalid)
        assertTrue(datagrams.isEmpty())
    }

    @Test
    fun frameAndUnframe_roundTrip() {
        val payload = "openvpn-payload".encodeToByteArray()
        val framed = UdpToWssBridge.frameDatagramForWs(payload, payload.size)
        val restored = UdpToWssBridge.unframeWsPayloadsToDatagrams(framed).single()
        assertArrayEquals(payload, restored)
    }
}
