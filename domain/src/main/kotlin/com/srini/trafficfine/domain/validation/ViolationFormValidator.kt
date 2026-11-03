package com.srini.trafficfine.domain.validation

import com.srini.trafficfine.domain.model.ViolationCatalogue
import javax.inject.Inject

/** Raw input captured by the "issue violation" form. */
data class ViolationDraft(
    val vehicleNumber: String,
    val violationCodes: Set<String>,
    val offenderName: String = "",
    val offenderPhone: String = "",
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val address: String? = null,
    val localEvidencePath: String? = null,
)

enum class FormError {
    VEHICLE_NUMBER_INVALID,
    NO_VIOLATION_SELECTED,
    UNKNOWN_VIOLATION,
    PHONE_INVALID,
    NOTES_TOO_LONG,
    LOCATION_MISSING,
    EVIDENCE_REQUIRED,
}

class ViolationFormValidator @Inject constructor(
    private val plateValidator: VehicleNumberValidator,
) {
    /**
     * @param requireEvidence photo evidence is mandatory for some offences (e.g. red light, parking).
     */
    fun validate(draft: ViolationDraft, requireLocation: Boolean = true): Set<FormError> = buildSet {
        if (plateValidator.validate(draft.vehicleNumber) !is VehicleNumberValidator.Result.Valid) {
            add(FormError.VEHICLE_NUMBER_INVALID)
        }
        if (draft.violationCodes.isEmpty()) add(FormError.NO_VIOLATION_SELECTED)
        if (draft.violationCodes.any { ViolationCatalogue.find(it) == null }) add(FormError.UNKNOWN_VIOLATION)
        if (draft.offenderPhone.isNotBlank() && !PhoneValidator.isValid(draft.offenderPhone)) {
            add(FormError.PHONE_INVALID)
        }
        if (draft.notes.length > MAX_NOTES_LENGTH) add(FormError.NOTES_TOO_LONG)
        if (requireLocation && (draft.latitude == null || draft.longitude == null)) add(FormError.LOCATION_MISSING)
        if (draft.violationCodes.any { it in EVIDENCE_MANDATORY } && draft.localEvidencePath.isNullOrBlank()) {
            add(FormError.EVIDENCE_REQUIRED)
        }
    }

    companion object {
        const val MAX_NOTES_LENGTH = 500
        val EVIDENCE_MANDATORY = setOf("RED_LIGHT", "WRONG_PARKING", "OVERLOADING")
    }
}
