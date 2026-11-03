package com.srini.trafficfine.ui

import app.cash.turbine.test
import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.repository.DemoModeRepository
import com.srini.trafficfine.domain.usecase.SignInUseCase
import com.srini.trafficfine.domain.usecase.StartDemoUseCase
import com.srini.trafficfine.ui.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val signIn = mockk<SignInUseCase>()
    private val auth = mockk<AuthRepository>()
    private val startDemo = mockk<StartDemoUseCase>()
    private val demoMode = mockk<DemoModeRepository> {
        every { isBackendConfigured } returns true
        every { isDemoAvailable } returns true
    }
    private val viewModel = LoginViewModel(signIn, auth, startDemo, demoMode)

    @Test
    fun `submit is disabled until both fields are filled`() {
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onEmailChange("priya@tnpolice.gov.in")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onPasswordChange("password123")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `shows loading then clears password on success`() = runTest {
        val gate = CompletableDeferred<Result<com.srini.trafficfine.domain.model.Officer>>()
        coEvery { signIn("priya@tnpolice.gov.in", "password123") } coAnswers { gate.await() }
        viewModel.onEmailChange("priya@tnpolice.gov.in")
        viewModel.onPasswordChange("password123")

        viewModel.state.test {
            assertFalse(awaitItem().isLoading)
            viewModel.submit()
            assertTrue(awaitItem().isLoading)
            gate.complete(Result.success(Fixtures.officer))
            val done = awaitItem()
            assertFalse(done.isLoading)
            assertEquals("", done.password)
            assertNull(done.error)
        }
    }

    @Test
    fun `maps domain errors to messages`() = runTest {
        coEvery { signIn(any(), any()) } returns Result.failure(DomainError.NotAuthorized)
        viewModel.onEmailChange("a@b.in")
        viewModel.onPasswordChange("password123")

        viewModel.submit()

        assertEquals(DomainError.NotAuthorized.message, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `editing a field clears the previous error`() = runTest {
        coEvery { signIn(any(), any()) } returns Result.failure(DomainError.InvalidCredentials)
        viewModel.onEmailChange("a@b.in")
        viewModel.onPasswordChange("password123")
        viewModel.submit()
        viewModel.onPasswordChange("password1234")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `password reset validates the email first`() = runTest {
        viewModel.onEmailChange("not-an-email")
        viewModel.sendPasswordReset()
        assertEquals(DomainError.InvalidEmail.message, viewModel.state.value.error)
        coVerify(exactly = 0) { auth.sendPasswordReset(any()) }

        coEvery { auth.sendPasswordReset("a@b.in") } returns Result.success(Unit)
        viewModel.onEmailChange("a@b.in")
        viewModel.sendPasswordReset()
        assertEquals("Password reset link sent to a@b.in", viewModel.state.value.info)
    }

    @Test
    fun `try demo shows progress and signs in through the use case`() = runTest {
        val gate = CompletableDeferred<Result<com.srini.trafficfine.domain.model.Officer>>()
        coEvery { startDemo(Role.ADMIN) } coAnswers { gate.await() }

        viewModel.state.test {
            assertNull(awaitItem().startingDemo)
            viewModel.tryDemo(Role.ADMIN)
            val busy = awaitItem()
            assertEquals(Role.ADMIN, busy.startingDemo)
            assertFalse(busy.canSubmit)
            viewModel.tryDemo(Role.OFFICER) // ignored while busy
            gate.complete(Result.success(DemoData.ADMIN))
            assertNull(awaitItem().startingDemo)
        }
        coVerify(exactly = 1) { startDemo(any()) }
    }

    @Test
    fun `demo failures are shown`() = runTest {
        coEvery { startDemo(Role.OFFICER) } returns Result.failure(IllegalStateException("disk full"))
        viewModel.tryDemo(Role.OFFICER)
        assertEquals("Could not start the demo", viewModel.state.value.error)
        assertNull(viewModel.state.value.startingDemo)
    }

    @Test
    fun `without a backend only the demo is offered`() = runTest {
        every { demoMode.isBackendConfigured } returns false
        val vm = LoginViewModel(signIn, auth, startDemo, demoMode)
        vm.onEmailChange("a@b.in")
        vm.onPasswordChange("password123")

        assertFalse(vm.state.value.backendConfigured)
        assertFalse(vm.state.value.canSubmit)
        assertTrue(vm.state.value.demoAvailable)
        vm.sendPasswordReset()
        assertEquals(DomainError.BackendNotConfigured.message, vm.state.value.error)
        coVerify(exactly = 0) { auth.sendPasswordReset(any()) }
    }

    @Test
    fun `demo is not started when the build disables it`() = runTest {
        every { demoMode.isDemoAvailable } returns false
        val vm = LoginViewModel(signIn, auth, startDemo, demoMode)
        vm.tryDemo(Role.ADMIN)
        coVerify(exactly = 0) { startDemo(any()) }
    }
}
