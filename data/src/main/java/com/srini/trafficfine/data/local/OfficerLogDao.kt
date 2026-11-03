package com.srini.trafficfine.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficerLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: OfficerLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<OfficerLogEntity>)

    @Query("DELETE FROM officer_logs WHERE officerId LIKE :prefix || '%'")
    suspend fun deleteByOfficerPrefix(prefix: String): Int

    @Query("SELECT * FROM officer_logs WHERE officerId = :officerId ORDER BY timestamp DESC LIMIT :limit")
    fun observe(officerId: String, limit: Int): Flow<List<OfficerLogEntity>>

    @Query("SELECT * FROM officer_logs WHERE pending = 1 ORDER BY timestamp")
    suspend fun pending(): List<OfficerLogEntity>

    @Query("SELECT COUNT(*) FROM officer_logs WHERE pending = 1")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE officer_logs SET pending = 0 WHERE id = :id")
    suspend fun markSynced(id: String)
}
