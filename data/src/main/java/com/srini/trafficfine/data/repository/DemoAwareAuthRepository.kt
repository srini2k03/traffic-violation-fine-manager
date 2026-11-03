package com.srini.trafficfine.data.repository

import com.srini.trafficfine.data.demo.DemoSessionStore
import com.srini.trafficfine.data.demo.RoomDemoModeRepository
import com.srini.trafficfine.data.remote.FirebaseAvailability
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.repository.AuthRepository
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [AuthRepository] the app uses. A demo session (if any) takes precedence; otherwise calls
 * go to Firebase, which is only constructed when the build actually has a Firebase config, so a
 * build without `google-services.json` starts on the login screen instead of crashing.
 */
@Singleton
class DemoAwareAuthRepository @Inject constructor(
    private val firebase: Lazy<FirebaseAuthRepository>,
    private val availability: FirebaseAvailability,
    private val demoSession: DemoSessionStore,
    private val demo: Lazy<RoomDemoModeRepository>,
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentOfficer: Flow<Officer?> = demoSession.officer.flatMapLatest { demoOfficer ->
        when {
            demoOfficer != null -> flowOf(demoOfficer)
            availability.isConfigured -> firebase.get().currentOfficer
            else -> flowOf(null)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Officer> =
        if (availability.isConfigured) firebase.get().signIn(email, password)
        else Result.failure(DomainError.BackendNotConfigured)

    override suspend fun signOut() {
        if (demoSession.isActive) {
            demo.get().end()
        } else if (availability.isConfigured) {
            firebase.get().signOut()
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = when {
        demoSession.isActive -> Result.failure(DomainError.InvalidState("Password reset is not available in the demo"))
        availability.isConfigured -> firebase.get().sendPasswordReset(email)
        else -> Result.failure(DomainError.BackendNotConfigured)
    }
}
