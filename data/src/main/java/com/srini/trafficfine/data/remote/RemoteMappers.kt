package com.srini.trafficfine.data.remote

import com.srini.trafficfine.data.local.enumValueOrDefault
import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.model.FineLine
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationStatus

/*
 * Hand-written mapping between domain models and the Realtime Database JSON tree.
 * Using plain maps (instead of Firebase's reflection-based POJO mapper) keeps the
 * schema explicit, survives R8 without keep rules and is unit-testable on the JVM.
 *
 * The device-local evidence path is never uploaded; only the Storage download URL is.
 */

fun Violation.toRemoteMap(): Map<String, Any?> = mapOf(
    "challanNumber" to challanNumber,
    "vehicleNumber" to vehicleNumber,
    "offenderName" to offenderName,
    "offenderPhone" to offenderPhone,
    "fine" to mapOf(
        "total" to fine.total,
        "lines" to fine.lines.associate { line ->
            line.code to mapOf(
                "title" to line.title,
                "section" to line.section,
                "baseAmount" to line.baseAmount,
                "priorOffences" to line.priorOffences,
                "multiplierPercent" to line.multiplierPercent,
                "amount" to line.amount,
            )
        },
    ),
    "location" to location?.let {
        mapOf(
            "lat" to it.latitude,
            "lng" to it.longitude,
            "accuracy" to it.accuracyMeters?.toDouble(),
            "address" to it.address,
        )
    },
    "evidencePhotoUrl" to evidencePhotoUrl,
    "notes" to notes,
    "officerId" to officerId,
    "officerName" to officerName,
    "officerBadge" to officerBadge,
    "issuedAt" to issuedAt,
    "status" to status.name,
    "payment" to payment?.toRemoteMap(),
    "cancelReason" to cancelReason,
    "updatedAt" to updatedAt,
)

fun Payment.toRemoteMap(): Map<String, Any?> = mapOf(
    "mode" to mode.name,
    "reference" to reference,
    "paidAt" to paidAt,
    "collectedBy" to collectedBy,
)

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

private fun Any?.asLong(): Long? = (this as? Number)?.toLong()

private fun Any?.asDouble(): Double? = (this as? Number)?.toDouble()

/** Returns `null` for malformed nodes instead of crashing the whole sync. */
fun Map<String, Any?>.toViolation(id: String): Violation? {
    val challan = this["challanNumber"] as? String ?: return null
    val plate = this["vehicleNumber"] as? String ?: return null
    val officerId = this["officerId"] as? String ?: return null
    val issuedAt = this["issuedAt"].asLong() ?: return null

    val lines = this["fine"].asMap()?.get("lines").asMap().orEmpty().mapNotNull { (code, value) ->
        val line = value.asMap() ?: return@mapNotNull null
        FineLine(
            code = code,
            title = line["title"] as? String ?: code,
            section = line["section"] as? String ?: "",
            baseAmount = line["baseAmount"].asLong() ?: 0,
            priorOffences = line["priorOffences"].asLong()?.toInt() ?: 0,
            multiplierPercent = line["multiplierPercent"].asLong()?.toInt() ?: 100,
            amount = line["amount"].asLong() ?: 0,
        )
    }.sortedWith(compareByDescending<FineLine> { it.baseAmount }.thenBy { it.code })

    val location = this["location"].asMap()?.let { loc ->
        val lat = loc["lat"].asDouble()
        val lng = loc["lng"].asDouble()
        if (lat != null && lng != null) {
            GeoPoint(lat, lng, loc["accuracy"].asDouble()?.toFloat(), loc["address"] as? String)
        } else {
            null
        }
    }

    val payment = this["payment"].asMap()?.let { p ->
        val paidAt = p["paidAt"].asLong() ?: return@let null
        Payment(
            mode = enumValueOrDefault(p["mode"] as? String, PaymentMode.CASH),
            reference = p["reference"] as? String ?: "",
            paidAt = paidAt,
            collectedBy = p["collectedBy"] as? String ?: "",
        )
    }

    return Violation(
        id = id,
        challanNumber = challan,
        vehicleNumber = plate,
        offenderName = this["offenderName"] as? String,
        offenderPhone = this["offenderPhone"] as? String,
        fine = FineBreakdown(lines),
        location = location,
        evidencePhotoUrl = this["evidencePhotoUrl"] as? String,
        localEvidencePath = null,
        notes = this["notes"] as? String ?: "",
        officerId = officerId,
        officerName = this["officerName"] as? String ?: "",
        officerBadge = this["officerBadge"] as? String ?: "",
        issuedAt = issuedAt,
        status = enumValueOrDefault(this["status"] as? String, ViolationStatus.PENDING),
        payment = payment,
        cancelReason = this["cancelReason"] as? String,
        syncState = SyncState.SYNCED,
        updatedAt = this["updatedAt"].asLong() ?: issuedAt,
    )
}

fun OfficerLog.toRemoteMap(): Map<String, Any?> = mapOf(
    "action" to action.name,
    "details" to details,
    "referenceId" to referenceId,
    "timestamp" to timestamp,
)
