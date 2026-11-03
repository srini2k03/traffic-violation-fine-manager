package com.srini.trafficfine.domain.usecase

import app.cash.turbine.test
import com.srini.trafficfine.domain.TestData
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.OfficerDirectoryRepository
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.repository.ViolationRepository
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AdminUseCasesTest {

    private val now = Instant.parse("2023-09-15T10:00:00Z")
    private val time = TimeProvider(Clock.fixed(now, ZoneOffset.UTC))
    private val violations = mockk<ViolationRepository>(relaxUnitFun = true)
    private val directory = mockk<OfficerDirectoryRepository>(relaxUnitFun = true)
    private val logs = mockk<OfficerLogRepository>(relaxUnitFun = true)
    private val record = RecordActivityUseCase(logs, IdGenerator { "log" }, time, mockk<SyncScheduler>(relaxed = true))

    @Test
    fun `analytics covers the requested window for admins only`() = runTest {
        val from = Instant.parse("2023-09-09T00:00:00Z").toEpochMilli()
        every { violations.observeViolations(ViolationFilter(fromMillis = from)) } returns
            flowOf(listOf(TestData.violation("1", issuedAt = now.toEpochMilli())))
        val useCase = ObserveAnalyticsUseCase(violations, time)

        useCase(TestData.admin, 7).test {
            val summary = awaitItem()
            assertEquals(7, summary.days.size)
            assertEquals(1, summary.totalIssued)
            awaitComplete()
        }
        useCase(TestData.officer, 7).test {
            assertEquals(0, awaitItem().totalIssued)
            awaitComplete()
        }
        verify(exactly = 1) { violations.observeViolations(any()) }
    }

    @Test
    fun `only admins can change officer status and never their own`() = runTest {
        val useCase = SetOfficerActiveUseCase(directory)
        assertEquals(DomainError.AdminOnly, useCase(TestData.officer, TestData.admin, false).exceptionOrNull())
        assertTrue(useCase(TestData.admin, TestData.admin, false).exceptionOrNull() is DomainError.InvalidState)
        assertTrue(useCase(TestData.admin, TestData.officer, false).isSuccess)
        coVerify(exactly = 1) { directory.setActive("officer-1", false) }
    }

    @Test
    fun `admins cancel pending challans with a reason`() = runTest {
        coEvery { violations.getViolation("v1") } returns TestData.violation("v1", issuedAt = 0)
        val useCase = CancelViolationUseCase(violations, time, record)

        assertEquals(DomainError.AdminOnly, useCase(TestData.officer, "v1", "duplicate entry").exceptionOrNull())
        assertTrue(useCase(TestData.admin, "v1", "dup").exceptionOrNull() is DomainError.InvalidState)
        assertTrue(useCase(TestData.admin, "v1", "  Duplicate entry  ").isSuccess)

        coVerify(exactly = 1) { violations.cancel("v1", "Duplicate entry", now.toEpochMilli()) }
        coVerify { logs.record(match { it.action == LogAction.CHALLAN_CANCELLED && it.officerId == "admin-1" }) }
    }

    @Test
    fun `paid challans cannot be cancelled`() = runTest {
        coEvery { violations.getViolation("v1") } returns TestData.violation("v1", issuedAt = 0, status = ViolationStatus.PAID)
        val result = CancelViolationUseCase(violations, time, record)(TestData.admin, "v1", "Duplicate entry")
        assertTrue(result.exceptionOrNull() is DomainError.InvalidState)
    }
}
