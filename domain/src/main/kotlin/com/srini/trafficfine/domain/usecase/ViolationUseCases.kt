package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.model.DashboardStats
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.repository.ViolationRepository
import com.srini.trafficfine.domain.util.ChallanNumberGenerator
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import com.srini.trafficfine.domain.validation.PhoneValidator
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import com.srini.trafficfine.domain.validation.ViolationDraft
import com.srini.trafficfine.domain.validation.ViolationFormValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Computes the fine (including repeat-offender escalation) for a plate + offences. */
class PreviewFineUseCase @Inject constructor(
    private val calculator: FineCalculator,
    private val violations: ViolationRepository,
    private val plateValidator: VehicleNumberValidator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(vehicleNumber: String, codes: Set<String>): FineBreakdown {
        val types = codes.mapNotNull(ViolationCatalogue::find)
        if (types.isEmpty()) return FineBreakdown.EMPTY
        val plate = (plateValidator.validate(vehicleNumber) as? VehicleNumberValidator.Result.Valid)?.normalized
        val priors = if (plate == null) {
            emptyMap()
        } else {
            violations.priorOffenceCounts(plate, time.now() - calculator.policy.lookbackMillis)
        }
        return calculator.calculate(types, priors)
    }
}

class IssueViolationUseCase @Inject constructor(
    private val formValidator: ViolationFormValidator,
    private val plateValidator: VehicleNumberValidator,
    private val previewFine: PreviewFineUseCase,
    private val violations: ViolationRepository,
    private val challanNumbers: ChallanNumberGenerator,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val recordActivity: RecordActivityUseCase,
    private val sync: SyncScheduler,
) {
    suspend operator fun invoke(officer: Officer, draft: ViolationDraft): Result<Violation> {
        val errors = formValidator.validate(draft)
        if (errors.isNotEmpty()) return Result.failure(DomainError.Validation(errors))

        val plate = plateValidator.validate(draft.vehicleNumber) as VehicleNumberValidator.Result.Valid
        val fine = previewFine(plate.normalized, draft.violationCodes)
        val now = time.now()
        val stateCode = if (plate.isBharatSeries) "BH" else plate.normalized.take(2)

        val violation = Violation(
            id = ids.newId(),
            challanNumber = challanNumbers.generate(stateCode, officer.badgeNumber, now, time.zone()),
            vehicleNumber = plate.normalized,
            offenderName = draft.offenderName.trim().ifBlank { null },
            offenderPhone = PhoneValidator.normalize(draft.offenderPhone),
            fine = fine,
            location = if (draft.latitude != null && draft.longitude != null) {
                GeoPoint(draft.latitude, draft.longitude, draft.accuracyMeters, draft.address)
            } else {
                null
            },
            evidencePhotoUrl = null,
            localEvidencePath = draft.localEvidencePath,
            notes = draft.notes.trim(),
            officerId = officer.uid,
            officerName = officer.name,
            officerBadge = officer.badgeNumber,
            issuedAt = now,
            status = ViolationStatus.PENDING,
            payment = null,
            syncState = SyncState.PENDING,
            updatedAt = now,
        )
        return runCatching { violations.saveNew(violation) }
            .map {
                recordActivity(
                    officer.uid,
                    LogAction.VIOLATION_ISSUED,
                    "${violation.challanNumber} · ${plate.formatted} · ₹${fine.total}",
                    violation.id,
                )
                sync.requestSync()
                violation
            }
    }
}

class MarkFinePaidUseCase @Inject constructor(
    private val violations: ViolationRepository,
    private val time: TimeProvider,
    private val recordActivity: RecordActivityUseCase,
) {
    suspend operator fun invoke(
        officer: Officer,
        violationId: String,
        mode: PaymentMode,
        reference: String,
    ): Result<Unit> {
        val violation = violations.getViolation(violationId)
            ?: return Result.failure(DomainError.NotFound(violationId))
        if (violation.status != ViolationStatus.PENDING) {
            return Result.failure(DomainError.InvalidState("Challan is already ${violation.status.name.lowercase()}"))
        }
        val ref = reference.trim()
        if (mode != PaymentMode.CASH && ref.length < MIN_REFERENCE_LENGTH) {
            return Result.failure(DomainError.InvalidState("A transaction reference is required for ${mode.name}"))
        }
        val now = time.now()
        val payment = Payment(
            mode = mode,
            reference = ref.ifEmpty { "CASH-${violation.challanNumber.takeLast(6)}" },
            paidAt = now,
            collectedBy = officer.uid,
        )
        return runCatching { violations.markPaid(violationId, payment, now) }
            .onSuccess {
                recordActivity(
                    officer.uid,
                    LogAction.FINE_PAID,
                    "${violation.challanNumber} · ₹${violation.totalFine} via ${mode.name}",
                    violationId,
                )
            }
    }

    companion object {
        const val MIN_REFERENCE_LENGTH = 6
    }
}

class ObserveViolationsUseCase @Inject constructor(
    private val violations: ViolationRepository,
) {
    operator fun invoke(filter: ViolationFilter): Flow<List<Violation>> = violations.observeViolations(filter)
}

class ObserveViolationUseCase @Inject constructor(
    private val violations: ViolationRepository,
) {
    operator fun invoke(id: String): Flow<Violation?> = violations.observeViolation(id)
}

class ObserveDashboardStatsUseCase @Inject constructor(
    private val violations: ViolationRepository,
    private val time: TimeProvider,
) {
    /** Today's numbers for [officer]; admins see the whole station's cached data. */
    operator fun invoke(officer: Officer): Flow<DashboardStats> {
        val today = time.dayBounds()
        val filter = if (officer.isAdmin) ViolationFilter() else ViolationFilter(officerId = officer.uid)
        return violations.observeViolations(filter).map { list ->
            DashboardStats.from(list, today.first, today.last + 1)
        }
    }
}
