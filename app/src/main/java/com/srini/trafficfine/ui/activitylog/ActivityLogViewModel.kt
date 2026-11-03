package com.srini.trafficfine.ui.activitylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.usecase.ObserveActivityLogUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ActivityLogUiState(
    val groups: List<Pair<LocalDate, List<OfficerLog>>> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    observeActivityLog: ObserveActivityLogUseCase,
) : ViewModel() {

    val state: StateFlow<ActivityLogUiState> = observeCurrentOfficer()
        .filterNotNull()
        .flatMapLatest { observeActivityLog(it.uid) }
        .map { logs -> ActivityLogUiState(groupByDay(logs, ZoneId.systemDefault()), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityLogUiState())

    companion object {
        /** Groups newest-first logs into newest-first day buckets. */
        fun groupByDay(logs: List<OfficerLog>, zone: ZoneId): List<Pair<LocalDate, List<OfficerLog>>> =
            logs.sortedByDescending { it.timestamp }
                .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                .toList()
    }
}
