package com.imkolganov.datagate.ui.screens.access

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imkolganov.datagate.util.userFriendlyApiError
import com.imkolganov.datagate.vpn.ServerSelectionMode
import com.imkolganov.datagate.vpn.VpnServerSelectionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

open class AccessViewModel(
    private val repo: AccessRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(
        AccessContract.UiState(
            // No init-refresh: AppRoot.onUserSessionReady() is the single cold-start load.
            isLoading = true,
            serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
            selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext)
        )
    )
    val state: StateFlow<AccessContract.UiState> = _state

    private val refreshGeneration = AtomicInteger(0)
    private var refreshJob: Job? = null

    fun onEvent(event: AccessContract.UiEvent) {
        when (event) {
            AccessContract.UiEvent.Refresh -> refresh()

            is AccessContract.UiEvent.SetServerSelectionMode -> {
                VpnServerSelectionStore.setMode(appContext, event.mode)
                _state.update { prev ->
                    var next = prev.copy(serverSelectionMode = event.mode)
                    if (event.mode == ServerSelectionMode.MANUAL) {
                        val id = AccessServerSelectionPolicy.resolveSelectedServerId(
                            mode = ServerSelectionMode.MANUAL,
                            previousSelectedId = next.selectedServerId,
                            servers = next.servers,
                        )
                        VpnServerSelectionStore.setSelectedServerId(appContext, id)
                        next = next.copy(selectedServerId = id)
                    }
                    next
                }
            }

            is AccessContract.UiEvent.SelectServer -> {
                val allowedId = AccessServerSelectionPolicy.selectableServerId(
                    serverId = event.serverId,
                    servers = _state.value.servers,
                ) ?: return
                VpnServerSelectionStore.setSelectedServerId(appContext, allowedId)
                _state.update { it.copy(selectedServerId = allowedId) }
            }

            AccessContract.UiEvent.ClearError ->
                _state.update { it.copy(errorText = null) }
        }
    }

    /**
     * After [VpnServerSelectionStore.clear] on logout: drop in-memory mode/selection/servers and
     * invalidate any in-flight refresh so a later login cannot apply the previous account's data.
     */
    fun resetSessionLocalState() {
        refreshJob?.cancel()
        refreshJob = null
        refreshGeneration.incrementAndGet()
        _state.value = AccessContract.UiState(
            isLoading = false,
            serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
            selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext),
        )
    }

    /** Fresh login (or re-entry): reload servers for the current token/quota plan. */
    fun onUserSessionReady() {
        _state.update { prev ->
            prev.copy(
                serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
                selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext),
            )
        }
        refresh()
    }

    private fun refresh() {
        val generation = refreshGeneration.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorText = null) }

            try {
                val servers = repo.getServers()
                ensureActive()
                if (generation != refreshGeneration.get()) return@launch

                val connections = repo.getMyActiveConnections()
                ensureActive()
                if (generation != refreshGeneration.get()) return@launch

                val quotaRaw = repo.loadQuotaUi()
                ensureActive()
                if (generation != refreshGeneration.get()) return@launch

                val res = appContext.resources
                val quota = quotaRaw.copy(
                    errorText = quotaRaw.errorText?.let { res.userFriendlyApiError(it) }
                )

                if (generation != refreshGeneration.get()) return@launch

                _state.update { prev ->
                    val selectedId = AccessServerSelectionPolicy.resolveSelectedServerId(
                        mode = prev.serverSelectionMode,
                        previousSelectedId = prev.selectedServerId,
                        servers = servers,
                    )
                    VpnServerSelectionStore.setSelectedServerId(appContext, selectedId)

                    prev.copy(
                        isLoading = false,
                        servers = servers,
                        activeConnections = connections,
                        quota = quota,
                        selectedServerId = selectedId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != refreshGeneration.get()) return@launch
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorText = appContext.resources.userFriendlyApiError(e)
                    )
                }
            }
        }
    }
}
