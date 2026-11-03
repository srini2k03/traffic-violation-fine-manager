package com.srini.trafficfine.data.repository

import com.srini.trafficfine.data.demo.DemoSessionStore
import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.repository.OfficerDirectoryRepository
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Serves the fixed demo roster during a demo session and `/officers` from Firebase otherwise. */
@Singleton
class DemoAwareOfficerDirectoryRepository @Inject constructor(
    private val firebase: Lazy<FirebaseOfficerDirectoryRepository>,
    private val demoSession: DemoSessionStore,
) : OfficerDirectoryRepository {

    private val demoRoster = MutableStateFlow(DemoData.officers)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOfficers(): Flow<List<Officer>> = demoSession.officer.flatMapLatest { demo ->
        if (demo != null) demoRoster else firebase.get().observeOfficers()
    }

    override suspend fun setActive(uid: String, active: Boolean) {
        if (demoSession.isActive) {
            demoRoster.update { list -> list.map { if (it.uid == uid) it.copy(active = active) else it } }
        } else {
            firebase.get().setActive(uid, active)
        }
    }
}
