package com.srini.trafficfine.data.sync

import com.srini.trafficfine.data.local.OfficerLogDao
import com.srini.trafficfine.data.local.PendingOp
import com.srini.trafficfine.data.local.ViolationDao
import com.srini.trafficfine.data.local.toDomain
import com.srini.trafficfine.data.local.toEntity
import com.srini.trafficfine.data.remote.EvidenceUploader
import com.srini.trafficfine.data.remote.ViolationRemoteDataSource
import com.srini.trafficfine.data.session.SessionStore
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.util.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class SyncReport(
    val pushedViolations: Int = 0,
    val pushedLogs: Int = 0,
    val pulled: Int = 0,
    val failures: Int = 0,
    val skipped: Boolean = false,
) {
    val hasFailures: Boolean get() = failures > 0
}

/**
 * Pushes locally pending changes to Firebase, then pulls remote changes into Room.
 * Each record is processed independently so one bad row never blocks the queue.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val auth: AuthRepository,
    private val violationDao: ViolationDao,
    private val logDao: OfficerLogDao,
    private val remote: ViolationRemoteDataSource,
    private val uploader: EvidenceUploader,
    private val session: SessionStore,
    private val time: TimeProvider,
) {
    suspend fun sync(): SyncReport {
        val officer = withTimeoutOrNull(AUTH_TIMEOUT_MS) { auth.currentOfficer.first() }
            ?: return SyncReport(skipped = true)

        var report = SyncReport()
        // Records may be added while we push; loop a few times to drain the queue.
        for (attemptNo in 1..MAX_PASSES) {
            val pass = pushViolations() + pushLogs()
            report += pass
            if (pass.pushedViolations + pass.pushedLogs == 0 || pass.hasFailures) break
        }
        report += pull(officer)
        return report
    }

    private suspend fun pushViolations(): SyncReport {
        var pushed = 0
        var failed = 0
        for (entity in violationDao.pending()) {
            val ok = attempt {
                when (entity.pendingOp) {
                    PendingOp.CREATE -> {
                        val photoUrl = entity.evidencePhotoUrl
                            ?: entity.localEvidencePath?.let { uploader.upload(entity.id, it) }
                        if (photoUrl != null && entity.evidencePhotoUrl == null) {
                            violationDao.setPhotoUrl(entity.id, photoUrl)
                        }
                        remote.create(entity.toDomain().copy(evidencePhotoUrl = photoUrl))
                        violationDao.markSynced(entity.id, entity.updatedAt, photoUrl)
                    }
                    PendingOp.UPDATE_PAYMENT -> {
                        val payment = entity.toDomain().payment
                            ?: error("Payment missing for ${entity.id}")
                        remote.markPaid(entity.id, payment, entity.updatedAt)
                        violationDao.markSynced(entity.id, entity.updatedAt, null)
                    }
                    null -> Unit
                }
            }
            if (ok) pushed++ else failed++
        }
        return SyncReport(pushedViolations = pushed, failures = failed)
    }

    private suspend fun pushLogs(): SyncReport {
        var pushed = 0
        var failed = 0
        for (entity in logDao.pending()) {
            val ok = attempt {
                remote.appendLog(entity.toDomain())
                logDao.markSynced(entity.id)
            }
            if (ok) pushed++ else failed++
        }
        return SyncReport(pushedLogs = pushed, failures = failed)
    }

    private suspend fun pull(officer: Officer): SyncReport {
        val startedAt = time.now()
        val rows = try {
            if (officer.isAdmin) {
                // Incremental pull with a small overlap to tolerate clock skew between devices.
                remote.fetchUpdatedSince((session.lastPullMillis - CLOCK_SKEW_MS).coerceAtLeast(0))
            } else {
                remote.fetchByOfficer(officer.uid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SyncReport(failures = 1)
        }
        violationDao.mergeRemote(rows.map { it.toEntity(pendingOp = null) })
        session.lastPullMillis = startedAt
        return SyncReport(pulled = rows.size)
    }

    private suspend inline fun attempt(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private operator fun SyncReport.plus(other: SyncReport) = SyncReport(
        pushedViolations = pushedViolations + other.pushedViolations,
        pushedLogs = pushedLogs + other.pushedLogs,
        pulled = pulled + other.pulled,
        failures = failures + other.failures,
        skipped = skipped && other.skipped,
    )

    private companion object {
        const val AUTH_TIMEOUT_MS = 10_000L
        const val CLOCK_SKEW_MS = 5 * 60_000L
        const val MAX_PASSES = 3
    }
}
