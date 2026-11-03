package com.srini.trafficfine.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.DomainError
import com.srini.trafficfine.domain.model.Role
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.repository.DemoModeRepository
import com.srini.trafficfine.domain.usecase.SignInUseCase
import com.srini.trafficfine.domain.usecase.StartDemoUseCase
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
    /** False in builds without a Firebase config: only the demo can be used. */
    val backendConfigured: Boolean = true,
    val demoAvailable: Boolean = false,
    /** Role whose demo is being prepared, if any. */
    val startingDemo: Role? = null,
) {
    val isBusy: Boolean get() = isLoading || startingDemo != null
    val canSubmit: Boolean get() = backendConfigured && email.isNotBlank() && password.isNotEmpty() && !isBusy
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val authRepository: AuthRepository,
    private val startDemo: StartDemoUseCase,
    demoMode: DemoModeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(backendConfigured = demoMode.isBackendConfigured, demoAvailable = demoMode.isDemoAvailable),
    )
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

    /** Seeds sample data and signs in locally; MainViewModel then shows the dashboard. */
    fun tryDemo(role: Role) {
        val current = _state.value
        if (!current.demoAvailable || current.isBusy) return
        _state.update { it.copy(startingDemo = role, error = null, info = null) }
        viewModelScope.launch {
            startDemo(role)
                .onSuccess { _state.update { it.copy(startingDemo = null) } }
                .onFailure { e -> _state.update { it.copy(startingDemo = null, error = e.toMessage("Could not start the demo")) } }
        }
    }

    fun sendPasswordReset() {
        if (!_state.value.backendConfigured) {
            _state.update { it.copy(error = DomainError.BackendNotConfigured.message) }
            return
        }
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

    private fun Throwable.toMessage(fallback: String = "Sign-in failed. Please try again."): String = when (this) {
        is DomainError -> message ?: fallback
        else -> fallback
    }
}
