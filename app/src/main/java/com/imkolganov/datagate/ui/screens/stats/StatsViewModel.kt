package com.imkolganov.datagate.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imkolganov.datagate.model.overview.Metric
import com.imkolganov.datagate.model.overview.OverviewSeriesResponse
import com.imkolganov.datagate.model.overview.StatsGrouping
import com.imkolganov.datagate.stats.StatsApiClient
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
    val response: OverviewSeriesResponse? = null
)

open class StatsViewModel(
    private val api: StatsApiClient,
    private val externalIdProvider: () -> String
) : ViewModel() {

    private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")
    private val nicosiaTz: TimeZone = TimeZone.getTimeZone("Europe/Nicosia")

    private val _state = MutableStateFlow(
        StatsUiState(
            filters = StatsFilters(
                fromIso = isoUtc(truncateToSeconds(System.currentTimeMillis() - daysToMillis(7))),
                toIso = isoUtc(truncateToSeconds(System.currentTimeMillis())),
                grouping = StatsGrouping.Auto,
                externalId = ""
            )
        )
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
        _state.update { it.copy(filters = it.filters.copy(fromIso = fromIso)) }
    }

    fun setToIso(toIso: String) {
        _state.update { it.copy(filters = it.filters.copy(toIso = toIso)) }
    }

    fun setLastDays(days: Long) {
        val todayStart = startOfTodayMillis(nicosiaTz)            // 00:00 in Nicosia
        val from = addDays(todayStart, -days.toInt(), nicosiaTz)  // minus N days (still local day boundaries)
        val toExclusive = addDays(todayStart, 1, nicosiaTz)       // start of tomorrow
        val to = toExclusive - 1000L                              // last second of today

        setFromIso(isoUtc(from))
        setToIso(isoUtc(to))
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }

            val f = _state.value.filters
            val externalId = externalIdProvider()

            try {
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
                        error = if (resp.success) null else resp.message
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (ex: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = ex.message ?: "Request failed"
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

    private fun truncateToSeconds(millis: Long): Long {
        return (millis / 1000L) * 1000L
    }

    private fun daysToMillis(days: Int): Long {
        return days.toLong() * 24L * 60L * 60L * 1000L
    }
}
