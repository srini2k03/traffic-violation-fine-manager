package com.srini.trafficfine.data.repository

import com.srini.trafficfine.data.local.OfficerLogDao
import com.srini.trafficfine.data.local.PendingOp
import com.srini.trafficfine.data.local.ViolationDao
import com.srini.trafficfine.data.local.toDomain
import com.srini.trafficfine.data.local.toEntity
import com.srini.trafficfine.data.remote.ViolationRemoteDataSource
import com.srini.trafficfine.domain.fine.PriorOffenceCounter
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Payment
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationFilter
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.ViolationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the single source of truth for the UI. Writes land in Room immediately with a
 * [PendingOp] marker and are pushed to Firebase by [com.srini.trafficfine.data.sync.SyncEngine].
 */
@Singleton
class OfflineFirstViolationRepository @Inject constructor(
    private val dao: ViolationDao,
    private val logDao: OfficerLogDao,
    private val remote: ViolationRemoteDataSource,
) : ViolationRepository {

    override fun observeViolations(filter: ViolationFilter): Flow<List<Violation>> =
        dao.observe(
            plate = filter.plateQuery?.let(ViolationFilter::normalizePlateQuery)?.takeIf { it.isNotEmpty() },
            status = filter.status?.name,
            fromMillis = filter.fromMillis,
            toMillis = filter.toMillis,
            officerId = filter.officerId,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeViolation(id: String): Flow<Violation?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getViolation(id: String): Violation? = dao.getById(id)?.toDomain()

    override suspend fun saveNew(violation: Violation) {
        dao.upsert(violation.toEntity(PendingOp.CREATE))
    }

    override suspend fun markPaid(id: String, payment: Payment, updatedAt: Long) {
        val current = dao.getById(id) ?: throw DomainError.NotFound(id)
        dao.upsert(
            current.copy(
                status = ViolationStatus.PAID.name,
                paymentMode = payment.mode.name,
                paymentReference = payment.reference,
                paidAt = payment.paidAt,
                collectedBy = payment.collectedBy,
                updatedAt = updatedAt,
                pendingOp = current.pendingOp.mergeWith(PendingOp.UPDATE_PAYMENT),
            ),
        )
    }

    override suspend fun cancel(id: String, reason: String, updatedAt: Long) {
        val current = dao.getById(id) ?: throw DomainError.NotFound(id)
        dao.upsert(
            current.copy(
                status = ViolationStatus.CANCELLED.name,
                cancelReason = reason,
                updatedAt = updatedAt,
                pendingOp = current.pendingOp.mergeWith(PendingOp.CANCEL),
            ),
        )
    }

    override fun observeUnsyncedCount(): Flow<Int> =
        combine(dao.observePendingCount(), logDao.observePendingCount()) { violations, logs -> violations + logs }

    override suspend fun priorOffenceCounts(vehicleNumber: String, sinceMillis: Long): Map<String, Int> {
        val local = dao.findByPlateSince(vehicleNumber, sinceMillis).map { it.toDomain() }
        val remoteRows = withTimeoutOrNull(REMOTE_LOOKUP_TIMEOUT_MS) {
            try {
                remote.fetchByPlate(vehicleNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }.orEmpty()
        return PriorOffenceCounter.count(local + remoteRows, sinceMillis)
    }

    private companion object {
        const val REMOTE_LOOKUP_TIMEOUT_MS = 4_000L

        /** A record not yet created remotely is pushed in full, so CREATE absorbs later edits. */
        fun PendingOp?.mergeWith(next: PendingOp): PendingOp = if (this == PendingOp.CREATE) PendingOp.CREATE else next
    }
}
