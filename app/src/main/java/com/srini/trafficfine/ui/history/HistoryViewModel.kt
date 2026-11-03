package com.srini.trafficfine.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.usecase.ObserveViolationsUseCase
import com.srini.trafficfine.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class DateRange(val label: String, val days: Int?) {
    TODAY("Today", 1),
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("All", null),
}

data class HistoryFilterState(
    val query: String = "",
    val status: ViolationStatus? = null,
    val range: DateRange = DateRange.ALL,
)

data class HistoryUiState(
    val filters: HistoryFilterState = HistoryFilterState(),
    val violations: List<Violation> = emptyList(),
    val totalAmount: Long = 0,
    val isLoading: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeViolations: ObserveViolationsUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val filters = MutableStateFlow(HistoryFilterState())

    val state: StateFlow<HistoryUiState> = filters
        .debounce { if (it.query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .map(::toDomainFilter)
        .distinctUntilChanged()
        .flatMapLatest { observeViolations(it) }
        .combine(filters) { list, f -> HistoryUiState(f, list, list.sumOf { it.totalFine }, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    val filterState: StateFlow<HistoryFilterState> = filters.asStateFlow()

    fun onQueryChange(query: String) = filters.update { it.copy(query = query.take(20)) }

    fun onStatusSelected(status: ViolationStatus?) = filters.update { it.copy(status = status) }

    fun onRangeSelected(range: DateRange) = filters.update { it.copy(range = range) }

    fun clearFilters() = filters.update { HistoryFilterState() }

    internal fun toDomainFilter(f: HistoryFilterState): ViolationFilter {
        val from = f.range.days?.let { days ->
            val today = time.dayBounds()
            today.first - (days - 1) * DAY_MS
        }
        return ViolationFilter(
            plateQuery = f.query.trim().ifEmpty { null },
            status = f.status,
            fromMillis = from,
            toMillis = null,
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
