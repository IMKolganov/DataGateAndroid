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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    /** Matches "Last 7 days" / "Last 30 days" chips; null after a custom date range. */
    val selectedPresetDays: Int? = 7
)

open class StatsViewModel(
    application: Application,
    private val api: StatsApiClient,
    private val externalIdProvider: () -> String
) : AndroidViewModel(application) {

    private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")

    private val _state = MutableStateFlow(
        run {
            val (fromIso, toIso) = rangeForLastCalendarDays(7)
            StatsUiState(
                filters = StatsFilters(
                    fromIso = fromIso,
                    toIso = toIso,
                    grouping = StatsGrouping.Auto,
                    externalId = ""
                ),
                selectedPresetDays = 7
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
                selectedPresetDays = null
            )
        }
    }

    fun setToIso(toIso: String) {
        _state.update {
            it.copy(
                filters = it.filters.copy(toIso = toIso),
                selectedPresetDays = null
            )
        }
    }

    fun setLastDays(days: Long) {
        val (fromIso, toIso) = rangeForLastCalendarDays(days.toInt())
        _state.update {
            it.copy(
                filters = it.filters.copy(fromIso = fromIso, toIso = toIso),
                selectedPresetDays = days.toInt()
            )
        }
    }

    /** Last [days] calendar days in the device default timezone, through end of “today” there — matches preset chips. */
    private fun rangeForLastCalendarDays(days: Int): Pair<String, String> {
        val tz = TimeZone.getDefault()
        val todayStart = startOfTodayMillis(tz)
        val from = addDays(todayStart, -days, tz)
        val toExclusive = addDays(todayStart, 1, tz)
        val to = toExclusive - 1000L
        return isoUtc(from) to isoUtc(to)
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
                        error = res.userFriendlyApiError(ex.message)
                    )
                }
            }
        }
    }

    private fun isoUtc(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = utcTz
        return fmt.format(Date(millis))
    }

    private fun startOfTodayMillis(tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun addDays(millis: Long, days: Int, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = millis
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

}
