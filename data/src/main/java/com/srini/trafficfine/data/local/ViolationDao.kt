package com.srini.trafficfine.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ViolationDao {

    @Query(
        """
        SELECT * FROM violations
        WHERE (:plate IS NULL OR vehicleNumber LIKE '%' || :plate || '%' OR challanNumber LIKE '%' || :plate || '%')
          AND (:status IS NULL OR status = :status)
          AND (:fromMillis IS NULL OR issuedAt >= :fromMillis)
          AND (:toMillis IS NULL OR issuedAt < :toMillis)
          AND (:officerId IS NULL OR officerId = :officerId)
        ORDER BY issuedAt DESC
        """,
    )
    abstract fun observe(
        plate: String?,
        status: String?,
        fromMillis: Long?,
        toMillis: Long?,
        officerId: String?,
    ): Flow<List<ViolationEntity>>

    @Query("SELECT * FROM violations WHERE id = :id")
    abstract fun observeById(id: String): Flow<ViolationEntity?>

    @Query("SELECT * FROM violations WHERE id = :id")
    abstract suspend fun getById(id: String): ViolationEntity?

    @Query("SELECT * FROM violations WHERE vehicleNumber = :plate AND issuedAt >= :sinceMillis")
    abstract suspend fun findByPlateSince(plate: String, sinceMillis: Long): List<ViolationEntity>

    @Query("SELECT * FROM violations WHERE pendingOp IS NOT NULL ORDER BY issuedAt")
    abstract suspend fun pending(): List<ViolationEntity>

    @Query("SELECT COUNT(*) FROM violations WHERE pendingOp IS NOT NULL")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT id FROM violations WHERE pendingOp IS NOT NULL")
    abstract suspend fun pendingIds(): List<String>

    @Upsert
    abstract suspend fun upsert(entity: ViolationEntity)

    @Upsert
    abstract suspend fun upsertAll(entities: List<ViolationEntity>)

    /**
     * Clears the pending flag only if the row was not modified again while the
     * upload was in flight (optimistic concurrency on [ViolationEntity.updatedAt]).
     */
    @Query(
        """
        UPDATE violations SET pendingOp = NULL, evidencePhotoUrl = COALESCE(:photoUrl, evidencePhotoUrl)
        WHERE id = :id AND updatedAt = :expectedUpdatedAt
        """,
    )
    abstract suspend fun markSynced(id: String, expectedUpdatedAt: Long, photoUrl: String?): Int

    @Query("DELETE FROM violations WHERE officerId LIKE :prefix || '%'")
    abstract suspend fun deleteByOfficerPrefix(prefix: String): Int

    @Query("UPDATE violations SET evidencePhotoUrl = :photoUrl WHERE id = :id")
    abstract suspend fun setPhotoUrl(id: String, photoUrl: String)

    /** Merge remote rows without clobbering local edits that have not been pushed yet. */
    @Transaction
    open suspend fun mergeRemote(remote: List<ViolationEntity>) {
        val pending = pendingIds().toSet()
        upsertAll(remote.filterNot { it.id in pending })
    }
}
