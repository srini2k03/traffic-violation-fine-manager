package com.srini.trafficfine.ui

import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.ui.activitylog.ActivityLogViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ActivityLogViewModelTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun log(id: String, iso: String) =
        OfficerLog(id, "officer-1", LogAction.VIOLATION_ISSUED, "details", null, Instant.parse(iso).toEpochMilli())

    @Test
    fun `groups logs by local day, newest first`() {
        val logs = listOf(
            log("a", "2023-09-14T10:00:00Z"),
            log("b", "2023-09-15T19:00:00Z"), // 16 Sep 00:30 IST
            log("c", "2023-09-15T05:00:00Z"),
            log("d", "2023-09-15T06:00:00Z"),
        )

        val groups = ActivityLogViewModel.groupByDay(logs, zone)

        assertEquals(
            listOf(LocalDate.of(2023, 9, 16), LocalDate.of(2023, 9, 15), LocalDate.of(2023, 9, 14)),
            groups.map { it.first },
        )
        assertEquals(listOf("d", "c"), groups[1].second.map { it.id })
    }
}
