package com.srini.trafficfine.domain.demo

import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.DemoModeRepository
import com.srini.trafficfine.domain.usecase.StartDemoUseCase
import com.srini.trafficfine.domain.util.TimeProvider
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StartDemoUseCaseTest {

    private val repo = mockk<DemoModeRepository>(relaxUnitFun = true)
    private val factory = DemoDataFactory(
        FineCalculator(RepeatOffenderPolicy.DEFAULT),
        VehicleNumberValidator(),
        TimeProvider(Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC)),
    )
    private val useCase = StartDemoUseCase(repo, factory)

    @Test
    fun `starts as the requested role with seeded data`() = runTest {
        every { repo.isDemoAvailable } returns true
        val seed = slot<DemoSeed>()
        coEvery { repo.start(DemoData.ADMIN, capture(seed)) } returns Unit

        val result = useCase(Role.ADMIN)

        assertEquals(DemoData.ADMIN, result.getOrThrow())
        assertTrue(seed.captured.violations.isNotEmpty())
        assertEquals(DemoData.OFFICER, useCase(Role.OFFICER).getOrThrow())
    }

    @Test
    fun `fails without touching storage when the build has no demo`() = runTest {
        every { repo.isDemoAvailable } returns false
        assertEquals(DomainError.DemoUnavailable, useCase(Role.ADMIN).exceptionOrNull())
        coVerify(exactly = 0) { repo.start(any(), any()) }
    }

    @Test
    fun `storage failures are reported`() = runTest {
        every { repo.isDemoAvailable } returns true
        coEvery { repo.start(any(), any()) } throws IllegalStateException("disk full")
        assertEquals("disk full", useCase(Role.OFFICER).exceptionOrNull()?.message)
    }
}
