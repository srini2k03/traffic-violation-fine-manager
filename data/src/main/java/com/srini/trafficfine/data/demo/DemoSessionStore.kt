package com.srini.trafficfine.data.demo

import android.content.Context
import androidx.core.content.edit
import com.srini.trafficfine.domain.demo.DemoData
import com.srini.trafficfine.domain.model.Officer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the active demo identity across restarts; `null` means no demo session. */
@Singleton
class DemoSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("demo_session", Context.MODE_PRIVATE)
    private val _officer = MutableStateFlow(DemoData.officerByUid(prefs.getString(KEY_UID, null)))

    val officer: StateFlow<Officer?> = _officer.asStateFlow()

    val isActive: Boolean get() = _officer.value != null

    fun activate(officer: Officer) {
        require(DemoData.isDemoUid(officer.uid)) { "Not a demo identity: ${officer.uid}" }
        prefs.edit { putString(KEY_UID, officer.uid) }
        _officer.value = officer
    }

    fun deactivate() {
        prefs.edit { remove(KEY_UID) }
        _officer.value = null
    }

    private companion object {
        const val KEY_UID = "uid"
    }
}
