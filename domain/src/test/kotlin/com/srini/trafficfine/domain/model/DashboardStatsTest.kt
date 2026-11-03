package com.srini.trafficfine.domain.model

import com.srini.trafficfine.domain.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardStatsTest {

    private val dayStart = 1_000_000L
    private val dayEnd = dayStart + 86_400_000L

    @Test
    fun `aggregates today's issued, collected and pending amounts`() {
        val violations = listOf(
            TestData.violation(id = "1", issuedAt = dayStart + 10, codes = listOf("NO_HELMET")),
            TestData.violation(id = "2", issuedAt = dayStart + 20, codes = listOf("NO_HELMET", "NO_LICENCE"))
                .let { it.copy(status = ViolationStatus.PAID, payment = TestData.payment(paidAt = dayStart + 30)) },
            TestData.violation(id = "3", issuedAt = dayStart - 10, codes = listOf("DRUNK_DRIVING"), syncState = SyncState.SYNCED),
        )

        val stats = DashboardStats.from(violations, dayStart, dayEnd)

        assertEquals(2, stats.issuedToday)
        assertEquals(1, stats.paidToday)
        assertEquals(1_000L + 6_000L, stats.amountIssuedToday)
        assertEquals(6_000L, stats.amountCollectedToday)
        assertEquals(2, stats.pendingCount)
        assertEquals(11_000L, stats.pendingAmount)
        assertEquals(2, stats.unsyncedCount)
        assertEquals(listOf("NO_HELMET" to 2, "NO_LICENCE" to 1), stats.topViolations)
    }

    @Test
    fun `filter matches plate, status and date range`() {
        val v = TestData.violation(id = "1", issuedAt = 500, plate = "TN09AB1234")
        assertEquals(true, ViolationFilter(plateQuery = "tn 09").matches(v))
        assertEquals(false, ViolationFilter(plateQuery = "KA").matches(v))
        assertEquals(false, ViolationFilter(status = ViolationStatus.PAID).matches(v))
        assertEquals(true, ViolationFilter(fromMillis = 500, toMillis = 501).matches(v))
        assertEquals(false, ViolationFilter(fromMillis = 501).matches(v))
        assertEquals(false, ViolationFilter(toMillis = 500).matches(v))
    }

    @Test
    fun `filter matches a challan number typed with its hyphens`() {
        val v = TestData.violation(id = "1", issuedAt = 500, plate = "TN09AB1234")
        assertEquals(true, ViolationFilter(plateQuery = "EC-TN-20230915-4821-ABCDEF").matches(v))
        assertEquals(true, ViolationFilter(plateQuery = "4821-abcdef").matches(v))
        assertEquals(true, ViolationFilter(plateQuery = "ABCDEF").matches(v))
        assertEquals(false, ViolationFilter(plateQuery = "EC-TN-20230915-4821-ZZZZZZ").matches(v))
    }
}
