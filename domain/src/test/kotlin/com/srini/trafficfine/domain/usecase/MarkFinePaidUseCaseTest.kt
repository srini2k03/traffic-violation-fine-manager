package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.TestData
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.repository.ViolationRepository
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MarkFinePaidUseCaseTest {

    private val now = Instant.parse("2023-09-20T10:00:00Z")
    private val time = TimeProvider(Clock.fixed(now, ZoneOffset.UTC))
    private val repository = mockk<ViolationRepository>(relaxUnitFun = true)
    private val logs = mockk<OfficerLogRepository>(relaxUnitFun = true)
    private val sync = mockk<SyncScheduler>(relaxed = true)
    private val useCase = MarkFinePaidUseCase(
        repository,
        time,
        RecordActivityUseCase(logs, IdGenerator { "log" }, time, sync),
    )

    @Test
    fun `marks a pending challan as paid`() = runTest {
        coEvery { repository.getViolation("v1") } returns TestData.violation("v1", issuedAt = 0)

        val result = useCase(TestData.officer, "v1", PaymentMode.UPI, " UPI-77812345 ")

        assertTrue(result.isSuccess)
        coVerify {
            repository.markPaid(
                "v1",
                match { it.reference == "UPI-77812345" && it.mode == PaymentMode.UPI && it.paidAt == now.toEpochMilli() },
                now.toEpochMilli(),
            )
        }
        coVerify { logs.record(match { it.action == LogAction.FINE_PAID }) }
    }

    @Test
    fun `cash payments get a generated receipt reference`() = runTest {
        coEvery { repository.getViolation("v1") } returns TestData.violation("v1", issuedAt = 0)
        useCase(TestData.officer, "v1", PaymentMode.CASH, "")
        coVerify { repository.markPaid("v1", match { it.reference == "CASH-ABCDEF" }, any()) }
    }

    @Test
    fun `non-cash payments require a reference`() = runTest {
        coEvery { repository.getViolation("v1") } returns TestData.violation("v1", issuedAt = 0)
        val result = useCase(TestData.officer, "v1", PaymentMode.CARD, "12")
        assertTrue(result.exceptionOrNull() is DomainError.InvalidState)
        coVerify(exactly = 0) { repository.markPaid(any(), any(), any()) }
    }

    @Test
    fun `cannot pay an already paid challan`() = runTest {
        coEvery { repository.getViolation("v1") } returns
            TestData.violation("v1", issuedAt = 0, status = ViolationStatus.PAID)
        val result = useCase(TestData.officer, "v1", PaymentMode.CASH, "")
        assertTrue(result.exceptionOrNull() is DomainError.InvalidState)
    }

    @Test
    fun `unknown challan fails with NotFound`() = runTest {
        coEvery { repository.getViolation("nope") } returns null
        assertEquals(DomainError.NotFound("nope"), useCase(TestData.officer, "nope", PaymentMode.CASH, "").exceptionOrNull())
    }
}
