package com.srini.trafficfine.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.usecase.SignInUseCase
import com.srini.trafficfine.domain.validation.CredentialsValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null, info = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null, info = null) }

    /** On success the session flow in MainViewModel switches the UI to the dashboard. */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            signIn(current.email, current.password)
                .onSuccess { _state.update { it.copy(isLoading = false, password = "") } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toMessage()) } }
        }
    }

    fun sendPasswordReset() {
        val email = _state.value.email.trim()
        if (!CredentialsValidator.isValidEmail(email)) {
            _state.update { it.copy(error = DomainError.InvalidEmail.message) }
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess { _state.update { it.copy(info = "Password reset link sent to $email") } }
                .onFailure { _state.update { it.copy(error = "Could not send reset email") } }
        }
    }

    private fun Throwable.toMessage(): String = when (this) {
        is DomainError -> message ?: "Sign-in failed"
        else -> "Sign-in failed. Please try again."
    }
}
