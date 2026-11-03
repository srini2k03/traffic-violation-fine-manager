package com.srini.trafficfine.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.AnalyticsSummary
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.usecase.ObserveAnalyticsUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.ObserveOfficersUseCase
import com.srini.trafficfine.domain.usecase.SetOfficerActiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AnalyticsRange(val label: String, val days: Int) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    QUARTER("90 days", 90),
}

enum class AdminTab { ANALYTICS, OFFICERS }

data class OfficersState(
    val officers: List<Officer> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class AdminUiState(
    val tab: AdminTab = AdminTab.ANALYTICS,
    val range: AnalyticsRange = AnalyticsRange.WEEK,
    val analytics: AnalyticsSummary = AnalyticsSummary.EMPTY,
    val officers: OfficersState = OfficersState(),
    val pendingToggle: String? = null,
    val message: String? = null,
)

private data class AdminInputs(
    val tab: AdminTab = AdminTab.ANALYTICS,
    val range: AnalyticsRange = AnalyticsRange.WEEK,
    val pendingToggle: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    observeAnalytics: ObserveAnalyticsUseCase,
    observeOfficers: ObserveOfficersUseCase,
    private val setOfficerActive: SetOfficerActiveUseCase,
) : ViewModel() {

    private val inputs = MutableStateFlow(AdminInputs())
    private val admin = observeCurrentOfficer().filterNotNull()

    private val analytics = combine(admin, inputs.map { it.range }) { a, r -> a to r }
        .flatMapLatest { (a, r) -> observeAnalytics(a, r.days) }

    private val officers = admin
        .flatMapLatest { a ->
            observeOfficers(a)
                .map { OfficersState(it, isLoading = false) }
                .onStart { emit(OfficersState()) }
                .catch { emit(OfficersState(isLoading = false, error = "Officer list unavailable: ${it.message}")) }
        }

    val state: StateFlow<AdminUiState> = combine(inputs, analytics, officers) { i, a, o ->
        AdminUiState(i.tab, i.range, a, o, i.pendingToggle, i.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    fun selectTab(tab: AdminTab) = inputs.update { it.copy(tab = tab) }

    fun selectRange(range: AnalyticsRange) = inputs.update { it.copy(range = range) }

    fun setActive(target: Officer, active: Boolean) {
        if (inputs.value.pendingToggle != null) return
        inputs.update { it.copy(pendingToggle = target.uid) }
        viewModelScope.launch {
            val requester = observeCurrentOfficer().filterNotNull().first()
            val result = setOfficerActive(requester, target, active)
            inputs.update {
                it.copy(
                    pendingToggle = null,
                    message = result.fold(
                        onSuccess = { "${target.name} ${if (active) "re-activated" else "deactivated"}" },
                        onFailure = { e -> e.message ?: "Could not update ${target.name}" },
                    ),
                )
            }
        }
    }

    fun onMessageShown() = inputs.update { it.copy(message = null) }
}
