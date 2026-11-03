package com.srini.trafficfine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ViolationEntity::class, OfficerLogEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TrafficFineDatabase : RoomDatabase() {
    abstract fun violationDao(): ViolationDao
    abstract fun officerLogDao(): OfficerLogDao

    companion object {
        const val NAME = "traffic_fine.db"
    }
}
