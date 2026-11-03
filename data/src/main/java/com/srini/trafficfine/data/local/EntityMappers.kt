package com.srini.trafficfine.data.local

import com.srini.trafficfine.domain.model.FineBreakdown
import com.srini.trafficfine.domain.model.FineLine
import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.PaymentMode
import com.srini.trafficfine.domain.model.SyncState
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class FineLineJson(
    val code: String,
    val title: String,
    val section: String,
    val baseAmount: Long,
    val priorOffences: Int,
    val multiplierPercent: Int,
    val amount: Long,
)

private val json = Json { ignoreUnknownKeys = true }

internal fun List<FineLine>.encode(): String = json.encodeToString(
    map { FineLineJson(it.code, it.title, it.section, it.baseAmount, it.priorOffences, it.multiplierPercent, it.amount) },
)

internal fun decodeFineLines(raw: String): List<FineLine> =
    runCatching { json.decodeFromString<List<FineLineJson>>(raw) }
        .getOrDefault(emptyList())
        .map { FineLine(it.code, it.title, it.section, it.baseAmount, it.priorOffences, it.multiplierPercent, it.amount) }

fun Violation.toEntity(pendingOp: PendingOp?): ViolationEntity = ViolationEntity(
    id = id,
    challanNumber = challanNumber,
    vehicleNumber = vehicleNumber,
    offenderName = offenderName,
    offenderPhone = offenderPhone,
    fineLinesJson = fine.lines.encode(),
    totalFine = fine.total,
    latitude = location?.latitude,
    longitude = location?.longitude,
    accuracyMeters = location?.accuracyMeters,
    address = location?.address,
    evidencePhotoUrl = evidencePhotoUrl,
    localEvidencePath = localEvidencePath,
    notes = notes,
    officerId = officerId,
    officerName = officerName,
    officerBadge = officerBadge,
    issuedAt = issuedAt,
    status = status.name,
    paymentMode = payment?.mode?.name,
    paymentReference = payment?.reference,
    paidAt = payment?.paidAt,
    collectedBy = payment?.collectedBy,
    updatedAt = updatedAt,
    pendingOp = pendingOp,
)

fun ViolationEntity.toDomain(): Violation = Violation(
    id = id,
    challanNumber = challanNumber,
    vehicleNumber = vehicleNumber,
    offenderName = offenderName,
    offenderPhone = offenderPhone,
    fine = FineBreakdown(decodeFineLines(fineLinesJson)),
    location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude, accuracyMeters, address) else null,
    evidencePhotoUrl = evidencePhotoUrl,
    localEvidencePath = localEvidencePath,
    notes = notes,
    officerId = officerId,
    officerName = officerName,
    officerBadge = officerBadge,
    issuedAt = issuedAt,
    status = enumValueOrDefault(status, ViolationStatus.PENDING),
    payment = if (paymentMode != null && paidAt != null) {
        Payment(
            mode = enumValueOrDefault(paymentMode, PaymentMode.CASH),
            reference = paymentReference.orEmpty(),
            paidAt = paidAt,
            collectedBy = collectedBy.orEmpty(),
        )
    } else {
        null
    },
    syncState = if (pendingOp == null) SyncState.SYNCED else SyncState.PENDING,
    updatedAt = updatedAt,
)

fun OfficerLog.toEntity(): OfficerLogEntity = OfficerLogEntity(
    id = id,
    officerId = officerId,
    action = action.name,
    details = details,
    referenceId = referenceId,
    timestamp = timestamp,
    pending = syncState == SyncState.PENDING,
)

fun OfficerLogEntity.toDomain(): OfficerLog = OfficerLog(
    id = id,
    officerId = officerId,
    action = enumValueOrDefault(action, LogAction.SYNC_COMPLETED),
    details = details,
    referenceId = referenceId,
    timestamp = timestamp,
    syncState = if (pending) SyncState.PENDING else SyncState.SYNCED,
)

internal inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
