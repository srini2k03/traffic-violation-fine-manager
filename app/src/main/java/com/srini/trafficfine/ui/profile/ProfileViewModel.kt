package com.srini.trafficfine.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.repository.AuthRepository
import com.srini.trafficfine.domain.repository.SyncScheduler
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.ObserveUnsyncedCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val unsyncedCount: Int = 0,
    val isSyncing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    observeUnsyncedCount: ObserveUnsyncedCountUseCase,
    private val observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    private val syncScheduler: SyncScheduler,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ProfileUiState> =
        combine(observeUnsyncedCount(), syncScheduler.isSyncing, message) { unsynced, syncing, msg ->
            ProfileUiState(unsynced, syncing, msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun syncNow() {
        syncScheduler.requestSync()
        message.value = "Sync requested — it runs as soon as the device is online"
    }

    fun sendPasswordReset() {
        viewModelScope.launch {
            val email = observeCurrentOfficer().filterNotNull().first().email
            message.value = authRepository.sendPasswordReset(email).fold(
                onSuccess = { "Password reset link sent to $email" },
                onFailure = { "Could not send the reset email" },
            )
        }
    }

    fun onMessageShown() = message.update { null }
}
