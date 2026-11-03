package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.model.AnalyticsSummary
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.OfficerDirectoryRepository
import com.srini.trafficfine.domain.repository.ViolationRepository
import com.srini.trafficfine.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ObserveAnalyticsUseCase @Inject constructor(
    private val violations: ViolationRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(officer: Officer, dayCount: Int): Flow<AnalyticsSummary> {
        if (!officer.isAdmin) return flowOf(AnalyticsSummary.EMPTY)
        val today = Instant.ofEpochMilli(time.now()).atZone(time.zone()).toLocalDate()
        val from = time.dayBounds(today.minusDays(dayCount - 1L)).first
        return violations.observeViolations(ViolationFilter(fromMillis = from))
            .map { AnalyticsSummary.from(it, today, dayCount, time.zone()) }
    }
}

class ObserveOfficersUseCase @Inject constructor(
    private val directory: OfficerDirectoryRepository,
) {
    operator fun invoke(requester: Officer): Flow<List<Officer>> =
        if (requester.isAdmin) directory.observeOfficers() else flowOf(emptyList())
}

class SetOfficerActiveUseCase @Inject constructor(
    private val directory: OfficerDirectoryRepository,
) {
    suspend operator fun invoke(requester: Officer, target: Officer, active: Boolean): Result<Unit> {
        if (!requester.isAdmin) return Result.failure(DomainError.AdminOnly)
        if (requester.uid == target.uid && !active) {
            return Result.failure(DomainError.InvalidState("You cannot deactivate your own account"))
        }
        return runCatching { directory.setActive(target.uid, active) }
    }
}

class CancelViolationUseCase @Inject constructor(
    private val violations: ViolationRepository,
    private val time: TimeProvider,
    private val recordActivity: RecordActivityUseCase,
) {
    suspend operator fun invoke(requester: Officer, violationId: String, reason: String): Result<Unit> {
        if (!requester.isAdmin) return Result.failure(DomainError.AdminOnly)
        val violation = violations.getViolation(violationId)
            ?: return Result.failure(DomainError.NotFound(violationId))
        if (violation.status != ViolationStatus.PENDING) {
            return Result.failure(DomainError.InvalidState("Only pending challans can be cancelled"))
        }
        val trimmed = reason.trim()
        if (trimmed.length < MIN_REASON_LENGTH) {
            return Result.failure(DomainError.InvalidState("Give a reason of at least $MIN_REASON_LENGTH characters"))
        }
        return runCatching { violations.cancel(violationId, trimmed.take(MAX_REASON_LENGTH), time.now()) }
            .onSuccess {
                recordActivity(requester.uid, LogAction.CHALLAN_CANCELLED, "${violation.challanNumber} · $trimmed", violationId)
            }
    }

    companion object {
        const val MIN_REASON_LENGTH = 5
        const val MAX_REASON_LENGTH = 200
    }
}

class ObserveUnsyncedCountUseCase @Inject constructor(
    private val violations: ViolationRepository,
) {
    operator fun invoke(): Flow<Int> = violations.observeUnsyncedCount()
}
