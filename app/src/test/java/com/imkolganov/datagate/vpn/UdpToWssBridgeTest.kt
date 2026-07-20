package com.imkolganov.datagate.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    fun frameDatagramForWs_rejectsLengthBeyondPayload() {
        val payload = byteArrayOf(0x0A, 0x0B)
        try {
            UdpToWssBridge.frameDatagramForWs(payload, 99)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun frameDatagramForWs_encodesMaxU16Payload() {
        val payload = ByteArray(UdpToWssBridge.MAX_U16_PAYLOAD) { 0x7A }
        val framed = UdpToWssBridge.frameDatagramForWs(payload, payload.size)
        assertEquals(2 + UdpToWssBridge.MAX_U16_PAYLOAD, framed.size)
        assertEquals(0xFF, framed[0].toInt() and 0xFF)
        assertEquals(0xFF, framed[1].toInt() and 0xFF)
        assertArrayEquals(payload, framed.copyOfRange(2, framed.size))
    }

    @Test
    fun frameDatagramForWs_rejects65536_doesNotEncodeAsZeroHeader() {
        val payload = ByteArray(65536) { 1 }
        try {
            UdpToWssBridge.frameDatagramForWs(payload, 65536)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("65536"))
        }
    }

    @Test
    fun frameDatagramForWs_emptyPayloadAllowed() {
        val framed = UdpToWssBridge.frameDatagramForWs(ByteArray(0), 0)
        assertEquals(2, framed.size)
        assertEquals(0, framed[0].toInt())
        assertEquals(0, framed[1].toInt())
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
    fun unframeWsPayloadsToDatagrams_stopsOnTrailingPartialHeader() {
        val payload = byteArrayOf(0x01)
        val framed = UdpToWssBridge.frameDatagramForWs(payload, payload.size) + byteArrayOf(0x00)
        val datagrams = UdpToWssBridge.unframeWsPayloadsToDatagrams(framed)
        assertEquals(1, datagrams.size)
        assertArrayEquals(payload, datagrams[0])
    }

    @Test
    fun frameAndUnframe_roundTrip() {
        val payload = "openvpn-payload".encodeToByteArray()
        val framed = UdpToWssBridge.frameDatagramForWs(payload, payload.size)
        val restored = UdpToWssBridge.unframeWsPayloadsToDatagrams(framed).single()
        assertArrayEquals(payload, restored)
    }

    @Test
    fun protectDatagramSocket_logsAndReturnsInjectedResult() {
        DatagramSocket(null).use { ds ->
            ds.bind(InetSocketAddress("127.0.0.1", 0))
            val logs = mutableListOf<String>()
            val ok = UdpToWssBridge.protectDatagramSocket(
                socket = ds,
                protect = { true },
                log = { logs += it },
            )
            assertTrue(ok)
            assertTrue(logs.single().contains("bridge.proto=udp"))
            assertTrue(logs.single().contains("socket.role=local_bridge"))
            assertTrue(logs.single().contains("result=true"))
        }
    }

    @Test
    fun protectDatagramSocket_falseFromCallback() {
        DatagramSocket(null).use { ds ->
            ds.bind(InetSocketAddress("127.0.0.1", 0))
            val ok = UdpToWssBridge.protectDatagramSocket(
                socket = ds,
                protect = { false },
                log = {},
            )
            assertFalse(ok)
        }
    }
}

class WssSendRejectionTest {

    @Test
    fun acceptOrHandleRejection_trueContinuesWithoutNotify() {
        val notified = AtomicBoolean(false)
        var lost = 0
        var closed = 0
        val logs = mutableListOf<String>()
        val cont = WssSendRejection.acceptOrHandleRejection(
            sendAccepted = true,
            phase = "datagram",
            proto = "udp",
            sessionId = 9,
            notified = notified,
            onTransportLost = { lost++ },
            closeLocal = { closed++ },
            log = { logs += it },
        )
        assertTrue(cont)
        assertEquals(0, lost)
        assertEquals(0, closed)
        assertTrue(logs.isEmpty())
        assertFalse(notified.get())
    }

    @Test
    fun acceptOrHandleRejection_falseNotifiesOnce_closes_andLogsPhase() {
        val notified = AtomicBoolean(false)
        val lostReasons = mutableListOf<String>()
        val closed = AtomicInteger(0)
        val logs = mutableListOf<String>()

        fun reject(phase: String): Boolean =
            WssSendRejection.acceptOrHandleRejection(
                sendAccepted = false,
                phase = phase,
                proto = "udp",
                sessionId = 3,
                notified = notified,
                onTransportLost = { lostReasons += it },
                closeLocal = { closed.incrementAndGet() },
                log = { logs += it },
            )

        assertFalse(reject("connect_control"))
        assertFalse(reject("first_datagram"))
        assertFalse(reject("datagram"))

        assertEquals(1, lostReasons.size)
        assertEquals(BridgeTransportLoss.formatSendRejectedReason(), lostReasons.single())
        assertEquals(3, closed.get())
        assertTrue(logs[0].contains("phase=connect_control"))
        assertTrue(logs[0].contains("bridge.proto=udp"))
        assertTrue(logs[0].contains("bridge.session.id=3"))
    }
}

class UdpWssHandshakePolicyTest {

    @Test
    fun timeoutWhileRunningAndOpen_notifies() {
        assertTrue(
            UdpWssHandshakePolicy.shouldNotifyTransportLostOnHandshakeAwaitEnd(
                awaitSucceeded = false,
                running = true,
                datagramSocketClosed = false,
            )
        )
    }

    @Test
    fun timeoutAfterStop_doesNotNotify() {
        assertFalse(
            UdpWssHandshakePolicy.shouldNotifyTransportLostOnHandshakeAwaitEnd(
                awaitSucceeded = false,
                running = false,
                datagramSocketClosed = false,
            )
        )
    }

    @Test
    fun timeoutAfterSocketClosed_doesNotNotify() {
        assertFalse(
            UdpWssHandshakePolicy.shouldNotifyTransportLostOnHandshakeAwaitEnd(
                awaitSucceeded = false,
                running = true,
                datagramSocketClosed = true,
            )
        )
    }

    @Test
    fun awaitSucceeded_doesNotNotify() {
        assertFalse(
            UdpWssHandshakePolicy.shouldNotifyTransportLostOnHandshakeAwaitEnd(
                awaitSucceeded = true,
                running = true,
                datagramSocketClosed = false,
            )
        )
    }
}

class BridgeLogSanitizerTest {

    @Test
    fun line_stripsNewlines() {
        assertEquals("a  b c", BridgeLogSanitizer.line("a\r\nb\nc"))
    }
}
