package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.TestData
import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.repository.ViolationRepository
import com.srini.trafficfine.domain.util.ChallanNumberGenerator
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import com.srini.trafficfine.domain.validation.FormError
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import com.srini.trafficfine.domain.validation.ViolationDraft
import com.srini.trafficfine.domain.validation.ViolationFormValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

class IssueViolationUseCaseTest {

    private val now = Instant.parse("2023-09-15T05:00:00Z")
    private val time = TimeProvider(Clock.fixed(now, ZoneId.of("Asia/Kolkata")))
    private val repository = mockk<ViolationRepository>(relaxUnitFun = true)
    private val logs = mockk<OfficerLogRepository>(relaxUnitFun = true)
    private val sync = mockk<SyncScheduler>(relaxed = true)
    private val plateValidator = VehicleNumberValidator()
    private var nextId = 0
    private val ids = IdGenerator { "id-${nextId++}" }

    private val calculator = FineCalculator(RepeatOffenderPolicy.DEFAULT)
    private val useCase = IssueViolationUseCase(
        formValidator = ViolationFormValidator(plateValidator),
        plateValidator = plateValidator,
        previewFine = PreviewFineUseCase(calculator, repository, plateValidator, time),
        violations = repository,
        challanNumbers = ChallanNumberGenerator(Random(3)),
        ids = ids,
        time = time,
        recordActivity = RecordActivityUseCase(logs, ids, time, sync),
        sync = sync,
    )

    private val draft = ViolationDraft(
        vehicleNumber = "tn 09 ab 1234",
        violationCodes = setOf("NO_HELMET", "NO_LICENCE"),
        offenderPhone = "+91 98765 43210",
        notes = "  stopped at Adyar signal ",
        latitude = 13.0067,
        longitude = 80.2570,
    )

    @Test
    fun `issues a violation with normalized data and repeat offender fine`() = runTest {
        val lookbackStart = now.toEpochMilli() - RepeatOffenderPolicy.DEFAULT.lookbackMillis
        coEvery { repository.priorOffenceCounts("TN09AB1234", lookbackStart) } returns mapOf("NO_HELMET" to 1)
        val saved = slot<Violation>()
        coEvery { repository.saveNew(capture(saved)) } returns Unit

        val result = useCase(TestData.officer, draft)

        val violation = result.getOrThrow()
        assertEquals(violation, saved.captured)
        assertEquals("TN09AB1234", violation.vehicleNumber)
        assertEquals("9876543210", violation.offenderPhone)
        assertNull(violation.offenderName)
        assertEquals("stopped at Adyar signal", violation.notes)
        assertEquals(5_000L + 1_500L, violation.totalFine)
        assertEquals(ViolationStatus.PENDING, violation.status)
        assertEquals(SyncState.PENDING, violation.syncState)
        assertEquals(now.toEpochMilli(), violation.issuedAt)
        assertTrue(violation.challanNumber.startsWith("EC-TN-20230915-4821-"))
        coVerify { logs.record(match { it.action == LogAction.VIOLATION_ISSUED && it.referenceId == violation.id }) }
        verify(atLeast = 1) { sync.requestSync() }
    }

    @Test
    fun `returns validation error without touching the repository`() = runTest {
        val result = useCase(TestData.officer, draft.copy(vehicleNumber = "INVALID", violationCodes = emptySet()))

        val error = result.exceptionOrNull() as DomainError.Validation
        assertEquals(setOf(FormError.VEHICLE_NUMBER_INVALID, FormError.NO_VIOLATION_SELECTED), error.errors)
        coVerify(exactly = 0) { repository.saveNew(any()) }
        coVerify(exactly = 0) { logs.record(any()) }
    }

    @Test
    fun `propagates storage failures`() = runTest {
        coEvery { repository.priorOffenceCounts(any(), any()) } returns emptyMap()
        coEvery { repository.saveNew(any()) } throws IllegalStateException("disk full")

        val result = useCase(TestData.officer, draft)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { logs.record(any()) }
    }

    @Test
    fun `Bharat series plates use BH in the challan number`() = runTest {
        coEvery { repository.priorOffenceCounts(any(), any()) } returns emptyMap()
        val result = useCase(TestData.officer, draft.copy(vehicleNumber = "22BH1234AB"))
        assertTrue(result.getOrThrow().challanNumber.startsWith("EC-BH-"))
    }
}
