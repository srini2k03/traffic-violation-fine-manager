package com.srini.trafficfine.domain.model

enum class ViolationStatus { PENDING, PAID, CANCELLED }

enum class PaymentMode { CASH, UPI, CARD, ONLINE }

/** Local-first sync bookkeeping; a record is [PENDING] until mirrored to Firebase. */
enum class SyncState { SYNCED, PENDING }

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val address: String? = null,
)

data class Payment(
    val mode: PaymentMode,
    val reference: String,
    val paidAt: Long,
    val collectedBy: String,
)

data class Violation(
    val id: String,
    val challanNumber: String,
    val vehicleNumber: String,
    val offenderName: String?,
    val offenderPhone: String?,
    val fine: FineBreakdown,
    val location: GeoPoint?,
    val evidencePhotoUrl: String?,
    val localEvidencePath: String?,
    val notes: String,
    val officerId: String,
    val officerName: String,
    val officerBadge: String,
    val issuedAt: Long,
    val status: ViolationStatus,
    val payment: Payment?,
    /** Set when an admin cancels the challan (status = CANCELLED). */
    val cancelReason: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val updatedAt: Long = issuedAt,
) {
    val typeCodes: List<String> get() = fine.lines.map { it.code }
    val totalFine: Long get() = fine.total
}

data class FineLine(
    val code: String,
    val title: String,
    val section: String,
    val baseAmount: Long,
    val priorOffences: Int,
    val multiplierPercent: Int,
    val amount: Long,
)

data class FineBreakdown(
    val lines: List<FineLine>,
) {
    val total: Long get() = lines.sumOf { it.amount }
    val isRepeatOffence: Boolean get() = lines.any { it.priorOffences > 0 }

    companion object {
        val EMPTY = FineBreakdown(emptyList())
    }
}
