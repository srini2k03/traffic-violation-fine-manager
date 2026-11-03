package com.srini.trafficfine.domain.repository

import com.srini.trafficfine.domain.model.GeoPoint
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationFilter
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the signed-in officer (with role resolved from the custom claim) or `null`. */
    val currentOfficer: Flow<Officer?>

    suspend fun signIn(email: String, password: String): Result<Officer>

    suspend fun signOut()

    suspend fun sendPasswordReset(email: String): Result<Unit>
}

/**
 * Offline-first store of violations. Implementations persist locally first and
 * mirror to the remote backend asynchronously (see [SyncScheduler]).
 */
interface ViolationRepository {
    fun observeViolations(filter: ViolationFilter = ViolationFilter()): Flow<List<Violation>>

    fun observeViolation(id: String): Flow<Violation?>

    suspend fun getViolation(id: String): Violation?

    suspend fun saveNew(violation: Violation)

    suspend fun markPaid(id: String, payment: Payment, updatedAt: Long)

    /**
     * Number of earlier offences per violation code recorded against [vehicleNumber]
     * since [sinceMillis]. Implementations should consult the remote store when online
     * and fall back to the local cache otherwise.
     */
    suspend fun priorOffenceCounts(vehicleNumber: String, sinceMillis: Long): Map<String, Int>
}

interface OfficerLogRepository {
    suspend fun record(log: OfficerLog)

    fun observeLogs(officerId: String, limit: Int = 200): Flow<List<OfficerLog>>
}

interface LocationProvider {
    /** Best-effort current fix; returns `null` if unavailable or permission denied. */
    suspend fun currentLocation(): GeoPoint?
}

interface SyncScheduler {
    /** Request an expedited one-off sync (no-op if one is already queued). */
    fun requestSync()

    /** Observe whether a sync is currently running. */
    val isSyncing: Flow<Boolean>
}

data class ChallanDocument(val absolutePath: String, val mimeType: String = "application/pdf")

interface ChallanDocumentGenerator {
    suspend fun generate(violation: Violation): ChallanDocument
}
