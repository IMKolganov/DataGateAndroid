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
            isServersLoading = true,
            isQuotaLoading = true,
            isTrafficLoading = true,
            serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
            selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext)
        )
    )
    val state: StateFlow<AccessContract.UiState> = _state

    private val serversGeneration = AtomicInteger(0)
    private val quotaGeneration = AtomicInteger(0)
    private var serversJob: Job? = null
    private var quotaJob: Job? = null

    fun onEvent(event: AccessContract.UiEvent) {
        when (event) {
            AccessContract.UiEvent.Refresh -> refresh()
            AccessContract.UiEvent.RefreshServers -> refreshServers(restartIfActive = false)
            AccessContract.UiEvent.RefreshQuota -> refreshQuota(restartIfActive = false)

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
                _state.update {
                    it.copy(
                        serversErrorText = null,
                        quota = it.quota.copy(errorText = null)
                    )
                }
        }
    }

    /**
     * After [VpnServerSelectionStore.clear] on logout: drop in-memory mode/selection/servers and
     * invalidate any in-flight refresh so a later login cannot apply the previous account's data.
     */
    fun resetSessionLocalState() {
        serversJob?.cancel()
        quotaJob?.cancel()
        serversJob = null
        quotaJob = null
        serversGeneration.incrementAndGet()
        quotaGeneration.incrementAndGet()
        _state.value = AccessContract.UiState(
            isServersLoading = false,
            isQuotaLoading = false,
            isTrafficLoading = false,
            serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
            selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext),
        )
    }

    /** Fresh login (or re-entry): reload servers and quota for the current token. */
    fun onUserSessionReady() {
        _state.update { prev ->
            prev.copy(
                serverSelectionMode = VpnServerSelectionStore.getMode(appContext),
                selectedServerId = VpnServerSelectionStore.getSelectedServerId(appContext),
            )
        }
        // Always restart: account/token may have changed; cancel orphans via executeSuspending.
        refreshServers(restartIfActive = true)
        refreshQuota(restartIfActive = true)
    }

    private fun refresh() {
        // Pull-to-refresh while a request is in flight: keep the current job.
        // Cancel+restart used to orphan blocking OkHttp execute() sockets (VPN hang → connect timeout).
        refreshServers(restartIfActive = false)
        refreshQuota(restartIfActive = false)
    }

    private fun refreshServers(restartIfActive: Boolean = true) {
        if (!restartIfActive && serversJob?.isActive == true) return

        val generation = serversGeneration.incrementAndGet()
        serversJob?.cancel()
        serversJob = viewModelScope.launch {
            _state.update { it.copy(isServersLoading = true, serversErrorText = null) }

            try {
                val servers = repo.getServers()
                ensureActive()
                if (generation != serversGeneration.get()) return@launch

                val connections = repo.getMyActiveConnections()
                ensureActive()
                if (generation != serversGeneration.get()) return@launch

                _state.update { prev ->
                    val selectedId = AccessServerSelectionPolicy.resolveSelectedServerId(
                        mode = prev.serverSelectionMode,
                        previousSelectedId = prev.selectedServerId,
                        servers = servers,
                    )
                    VpnServerSelectionStore.setSelectedServerId(appContext, selectedId)

                    prev.copy(
                        isServersLoading = false,
                        servers = servers,
                        activeConnections = connections,
                        selectedServerId = selectedId,
                        serversErrorText = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != serversGeneration.get()) return@launch
                _state.update {
                    it.copy(
                        isServersLoading = false,
                        serversErrorText = appContext.resources.userFriendlyApiError(e)
                    )
                }
            }
        }
    }

    private fun refreshQuota(restartIfActive: Boolean = true) {
        if (!restartIfActive && quotaJob?.isActive == true) return

        val generation = quotaGeneration.incrementAndGet()
        quotaJob?.cancel()
        quotaJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isQuotaLoading = true,
                    isTrafficLoading = true,
                    quota = it.quota.copy(errorText = null),
                )
            }

            try {
                val quotaRaw = repo.loadQuotaPlanUi()
                ensureActive()
                if (generation != quotaGeneration.get()) return@launch

                val res = appContext.resources
                val planQuota = quotaRaw.copy(
                    errorText = quotaRaw.errorText?.let { res.userFriendlyApiError(it) }
                )

                _state.update {
                    it.copy(
                        isQuotaLoading = false,
                        quota = planQuota,
                    )
                }

                val needTraffic = planQuota.errorText == null &&
                    planQuota.quotaLimitBytes > 0L &&
                    !planQuota.trafficUsageNeedsExternalId

                if (!needTraffic) {
                    if (generation != quotaGeneration.get()) return@launch
                    _state.update { it.copy(isTrafficLoading = false) }
                    return@launch
                }

                val used = repo.loadQuotaTrafficUsedBytes(planQuota.quotaPeriodIsMonthly)
                ensureActive()
                if (generation != quotaGeneration.get()) return@launch

                _state.update { prev ->
                    prev.copy(
                        isTrafficLoading = false,
                        quota = prev.quota.copy(trafficUsedBytesForPeriod = used),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != quotaGeneration.get()) return@launch
                _state.update {
                    it.copy(
                        isQuotaLoading = false,
                        isTrafficLoading = false,
                        quota = it.quota.copy(
                            errorText = appContext.resources.userFriendlyApiError(e)
                        ),
                    )
                }
            }
        }
    }
}
