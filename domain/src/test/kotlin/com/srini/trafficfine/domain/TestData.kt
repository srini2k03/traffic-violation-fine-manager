package com.srini.trafficfine.domain

import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationStatus

object TestData {
    val officer = Officer(
        uid = "officer-1",
        name = "Insp. Priya Raman",
        email = "priya@tnpolice.gov.in",
        badgeNumber = "TNP-4821",
        station = "Adyar Traffic",
        role = Role.OFFICER,
    )

    val admin = officer.copy(uid = "admin-1", role = Role.ADMIN)

    fun payment(paidAt: Long) = Payment(PaymentMode.UPI, "UPI123456", paidAt, officer.uid)

    fun violation(
        id: String,
        issuedAt: Long,
        codes: List<String> = listOf("NO_HELMET"),
        plate: String = "TN09AB1234",
        status: ViolationStatus = ViolationStatus.PENDING,
        syncState: SyncState = SyncState.PENDING,
    ) = Violation(
        id = id,
        challanNumber = "EC-TN-20230915-4821-ABCDEF",
        vehicleNumber = plate,
        offenderName = null,
        offenderPhone = null,
        fine = FineCalculator(RepeatOffenderPolicy.DEFAULT).calculate(codes.map(ViolationCatalogue::require)),
        location = null,
        evidencePhotoUrl = null,
        localEvidencePath = null,
        notes = "",
        officerId = officer.uid,
        officerName = officer.name,
        officerBadge = officer.badgeNumber,
        issuedAt = issuedAt,
        status = status,
        payment = null,
        syncState = syncState,
    )
}
