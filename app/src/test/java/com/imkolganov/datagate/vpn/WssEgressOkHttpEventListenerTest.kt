package com.imkolganov.datagate.vpn

import okhttp3.Call
import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.net.Proxy as JavaProxy
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.SocketFactory

class WssEgressOkHttpEventListenerTest {

    @Test
    fun factory_assignsUniqueCallIds() {
        val a = WssEgressOkHttpEventListener.allocateCallIdForTest()
        val b = WssEgressOkHttpEventListener.allocateCallIdForTest()
        assertNotEquals(a, b)
        assertTrue(b > a)
    }

    @Test
    fun connectStart_incrementsAttempt_onSameListener() {
        val lines = CopyOnWriteArrayList<String>()
        val listener = WssEgressOkHttpEventListener.forTest(callId = 42L, log = { lines += it })
        val call = unusedCall()
        val addr1 = InetSocketAddress("127.0.0.1", 443)
        val addr2 = InetSocketAddress("127.0.0.1", 8443)

        listener.connectStart(call, addr1, JavaProxy.NO_PROXY)
        listener.connectFailed(call, addr1, JavaProxy.NO_PROXY, null, IOException("timeout"))
        listener.connectStart(call, addr2, JavaProxy.NO_PROXY)
        listener.connectEnd(call, addr2, JavaProxy.NO_PROXY, Protocol.HTTP_1_1)

        assertTrue(lines.any { it.contains("call.id=42 attempt=1 event=connect_start") })
        assertTrue(lines.any { it.contains("call.id=42 attempt=1 event=connect_failed") })
        assertTrue(lines.any { it.contains("call.id=42 attempt=2 event=connect_start") })
        assertTrue(lines.any { it.contains("call.id=42 attempt=2 event=connect_end") })
    }

    private fun unusedCall(): Call =
        Proxy.newProxyInstance(
            Call::class.java.classLoader,
            arrayOf(Call::class.java),
        ) { _, _, _ -> null } as Call
}

class ProtectingSocketFactoryDiagRegressionTest {

    @Test
    fun createSocket_stillProtectsOnce_beforeConnected_andLogsSocketId() {
        val logs = CopyOnWriteArrayList<String>()
        var protectCalls = 0
        val factory = ProtectingSocketFactory(
            delegate = SocketFactory.getDefault(),
            protect = {
                protectCalls++
                assertFalse(it.isConnected)
                true
            },
            log = { logs += it },
        )

        val socket = factory.createSocket()
        try {
            assertFalse(socket.isConnected)
            assertEquals(1, protectCalls)
            assertTrue(logs.single().contains("socket.id=1"))
            assertTrue(logs.single().contains("phase=before_connect"))
            assertTrue(logs.single().contains("result=true"))
            assertTrue(logs.single().contains("connected=false"))
            assertTrue(logs.single().contains("socket.role=wss_egress"))
        } finally {
            socket.close()
        }
    }
}
