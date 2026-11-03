package com.srini.trafficfine.ui

import com.srini.trafficfine.domain.fine.FineCalculator
import com.srini.trafficfine.domain.fine.RepeatOffenderPolicy
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationCatalogue
import com.srini.trafficfine.domain.model.ViolationStatus

object Fixtures {
    val officer = Officer("officer-1", "Insp. Priya Raman", "priya@tnpolice.gov.in", "TNP-4821", "Adyar Traffic", Role.OFFICER)

    fun violation(id: String, plate: String = "TN09AB1234", issuedAt: Long = 0L) = Violation(
        id = id,
        challanNumber = "EC-TN-20230915-4821-$id",
        vehicleNumber = plate,
        offenderName = null,
        offenderPhone = null,
        fine = FineCalculator(RepeatOffenderPolicy.DEFAULT).calculate(listOf(ViolationCatalogue.require("NO_HELMET"))),
        location = null,
        evidencePhotoUrl = null,
        localEvidencePath = null,
        notes = "",
        officerId = officer.uid,
        officerName = officer.name,
        officerBadge = officer.badgeNumber,
        issuedAt = issuedAt,
        status = ViolationStatus.PENDING,
        payment = null,
    )
}
