package com.imkolganov.datagate.logger

import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded async file writer for [VpnDebugLogger]. Callers enqueue on the hot path; a single
 * daemon thread performs rotate + append. When the queue is full, **newest** appends are dropped.
 * Control tasks (flush/clear) use a timed offer so they are not dropped under Append pressure
 * without blocking the caller forever.
 */
internal class VpnDebugLogWriter(
    private val dir: File,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vpn-debug-log").apply { isDaemon = true }
    },
) {
    private sealed interface Task {
        data class Append(val text: String) : Task
        data class Flush(val latch: CountDownLatch) : Task
        data class Clear(val latch: CountDownLatch) : Task
    }

    private val queue = ArrayBlockingQueue<Task>(queueCapacity.coerceAtLeast(4))
    private val droppedNewest = AtomicLong(0)
    private val running = AtomicBoolean(true)

    init {
        executor.execute { drainLoop() }
    }

    fun enqueue(text: String) {
        if (!running.get()) return
        if (!queue.offer(Task.Append(text))) {
            droppedNewest.incrementAndGet()
        }
    }

    fun droppedCount(): Long = droppedNewest.get()

    fun flush(timeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        val latch = CountDownLatch(1)
        try {
            if (!queue.offer(Task.Flush(latch), timeoutMs, TimeUnit.MILLISECONDS)) {
                return false
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun clearAndAwait(timeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS): Boolean {
        val latch = CountDownLatch(1)
        try {
            if (!queue.offer(Task.Clear(latch), timeoutMs, TimeUnit.MILLISECONDS)) {
                return false
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun shutdown(timeoutMs: Long = DEFAULT_FLUSH_TIMEOUT_MS) {
        running.set(false)
        flush(timeoutMs)
        executor.shutdownNow()
    }

    private fun drainLoop() {
        while (running.get() || queue.isNotEmpty()) {
            val task = try {
                queue.poll(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } ?: continue

            when (task) {
                is Task.Append -> writeOne(task.text)
                is Task.Flush -> {
                    // Prior appends already drained in order; signal after this point.
                    task.latch.countDown()
                }
                is Task.Clear -> {
                    while (true) {
                        val pending = queue.poll() ?: break
                        if (pending is Task.Append) {
                            writeOne(pending.text)
                        } else if (pending is Task.Flush) {
                            pending.latch.countDown()
                        } else if (pending is Task.Clear) {
                            pending.latch.countDown()
                        }
                    }
                    dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                        ?.forEach { runCatching { it.delete() } }
                    task.latch.countDown()
                }
            }
        }
    }

    private fun writeOne(text: String) {
        runCatching {
            dir.mkdirs()
            val file = File(dir, VpnDebugLogger.CURRENT_FILE)
            if (file.exists() && VpnDebugLogRotation.shouldRotate(file.length())) {
                VpnDebugLogRotation.rotate(dir, file)
            }
            File(dir, VpnDebugLogger.CURRENT_FILE).appendText(text)
        }
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 256
        const val DEFAULT_FLUSH_TIMEOUT_MS = 3_000L
    }
}
