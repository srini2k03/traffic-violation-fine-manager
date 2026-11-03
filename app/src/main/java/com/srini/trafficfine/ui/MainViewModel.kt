package com.srini.trafficfine.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.usecase.ObserveCurrentOfficerUseCase
import com.srini.trafficfine.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val officer: Officer) : SessionState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    observeCurrentOfficer: ObserveCurrentOfficerUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    val session: StateFlow<SessionState> = observeCurrentOfficer()
        .map { officer -> officer?.let(SessionState::SignedIn) ?: SessionState.SignedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    fun onSignOut() {
        viewModelScope.launch { signOut() }
    }
}
