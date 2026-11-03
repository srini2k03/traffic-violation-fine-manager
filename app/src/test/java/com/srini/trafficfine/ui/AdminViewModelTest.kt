package com.srini.trafficfine.ui

import com.srini.trafficfine.domain.model.AnalyticsSummary
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.usecase.ObserveAnalyticsUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.ObserveOfficersUseCase
import com.srini.trafficfine.domain.usecase.SetOfficerActiveUseCase
import com.srini.trafficfine.ui.admin.AdminTab
import com.srini.trafficfine.ui.admin.AdminViewModel
import com.srini.trafficfine.ui.admin.AnalyticsRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val admin = Fixtures.officer.copy(uid = "admin-1", role = Role.ADMIN)
    private val observeOfficer = mockk<ObserveCurrentOfficerUseCase> { every { this@mockk.invoke() } returns flowOf(admin) }
    private val observeAnalytics = mockk<ObserveAnalyticsUseCase>()
    private val observeOfficers = mockk<ObserveOfficersUseCase>()
    private val setActive = mockk<SetOfficerActiveUseCase>()

    private fun summary(issued: Int) = AnalyticsSummary.EMPTY.copy(totalIssued = issued)

    private fun viewModel() = AdminViewModel(observeOfficer, observeAnalytics, observeOfficers, setActive)

    @Test
    fun `switching range re-queries analytics`() = runTest {
        every { observeAnalytics(admin, 7) } returns flowOf(summary(3))
        every { observeAnalytics(admin, 30) } returns flowOf(summary(12))
        every { observeOfficers(admin) } returns flowOf(listOf(Fixtures.officer))
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals(3, vm.state.value.analytics.totalIssued)
        assertEquals(listOf(Fixtures.officer), vm.state.value.officers.officers)
        assertFalse(vm.state.value.officers.isLoading)

        vm.selectRange(AnalyticsRange.MONTH)
        vm.selectTab(AdminTab.OFFICERS)

        assertEquals(12, vm.state.value.analytics.totalIssued)
        assertEquals(AdminTab.OFFICERS, vm.state.value.tab)
        verify { observeAnalytics(admin, 30) }
    }

    @Test
    fun `officer list failures are shown instead of crashing`() = runTest {
        every { observeAnalytics(any(), any()) } returns flowOf(summary(0))
        every { observeOfficers(admin) } returns flow { throw IllegalStateException("Permission denied") }
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertEquals("Officer list unavailable: Permission denied", vm.state.value.officers.error)
    }

    @Test
    fun `toggling an officer reports the outcome`() = runTest {
        every { observeAnalytics(any(), any()) } returns flowOf(summary(0))
        every { observeOfficers(admin) } returns flowOf(listOf(Fixtures.officer))
        coEvery { setActive(admin, Fixtures.officer, false) } returns Result.success(Unit)
        coEvery { setActive(admin, admin, false) } returns Result.failure(DomainError.InvalidState("You cannot deactivate your own account"))
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.setActive(Fixtures.officer, false)
        assertEquals("${Fixtures.officer.name} deactivated", vm.state.value.message)
        assertNull(vm.state.value.pendingToggle)

        vm.onMessageShown()
        vm.setActive(admin, false)
        assertEquals("You cannot deactivate your own account", vm.state.value.message)
    }
}
