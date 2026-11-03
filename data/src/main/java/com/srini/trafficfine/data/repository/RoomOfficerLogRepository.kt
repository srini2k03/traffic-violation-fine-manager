package com.srini.trafficfine.data.repository

import com.srini.trafficfine.data.local.OfficerLogDao
import com.srini.trafficfine.data.local.toDomain
import com.srini.trafficfine.data.local.toEntity
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomOfficerLogRepository @Inject constructor(
    private val dao: OfficerLogDao,
) : OfficerLogRepository {

    override suspend fun record(log: OfficerLog) = dao.insert(log.toEntity())

    override fun observeLogs(officerId: String, limit: Int): Flow<List<OfficerLog>> =
        dao.observe(officerId, limit).map { rows -> rows.map { it.toDomain() } }
}
