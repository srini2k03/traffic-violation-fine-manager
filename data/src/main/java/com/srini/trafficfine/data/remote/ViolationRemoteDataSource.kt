package com.srini.trafficfine.data.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.Violation
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViolationRemoteDataSource @Inject constructor(
    private val database: FirebaseDatabase,
) {
    private val violations get() = database.getReference(FirebasePaths.VIOLATIONS)

    /** Creates the record; security rules only allow this when the node does not exist yet. */
    suspend fun create(violation: Violation) {
        violations.child(violation.id).setValue(violation.toRemoteMap()).await()
    }

    /**
     * Multi-path update touching only the fields an officer is allowed to change
     * after creation (status, payment, updatedAt).
     */
    suspend fun markPaid(id: String, payment: Payment, updatedAt: Long) {
        violations.child(id).updateChildren(
            mapOf(
                "status" to "PAID",
                "payment" to payment.toRemoteMap(),
                "updatedAt" to updatedAt,
            ),
        ).await()
    }

    suspend fun fetchByPlate(plate: String): List<Violation> =
        violations.orderByChild("vehicleNumber").equalTo(plate).get().await().toViolations()

    suspend fun fetchByOfficer(officerId: String): List<Violation> =
        violations.orderByChild("officerId").equalTo(officerId).get().await().toViolations()

    suspend fun fetchUpdatedSince(sinceMillis: Long): List<Violation> =
        violations.orderByChild("updatedAt").startAt(sinceMillis.toDouble()).get().await().toViolations()

    suspend fun appendLog(log: OfficerLog) {
        database.getReference(FirebasePaths.OFFICER_LOGS)
            .child(log.officerId)
            .child(log.id)
            .setValue(log.toRemoteMap())
            .await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun DataSnapshot.toViolations(): List<Violation> = children.mapNotNull { child ->
        val key = child.key ?: return@mapNotNull null
        (child.value as? Map<String, Any?>)?.toViolation(key)
    }
}
