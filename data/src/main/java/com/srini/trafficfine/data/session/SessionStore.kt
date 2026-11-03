package com.srini.trafficfine.data.session

import android.content.Context
import androidx.core.content.edit
import com.srini.trafficfine.domain.model.Officer
import com.srini.trafficfine.domain.model.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small key-value store for session metadata: the last verified officer profile
 * (so an officer can keep working offline when the ID token cannot be refreshed)
 * and the last successful pull timestamp used for incremental sync.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CachedOfficer(
        val uid: String,
        val name: String,
        val email: String,
        val badgeNumber: String,
        val station: String,
        val role: String,
    )

    fun cachedOfficer(uid: String): Officer? {
        val raw = prefs.getString(KEY_OFFICER, null) ?: return null
        val cached = runCatching { json.decodeFromString<CachedOfficer>(raw) }.getOrNull() ?: return null
        if (cached.uid != uid) return null
        val role = runCatching { Role.valueOf(cached.role) }.getOrNull() ?: return null
        return Officer(cached.uid, cached.name, cached.email, cached.badgeNumber, cached.station, role)
    }

    fun cacheOfficer(officer: Officer) {
        val cached = CachedOfficer(
            officer.uid, officer.name, officer.email, officer.badgeNumber, officer.station, officer.role.name,
        )
        prefs.edit { putString(KEY_OFFICER, json.encodeToString(cached)) }
    }

    var lastPullMillis: Long
        get() = prefs.getLong(KEY_LAST_PULL, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_PULL, value) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_OFFICER = "officer"
        const val KEY_LAST_PULL = "last_pull"
    }
}
