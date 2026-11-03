package com.srini.trafficfine.ui

import app.cash.turbine.test
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.usecase.SignInUseCase
import com.srini.trafficfine.ui.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val viewModel = LoginViewModel(signIn, auth)

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
}
