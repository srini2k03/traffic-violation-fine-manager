package com.srini.trafficfine.ui

import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.model.FineLine
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.repository.LocationProvider
import com.srini.trafficfine.domain.usecase.IssueViolationUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.PreviewFineUseCase
import com.srini.trafficfine.domain.validation.FormError
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import com.srini.trafficfine.ui.issue.IssueViolationViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssueViolationViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val observeOfficer = mockk<ObserveCurrentOfficerUseCase> {
        every { this@mockk.invoke() } returns flowOf(Fixtures.officer)
    }
    private val previewFine = mockk<PreviewFineUseCase>()
    private val issueViolation = mockk<IssueViolationUseCase>()
    private val location = mockk<LocationProvider>()

    private fun viewModel() = IssueViolationViewModel(observeOfficer, previewFine, issueViolation, location, VehicleNumberValidator())

    private val repeatFine = FineBreakdown(
        listOf(FineLine("NO_HELMET", "Riding without helmet", "194D", 1_000, 1, 150, 1_500)),
    )

    @Test
    fun `plate input is upper-cased and validated as the officer types`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        val vm = viewModel()

        vm.onVehicleNumberChange("tn09ab1234")

        assertEquals("TN09AB1234", vm.state.value.vehicleNumber)
        assertTrue(vm.state.value.plateValid)
        assertEquals("TN 09 AB 1234", vm.state.value.plateHint)

        vm.onVehicleNumberChange("tn09")
        assertFalse(vm.state.value.plateValid)
        assertNull(vm.state.value.plateHint)
    }

    @Test
    fun `fine preview is debounced and reflects repeat offences`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        coEvery { previewFine("TN09AB1234", setOf("NO_HELMET")) } returns repeatFine
        val vm = viewModel()

        vm.onVehicleNumberChange("TN09AB1234")
        vm.onToggleViolation("NO_HELMET")
        advanceTimeBy(100)
        assertEquals(0L, vm.state.value.fine.total)

        advanceUntilIdle()
        assertEquals(1_500L, vm.state.value.fine.total)
        // Only the settled input was looked up, not every keystroke.
        coVerify(exactly = 1) { previewFine("TN09AB1234", setOf("NO_HELMET")) }
    }

    @Test
    fun `toggling a violation twice deselects it`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        val vm = viewModel()
        vm.onToggleViolation("NO_HELMET")
        vm.onToggleViolation("NO_HELMET")
        assertTrue(vm.state.value.selectedCodes.isEmpty())
    }

    @Test
    fun `refreshLocation stores the fix and clears the location error`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        coEvery { location.currentLocation() } returns GeoPoint(13.0, 80.2, 5f, "Adyar")
        val vm = viewModel()

        vm.refreshLocation()
        assertTrue(vm.state.value.isLocating)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLocating)
        assertEquals("Adyar", vm.state.value.location?.address)
        assertFalse(vm.state.value.locationUnavailable)
    }

    @Test
    fun `missing location marks it unavailable`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        coEvery { location.currentLocation() } returns null
        val vm = viewModel()
        vm.refreshLocation()
        advanceUntilIdle()
        assertTrue(vm.state.value.locationUnavailable)
    }

    @Test
    fun `validation errors from the use case are surfaced per field`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        coEvery { issueViolation(any(), any()) } returns
            Result.failure(DomainError.Validation(setOf(FormError.VEHICLE_NUMBER_INVALID, FormError.LOCATION_MISSING)))
        val vm = viewModel()

        vm.submit()
        advanceUntilIdle()

        assertEquals(setOf(FormError.VEHICLE_NUMBER_INVALID, FormError.LOCATION_MISSING), vm.state.value.errors)
        assertFalse(vm.state.value.isSubmitting)
        assertNull(vm.state.value.issuedViolationId)
    }

    @Test
    fun `successful submit exposes the new violation id once`() = runTest(mainRule.dispatcher) {
        coEvery { previewFine(any(), any()) } returns FineBreakdown.EMPTY
        coEvery { issueViolation(Fixtures.officer, match { it.vehicleNumber == "TN09AB1234" }) } returns
            Result.success(Fixtures.violation("v42"))
        val vm = viewModel()
        vm.onVehicleNumberChange("TN09AB1234")
        vm.onToggleViolation("NO_HELMET")

        vm.submit()
        advanceUntilIdle()

        assertEquals("v42", vm.state.value.issuedViolationId)
        vm.onNavigatedToIssued()
        assertNull(vm.state.value.issuedViolationId)
    }
}
