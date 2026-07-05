package com.imkolganov.datagate.vpn

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Dedicated non-pooled thread for libovpncli JNI / OpenSSL calls.
 *
 * [kotlinx.coroutines.Dispatchers.IO] workers can exit while OpenSSL still has per-thread
 * state registered, which triggers SIGSEGV in rand_delete_thread_state on Android.
 */
object OvpnNativeThread {
    const val THREAD_NAME = "OvpnNative"

    fun threadFactory(): ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, THREAD_NAME).apply {
            isDaemon = false
        }
    }

    fun createExecutorService(factory: ThreadFactory = threadFactory()): ExecutorService =
        Executors.newSingleThreadExecutor(factory)

    fun dispatcher(executor: ExecutorService = createExecutorService()): CoroutineDispatcher =
        executor.asCoroutineDispatcher()

    fun isNativeThreadName(threadName: String): Boolean =
        threadName == THREAD_NAME || threadName.startsWith("$THREAD_NAME ")

    fun runsOnNativeThread(threadName: String = Thread.currentThread().name): Boolean =
        isNativeThreadName(threadName)
}
