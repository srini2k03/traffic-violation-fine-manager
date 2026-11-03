package com.srini.trafficfine.ui

import androidx.lifecycle.SavedStateHandle
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.ChallanDocument
import com.srini.trafficfine.domain.repository.ChallanDocumentGenerator
import com.srini.trafficfine.domain.usecase.CancelViolationUseCase
import com.srini.trafficfine.domain.usecase.MarkFinePaidUseCase
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.ObserveViolationUseCase
import com.srini.trafficfine.domain.usecase.RecordActivityUseCase
import com.srini.trafficfine.ui.detail.ShareRequest
import com.srini.trafficfine.ui.detail.ViolationDetailViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ViolationDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val violation = Fixtures.violation("v1").copy(offenderPhone = "9876543210")
    private val observeViolation = mockk<ObserveViolationUseCase> { every { this@mockk.invoke("v1") } returns MutableStateFlow(violation) }
    private val officerFlow = MutableStateFlow(Fixtures.officer)
    private val observeOfficer = mockk<ObserveCurrentOfficerUseCase> { every { this@mockk.invoke() } returns officerFlow }
    private val markPaid = mockk<MarkFinePaidUseCase>()
    private val documents = mockk<ChallanDocumentGenerator>()
    private val record = mockk<RecordActivityUseCase>(relaxed = true)
    private val cancel = mockk<CancelViolationUseCase>()

    private fun viewModel() = ViolationDetailViewModel(
        SavedStateHandle(mapOf("id" to "v1")), observeViolation, observeOfficer, markPaid, documents, record, cancel,
    )

    @Test
    fun `exposes the violation and role`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals(violation, vm.state.value.violation)
        assertFalse(vm.state.value.isAdmin)
        officerFlow.value = Fixtures.officer.copy(role = Role.ADMIN)
        assertTrue(vm.state.value.isAdmin)
    }

    @Test
    fun `mark paid reports success`() = runTest {
        coEvery { markPaid(Fixtures.officer, "v1", PaymentMode.UPI, "UPI123456") } returns Result.success(Unit)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.markPaid(PaymentMode.UPI, "UPI123456")
        assertEquals("Payment recorded", vm.state.value.message)
        assertFalse(vm.state.value.isMarkingPaid)
    }

    @Test
    fun `share generates the PDF once and logs it`() = runTest {
        coEvery { documents.generate(violation) } returns ChallanDocument("/cache/challans/x.pdf")
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.shareChallan()

        assertEquals(ShareRequest("/cache/challans/x.pdf", violation.challanNumber, "9876543210"), vm.state.value.share)
        vm.onShareHandled()
        assertNull(vm.state.value.share)
        coVerify { record.invoke(Fixtures.officer.uid, any(), violation.challanNumber, "v1") }
    }

    @Test
    fun `pdf failures surface a message`() = runTest {
        coEvery { documents.generate(any()) } throws IllegalStateException("disk")
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.shareChallan()
        assertEquals("Could not generate PDF", vm.state.value.message)
        assertFalse(vm.state.value.isGeneratingPdf)
    }

    @Test
    fun `admin cancellation result is shown`() = runTest {
        val admin = Fixtures.officer.copy(role = Role.ADMIN)
        officerFlow.value = admin
        coEvery { cancel(admin, "v1", "Duplicate entry") } returns Result.success(Unit)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.cancel("Duplicate entry")
        assertEquals("Challan cancelled", vm.state.value.message)
    }
}
