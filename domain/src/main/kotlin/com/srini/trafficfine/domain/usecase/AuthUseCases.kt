package com.srini.trafficfine.domain.usecase

import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.LogAction
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.validation.CredentialsValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ObserveCurrentOfficerUseCase @Inject constructor(
    private val auth: AuthRepository,
) {
    operator fun invoke(): Flow<Officer?> = auth.currentOfficer
}

class SignInUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val recordActivity: RecordActivityUseCase,
) {
    suspend operator fun invoke(email: String, password: String): Result<Officer> {
        val trimmed = email.trim()
        if (!CredentialsValidator.isValidEmail(trimmed)) return Result.failure(DomainError.InvalidEmail)
        if (!CredentialsValidator.isValidPassword(password)) return Result.failure(DomainError.WeakPassword)
        return auth.signIn(trimmed, password).onSuccess { officer ->
            recordActivity(officer.uid, LogAction.SIGN_IN, "Signed in as ${officer.role.name.lowercase()}")
        }
    }
}

class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val recordActivity: RecordActivityUseCase,
) {
    suspend operator fun invoke() {
        auth.currentOfficer.first()?.let { recordActivity(it.uid, LogAction.SIGN_OUT, "Signed out") }
        auth.signOut()
    }
}
