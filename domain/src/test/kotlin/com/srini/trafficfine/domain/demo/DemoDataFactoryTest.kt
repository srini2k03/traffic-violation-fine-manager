package com.srini.trafficfine.domain.demo

import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.model.DashboardStats
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.util.ChallanNumberGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DemoDataFactoryTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val noon = Instant.parse("2026-11-03T06:30:00Z") // 12:00 IST
    private val time = TimeProvider(Clock.fixed(noon, zone))
    private val calculator = FineCalculator(RepeatOffenderPolicy.DEFAULT)
    private val validator = VehicleNumberValidator()
    private val seed = DemoDataFactory(calculator, validator, time).create()

    @Test
    fun `same clock produces identical data`() {
        assertEquals(seed, DemoDataFactory(calculator, validator, time).create())
    }

    @Test
    fun `covers a month with activity today`() {
        val v = seed.violations
        assertTrue("expected a realistic volume, got ${v.size}", v.size in 40..140)
        val oldest = time.dayBounds(noon.atZone(zone).toLocalDate().minusDays(DemoDataFactory.DAYS - 1L)).first
        assertTrue(v.all { it.issuedAt in oldest until noon.toEpochMilli() })
        val today = time.dayBounds()
        val stats = DashboardStats.from(v, today.first, today.last + 1)
        assertEquals(DemoDataFactory.TODAY_COUNT, stats.issuedToday)
        assertEquals(v.sortedByDescending { it.issuedAt }, v)
    }

    @Test
    fun `every record belongs to a demo officer and is marked synced`() {
        assertTrue(seed.violations.all { DemoData.isDemoUid(it.id) && DemoData.isDemoUid(it.officerId) })
        assertTrue(seed.violations.all { it.syncState == SyncState.SYNCED })
        assertTrue(seed.logs.all { DemoData.isDemoUid(it.id) && DemoData.isDemoUid(it.officerId) })
        assertEquals(seed.violations.size, seed.violations.map { it.id }.toSet().size)
        assertEquals(seed.logs.size, seed.logs.map { it.id }.toSet().size)
    }

    @Test
    fun `plates and challan numbers are valid and unique`() {
        seed.violations.forEach { v ->
            val result = validator.validate(v.vehicleNumber)
            assertTrue("${v.vehicleNumber} invalid", result is VehicleNumberValidator.Result.Valid)
            assertEquals(v.vehicleNumber, (result as VehicleNumberValidator.Result.Valid).normalized)
            assertTrue(v.challanNumber, ChallanNumberGenerator.PATTERN.matches(v.challanNumber))
        }
        assertEquals(seed.violations.size, seed.violations.map { it.challanNumber }.toSet().size)
        DemoDataFactory.PLATES.forEach { assertTrue(it, validator.isValid(it)) }
    }

    @Test
    fun `status details are consistent`() {
        val byStatus = seed.violations.groupBy { it.status }
        assertTrue(ViolationStatus.entries.all { byStatus[it].orEmpty().isNotEmpty() })
        seed.violations.forEach { v ->
            when (v.status) {
                ViolationStatus.PAID -> {
                    val p = requireNotNull(v.payment)
                    assertTrue(p.paidAt > v.issuedAt && p.paidAt < noon.toEpochMilli())
                    assertEquals(p.paidAt, v.updatedAt)
                    assertTrue(p.reference.length >= 6)
                }
                ViolationStatus.PENDING -> assertNull(v.payment)
                ViolationStatus.CANCELLED -> {
                    assertNull(v.payment)
                    assertTrue(!v.cancelReason.isNullOrBlank())
                }
            }
            assertNotNull(v.location?.address)
        }
    }

    @Test
    fun `fines match the calculator including repeat offender escalation`() {
        seed.violations.forEach { v ->
            val priors = v.fine.lines.associate { it.code to it.priorOffences }
            val expected = calculator.calculate(v.typeCodes.map(ViolationCatalogue::require), priors)
            assertEquals(expected.total, v.totalFine)
        }
        assertTrue("demo should show repeat offenders", seed.violations.any { it.fine.isRepeatOffence })
    }

    @Test
    fun `activity log mirrors issued and paid challans`() {
        val issued = seed.logs.count { it.action.name == "VIOLATION_ISSUED" }
        val paid = seed.logs.count { it.action.name == "FINE_PAID" }
        assertEquals(seed.violations.size, issued)
        assertEquals(seed.violations.count { it.status == ViolationStatus.PAID }, paid)
        assertTrue(seed.logs.any { it.officerId == DemoData.OFFICER.uid && it.action.name == "SIGN_IN" })
    }

    @Test
    fun `early morning clock still yields valid data`() {
        val early = TimeProvider(Clock.fixed(Instant.parse("2026-11-02T19:00:00Z"), zone)) // 00:30 IST
        val data = DemoDataFactory(calculator, validator, early).create()
        assertTrue(data.violations.isNotEmpty())
        assertTrue(data.violations.all { it.issuedAt < early.now() })
    }

    @Test
    fun `demo identities`() {
        assertTrue(DemoData.officers.all { DemoData.isDemoUid(it.uid) })
        assertEquals(DemoData.ADMIN, DemoData.officerByUid("demo-admin"))
        assertNull(DemoData.officerByUid("real-uid"))
        assertTrue(!DemoData.isDemoUid(null) && !DemoData.isDemoUid("officer-1"))
    }
}
