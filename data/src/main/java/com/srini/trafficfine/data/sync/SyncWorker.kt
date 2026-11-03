package com.srini.trafficfine.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val report = engine.sync()
        val output = workDataOf(
            KEY_PUSHED to report.pushedViolations + report.pushedLogs,
            KEY_PULLED to report.pulled,
            KEY_FAILURES to report.failures,
        )
        return when {
            report.skipped -> Result.success(output)
            report.hasFailures && runAttemptCount < MAX_RETRIES -> Result.retry()
            report.hasFailures -> Result.failure(output)
            else -> Result.success(output)
        }
    }

    companion object {
        const val KEY_PUSHED = "pushed"
        const val KEY_PULLED = "pulled"
        const val KEY_FAILURES = "failures"
        private const val MAX_RETRIES = 5
    }
}
