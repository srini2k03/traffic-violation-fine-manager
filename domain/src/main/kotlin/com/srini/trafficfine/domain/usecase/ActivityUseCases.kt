package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.OfficerLog
import com.srini.trafficfine.domain.repository.OfficerLogRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.util.IdGenerator
import com.srini.trafficfine.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RecordActivityUseCase @Inject constructor(
    private val logs: OfficerLogRepository,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val sync: SyncScheduler,
) {
    suspend operator fun invoke(
        officerId: String,
        action: LogAction,
        details: String,
        referenceId: String? = null,
    ) {
        logs.record(
            OfficerLog(
                id = ids.newId(),
                officerId = officerId,
                action = action,
                details = details,
                referenceId = referenceId,
                timestamp = time.now(),
            ),
        )
        sync.requestSync()
    }
}

class ObserveActivityLogUseCase @Inject constructor(
    private val logs: OfficerLogRepository,
) {
    operator fun invoke(officerId: String): Flow<List<OfficerLog>> = logs.observeLogs(officerId)
}
