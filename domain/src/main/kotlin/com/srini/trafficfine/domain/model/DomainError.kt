package com.srini.trafficfine.domain.model

import com.srini.trafficfine.domain.validation.FormError

sealed class DomainError(message: String) : Exception(message) {
    data object InvalidEmail : DomainError("Enter a valid email address")
    data object WeakPassword : DomainError("Password must be at least 8 characters")
    data object InvalidCredentials : DomainError("Incorrect email or password")
    data object NotAuthorized : DomainError("This account has no officer/admin role")
    data object NotSignedIn : DomainError("Session expired, please sign in again")
    data class Validation(val errors: Set<FormError>) : DomainError("Invalid form: $errors")
    data class NotFound(val id: String) : DomainError("Violation $id not found")
    data class InvalidState(val reason: String) : DomainError(reason)
    data class Network(val reason: String?) : DomainError(reason ?: "Network error")
}
