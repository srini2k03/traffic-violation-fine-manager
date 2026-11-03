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

    @Query("SELECT * FROM officer_logs WHERE officerId = :officerId ORDER BY timestamp DESC LIMIT :limit")
    fun observe(officerId: String, limit: Int): Flow<List<OfficerLogEntity>>

    @Query("SELECT * FROM officer_logs WHERE pending = 1 ORDER BY timestamp")
    suspend fun pending(): List<OfficerLogEntity>

    @Query("UPDATE officer_logs SET pending = 0 WHERE id = :id")
    suspend fun markSynced(id: String)
}
