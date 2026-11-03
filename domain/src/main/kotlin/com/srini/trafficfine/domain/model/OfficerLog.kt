package com.srini.trafficfine.domain.model

enum class LogAction {
    SIGN_IN,
    SIGN_OUT,
    VIOLATION_ISSUED,
    FINE_PAID,
    CHALLAN_SHARED,
    CHALLAN_CANCELLED,
    SYNC_COMPLETED,
}

data class OfficerLog(
    val id: String,
    val officerId: String,
    val action: LogAction,
    val details: String,
    val referenceId: String?,
    val timestamp: Long,
    val syncState: SyncState = SyncState.PENDING,
)
