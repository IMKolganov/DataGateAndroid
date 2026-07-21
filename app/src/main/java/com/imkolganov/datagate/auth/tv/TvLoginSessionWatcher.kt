package com.imkolganov.datagate.auth.tv

import android.util.Log
import com.google.gson.JsonObject
import com.imkolganov.datagate.model.auth.CreateTvLoginSessionResponse
import com.imkolganov.datagate.model.auth.TvLoginSessionPollResponse
import com.imkolganov.datagate.model.auth.TvLoginSessionStatus
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches a TV login session via SignalR (preferred) with HTTP poll fallback.
 *
 * On [TvLoginSessionStatus.APPROVED], performs at most one token GET (or reuses poll payload)
 * and delivers it via [onApproved].
 */
class TvLoginSessionWatcher(
    private val api: TvLoginApi,
    private val baseUrl: String,
) {
    private companion object {
        const val TAG = "TvLoginWatch"
        const val STATUS_EVENT = "TvLoginSessionStatusChanged"
        const val WATCH_METHOD = "WatchSession"
        const val UNWATCH_METHOD = "UnwatchSession"
    }

    fun start(
        scope: CoroutineScope,
        session: CreateTvLoginSessionResponse,
        onStatus: suspend (status: String, expiresAt: String?) -> Unit,
        onApproved: suspend (poll: TvLoginSessionPollResponse) -> Unit,
    ): Job {
        val sessionId = session.sessionId
        val pollSeconds = session.pollIntervalSeconds.coerceAtLeast(1)
        val hubPath = session.signalRHubPath.ifBlank { "/api/hubs/tv-login" }
        val terminal = AtomicBoolean(false)
        val approvedClaimed = AtomicBoolean(false)
        val statusMutex = Mutex()

        suspend fun emitNonApproved(status: String, expiresAt: String?) {
            val normalized = TvLoginSessionStatus.normalize(status)
            if (normalized.isEmpty() || normalized == TvLoginSessionStatus.APPROVED) return
            statusMutex.withLock {
                if (terminal.get()) return
                onStatus(normalized, expiresAt)
                if (TvLoginSessionStatus.isTerminal(normalized)) {
                    terminal.set(true)
                }
            }
        }

        suspend fun claimApproved(pollSnapshot: TvLoginSessionPollResponse?) {
            if (!approvedClaimed.compareAndSet(false, true)) return
            val poll = try {
                when {
                    pollSnapshot != null &&
                        TvLoginSessionStatus.normalize(pollSnapshot.status) ==
                        TvLoginSessionStatus.APPROVED &&
                        !pollSnapshot.token.isNullOrBlank() -> pollSnapshot
                    else -> api.getSession(sessionId)
                }
            } catch (ce: CancellationException) {
                approvedClaimed.set(false)
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to claim approved TV session tokens", t)
                approvedClaimed.set(false)
                statusMutex.withLock {
                    if (terminal.get()) return
                    onStatus(TvLoginSessionStatus.EXPIRED, null)
                    terminal.set(true)
                }
                return
            }
            statusMutex.withLock {
                if (terminal.get()) return
                onStatus(TvLoginSessionStatus.APPROVED, poll.expiresAt)
                onApproved(poll)
                terminal.set(true)
            }
        }

        suspend fun handleRawStatus(
            status: String,
            expiresAt: String?,
            pollSnapshot: TvLoginSessionPollResponse?,
        ) {
            val normalized = TvLoginSessionStatus.normalize(status)
            when (normalized) {
                TvLoginSessionStatus.APPROVED -> claimApproved(pollSnapshot)
                else -> emitNonApproved(normalized, expiresAt)
            }
        }

        return scope.launch {
            val childScope = CoroutineScope(coroutineContext + SupervisorJob())
            try {
                val signalRReady = CompletableDeferred<Boolean>()

                childScope.launch(Dispatchers.IO) {
                    val ok = runSignalRWatch(
                        hubUrl = joinUrl(baseUrl, hubPath),
                        sessionId = sessionId,
                        shouldStop = { terminal.get() },
                        onConnected = { signalRReady.complete(true) },
                        onStatus = { status, expiresAt ->
                            childScope.launch {
                                runCatching { handleRawStatus(status, expiresAt, null) }
                                    .onFailure { Log.w(TAG, "Status handler failed", it) }
                            }
                        },
                    )
                    if (!signalRReady.isCompleted) {
                        signalRReady.complete(ok)
                    }
                }

                childScope.launch {
                    val usingSignalR = try {
                        signalRReady.await()
                    } catch (_: Throwable) {
                        false
                    }
                    if (usingSignalR) {
                        while (isActive && !terminal.get()) {
                            delay(400)
                        }
                        return@launch
                    }

                    Log.i(TAG, "Using HTTP poll fallback for TV session")
                    while (isActive && !terminal.get()) {
                        try {
                            val poll = api.getSession(sessionId)
                            handleRawStatus(poll.status, poll.expiresAt, poll)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Log.w(TAG, "Poll failed for session $sessionId", t)
                        }
                        if (terminal.get() || !isActive) break
                        delay(pollSeconds * 1000L)
                    }
                }

                while (isActive && !terminal.get()) {
                    delay(200)
                }
            } finally {
                childScope.cancel()
            }
        }
    }

    private fun runSignalRWatch(
        hubUrl: String,
        sessionId: String,
        shouldStop: () -> Boolean,
        onConnected: () -> Unit,
        onStatus: (status: String, expiresAt: String?) -> Unit,
    ): Boolean {
        var connection: HubConnection? = null
        var watching = false
        try {
            connection = HubConnectionBuilder.create(hubUrl).build()
            connection.on(STATUS_EVENT, { json: JsonObject ->
                val status = jsonString(json, "status", "Status") ?: return@on
                val expiresAt = jsonString(json, "expiresAt", "ExpiresAt")
                onStatus(status, expiresAt)
            }, JsonObject::class.java)

            connection.start().blockingAwait(20, TimeUnit.SECONDS)
            if (connection.connectionState != HubConnectionState.CONNECTED) {
                Log.w(TAG, "SignalR not connected to $hubUrl")
                return false
            }
            connection.invoke(WATCH_METHOD, sessionId).blockingAwait(15, TimeUnit.SECONDS)
            watching = true
            onConnected()
            Log.d(TAG, "Watching TV session via SignalR")

            while (!shouldStop()) {
                if (connection.connectionState != HubConnectionState.CONNECTED) {
                    Log.w(TAG, "SignalR disconnected")
                    break
                }
                Thread.sleep(400)
            }
            return watching
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "SignalR watch unavailable; using poll fallback", t)
            return false
        } finally {
            val hub = connection
            if (hub != null) {
                if (watching) {
                    runCatching {
                        if (hub.connectionState == HubConnectionState.CONNECTED) {
                            hub.invoke(UNWATCH_METHOD, sessionId)
                                .blockingAwait(3, TimeUnit.SECONDS)
                        }
                    }
                }
                runCatching {
                    hub.stop().blockingAwait(3, TimeUnit.SECONDS)
                }
            }
        }
    }

    private fun jsonString(json: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            if (!json.has(key) || json.get(key).isJsonNull) continue
            val v = runCatching { json.get(key).asString }.getOrNull()?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith("/")) base.dropLast(1) else base
        val p = if (path.startsWith("/")) path.drop(1) else path
        return "$b/$p"
    }
}
