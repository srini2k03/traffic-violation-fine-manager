package com.srini.trafficfine.data.demo

import com.srini.trafficfine.data.local.OfficerLogDao
import com.srini.trafficfine.data.local.ViolationDao
import com.srini.trafficfine.data.local.toEntity
import com.srini.trafficfine.data.remote.FirebaseAvailability
import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.demo.DemoModeConfig
import com.srini.trafficfine.domain.demo.DemoSeed
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.repository.DemoModeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes demo records into the regular Room database (so every screen works unchanged) and
 * removes them again when the demo ends. Demo rows are recognisable by their `demo-` officer
 * uid; [com.srini.trafficfine.data.repository.OfflineFirstViolationRepository] keeps them
 * apart from real records and the sync engine never uploads them.
 */
@Singleton
class RoomDemoModeRepository @Inject constructor(
    private val config: DemoModeConfig,
    private val firebase: FirebaseAvailability,
    private val violationDao: ViolationDao,
    private val logDao: OfficerLogDao,
    private val session: DemoSessionStore,
) : DemoModeRepository {

    override val isDemoAvailable: Boolean get() = config.enabled

    override val isBackendConfigured: Boolean get() = firebase.isConfigured

    override suspend fun start(officer: Officer, seed: DemoSeed) {
        check(config.enabled) { "Demo mode is disabled in this build" }
        clearRecords()
        violationDao.upsertAll(seed.violations.map { it.toEntity(pendingOp = null) })
        logDao.insertAll(seed.logs.map { it.toEntity() })
        session.activate(officer)
    }

    /** Ends the demo session and deletes everything it created. */
    suspend fun end() {
        session.deactivate()
        clearRecords()
    }

    private suspend fun clearRecords() {
        violationDao.deleteByOfficerPrefix(DemoData.UID_PREFIX)
        logDao.deleteByOfficerPrefix(DemoData.UID_PREFIX)
    }
}
