package com.imkolganov.datagate.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.overview.Metric
import com.imkolganov.datagate.model.overview.OverviewSeriesResponse
import com.imkolganov.datagate.model.overview.StatsGrouping
import com.imkolganov.datagate.stats.StatsApiClient
import com.imkolganov.datagate.util.userFriendlyApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsFilters(
    val fromIso: String,
    val toIso: String,
    val grouping: StatsGrouping,
    val externalId: String
)

data class StatsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val filters: StatsFilters,
    val metric: Metric = Metric.TrafficTotal,
    val response: OverviewSeriesResponse? = null,
    /** Active quick-range chip; null after a custom date range. */
    val selectedPreset: StatsDatePreset? = StatsDatePreset.Last7Days
)

open class StatsViewModel(
    application: Application,
    private val api: StatsApiClient,
    private val externalIdProvider: () -> String
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        run {
            val (fromIso, toIso) = isoRangeForStatsPreset(StatsDatePreset.Last7Days)
            StatsUiState(
                filters = StatsFilters(
                    fromIso = fromIso,
                    toIso = toIso,
                    grouping = StatsGrouping.Auto,
                    externalId = ""
                ),
                selectedPreset = StatsDatePreset.Last7Days
            )
        }
    )

    val state: StateFlow<StatsUiState> = _state

    private var loadJob: Job? = null

    fun cancelLoad() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = false) }
    }

    fun setGrouping(grouping: StatsGrouping) {
        _state.update { it.copy(filters = it.filters.copy(grouping = grouping)) }
    }

    fun setMetric(metric: Metric) {
        _state.update { it.copy(metric = metric) }
    }

    fun setFromIso(fromIso: String) {
        _state.update {
            it.copy(
                filters = it.filters.copy(fromIso = fromIso),
                selectedPreset = null
            )
        }
    }

    fun setToIso(toIso: String) {
        _state.update {
            it.copy(
                filters = it.filters.copy(toIso = toIso),
                selectedPreset = null
            )
        }
    }

    fun applyPreset(preset: StatsDatePreset) {
        val (fromIso, toIso) = isoRangeForStatsPreset(preset)
        _state.update {
            it.copy(
                filters = it.filters.copy(fromIso = fromIso, toIso = toIso),
                selectedPreset = preset
            )
        }
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }

            val f = _state.value.filters
            val externalId = externalIdProvider()

            try {
                val res = getApplication<Application>().resources
                val resp = api.getOverviewSeries(
                    fromIso = f.fromIso,
                    toIso = f.toIso,
                    grouping = f.grouping.apiValue,
                    externalId = externalId
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        response = resp.data,
                        error = if (resp.success) null else res.userFriendlyApiError(resp.message)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (ex: Exception) {
                val res = getApplication<Application>().resources
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = res.userFriendlyApiError(ex)
                    )
                }
            }
        }
    }

}
