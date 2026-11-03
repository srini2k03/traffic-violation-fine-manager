package com.srini.trafficfine.ui

import app.cash.turbine.test
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.usecase.ObserveViolationsUseCase
import com.srini.trafficfine.domain.util.TimeProvider
import com.srini.trafficfine.ui.history.DateRange
import com.srini.trafficfine.ui.history.HistoryFilterState
import com.srini.trafficfine.ui.history.HistoryViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HistoryViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val now = Instant.parse("2023-09-15T10:00:00Z")
    private val time = TimeProvider(Clock.fixed(now, ZoneOffset.UTC))
    private val startOfToday = Instant.parse("2023-09-15T00:00:00Z").toEpochMilli()
    private val observe = mockk<ObserveViolationsUseCase>()

    @Test
    fun `maps UI filters to a domain filter`() {
        every { observe(any()) } returns flowOf(emptyList())
        val vm = HistoryViewModel(observe, time)

        assertEquals(ViolationFilter(), vm.toDomainFilter(HistoryFilterState()))
        assertEquals(
            ViolationFilter(plateQuery = "TN09", status = ViolationStatus.PAID, fromMillis = startOfToday),
            vm.toDomainFilter(HistoryFilterState(query = " TN09 ", status = ViolationStatus.PAID, range = DateRange.TODAY)),
        )
        assertEquals(
            startOfToday - 6 * 86_400_000L,
            vm.toDomainFilter(HistoryFilterState(range = DateRange.WEEK)).fromMillis,
        )
        assertNull(vm.toDomainFilter(HistoryFilterState(query = "   ")).plateQuery)
    }

    @Test
    fun `emits results with totals for the current filter`() = runTest {
        val all = listOf(Fixtures.violation("1"), Fixtures.violation("2", plate = "KA01MJ2023"))
        every { observe(ViolationFilter()) } returns flowOf(all)
        every { observe(ViolationFilter(status = ViolationStatus.PAID)) } returns flowOf(emptyList())
        val vm = HistoryViewModel(observe, time)

        vm.state.test {
            var item = awaitItem()
            if (item.isLoading) item = awaitItem()
            assertEquals(2, item.violations.size)
            assertEquals(2_000L, item.totalAmount)

            vm.onStatusSelected(ViolationStatus.PAID)
            var filtered = awaitItem()
            while (filtered.violations.isNotEmpty()) filtered = awaitItem()
            assertEquals(ViolationStatus.PAID, filtered.filters.status)
            assertEquals(0L, filtered.totalAmount)
            cancelAndIgnoreRemainingEvents()
        }
        verify { observe(ViolationFilter(status = ViolationStatus.PAID)) }
    }
}
