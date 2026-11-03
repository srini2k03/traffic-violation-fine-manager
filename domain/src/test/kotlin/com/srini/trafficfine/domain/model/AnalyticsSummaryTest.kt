package com.srini.trafficfine.domain.model

import com.srini.trafficfine.domain.TestData
import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsSummaryTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2023, 9, 15)
    private fun at(date: LocalDate, hour: Int = 10) = date.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test
    fun `aggregates the window per day, status, category and officer`() {
        val paid = TestData.payment(paidAt = at(today))
        val violations = listOf(
            TestData.violation("1", at(today), listOf("NO_HELMET")),
            TestData.violation("2", at(today), listOf("DRUNK_DRIVING", "NO_LICENCE")).copy(status = ViolationStatus.PAID, payment = paid),
            TestData.violation("3", at(today.minusDays(1)), listOf("NO_HELMET")).copy(officerId = "officer-2", officerName = "SI Arun"),
            TestData.violation("4", at(today.minusDays(1)), listOf("WRONG_PARKING"), status = ViolationStatus.CANCELLED),
            TestData.violation("5", at(today.minusDays(7)), listOf("NO_HELMET")), // outside 7-day window
        )

        val summary = AnalyticsSummary.from(violations, today, dayCount = 7, zone = zone)

        assertEquals(7, summary.days.size)
        assertEquals(today.minusDays(6), summary.days.first().date)
        assertEquals(DailyPoint(today, 2, 16_000, 15_000), summary.days.last())
        assertEquals(DailyPoint(today.minusDays(1), 1, 1_000, 0), summary.days[5])
        assertEquals(3, summary.totalIssued)
        assertEquals(17_000L, summary.totalAmount)
        assertEquals(15_000L, summary.totalCollected)
        assertEquals(15_000.0 / 17_000.0, summary.collectionRate, 1e-9)
        assertEquals(mapOf(ViolationStatus.PENDING to 2, ViolationStatus.PAID to 1, ViolationStatus.CANCELLED to 1), summary.byStatus)
        assertEquals(
            listOf(ViolationType.Category.SAFETY to 2, ViolationType.Category.DOCUMENTS to 1, ViolationType.Category.BEHAVIOUR to 1),
            summary.byCategory,
        )
        assertEquals("NO_HELMET" to 2, summary.topOffences.first())
        assertEquals(listOf("officer-1", "officer-2"), summary.officers.map { it.officerId })
        assertEquals(OfficerPerformance("officer-1", TestData.officer.name, 2, 16_000, 15_000), summary.officers.first())
    }

    @Test
    fun `repeat offender share counts challans with any escalated line`() {
        val repeat = TestData.violation("1", at(today)).copy(
            fine = FineCalculator(RepeatOffenderPolicy.DEFAULT)
                .calculate(listOf(ViolationCatalogue.require("NO_HELMET")), mapOf("NO_HELMET" to 1)),
        )
        val summary = AnalyticsSummary.from(listOf(repeat, TestData.violation("2", at(today))), today, 1, zone)
        assertEquals(0.5, summary.repeatOffenderShare, 1e-9)
    }

    @Test
    fun `empty input yields zero-filled days`() {
        val summary = AnalyticsSummary.from(emptyList(), today, 30, zone)
        assertEquals(30, summary.days.size)
        assertEquals(0.0, summary.collectionRate, 0.0)
        assertEquals(0, summary.days.sumOf { it.count })
    }
}
