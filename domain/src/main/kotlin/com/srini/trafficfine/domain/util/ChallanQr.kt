package com.srini.trafficfine.domain.util

import com.srini.trafficfine.domain.model.Violation
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds the payload encoded in the challan QR code. Anyone scanning it lands on a
 * verification URL carrying just enough information to look the challan up — never
 * personal details such as the offender's name or phone.
 */
object ChallanQr {
    const val VERIFY_BASE_URL = "https://echallan.example.in/verify"

    fun payload(violation: Violation, baseUrl: String = VERIFY_BASE_URL): String {
        val params = linkedMapOf(
            "c" to violation.challanNumber,
            "v" to violation.vehicleNumber,
            "a" to violation.totalFine.toString(),
            "s" to violation.status.name,
        )
        return baseUrl + "?" + params.entries.joinToString("&") { (k, v) ->
            "$k=" + URLEncoder.encode(v, StandardCharsets.UTF_8.name())
        }
    }
}
