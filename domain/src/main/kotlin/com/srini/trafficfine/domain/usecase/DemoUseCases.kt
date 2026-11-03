package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.demo.DemoDataFactory
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.DemoModeRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Seeds sample data and signs in locally as the demo officer or admin. */
class StartDemoUseCase @Inject constructor(
    private val demo: DemoModeRepository,
    private val factory: DemoDataFactory,
) {
    suspend operator fun invoke(role: Role): Result<Officer> {
        if (!demo.isDemoAvailable) return Result.failure(DomainError.DemoUnavailable)
        val officer = DemoData.officerFor(role)
        return try {
            demo.start(officer, factory.create())
            Result.success(officer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
