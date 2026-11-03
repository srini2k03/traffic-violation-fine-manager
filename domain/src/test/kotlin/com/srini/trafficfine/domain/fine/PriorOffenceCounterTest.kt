package com.srini.trafficfine.domain.fine

import com.srini.trafficfine.domain.TestData
import com.srini.trafficfine.domain.model.ViolationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PriorOffenceCounterTest {

    @Test
    fun `de-duplicates local and remote copies and ignores cancelled or old rows`() {
        val base = TestData.violation("v1", issuedAt = 100, codes = listOf("NO_HELMET", "NO_LICENCE"))
        val rows = listOf(
            base,
            base, // same record returned by both Room and Firebase
            base.copy(id = "v2"),
            base.copy(id = "v3", status = ViolationStatus.CANCELLED),
            base.copy(id = "v4", issuedAt = 10),
        )
        assertEquals(mapOf("NO_HELMET" to 2, "NO_LICENCE" to 2), PriorOffenceCounter.count(rows, sinceMillis = 50))
    }

    @Test
    fun `no history means no priors`() {
        assertEquals(emptyMap<String, Int>(), PriorOffenceCounter.count(emptyList(), 0))
    }
}
