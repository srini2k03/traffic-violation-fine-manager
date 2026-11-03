package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.TestData
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock

class SignInUseCaseTest {

    private val auth = mockk<AuthRepository>()
    private val logs = mockk<OfficerLogRepository>(relaxUnitFun = true)
    private val record = RecordActivityUseCase(
        logs,
        IdGenerator { "log" },
        TimeProvider(Clock.systemUTC()),
        mockk<SyncScheduler>(relaxed = true),
    )
    private val signIn = SignInUseCase(auth, record)

    @Test
    fun `rejects malformed email before calling Firebase`() = runTest {
        assertEquals(DomainError.InvalidEmail, signIn("nope", "password123").exceptionOrNull())
        coVerify(exactly = 0) { auth.signIn(any(), any()) }
    }

    @Test
    fun `rejects short password`() = runTest {
        assertEquals(DomainError.WeakPassword, signIn("a@b.in", "123").exceptionOrNull())
    }

    @Test
    fun `logs successful sign in`() = runTest {
        coEvery { auth.signIn("priya@tnpolice.gov.in", "password123") } returns Result.success(TestData.officer)

        val result = signIn("  priya@tnpolice.gov.in ", "password123")

        assertEquals(TestData.officer, result.getOrNull())
        coVerify { logs.record(match { it.action == LogAction.SIGN_IN && it.officerId == "officer-1" }) }
    }

    @Test
    fun `does not log failed sign in`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns Result.failure(DomainError.InvalidCredentials)
        assertEquals(DomainError.InvalidCredentials, signIn("a@b.in", "password123").exceptionOrNull())
        coVerify(exactly = 0) { logs.record(any()) }
    }
}
