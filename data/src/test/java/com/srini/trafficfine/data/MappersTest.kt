package com.srini.trafficfine.data

import com.srini.trafficfine.data.local.PendingOp
import com.srini.trafficfine.data.local.toDomain
import com.srini.trafficfine.data.local.toEntity
import com.srini.trafficfine.data.remote.toRemoteMap
import com.srini.trafficfine.data.remote.toViolation
import com.srini.trafficfine.data.repository.OfflineFirstViolationRepository
import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    private val violation = Violation(
        id = "v1",
        challanNumber = "EC-TN-20230915-4821-ABCDEF",
        vehicleNumber = "TN09AB1234",
        offenderName = "Ravi",
        offenderPhone = "9876543210",
        fine = FineCalculator(RepeatOffenderPolicy.DEFAULT).calculate(
            listOf(ViolationCatalogue.require("NO_HELMET"), ViolationCatalogue.require("NO_LICENCE")),
            mapOf("NO_HELMET" to 1),
        ),
        location = GeoPoint(13.0067, 80.257, 12.5f, "Adyar, Chennai"),
        evidencePhotoUrl = "https://example.com/e.jpg",
        localEvidencePath = "/data/e.jpg",
        notes = "note",
        officerId = "officer-1",
        officerName = "Priya",
        officerBadge = "TNP-4821",
        issuedAt = 1_694_750_000_000,
        status = ViolationStatus.PAID,
        payment = Payment(PaymentMode.UPI, "UPI123456", 1_694_760_000_000, "officer-1"),
        syncState = SyncState.PENDING,
        updatedAt = 1_694_760_000_000,
    )

    @Test
    fun `entity round trip preserves every field`() {
        assertEquals(violation, violation.toEntity(PendingOp.CREATE).toDomain())
        assertEquals(SyncState.SYNCED, violation.toEntity(null).toDomain().syncState)
    }

    @Test
    fun `remote round trip preserves shared fields and drops device-local ones`() {
        // Simulate what the Realtime Database hands back: numbers come back as Long/Double.
        val parsed = violation.toRemoteMap().toViolation("v1")!!
        assertEquals(violation.copy(localEvidencePath = null, syncState = SyncState.SYNCED), parsed)
    }

    @Test
    fun `malformed remote nodes are skipped`() {
        assertNull(mapOf<String, Any?>("vehicleNumber" to "TN09AB1234").toViolation("x"))
    }

    @Test
    fun `prior offence counting de-duplicates and ignores cancelled or old rows`() {
        val base = violation.copy(status = ViolationStatus.PENDING, issuedAt = 100)
        val rows = listOf(
            base,
            base, // same record from local and remote
            base.copy(id = "v2"),
            base.copy(id = "v3", status = ViolationStatus.CANCELLED),
            base.copy(id = "v4", issuedAt = 10),
        )
        assertEquals(
            mapOf("NO_LICENCE" to 2, "NO_HELMET" to 2),
            OfflineFirstViolationRepository.countPriorOffences(rows, sinceMillis = 50),
        )
    }
}
