package com.imkolganov.datagate.ui.screens.access

import android.content.Context
import androidx.lifecycle.ViewModel
import com.imkolganov.datagate.vpn.ServerSelectionMode
import com.imkolganov.datagate.util.userFriendlyApiError
import com.imkolganov.datagate.vpn.VpnServerSelectionStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

open class AccessViewModel(
    private val repo: AccessRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(
        AccessContract.UiState(
            isLoading = true,
            serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
            selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext)
        )
    )
    val state: StateFlow<AccessContract.UiState> = _state

    init {
        refresh()
    }

    fun onEvent(event: AccessContract.UiEvent) {
        when (event) {
            AccessContract.UiEvent.Refresh -> refresh()

            is AccessContract.UiEvent.SetServerSelectionMode -> {
                VpnServerSelectionStore.setMode(appContext, event.mode)
                _state.update { prev ->
                    var next = prev.copy(serverSelectionMode = event.mode)
                    if (event.mode == ServerSelectionMode.MANUAL) {
                        val id = next.selectedServerId
                            ?: next.servers.firstOrNull { it.isOnline }?.id
                            ?: next.servers.firstOrNull()?.id
                        if (id != null) {
                            VpnServerSelectionStore.setSelectedServerId(appContext, id)
                            next = next.copy(selectedServerId = id)
                        }
                    }
                    next
                }
            }

            is AccessContract.UiEvent.SelectServer -> {
                VpnServerSelectionStore.setSelectedServerId(appContext, event.serverId)
                _state.update { it.copy(selectedServerId = event.serverId) }
            }

            AccessContract.UiEvent.ClearError ->
                _state.update { it.copy(errorText = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorText = null) }

            try {
                val servers = repo.getServers()
                val connections = repo.getMyActiveConnections()
                val quotaRaw = repo.loadQuotaUi()
                val res = appContext.resources
                val quota = quotaRaw.copy(
                    errorText = quotaRaw.errorText?.let { res.userFriendlyApiError(it) }
                )

                _state.update { prev ->
                    var selectedId = prev.selectedServerId
                    if (selectedId != null && servers.none { it.id == selectedId }) {
                        selectedId = null
                    }
                    if (prev.serverSelectionMode == ServerSelectionMode.MANUAL && selectedId == null) {
                        selectedId = servers.firstOrNull { it.isOnline }?.id
                            ?: servers.firstOrNull()?.id
                    }
                    if (selectedId != null) {
                        VpnServerSelectionStore.setSelectedServerId(appContext, selectedId)
                    }

                    prev.copy(
                        isLoading = false,
                        servers = servers,
                        activeConnections = connections,
                        quota = quota,
                        selectedServerId = selectedId
                    )
                }
            } catch (e: Exception) {
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
