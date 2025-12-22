package com.imkolganov.datagate.ui.screens.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

open class AccessViewModel(
    private val repo: AccessRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccessContract.UiState(isLoading = true))
    val state: StateFlow<AccessContract.UiState> = _state

    init {
        refresh()
    }

    fun onEvent(event: AccessContract.UiEvent) {
        when (event) {
            AccessContract.UiEvent.Refresh -> refresh()
            is AccessContract.UiEvent.SelectServer ->
                _state.update { it.copy(selectedServerId = event.serverId) }

            is AccessContract.UiEvent.ConnectToServer -> {
                // Later: connect flow (call your interactor)
            }

            AccessContract.UiEvent.Disconnect -> {
                // Later: disconnect flow
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

                _state.update {
                    it.copy(
                        isLoading = false,
                        servers = servers,
                        activeConnections = connections
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorText = e.message ?: "Request failed"
                    )
                }
            }
        }
    }
}
