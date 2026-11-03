package com.srini.trafficfine.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Pending remote operation for a locally modified row; `null` means fully synced. */
enum class PendingOp { CREATE, UPDATE_PAYMENT, CANCEL }

@Entity(
    tableName = "violations",
    indices = [
        Index("vehicleNumber"),
        Index("issuedAt"),
        Index("officerId"),
        Index("pendingOp"),
        Index(value = ["challanNumber"], unique = true),
    ],
)
data class ViolationEntity(
    @PrimaryKey val id: String,
    val challanNumber: String,
    val vehicleNumber: String,
    val offenderName: String?,
    val offenderPhone: String?,
    /** JSON-encoded list of fine lines (see [FineLineJson]). */
    val fineLinesJson: String,
    val totalFine: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val address: String?,
    val evidencePhotoUrl: String?,
    val localEvidencePath: String?,
    val notes: String,
    val officerId: String,
    val officerName: String,
    val officerBadge: String,
    val issuedAt: Long,
    val status: String,
    val paymentMode: String?,
    val paymentReference: String?,
    val paidAt: Long?,
    val collectedBy: String?,
    val cancelReason: String?,
    val updatedAt: Long,
    val pendingOp: PendingOp?,
)

@Entity(
    tableName = "officer_logs",
    indices = [Index("officerId"), Index("timestamp"), Index("pending")],
)
data class OfficerLogEntity(
    @PrimaryKey val id: String,
    val officerId: String,
    val action: String,
    val details: String,
    val referenceId: String?,
    val timestamp: Long,
    val pending: Boolean,
)
