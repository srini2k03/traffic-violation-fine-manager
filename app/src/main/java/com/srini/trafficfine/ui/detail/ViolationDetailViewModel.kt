package com.srini.trafficfine.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.repository.ChallanDocumentGenerator
import com.srini.trafficfine.domain.usecase.MarkFinePaidUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.ObserveViolationUseCase
import com.srini.trafficfine.domain.usecase.RecordActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A PDF ready to hand to the share sheet. */
data class ShareRequest(val path: String, val challanNumber: String, val phone: String?)

data class ViolationDetailUiState(
    val violation: Violation? = null,
    val isLoading: Boolean = true,
    val isGeneratingPdf: Boolean = false,
    val isMarkingPaid: Boolean = false,
    val message: String? = null,
    val share: ShareRequest? = null,
)

private data class Transient(
    val isGeneratingPdf: Boolean = false,
    val isMarkingPaid: Boolean = false,
    val message: String? = null,
    val share: ShareRequest? = null,
)

@HiltViewModel
class ViolationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeViolation: ObserveViolationUseCase,
    private val observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    private val markFinePaid: MarkFinePaidUseCase,
    private val documents: ChallanDocumentGenerator,
    private val recordActivity: RecordActivityUseCase,
) : ViewModel() {

    /** Matches the `id` property of [com.srini.trafficfine.ui.navigation.Route.ViolationDetail]. */
    private val violationId: String = checkNotNull(savedStateHandle["id"]) { "Missing violation id" }

    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<ViolationDetailUiState> =
        combine(observeViolation(violationId), transient) { violation, t ->
            ViolationDetailUiState(
                violation = violation,
                isLoading = false,
                isGeneratingPdf = t.isGeneratingPdf,
                isMarkingPaid = t.isMarkingPaid,
                message = t.message,
                share = t.share,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViolationDetailUiState())

    fun markPaid(mode: PaymentMode, reference: String) {
        if (transient.value.isMarkingPaid) return
        transient.update { it.copy(isMarkingPaid = true, message = null) }
        viewModelScope.launch {
            val officer = observeCurrentOfficer().filterNotNull().first()
            val result = markFinePaid(officer, violationId, mode, reference)
            transient.update {
                it.copy(
                    isMarkingPaid = false,
                    message = result.fold({ "Payment recorded" }, { e -> e.message ?: "Could not record payment" }),
                )
            }
        }
    }

    fun shareChallan() {
        val violation = state.value.violation ?: return
        if (transient.value.isGeneratingPdf) return
        transient.update { it.copy(isGeneratingPdf = true, message = null) }
        viewModelScope.launch {
            try {
                val doc = documents.generate(violation)
                transient.update {
                    it.copy(
                        isGeneratingPdf = false,
                        share = ShareRequest(doc.absolutePath, violation.challanNumber, violation.offenderPhone),
                    )
                }
                observeCurrentOfficer().first()?.let { officer ->
                    recordActivity(officer.uid, LogAction.CHALLAN_SHARED, violation.challanNumber, violation.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transient.update { it.copy(isGeneratingPdf = false, message = "Could not generate PDF") }
            }
        }
    }

    fun onShareHandled() = transient.update { it.copy(share = null) }

    fun onMessageShown() = transient.update { it.copy(message = null) }
}
