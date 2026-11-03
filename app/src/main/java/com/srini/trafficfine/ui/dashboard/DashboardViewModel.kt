package com.srini.trafficfine.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.DashboardStats
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.ObserveDashboardStatsUseCase
import com.srini.trafficfine.domain.usecase.ObserveViolationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val stats: DashboardStats = DashboardStats.EMPTY,
    val recent: List<Violation> = emptyList(),
    val isSyncing: Boolean = false,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    observeStats: ObserveDashboardStatsUseCase,
    observeViolations: ObserveViolationsUseCase,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val officer = observeCurrentOfficer().filterNotNull()

    val state: StateFlow<DashboardUiState> = officer.flatMapLatest { o ->
        val scope = if (o.isAdmin) ViolationFilter() else ViolationFilter(officerId = o.uid)
        combine(
            observeStats(o),
            observeViolations(scope).map { it.take(RECENT_COUNT) },
            syncScheduler.isSyncing,
        ) { stats, recent, syncing -> DashboardUiState(stats, recent, syncing, isLoading = false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun syncNow() = syncScheduler.requestSync()

    private companion object {
        const val RECENT_COUNT = 5
    }
}
