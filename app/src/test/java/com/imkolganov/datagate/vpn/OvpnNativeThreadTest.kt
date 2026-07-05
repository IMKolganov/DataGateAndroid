package com.imkolganov.datagate.vpn

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OvpnNativeThreadTest {

    @Test
    fun threadFactory_usesDedicatedThreadName() {
        val factory = OvpnNativeThread.threadFactory()
        val thread = factory.newThread { }
        assertEquals(OvpnNativeThread.THREAD_NAME, thread.name)
    }

    @Test
    fun isNativeThreadName_acceptsCoroutineDebugSuffix() {
        assertTrue(OvpnNativeThread.isNativeThreadName("OvpnNative"))
        assertTrue(OvpnNativeThread.isNativeThreadName("OvpnNative @coroutine#1"))
        assertFalse(OvpnNativeThread.isNativeThreadName("DefaultDispatch"))
    }

    @Test
    fun createExecutorService_serializesWorkOnSingleThread() {
        val executor = OvpnNativeThread.createExecutorService()
        try {
            val names = List(3) {
                executor.submit<String> { Thread.currentThread().name }.get(2, TimeUnit.SECONDS)
            }
            assertTrue(names.all { OvpnNativeThread.isNativeThreadName(it) })
            assertEquals(1, names.map { it.substringBefore(" @") }.toSet().size)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun dispatcher_routesCoroutinesToNativeThread() = runBlocking {
        val executor = OvpnNativeThread.createExecutorService()
        try {
            val dispatcher = OvpnNativeThread.dispatcher(executor)
            val threadName = withContext(dispatcher) {
                Thread.currentThread().name
            }
            assertTrue(OvpnNativeThread.isNativeThreadName(threadName))
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun runsOnNativeThread_detectsCurrentThread() {
        val executor = OvpnNativeThread.createExecutorService()
        try {
            val onNative = AtomicReference<Boolean?>(null)
            assertFalse(OvpnNativeThread.runsOnNativeThread())
            executor.submit {
                onNative.set(OvpnNativeThread.runsOnNativeThread())
            }.get(2, TimeUnit.SECONDS)
            assertNotNull(onNative.get())
            assertTrue(onNative.get() == true)
        } finally {
            executor.shutdown()
        }
    }
}
