package com.srini.trafficfine.ui.issue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationType
import com.srini.trafficfine.domain.repository.LocationProvider
import com.srini.trafficfine.domain.usecase.IssueViolationUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.PreviewFineUseCase
import com.srini.trafficfine.domain.validation.FormError
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import com.srini.trafficfine.domain.validation.ViolationDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IssueViolationUiState(
    val vehicleNumber: String = "",
    val plateHint: String? = null,
    val plateValid: Boolean = false,
    val selectedCodes: Set<String> = emptySet(),
    val offenderName: String = "",
    val offenderPhone: String = "",
    val notes: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
    val locationUnavailable: Boolean = false,
    val evidencePath: String? = null,
    val fine: FineBreakdown = FineBreakdown.EMPTY,
    val errors: Set<FormError> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val issuedViolationId: String? = null,
) {
    val catalogue: List<ViolationType> get() = ViolationCatalogue.all
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class IssueViolationViewModel @Inject constructor(
    private val observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    private val previewFine: PreviewFineUseCase,
    private val issueViolation: IssueViolationUseCase,
    private val locationProvider: LocationProvider,
    private val plateValidator: VehicleNumberValidator,
) : ViewModel() {

    private val _state = MutableStateFlow(IssueViolationUiState())
    val state: StateFlow<IssueViolationUiState> = _state.asStateFlow()

    init {
        // Live fine preview including repeat-offender lookups, debounced while the officer types.
        _state
            .map { it.vehicleNumber to it.selectedCodes }
            .distinctUntilChanged()
            .debounce(PREVIEW_DEBOUNCE_MS)
            .mapLatest { (plate, codes) -> runCatching { previewFine(plate, codes) }.getOrDefault(FineBreakdown.EMPTY) }
            .onEach { fine -> _state.update { it.copy(fine = fine) } }
            .launchIn(viewModelScope)
    }

    fun onVehicleNumberChange(raw: String) {
        val upper = raw.uppercase().take(MAX_PLATE_INPUT)
        val result = plateValidator.validate(upper)
        _state.update {
            it.copy(
                vehicleNumber = upper,
                plateValid = result is VehicleNumberValidator.Result.Valid,
                plateHint = (result as? VehicleNumberValidator.Result.Valid)?.formatted,
                errors = it.errors - FormError.VEHICLE_NUMBER_INVALID,
            )
        }
    }

    fun onToggleViolation(code: String) = _state.update {
        val codes = if (code in it.selectedCodes) it.selectedCodes - code else it.selectedCodes + code
        it.copy(selectedCodes = codes, errors = it.errors - FormError.NO_VIOLATION_SELECTED - FormError.EVIDENCE_REQUIRED)
    }

    fun onOffenderNameChange(value: String) = _state.update { it.copy(offenderName = value.take(80)) }

    fun onOffenderPhoneChange(value: String) = _state.update {
        it.copy(offenderPhone = value.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16), errors = it.errors - FormError.PHONE_INVALID)
    }

    fun onNotesChange(value: String) = _state.update { it.copy(notes = value, errors = it.errors - FormError.NOTES_TOO_LONG) }

    fun onEvidenceCaptured(path: String) = _state.update { it.copy(evidencePath = path, errors = it.errors - FormError.EVIDENCE_REQUIRED) }

    fun onEvidenceRemoved() = _state.update { it.copy(evidencePath = null) }

    /** Called once location permission is granted (or re-requested by the officer). */
    fun refreshLocation() {
        if (_state.value.isLocating) return
        _state.update { it.copy(isLocating = true, locationUnavailable = false) }
        viewModelScope.launch {
            val location = locationProvider.currentLocation()
            _state.update {
                it.copy(
                    location = location ?: it.location,
                    isLocating = false,
                    locationUnavailable = location == null && it.location == null,
                    errors = if (location != null) it.errors - FormError.LOCATION_MISSING else it.errors,
                )
            }
        }
    }

    fun onLocationPermissionDenied() = _state.update { it.copy(locationUnavailable = true) }

    fun submit() {
        val s = _state.value
        if (s.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val officer = observeCurrentOfficer().filterNotNull().first()
            val draft = ViolationDraft(
                vehicleNumber = s.vehicleNumber,
                violationCodes = s.selectedCodes,
                offenderName = s.offenderName,
                offenderPhone = s.offenderPhone,
                notes = s.notes,
                latitude = s.location?.latitude,
                longitude = s.location?.longitude,
                accuracyMeters = s.location?.accuracyMeters,
                address = s.location?.address,
                localEvidencePath = s.evidencePath,
            )
            issueViolation(officer, draft)
                .onSuccess { v -> _state.update { it.copy(isSubmitting = false, issuedViolationId = v.id) } }
                .onFailure { e ->
                    _state.update {
                        when (e) {
                            is DomainError.Validation -> it.copy(isSubmitting = false, errors = e.errors)
                            else -> it.copy(isSubmitting = false, submitError = e.message ?: "Could not save challan")
                        }
                    }
                }
        }
    }

    fun onNavigatedToIssued() = _state.update { it.copy(issuedViolationId = null) }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 300L
        const val MAX_PLATE_INPUT = 14
    }
}
